package com.touchbase.user.worker

import android.app.NotificationChannel
import android.app.NotificationManager
import android.content.Intent
import android.os.Build
import android.util.Log
import androidx.core.app.NotificationCompat
import com.google.firebase.messaging.FirebaseMessaging
import com.google.firebase.messaging.FirebaseMessagingService
import com.google.firebase.messaging.RemoteMessage
import com.touchbase.user.BuildConfig
import com.touchbase.user.data.remote.ApiModule
import com.touchbase.user.data.remote.DeviceTokenManager
import com.touchbase.user.data.remote.DeviceAuthRecovery
import com.touchbase.user.ui.lock.LockTaskActivity
import com.touchbase.user.MainActivity
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch

class FcmService : FirebaseMessagingService() {

    override fun onNewToken(token: String) {
        super.onNewToken(token)
        Log.d(TAG, "New FCM token received")
        tokenHolder = token
        FirebaseMessaging.getInstance().subscribeToTopic(UPDATE_TOPIC)
            .addOnFailureListener { Log.w(TAG, "Update topic subscription failed after token refresh", it) }

        val tokenManager = runCatching { DeviceTokenManager(this) }.getOrNull() ?: return
        tokenManager.saveFcmToken(token)

        if (tokenManager.isRegistered) {
            syncFcmToken(token, tokenManager)
        }
    }

    override fun onMessageReceived(message: RemoteMessage) {
        super.onMessageReceived(message)
        val data = message.data
        val type = data["type"]
        val accountId = data["accountId"]

        Log.d(TAG, "FCM message received: type=$type accountId=$accountId")

        when (type) {
            "stolen" -> {
                val tokenManager = DeviceTokenManager(this)
                val id = accountId ?: tokenManager.accountId
                if (!id.isNullOrBlank()) TrackingService.start(this, id)
                val intent = Intent(this, LockTaskActivity::class.java).apply {
                    addFlags(Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_CLEAR_TOP)
                }
                startActivity(intent)
            }
            "lock" -> {
                if (data["isStolen"] == "true") {
                    val tokenManager = DeviceTokenManager(this)
                    val id = accountId ?: tokenManager.accountId
                    if (!id.isNullOrBlank()) TrackingService.start(this, id)
                }
                val intent = Intent(this, LockTaskActivity::class.java).apply {
                    addFlags(Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_CLEAR_TOP)
                }
                startActivity(intent)
            }
            "update" -> {
                AppUpdateWorker.runNow(this)
            }
            "unlock" -> {
                TrackingService.stop(this)
                CoroutineScope(Dispatchers.IO).launch {
                    runCatching {
                        val tokenManager = DeviceTokenManager(this@FcmService)
                        val signingSecret = tokenManager.apiSecret
                            ?: DeviceAuthRecovery.ensureDeviceApiSecret(this@FcmService, tokenManager)
                            ?: return@runCatching
                        val deviceId = tokenManager.accountId ?: tokenManager.imei.orEmpty()
                        val api = ApiModule.provideApi(signingSecret, deviceId)
                        api.deviceHeartbeat(
                            mapOf(
                                "imei" to (tokenManager.imei ?: ""),
                                "accountId" to (tokenManager.accountId ?: "")
                            )
                        )
                    }.onSuccess {
                        val intent = MainActivity.newLaunchIntent(this@FcmService)
                        startActivity(intent)
                    }
                }
            }
            "sync" -> {
                CoroutineScope(Dispatchers.IO).launch {
                    runCatching {
                        val tokenManager = DeviceTokenManager(this@FcmService)
                        val signingSecret = tokenManager.apiSecret
                            ?: DeviceAuthRecovery.ensureDeviceApiSecret(this@FcmService, tokenManager)
                            ?: return@runCatching
                        val deviceId = tokenManager.accountId ?: tokenManager.imei.orEmpty()
                        val api = ApiModule.provideApi(signingSecret, deviceId)
                        api.syncReport(
                            mapOf(
                                "accountId" to (tokenManager.accountId ?: ""),
                                "imei" to (tokenManager.imei ?: ""),
                                "appVersion" to "${BuildConfig.VERSION_NAME} (${BuildConfig.VERSION_CODE})",
                                "batteryLevel" to "${getBatteryLevel()}"
                            )
                        )
                    }
                }
            }
            "notification" -> {
                val title = data["title"] ?: "SecurePay"
                val body = data["body"] ?: "You have a new message from your dealer"
                showLocalNotification(title, body)
            }
        }
    }

    private fun syncFcmToken(token: String, tokenManager: DeviceTokenManager) {
        CoroutineScope(Dispatchers.IO).launch {
            runCatching {
                val accountId = tokenManager.accountId ?: return@launch
                val imei = tokenManager.imei ?: return@launch
                val signingSecret = tokenManager.apiSecret
                    ?: DeviceAuthRecovery.ensureDeviceApiSecret(this@FcmService, tokenManager)
                    ?: return@runCatching
                val deviceId = accountId
                val api = ApiModule.provideApi(signingSecret, deviceId)
                api.uploadFcmToken(
                    mapOf(
                        "accountId" to accountId,
                        "imei" to imei,
                        "fcmToken" to token
                    )
                )
            }
        }
    }

    private fun getBatteryLevel(): Int {
        return runCatching {
            val intent = registerReceiver(null, android.content.IntentFilter(Intent.ACTION_BATTERY_CHANGED))
            val level = intent?.getIntExtra("level", -1) ?: -1
            val scale = intent?.getIntExtra("scale", 100) ?: 100
            if (level < 0 || scale <= 0) -1
            else (level * 100 / scale)
        }.getOrDefault(-1)
    }

    private fun showLocalNotification(title: String, body: String) {
        val channelId = "securepay_admin_alerts"
        val manager = getSystemService(android.app.NotificationManager::class.java)

        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            val channel = NotificationChannel(
                channelId, "Admin Alerts",
                NotificationManager.IMPORTANCE_HIGH
            ).apply {
                description = "Notifications sent by your dealer"
            }
            manager.createNotificationChannel(channel)
        }

        val notification = NotificationCompat.Builder(this, channelId)
            .setSmallIcon(android.R.drawable.ic_dialog_info)
            .setContentTitle(title)
            .setContentText(body)
            .setPriority(NotificationCompat.PRIORITY_HIGH)
            .setAutoCancel(true)
            .build()

        manager.notify(System.currentTimeMillis().toInt(), notification)
    }

    companion object {
        private const val TAG = "FcmService"
        private const val UPDATE_TOPIC = "tb-customer-updates"

        @Volatile
        var tokenHolder: String? = null

        fun getToken(): String? = tokenHolder
    }
}
