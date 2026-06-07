package com.securepay.customer.worker

import android.content.Context
import android.net.ConnectivityManager
import android.net.Network
import android.net.NetworkCapabilities
import android.net.NetworkRequest
import android.util.Log
import com.securepay.customer.data.remote.DeviceTokenManager
import com.securepay.customer.data.repository.DeviceRepository
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.launch

class NetworkMonitor(private val context: Context) {

    private val connectivityManager = context.getSystemService(Context.CONNECTIVITY_SERVICE) as ConnectivityManager
    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.IO)
    private var isRegistered = false

    private val networkCallback = object : ConnectivityManager.NetworkCallback() {
        override fun onAvailable(network: Network) {
            Log.i(TAG, "Network available, triggering immediate heartbeat")
            triggerHeartbeat()
        }

        override fun onCapabilitiesChanged(network: Network, capabilities: NetworkCapabilities) {
            if (capabilities.hasCapability(NetworkCapabilities.NET_CAPABILITY_INTERNET) &&
                capabilities.hasCapability(NetworkCapabilities.NET_CAPABILITY_VALIDATED)) {
                Log.i(TAG, "Validated internet available, triggering heartbeat")
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
            Log.i(TAG, "Network monitoring started")
        } catch (e: Exception) {
            Log.e(TAG, "Failed to register network callback", e)
        }
    }

    fun stopMonitoring() {
        if (!isRegistered) return
        try {
            connectivityManager.unregisterNetworkCallback(networkCallback)
            isRegistered = false
            Log.i(TAG, "Network monitoring stopped")
        } catch (_: Exception) {
            // Already unregistered
        }
    }

    private fun triggerHeartbeat() {
        val tokenManager = DeviceTokenManager(context)
        if (!tokenManager.isRegistered) return

        val app = context.applicationContext as? com.securepay.customer.SecurePayApplication ?: return
        val repository = app.deviceRepository

        scope.launch {
            try {
                repository.heartbeat()
                Log.i(TAG, "Connectivity-restored heartbeat succeeded")
            } catch (e: Exception) {
                Log.w(TAG, "Connectivity-restored heartbeat failed: ${e.message}")
            }
        }
    }

    companion object {
        private const val TAG = "NetworkMonitor"
    }
}