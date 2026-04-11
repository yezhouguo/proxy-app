package cn.ys1231.appproxy

import android.Manifest.permission.POST_NOTIFICATIONS
import android.Manifest.permission.READ_EXTERNAL_STORAGE
import android.app.Activity
import android.content.ComponentName
import android.content.Context
import android.content.Intent
import android.content.ServiceConnection
import android.content.pm.PackageManager
import android.net.Uri
import android.net.VpnService
import android.os.Build
import android.os.Bundle
import android.os.Environment
import android.os.IBinder
import android.provider.Settings
import android.util.Log
import android.widget.Toast
import androidx.core.app.ActivityCompat
import androidx.core.content.ContextCompat
import androidx.core.content.ContextCompat.startForegroundService
import cn.ys1231.appproxy.IyueService.IyueVPNService
import cn.ys1231.appproxy.data.Utils
import io.flutter.embedding.android.FlutterActivity
import io.flutter.embedding.engine.FlutterEngine
import io.flutter.plugin.common.MethodChannel

class MainActivity : FlutterActivity() {
    private val TAG = "iyue->${this.javaClass.simpleName}"
    private val CHANNEL = "cn.ys1231/appproxy"
    private val CHANNEL_VPN = "cn.ys1231/appproxy/vpn"
    private val CHANNEL_APP_UPDATE = "cn.ys1231/appproxy/appupdate"
    private var FLUTTER_VPN_CHANNEL: MethodChannel? = null
    private var FLUTTER_CHANNEL: MethodChannel? = null

    private var utils: Utils? = null
    private var intentVpnService: Intent? = null
    private var iyueVpnService: IyueVPNService? = null
    private var isBind: Boolean = false
    private var conn: ServiceConnection? = null
    private lateinit var configRepository: AppProxyConfigRepository
    private var pendingStartAfterConsent = false
    private var vpnMonitorThread: Thread? = null

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        configRepository = AppProxyConfigRepository(this)
        intentVpnService = Intent(this, IyueVPNService::class.java)
        conn = object : ServiceConnection {
            override fun onServiceConnected(name: ComponentName?, service: IBinder?) {
                Log.d(TAG, "onServiceConnected: $name")
                iyueVpnService = if (service is IyueVPNService.VPNServiceBinder) {
                    service.getService()
                } else {
                    Log.d(TAG, "onServiceConnected: ClassCastException")
                    null
                }
            }

            override fun onServiceDisconnected(name: ComponentName?) {
                Log.d(TAG, "onServiceDisconnected: $name")
                iyueVpnService = null
            }
        }
        if (bindService(intentVpnService!!, conn!!, Context.BIND_AUTO_CREATE)) {
            isBind = true
        }

        ensureIniAccessPermissionIfNeeded()
        startAutoVpnIfEligible()
    }

    private fun startAutoVpnIfEligible() {
        configRepository.markVpnRunning(false)
        configRepository.refreshFromDefaultIniIfPossible()
        if (!configRepository.hasCachedConfig() || !configRepository.isAutoStartEnabled()) {
            return
        }

        requestNotificationPermissionIfNeeded()
        val intent = VpnService.prepare(this)
        if (intent != null) {
            Log.d(TAG, "startAutoVpnIfEligible: requesting vpn consent")
            configRepository.markVpnConsentGranted(false)
            pendingStartAfterConsent = true
            startActivityForResult(intent, VPN_REQUEST_CODE)
            return
        }

        configRepository.markVpnConsentGranted(true)
        startVpnServiceByIntent()
    }

    private fun startVpnServiceByIntent() {
        val startIntent = IyueVPNService.createStartIntent(this)
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            startForegroundService(this, startIntent)
        } else {
            startService(startIntent)
        }
        startVpnStateMonitor(5000)
    }

    private fun stopVpnService() {
        Log.d(TAG, "stopVpnService: ...... ")
        val stopIntent = IyueVPNService.createStopIntent(this)
        startService(stopIntent)
    }

    private fun startVpnFromFlutter(arguments: Map<String, Any>?): Boolean {
        val normalizedConfig = configRepository.saveProxyFromFlutter(arguments ?: emptyMap()) ?: return false
        Log.d(TAG, "startVpnFromFlutter: $normalizedConfig")
        requestNotificationPermissionIfNeeded()

        val intent = VpnService.prepare(this)
        return if (intent != null) {
            pendingStartAfterConsent = true
            startActivityForResult(intent, VPN_REQUEST_CODE)
            true
        } else {
            configRepository.markVpnConsentGranted(true)
            startVpnServiceByIntent()
            true
        }
    }

    private fun startVpnStateMonitor(startupGraceMillis: Long = 0) {
        if (vpnMonitorThread?.isAlive == true) {
            return
        }

        vpnMonitorThread = Thread {
            Log.d(TAG, "startVpnStateMonitor: start")
            val startupDeadline = System.currentTimeMillis() + startupGraceMillis
            try {
                while (!Thread.currentThread().isInterrupted) {
                    val running = iyueVpnService?.isRunning() == true || configRepository.isVpnRunning()
                    if (running) {
                        Thread.sleep(1000)
                    } else if (System.currentTimeMillis() < startupDeadline) {
                        Thread.sleep(500)
                    } else {
                        runOnUiThread {
                            FLUTTER_VPN_CHANNEL?.invokeMethod("stopVpn", null)
                        }
                        break
                    }
                }
            } catch (_: InterruptedException) {
                Thread.currentThread().interrupt()
            }
        }.apply { start() }
    }

    private fun requestNotificationPermissionIfNeeded() {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
            if (ContextCompat.checkSelfPermission(
                    this,
                    POST_NOTIFICATIONS
                ) != PackageManager.PERMISSION_GRANTED
            ) {
                ActivityCompat.requestPermissions(
                    this,
                    arrayOf(POST_NOTIFICATIONS),
                    REQUEST_NOTIFICATION_PERMISSION
                )
            } else {
                Log.d(TAG, "requestNotificationPermissionIfNeeded: already granted")
            }
        }
    }

    private fun ensureIniAccessPermissionIfNeeded() {
        if (Build.VERSION.SDK_INT < Build.VERSION_CODES.R) {
            if (ContextCompat.checkSelfPermission(this, READ_EXTERNAL_STORAGE) != PackageManager.PERMISSION_GRANTED) {
                ActivityCompat.requestPermissions(
                    this,
                    arrayOf(READ_EXTERNAL_STORAGE),
                    REQUEST_READ_STORAGE_PERMISSION
                )
            }
            return
        }
        if (Environment.isExternalStorageManager() || configRepository.hasPromptedForIniAccess()) {
            return
        }

        configRepository.markPromptedForIniAccess()
        try {
            val intent = Intent(Settings.ACTION_MANAGE_APP_ALL_FILES_ACCESS_PERMISSION).apply {
                data = Uri.parse("package:$packageName")
            }
            startActivity(intent)
        } catch (e: Exception) {
            startActivity(Intent(Settings.ACTION_MANAGE_ALL_FILES_ACCESS_PERMISSION))
        }
    }

    private fun startDownload(url: String?) {
        val downloadIntent = Intent(Intent.ACTION_VIEW)
        downloadIntent.data = Uri.parse(url)
        downloadIntent.flags = Intent.FLAG_ACTIVITY_NEW_TASK
        startActivity(downloadIntent)
    }

    override fun configureFlutterEngine(flutterEngine: FlutterEngine) {
        super.configureFlutterEngine(flutterEngine)
        utils = Utils(this)

        FLUTTER_CHANNEL = MethodChannel(
            flutterEngine.dartExecutor.binaryMessenger,
            CHANNEL
        )
        FLUTTER_CHANNEL!!.setMethodCallHandler { call, result ->
            if (call.method == "getAppList") {
                try {
                    Log.d(TAG, "configureFlutterEngine ${call.method} ")
                    val appList = utils!!.getAppList()
                    result.success(appList)
                } catch (e: Exception) {
                    result.error("-1", e.message, null)
                }
            }
        }

        FLUTTER_VPN_CHANNEL = MethodChannel(
            flutterEngine.dartExecutor.binaryMessenger,
            CHANNEL_VPN
        )
        FLUTTER_VPN_CHANNEL!!.setMethodCallHandler { call, result ->
            when (call.method) {
                "startVpn" -> {
                    try {
                        val started = startVpnFromFlutter(call.arguments())
                        result.success(started)
                    } catch (e: Exception) {
                        result.error("-1", e.message, null)
                    }
                }

                "stopVpn" -> {
                    try {
                        stopVpnService()
                        result.success(true)
                    } catch (e: Exception) {
                        result.error("-1", e.message, null)
                    }
                }
            }
        }
        MethodChannel(
            flutterEngine.dartExecutor.binaryMessenger,
            CHANNEL_APP_UPDATE
        ).setMethodCallHandler { call, result ->
            if (call.method == "startDownload") {
                try {
                    Log.d(TAG, "configureFlutterEngine ${call.method} ")
                    val url: String? = call.arguments<String>()
                    startDownload(url)
                    result.success(true)
                } catch (e: Exception) {
                    result.error("-1", e.message, null)
                }
            }
        }

        Thread {
            Log.d(TAG, "configureFlutterEngine: start get app list info")
            utils!!.initAppList()
            runOnUiThread {
                Log.d(TAG, "configureFlutterEngine: call onRefresh")
                FLUTTER_CHANNEL!!.invokeMethod("onRefresh", null)
                Log.d(TAG, "configureFlutterEngine: end get app list info")
            }
        }.start()
    }

    private val VPN_REQUEST_CODE = 100
    private val REQUEST_NOTIFICATION_PERMISSION = 1231
    private val REQUEST_READ_STORAGE_PERMISSION = 1232

    override fun onActivityResult(requestCode: Int, resultCode: Int, data: Intent?) {
        super.onActivityResult(requestCode, resultCode, data)
        if (requestCode == VPN_REQUEST_CODE) {
            if (resultCode == Activity.RESULT_OK) {
                Log.d(TAG, "onActivityResult: 用户授权成功，启动VPN服务")
                configRepository.markVpnConsentGranted(true)
                if (pendingStartAfterConsent) {
                    pendingStartAfterConsent = false
                    startVpnServiceByIntent()
                }
            } else {
                Log.d(TAG, "onActivityResult: 用户拒绝授权")
                pendingStartAfterConsent = false
                configRepository.markVpnConsentGranted(false)
                FLUTTER_VPN_CHANNEL?.invokeMethod("stopVpn", null)
            }
        }
    }

    override fun onRequestPermissionsResult(
        requestCode: Int,
        permissions: Array<out String>,
        grantResults: IntArray
    ) {
        super.onRequestPermissionsResult(requestCode, permissions, grantResults)
        if (requestCode == REQUEST_NOTIFICATION_PERMISSION) {
            if (grantResults.isNotEmpty() && grantResults[0] == PackageManager.PERMISSION_GRANTED) {
                Toast.makeText(this, "通知权限被授予", Toast.LENGTH_SHORT).show()
            } else {
                Toast.makeText(this, "此应用程序需要通知权限", Toast.LENGTH_SHORT).show()
                startNotificationSetting()
            }
        }
        if (requestCode == REQUEST_READ_STORAGE_PERMISSION) {
            if (grantResults.isNotEmpty() && grantResults[0] == PackageManager.PERMISSION_GRANTED) {
                Log.d(TAG, "onRequestPermissionsResult: read storage granted")
            } else {
                Toast.makeText(this, "需要存储权限读取默认配置文件", Toast.LENGTH_SHORT).show()
            }
        }
    }

    private fun startNotificationSetting() {
        val applicationInfo = applicationInfo
        try {
            val intent = Intent()
            intent.flags = Intent.FLAG_ACTIVITY_NEW_TASK
            intent.action = "android.settings.APP_NOTIFICATION_SETTINGS"
            intent.putExtra("app_package", applicationInfo.packageName)
            intent.putExtra("android.provider.extra.APP_PACKAGE", applicationInfo.packageName)
            intent.putExtra("app_uid", applicationInfo.uid)
            startActivity(intent)
        } catch (e: Exception) {
            val intent = Intent()
            intent.flags = Intent.FLAG_ACTIVITY_NEW_TASK
            intent.action = "android.settings.APPLICATION_DETAILS_SETTINGS"
            intent.data = Uri.fromParts("package", applicationInfo.packageName, null)
            startActivity(intent)
        }
    }

    override fun onDestroy() {
        vpnMonitorThread?.interrupt()
        vpnMonitorThread = null
        super.onDestroy()
        if (isBind) {
            unbindService(conn!!)
            isBind = false
        }
    }
}
