package com.securepay.customer

import android.app.Application
import com.securepay.customer.data.repository.DeviceRepository
import com.securepay.customer.data.repository.MockDeviceRepository

/**
 * Process-wide container for the (otherwise DI-injected) repository singleton so
 * the account StateFlow survives configuration changes and is shared between the
 * dashboard and lock surfaces.
 */
class SecurePayApplication : Application() {
    val deviceRepository: DeviceRepository by lazy { MockDeviceRepository() }
}
