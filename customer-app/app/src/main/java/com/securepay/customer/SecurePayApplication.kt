package com.securepay.customer

import android.app.Application
import com.securepay.customer.data.remote.ApiModule
import com.securepay.customer.data.remote.DeviceTokenManager
import com.securepay.customer.data.remote.SecurePayApi
import com.securepay.customer.data.repository.DeviceRepository

class SecurePayApplication : Application() {
    val tokenManager: DeviceTokenManager by lazy { DeviceTokenManager(this) }

    val api: SecurePayApi by lazy {
        val secret = tokenManager.imei ?: "unregistered-device"
        ApiModule.provideApi(secret)
    }

    val deviceRepository: DeviceRepository by lazy {
        DeviceRepository(api, tokenManager)
    }
}