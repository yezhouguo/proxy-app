package cn.ys1231.appproxy.IyueService

import android.app.Notification
import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.PendingIntent
import android.content.Context
import android.content.Intent
import android.graphics.Color
import android.net.VpnService
import android.os.Binder
import android.os.Build
import android.os.IBinder
import android.os.ParcelFileDescriptor
import android.util.Log
import androidx.core.app.NotificationCompat
import cn.ys1231.appproxy.AppProxyConfigRepository
import cn.ys1231.appproxy.MainActivity
import cn.ys1231.appproxy.R
import com.google.gson.Gson
import engine.Engine
import engine.Key

class IyueVPNService : VpnService() {
    companion object {
        private const val ACTION_START_VPN = "cn.ys1231.appproxy.action.START_VPN"
        private const val ACTION_STOP_VPN = "cn.ys1231.appproxy.action.STOP_VPN"

        fun createStartIntent(context: Context): Intent {
            return Intent(context, IyueVPNService::class.java).setAction(ACTION_START_VPN)
        }

        fun createStopIntent(context: Context): Intent {
            return Intent(context, IyueVPNService::class.java).setAction(ACTION_STOP_VPN)
        }
    }

    private val TAG = "iyue->${this.javaClass.simpleName} "

    private var vpnInterface: ParcelFileDescriptor? = null
    private var isRunning = false
    private val binder = VPNServiceBinder()
    private lateinit var configRepository: AppProxyConfigRepository

    inner class VPNServiceBinder : Binder() {
        fun getService(): IyueVPNService = this@IyueVPNService
    }

    override fun onCreate() {
        super.onCreate()
        Log.d(TAG, "onCreate: VPNServiceBinder")
        configRepository = AppProxyConfigRepository(this)
        configRepository.markBootServiceStage("service", "on_create")

        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            val channelId = "iyue_vpn_channel"
            val channelName = "Iyue VPN"
            val importance = NotificationManager.IMPORTANCE_DEFAULT
            val channel = NotificationChannel(channelId, channelName, importance).apply {
                description = "Iyue VPN Service Channel"
                lightColor = Color.BLUE
                lockscreenVisibility = Notification.VISIBILITY_PRIVATE
            }
            val notificationManager: NotificationManager =
                getSystemService(Context.NOTIFICATION_SERVICE) as NotificationManager
            notificationManager.createNotificationChannel(channel)
        }
    }

    override fun onBind(intent: Intent?): IBinder {
        Log.d(TAG, "onBind: VPNServiceBinder")
        return binder
    }

    override fun onStartCommand(intent: Intent?, flags: Int, startId: Int): Int {
        Log.d(TAG, "onStartCommand: ${intent.toString()}")
        configRepository.markBootServiceStage(intent?.action ?: "", "on_start_command")
        when (intent?.action) {
            ACTION_START_VPN -> {
                val config = configRepository.refreshFromDefaultIniIfPossible() ?: configRepository.loadCachedConfig()
                if (config == null) {
                    configRepository.markBootServiceStage(intent.action ?: "", "config_missing")
                    Log.w(TAG, "onStartCommand: no cached proxy config")
                    stopSelf(startId)
                } else if (!isRunning) {
                    startVpnService(config)
                } else {
                    Log.d(TAG, "onStartCommand: vpn already running")
                }
            }

            ACTION_STOP_VPN -> {
                stopVpnService()
                stopSelf(startId)
            }
        }
        return START_STICKY
    }

    fun startVpnService(data: Map<String, Any>) {
        Log.d(TAG, "startVpnService: $data")
        stopVpnService()

        val proxyName = data["proxyName"].toString()
        val proxyHost = data["proxyHost"].toString()
        val proxyPort = data["proxyPort"].toString().toInt()
        val proxyType = data["proxyType"].toString().ifBlank { "socks5" }
        val proxyUser = data["proxyUser"].toString()
        val proxyPass = data["proxyPass"].toString()

        val notificationIntent = Intent(this, MainActivity::class.java)
            .putExtra("iyue_vpn_channel", true)
        val pendingIntent = PendingIntent.getActivity(
            this,
            0,
            notificationIntent,
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
        )

        val notification = NotificationCompat.Builder(this, "iyue_vpn_channel")
            .setContentTitle("${applicationInfo.loadLabel(packageManager)}: $proxyName")
            .setContentText("$proxyType: $proxyHost:$proxyPort")
            .setSmallIcon(R.mipmap.vpn, 3)
            .setContentIntent(pendingIntent)
            .build()

        startForeground(1, notification)

        val builder = Builder()
            .addAddress("10.0.0.2", 24)
            .addRoute("0.0.0.0", 0)
            .setMtu(1500)
            .setSession(packageName)
        val allowedApps = jsonToList(data["appProxyPackageList"].toString())
        if (allowedApps.isEmpty()) {
            builder.addDisallowedApplication(packageName)
        } else {
            for (appPackageName in allowedApps) {
                try {
                    Log.d(TAG, "addAllowedApplication: $appPackageName")
                    builder.addAllowedApplication(appPackageName)
                } catch (e: Exception) {
                    Log.e(TAG, "addAllowedApplication: ${e.message}")
                }
            }
        }

        try {
            vpnInterface = builder.establish()
            if (vpnInterface == null) {
                Log.e(TAG, "vpnInterface: create establish error ")
                configRepository.markBootServiceStage("start", "establish_null")
                configRepository.markVpnRunning(false)
                stopSelf()
                return
            }

            val key = Key()
            key.mark = 0
            key.mtu = 1500
            key.device = "fd://${vpnInterface!!.fd}"
            key.setInterface("")
            key.logLevel = "error"
            key.proxy = if (proxyUser.isBlank() && proxyPass.isBlank()) {
                "${proxyType}://${proxyHost}:${proxyPort}"
            } else {
                "${proxyType}://${proxyUser}:${proxyPass}@${proxyHost}:${proxyPort}"
            }
            key.restAPI = ""
            key.tcpSendBufferSize = ""
            key.tcpReceiveBufferSize = ""
            key.tcpModerateReceiveBuffer = false
            Engine.insert(key)
            Engine.start()
            Log.d(TAG, "startEngine: $key")
            isRunning = true
            configRepository.markBootServiceStage("start", "engine_started")
            configRepository.markVpnRunning(true)
        } catch (e: Exception) {
            Log.e(TAG, "startEngine: error ${e.message}")
            configRepository.markBootServiceStage("start", "engine_failed", e.javaClass.simpleName + ": " + (e.message ?: ""))
            configRepository.markVpnRunning(false)
            stopVpnService()
        }
    }

    fun stopVpnService() {
        Log.d(TAG, "stopVpnService: vpnInterface $vpnInterface")
        try {
            if (vpnInterface != null) {
                vpnInterface?.close()
                vpnInterface = null
            }
            isRunning = false
            configRepository.markVpnRunning(false)
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.N) {
                stopForeground(STOP_FOREGROUND_REMOVE)
            } else {
                stopForeground(true)
            }
            Log.d(TAG, "stopEngine: success!")
        } catch (e: Exception) {
            Log.e(TAG, "stopVpnService: ${e.message}")
        }
    }

    fun isRunning(): Boolean {
        return isRunning
    }

    private fun jsonToList(jsonString: String): List<String> {
        if (jsonString.isBlank()) {
            return emptyList()
        }
        val gson = Gson()
        return gson.fromJson(jsonString, Array<String>::class.java)?.toList() ?: emptyList()
    }

    override fun onUnbind(intent: Intent?): Boolean {
        Log.d(TAG, "onUnbind: IyueVPNService")
        return super.onUnbind(intent)
    }

    override fun onDestroy() {
        super.onDestroy()
        Log.d(TAG, "onDestroy: IyueVPNService")
        configRepository.markVpnRunning(false)
    }
}
