package com.touchbase.user.worker

import android.content.Intent
import android.util.Log
import com.google.firebase.messaging.FirebaseMessaging
import com.google.firebase.messaging.FirebaseMessagingService
import com.google.firebase.messaging.RemoteMessage
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
            "unlock", "sync" -> {
                if (type == "unlock") TrackingService.stop(this)
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
                        if (type == "unlock") {
                            val intent = MainActivity.newLaunchIntent(this@FcmService)
                            startActivity(intent)
                        }
                    }
                }
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

    companion object {
        private const val TAG = "FcmService"
        private const val UPDATE_TOPIC = "tb-customer-updates"

        @Volatile
        var tokenHolder: String? = null

        fun getToken(): String? = tokenHolder
    }
}
