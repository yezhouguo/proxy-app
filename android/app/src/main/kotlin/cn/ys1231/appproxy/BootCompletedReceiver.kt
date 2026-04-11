package cn.ys1231.appproxy

import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.net.VpnService
import android.os.Build
import android.util.Log
import androidx.core.content.ContextCompat.startForegroundService
import cn.ys1231.appproxy.IyueService.IyueVPNService

class BootCompletedReceiver : BroadcastReceiver() {
    private val TAG = "iyue->${this.javaClass.simpleName}"

    override fun onReceive(context: Context, intent: Intent?) {
        val action = intent?.action ?: return
        if (action != Intent.ACTION_BOOT_COMPLETED && action != Intent.ACTION_MY_PACKAGE_REPLACED) {
            return
        }

        val configRepository = AppProxyConfigRepository(context)
        configRepository.markBootReceiverStage(action, "received")
        configRepository.refreshFromDefaultIniIfPossible()
        if (!configRepository.hasCachedConfig() || !configRepository.isAutoStartEnabled()) {
            configRepository.updateBootReceiverStage("config_missing_or_disabled")
            Log.d(TAG, "onReceive: autostart disabled or config missing")
            return
        }
        if (!configRepository.hasVpnConsentGranted()) {
            configRepository.updateBootReceiverStage("consent_not_granted")
            Log.d(TAG, "onReceive: vpn consent not granted yet")
            return
        }
        if (VpnService.prepare(context) != null) {
            configRepository.updateBootReceiverStage("prepare_requires_ui")
            Log.d(TAG, "onReceive: vpn prepare requires user interaction")
            configRepository.markVpnConsentGranted(false)
            return
        }

        val startIntent = IyueVPNService.createStartIntent(context)
        try {
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
                startForegroundService(context, startIntent)
            } else {
                context.startService(startIntent)
            }
            configRepository.updateBootReceiverStage("start_service_called")
        } catch (e: Exception) {
            configRepository.updateBootReceiverStage("start_service_failed", e.javaClass.simpleName + ": " + (e.message ?: ""))
            throw e
        }
    }
}
