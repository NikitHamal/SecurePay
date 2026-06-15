package com.touchbase.user

import android.app.Application
import com.touchbase.user.data.remote.ApiModule
import com.touchbase.user.data.remote.DeviceTokenManager
import com.touchbase.user.data.remote.SecurePayApi
import com.touchbase.user.data.repository.DeviceRepository
import com.touchbase.user.worker.HeartbeatWorker

class SecurePayApplication : Application() {
    val tokenManager: DeviceTokenManager by lazy { DeviceTokenManager(this) }

    val api: SecurePayApi by lazy {
        val secret = tokenManager.imei ?: "unregistered-device"
        ApiModule.provideApi(secret)
    }

    val deviceRepository: DeviceRepository by lazy {
        DeviceRepository(api, tokenManager)
    }

    override fun onCreate() {
        super.onCreate()
        if (tokenManager.isRegistered) {
            HeartbeatWorker.schedule(this)
        }
    }
}
