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

            // Signature is void reboot(boolean confirm, String reason, boolean wait)
            // (primitive booleans — using Boolean.class would throw NoSuchMethodException).
            invokeReboot(powerManager)
        }.onFailure {
            SecureLog.e(TAG, "Power off failed", it)
            runCatching { SecureLog.e(TAG, "Reboot method variants: ${android.os.PowerManager::class.java.name}") }
        }
    }

    private fun invokeReboot(powerManager: Any) {
        val boolT = java.lang.Boolean.TYPE
        val stringT = String::class.java
        val rebootMethods = powerManager.javaClass.methods.filter { it.name == "reboot" }

        if (rebootMethods.isEmpty()) {
            SecureLog.e(TAG, "No reboot method found on ${powerManager.javaClass.name}")
            return
        }

        // Try the standard 3-arg signature first, then fall back to other variants.
        val exact = rebootMethods.firstOrNull {
            it.parameterTypes.contentEquals(arrayOf(boolT, stringT, boolT))
        }
        val method = exact ?: rebootMethods.first()
        SecureLog.i(TAG, "Invoking ${method.toGenericString()}")

        val expected = arrayOf(boolT, stringT, boolT)
        val args = if (method.parameterTypes.contentEquals(expected)) {
            arrayOf(false, null, true)
        } else {
            // Unknown signature — fill positional args conservatively.
            Array<Any?>(method.parameterCount) { null }.also { a ->
                a[0] = false
                if (a.size >= 3) a[2] = true
            }
        }
        method.invoke(powerManager, *args)
    }
}
