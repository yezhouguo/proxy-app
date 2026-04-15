package cn.ys1231.appproxy

import android.content.Context
import android.util.Log
import com.google.gson.Gson
import java.io.File

class AppProxyConfigRepository(private val context: Context) {
    companion object {
        const val DEFAULT_INI_PATH = "/sdcard/Download/proxy-app.ini"
        const val LEGACY_INI_PATH = "/sdcard/Download/appproxy.ini"
        private const val DEFAULT_INI_CONFIG_SOURCE = "defaultIni"

        private const val PREFS_NAME = "appproxy_native_config"
        private const val KEY_PROXY_NAME = "proxy_name"
        private const val KEY_PROXY_TYPE = "proxy_type"
        private const val KEY_PROXY_HOST = "proxy_host"
        private const val KEY_PROXY_PORT = "proxy_port"
        private const val KEY_PROXY_USER = "proxy_user"
        private const val KEY_PROXY_PASS = "proxy_pass"
        private const val KEY_APP_PROXY_PACKAGE_LIST = "app_proxy_package_list"
        private const val KEY_AUTO_START = "auto_start"
        private const val KEY_VPN_CONSENT_GRANTED = "vpn_consent_granted"
        private const val KEY_VPN_RUNNING = "vpn_running"
        private const val KEY_INI_ACCESS_PROMPTED = "ini_access_prompted"
        private const val KEY_BOOT_DIAG_ATTEMPT_ID = "boot_diag_attempt_id"
        private const val KEY_BOOT_DIAG_RECEIVER_STAGE = "boot_diag_receiver_stage"
        private const val KEY_BOOT_DIAG_RECEIVER_ACTION = "boot_diag_receiver_action"
        private const val KEY_BOOT_DIAG_RECEIVER_AT_MS = "boot_diag_receiver_at_ms"
        private const val KEY_BOOT_DIAG_RECEIVER_ERROR = "boot_diag_receiver_error"
        private const val KEY_BOOT_DIAG_SERVICE_STAGE = "boot_diag_service_stage"
        private const val KEY_BOOT_DIAG_SERVICE_ACTION = "boot_diag_service_action"
        private const val KEY_BOOT_DIAG_SERVICE_AT_MS = "boot_diag_service_at_ms"
        private const val KEY_BOOT_DIAG_SERVICE_ERROR = "boot_diag_service_error"
    }

    data class BootDiagSnapshot(
        val attemptId: String,
        val receiverStage: String,
        val receiverAction: String,
        val receiverAtMs: Long,
        val receiverError: String,
        val serviceStage: String,
        val serviceAction: String,
        val serviceAtMs: Long,
        val serviceError: String,
    )

    private val TAG = "iyue->${this.javaClass.simpleName}"
    private val prefs = context.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)
    private val gson = Gson()

    fun refreshFromDefaultIniIfPossible(): Map<String, Any>? {
        val iniFile = resolveIniFile() ?: return loadCachedConfig()

        return try {
            val normalizedConfig = parseIniText(iniFile.readText())
            if (normalizedConfig != null) {
                saveNormalizedConfig(normalizedConfig)
                normalizedConfig
            } else {
                Log.w(TAG, "refreshFromDefaultIniIfPossible: ini file is invalid")
                loadCachedConfig()
            }
        } catch (e: SecurityException) {
            Log.w(TAG, "refreshFromDefaultIniIfPossible: ${e.message}")
            loadCachedConfig()
        } catch (e: Exception) {
            Log.e(TAG, "refreshFromDefaultIniIfPossible: ${e.message}")
            loadCachedConfig()
        }
    }

    fun loadCachedConfig(): Map<String, Any>? {
        val proxyHost = prefs.getString(KEY_PROXY_HOST, null)?.trim().orEmpty()
        val proxyPort = prefs.getString(KEY_PROXY_PORT, null)?.trim().orEmpty()
        if (proxyHost.isBlank() || proxyPort.isBlank()) {
            return null
        }

        val proxyType = prefs.getString(KEY_PROXY_TYPE, "socks5").orEmpty().ifBlank { "socks5" }
        val proxyName = prefs.getString(
            KEY_PROXY_NAME,
            buildDefaultProxyName(proxyType, proxyHost, proxyPort)
        ).orEmpty().ifBlank { buildDefaultProxyName(proxyType, proxyHost, proxyPort) }

        return mapOf(
            "proxyName" to proxyName,
            "proxyType" to proxyType,
            "proxyHost" to proxyHost,
            "proxyPort" to proxyPort,
            "proxyUser" to prefs.getString(KEY_PROXY_USER, "").orEmpty(),
            "proxyPass" to prefs.getString(KEY_PROXY_PASS, "").orEmpty(),
            "appProxyPackageList" to prefs.getString(KEY_APP_PROXY_PACKAGE_LIST, "[]").orEmpty().ifBlank { "[]" },
            "autoStart" to prefs.getBoolean(KEY_AUTO_START, true)
        )
    }

    fun saveProxyFromFlutter(data: Map<String, Any>): Map<String, Any>? {
        val autoStart = parseBoolean(
            data["autoStart"]?.toString() ?: data["autostart"]?.toString(),
            prefs.getBoolean(KEY_AUTO_START, true)
        )
        val normalizedConfig = normalizeProxyConfig(data, autoStart)
        if (normalizedConfig != null) {
            saveNormalizedConfig(normalizedConfig)
        }
        return normalizedConfig
    }

    fun loadDefaultIniConfigForFlutter(): Map<String, Any>? {
        val iniFile = resolveIniFile() ?: return null
        return try {
            val normalizedConfig = parseIniText(iniFile.readText()) ?: return null
            saveNormalizedConfig(normalizedConfig)
            attachIniMetadata(normalizedConfig, iniFile.absolutePath)
        } catch (e: SecurityException) {
            Log.w(TAG, "loadDefaultIniConfigForFlutter: ${e.message}")
            null
        } catch (e: Exception) {
            Log.e(TAG, "loadDefaultIniConfigForFlutter: ${e.message}")
            null
        }
    }

    fun saveDefaultIniConfigFromFlutter(data: Map<String, Any>): Map<String, Any>? {
        val autoStart = parseBoolean(
            data["autoStart"]?.toString() ?: data["autostart"]?.toString(),
            prefs.getBoolean(KEY_AUTO_START, true)
        )
        val normalizedConfig = normalizeProxyConfig(data, autoStart) ?: return null
        val iniFile = resolveIniFile() ?: File(DEFAULT_INI_PATH)
        iniFile.parentFile?.mkdirs()
        iniFile.writeText(buildIniText(normalizedConfig))
        saveNormalizedConfig(normalizedConfig)
        return attachIniMetadata(normalizedConfig, iniFile.absolutePath)
    }

    fun hasCachedConfig(): Boolean {
        return loadCachedConfig() != null
    }

    fun isAutoStartEnabled(): Boolean {
        return prefs.getBoolean(KEY_AUTO_START, true)
    }

    fun markVpnConsentGranted(granted: Boolean) {
        prefs.edit().putBoolean(KEY_VPN_CONSENT_GRANTED, granted).apply()
    }

    fun hasVpnConsentGranted(): Boolean {
        return prefs.getBoolean(KEY_VPN_CONSENT_GRANTED, false)
    }

    fun markVpnRunning(isRunning: Boolean) {
        prefs.edit().putBoolean(KEY_VPN_RUNNING, isRunning).apply()
    }

    fun isVpnRunning(): Boolean {
        return prefs.getBoolean(KEY_VPN_RUNNING, false)
    }

    fun hasPromptedForIniAccess(): Boolean {
        return prefs.getBoolean(KEY_INI_ACCESS_PROMPTED, false)
    }

    fun markPromptedForIniAccess() {
        prefs.edit().putBoolean(KEY_INI_ACCESS_PROMPTED, true).apply()
    }

    fun markBootReceiverStage(action: String, stage: String, error: String? = null): String {
        val attemptId = System.currentTimeMillis().toString()
        prefs.edit()
            .putString(KEY_BOOT_DIAG_ATTEMPT_ID, attemptId)
            .putString(KEY_BOOT_DIAG_RECEIVER_ACTION, action)
            .putString(KEY_BOOT_DIAG_RECEIVER_STAGE, stage)
            .putLong(KEY_BOOT_DIAG_RECEIVER_AT_MS, System.currentTimeMillis())
            .putString(KEY_BOOT_DIAG_RECEIVER_ERROR, error ?: "")
            .apply()
        return attemptId
    }

    fun updateBootReceiverStage(stage: String, error: String? = null) {
        prefs.edit()
            .putString(KEY_BOOT_DIAG_RECEIVER_STAGE, stage)
            .putLong(KEY_BOOT_DIAG_RECEIVER_AT_MS, System.currentTimeMillis())
            .putString(KEY_BOOT_DIAG_RECEIVER_ERROR, error ?: "")
            .apply()
    }

    fun markBootServiceStage(action: String, stage: String, error: String? = null) {
        prefs.edit()
            .putString(KEY_BOOT_DIAG_SERVICE_ACTION, action)
            .putString(KEY_BOOT_DIAG_SERVICE_STAGE, stage)
            .putLong(KEY_BOOT_DIAG_SERVICE_AT_MS, System.currentTimeMillis())
            .putString(KEY_BOOT_DIAG_SERVICE_ERROR, error ?: "")
            .apply()
    }

    fun getBootDiagSnapshot(): BootDiagSnapshot {
        return BootDiagSnapshot(
            attemptId = prefs.getString(KEY_BOOT_DIAG_ATTEMPT_ID, "") ?: "",
            receiverStage = prefs.getString(KEY_BOOT_DIAG_RECEIVER_STAGE, "") ?: "",
            receiverAction = prefs.getString(KEY_BOOT_DIAG_RECEIVER_ACTION, "") ?: "",
            receiverAtMs = prefs.getLong(KEY_BOOT_DIAG_RECEIVER_AT_MS, 0L),
            receiverError = prefs.getString(KEY_BOOT_DIAG_RECEIVER_ERROR, "") ?: "",
            serviceStage = prefs.getString(KEY_BOOT_DIAG_SERVICE_STAGE, "") ?: "",
            serviceAction = prefs.getString(KEY_BOOT_DIAG_SERVICE_ACTION, "") ?: "",
            serviceAtMs = prefs.getLong(KEY_BOOT_DIAG_SERVICE_AT_MS, 0L),
            serviceError = prefs.getString(KEY_BOOT_DIAG_SERVICE_ERROR, "") ?: "",
        )
    }

    private fun resolveIniFile(): File? {
        val defaultIniFile = File(DEFAULT_INI_PATH)
        if (defaultIniFile.exists()) {
            return defaultIniFile
        }

        val legacyIniFile = File(LEGACY_INI_PATH)
        return if (legacyIniFile.exists()) legacyIniFile else null
    }

    private fun saveNormalizedConfig(normalizedConfig: Map<String, Any>) {
        prefs.edit()
            .putString(KEY_PROXY_NAME, normalizedConfig["proxyName"].toString())
            .putString(KEY_PROXY_TYPE, normalizedConfig["proxyType"].toString())
            .putString(KEY_PROXY_HOST, normalizedConfig["proxyHost"].toString())
            .putString(KEY_PROXY_PORT, normalizedConfig["proxyPort"].toString())
            .putString(KEY_PROXY_USER, normalizedConfig["proxyUser"].toString())
            .putString(KEY_PROXY_PASS, normalizedConfig["proxyPass"].toString())
            .putString(KEY_APP_PROXY_PACKAGE_LIST, normalizedConfig["appProxyPackageList"].toString())
            .putBoolean(KEY_AUTO_START, normalizedConfig["autoStart"] as Boolean)
            .apply()
    }

    private fun attachIniMetadata(config: Map<String, Any>, iniPath: String): Map<String, Any> {
        return config.toMutableMap().apply {
            this["configSource"] = DEFAULT_INI_CONFIG_SOURCE
            this["configSourcePath"] = iniPath
        }
    }

    private fun buildIniText(config: Map<String, Any>): String {
        return buildString {
            appendLine("[proxy]")
            appendLine("type=${config["proxyType"]}")
            appendLine("host=${config["proxyHost"]}")
            appendLine("port=${config["proxyPort"]}")
            appendLine("username=${config["proxyUser"]}")
            appendLine("password=${config["proxyPass"]}")
            appendLine("autostart=${config["autoStart"]}")
        }
    }

    private fun parseIniText(text: String): Map<String, Any>? {
        val iniValues = mutableMapOf<String, String>()
        var currentSection: String? = null

        text.lineSequence().forEach { rawLine ->
            val line = rawLine.trim()
            if (line.isBlank() || line.startsWith("#") || line.startsWith(";")) {
                return@forEach
            }

            if (line.startsWith("[") && line.endsWith("]")) {
                currentSection = line.substring(1, line.length - 1).trim().lowercase()
                return@forEach
            }

            if (currentSection != "proxy") {
                return@forEach
            }

            val separatorIndex = line.indexOf('=')
            if (separatorIndex <= 0) {
                return@forEach
            }

            val key = line.substring(0, separatorIndex).trim().lowercase()
            val value = line.substring(separatorIndex + 1)
                .trim()
                .removeSurrounding("\"")
                .removeSurrounding("'")
            iniValues[key] = value
        }

        val normalizedInput = mapOf<String, Any?>(
            "proxyType" to iniValues["type"],
            "proxyHost" to iniValues["host"],
            "proxyPort" to iniValues["port"],
            "proxyUser" to iniValues["username"],
            "proxyPass" to iniValues["password"],
            "appProxyPackageList" to "[]"
        )

        return normalizeProxyConfig(normalizedInput, parseBoolean(iniValues["autostart"], true))
    }

    private fun normalizeProxyConfig(data: Map<String, Any?>, autoStart: Boolean): Map<String, Any>? {
        val proxyHost = readFirstString(data, "proxyHost", "host").trim()
        val proxyPort = readFirstString(data, "proxyPort", "port").trim()
        if (proxyHost.isBlank() || proxyPort.toIntOrNull() == null) {
            return null
        }

        val proxyType = readFirstString(data, "proxyType", "type")
            .trim()
            .ifBlank { "socks5" }
            .lowercase()
        val proxyName = readFirstString(data, "proxyName", "name")
            .trim()
            .ifBlank { buildDefaultProxyName(proxyType, proxyHost, proxyPort) }
        val proxyUser = readFirstString(data, "proxyUser", "username", "user")
        val proxyPass = readFirstString(data, "proxyPass", "password", "pass")

        return mapOf(
            "proxyName" to proxyName,
            "proxyType" to proxyType,
            "proxyHost" to proxyHost,
            "proxyPort" to proxyPort,
            "proxyUser" to proxyUser,
            "proxyPass" to proxyPass,
            "appProxyPackageList" to normalizeAppProxyPackageList(data["appProxyPackageList"]),
            "autoStart" to autoStart
        )
    }

    private fun readFirstString(data: Map<String, Any?>, vararg keys: String): String {
        keys.forEach { key ->
            val value = data[key] ?: return@forEach
            return value.toString()
        }
        return ""
    }

    private fun normalizeAppProxyPackageList(value: Any?): String {
        return when (value) {
            null -> "[]"
            is String -> {
                val trimmedValue = value.trim()
                when {
                    trimmedValue.isBlank() -> "[]"
                    trimmedValue.startsWith("[") -> trimmedValue
                    else -> gson.toJson(listOf(trimmedValue))
                }
            }
            is Collection<*> -> gson.toJson(value)
            is Array<*> -> gson.toJson(value)
            else -> gson.toJson(value)
        }
    }

    private fun parseBoolean(value: String?, defaultValue: Boolean): Boolean {
        return when (value?.trim()?.lowercase()) {
            null, "" -> defaultValue
            "1", "true", "yes", "on" -> true
            "0", "false", "no", "off" -> false
            else -> defaultValue
        }
    }

    private fun buildDefaultProxyName(proxyType: String, proxyHost: String, proxyPort: String): String {
        return "$proxyType://$proxyHost:$proxyPort"
    }
}
