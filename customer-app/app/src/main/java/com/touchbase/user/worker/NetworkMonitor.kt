package com.touchbase.user.worker

import android.content.Context
import android.net.ConnectivityManager
import android.net.Network
import android.net.NetworkCapabilities
import android.net.NetworkRequest
import com.touchbase.user.util.SecureLog
import com.touchbase.user.data.remote.DeviceTokenManager
import com.touchbase.user.data.repository.DeviceRepository
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.launch
import kotlinx.coroutines.cancel
import java.util.concurrent.atomic.AtomicBoolean
import java.util.concurrent.atomic.AtomicLong

class NetworkMonitor(private val context: Context) {

    private val connectivityManager = context.getSystemService(Context.CONNECTIVITY_SERVICE) as ConnectivityManager
    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.IO)
    private var isRegistered = false
    private val heartbeatInFlight = AtomicBoolean(false)
    private val lastHeartbeatAt = AtomicLong(0L)

    private val networkCallback = object : ConnectivityManager.NetworkCallback() {
        override fun onAvailable(network: Network) {
            SecureLog.i(TAG, "Network available, triggering immediate heartbeat")
            triggerHeartbeat()
        }

        override fun onCapabilitiesChanged(network: Network, capabilities: NetworkCapabilities) {
            if (capabilities.hasCapability(NetworkCapabilities.NET_CAPABILITY_INTERNET) &&
                capabilities.hasCapability(NetworkCapabilities.NET_CAPABILITY_VALIDATED)) {
                SecureLog.i(TAG, "Validated internet available, triggering heartbeat")
                triggerHeartbeat()
            }
        }
    }

    fun startMonitoring() {
        if (isRegistered) return

        val request = NetworkRequest.Builder()
            .addCapability(NetworkCapabilities.NET_CAPABILITY_INTERNET)
            .addCapability(NetworkCapabilities.NET_CAPABILITY_VALIDATED)
            .build()

        try {
            connectivityManager.registerNetworkCallback(request, networkCallback)
            isRegistered = true
            SecureLog.i(TAG, "Network monitoring started")
        } catch (e: Exception) {
            SecureLog.e(TAG, "Failed to register network callback", e)
        }
    }

    fun stopMonitoring() {
        if (!isRegistered) return
        try {
            connectivityManager.unregisterNetworkCallback(networkCallback)
            isRegistered = false
            SecureLog.i(TAG, "Network monitoring stopped")
        } catch (_: Exception) {
            // Already unregistered
        }
    }

    private fun triggerHeartbeat() {
        val tokenManager = DeviceTokenManager(context)
        if (!tokenManager.isRegistered) return

        val now = android.os.SystemClock.elapsedRealtime()
        val previous = lastHeartbeatAt.get()
        if (now - previous < MIN_TRIGGER_INTERVAL_MS) return
        if (!lastHeartbeatAt.compareAndSet(previous, now)) return
        if (!heartbeatInFlight.compareAndSet(false, true)) return

        val app = context.applicationContext as? com.touchbase.user.SecurePayApplication
        if (app == null) {
            heartbeatInFlight.set(false)
            return
        }
        val repository = app.deviceRepository

        scope.launch {
            try {
                val result = repository.heartbeat()
                if (result.isSuccess) {
                    SecureLog.i(TAG, "Connectivity-restored heartbeat succeeded")
                } else {
                    SecureLog.w(TAG, "Connectivity-restored heartbeat failed: ${result.exceptionOrNull()?.message}")
                }
            } finally {
                heartbeatInFlight.set(false)
            }
        }
    }

    companion object {
        private const val TAG = "NetworkMonitor"
        private const val MIN_TRIGGER_INTERVAL_MS = 20_000L
    }
}
