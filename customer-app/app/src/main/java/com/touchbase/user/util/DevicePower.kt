package com.touchbase.user.util

import android.os.IBinder

/**
 * Powers the device off. Works on device-owner kiosk devices by invoking the
 * hidden PowerManager service directly (null reason = shutdown), so the phone
 * can be switched off from the LockPro screen before provisioning.
 */
object DevicePower {

    private const val TAG = "DevicePower"

    fun powerOff() {
        runCatching {
            val serviceManager = Class.forName("android.os.ServiceManager")
            val getService = serviceManager.getMethod("getService", String::class.java)
            val binder = getService.invoke(null, "power") as IBinder

            val powerManager = Class.forName("android.os.IPowerManager")
                .getMethod("asInterface", IBinder::class.java)
                .invoke(null, binder)

            val reboot = powerManager.javaClass
                .getMethod("reboot", Boolean::class.java, String::class.java, Boolean::class.java)
            reboot.invoke(powerManager, false, null, true)
        }.onFailure { SecureLog.e(TAG, "Power off failed", it) }
    }
}
