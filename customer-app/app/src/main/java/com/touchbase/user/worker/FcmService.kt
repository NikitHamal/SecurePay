package com.touchbase.user.worker

import android.content.Intent
import android.util.Log
import com.google.firebase.messaging.FirebaseMessagingService
import com.google.firebase.messaging.RemoteMessage
import com.touchbase.user.BuildConfig
import com.touchbase.user.SecurePayApplication
import com.touchbase.user.data.remote.ApiModule
import com.touchbase.user.data.remote.DeviceTokenManager
import com.touchbase.user.ui.lock.LockTaskActivity
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch

class FcmService : FirebaseMessagingService() {

    override fun onNewToken(token: String) {
        super.onNewToken(token)
        Log.d(TAG, "New FCM token received")
        tokenHolder = token

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
            "lock" -> {
                val intent = Intent(this, LockTaskActivity::class.java).apply {
                    addFlags(Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_CLEAR_TOP)
                }
                startActivity(intent)
            }
            "unlock", "sync" -> {
                CoroutineScope(Dispatchers.IO).launch {
                    runCatching {
                        val app = applicationContext as? SecurePayApplication
                        val repository = app?.deviceRepository
                        if (repository != null) {
                            repository.heartbeat()
                        } else {
                            val tokenManager = DeviceTokenManager(this@FcmService)
                            val signingSecret = tokenManager.apiSecret ?: BuildConfig.HMAC_SECRET
                            val deviceId = tokenManager.accountId ?: tokenManager.imei.orEmpty()
                            val api = ApiModule.provideApi(signingSecret, deviceId)
                            api.deviceHeartbeat(
                                mapOf(
                                    "imei" to (tokenManager.imei ?: ""),
                                    "accountId" to (tokenManager.accountId ?: "")
                                )
                            )
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
                val signingSecret = tokenManager.apiSecret ?: BuildConfig.HMAC_SECRET
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

        @Volatile
        var tokenHolder: String? = null

        fun getToken(): String? = tokenHolder
    }
}
