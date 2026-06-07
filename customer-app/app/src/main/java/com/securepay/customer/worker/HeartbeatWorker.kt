package com.securepay.customer.worker

import android.content.Context
import android.util.Log
import androidx.work.CoroutineWorker
import androidx.work.ExistingPeriodicWorkPolicy
import androidx.work.PeriodicWorkRequestBuilder
import androidx.work.WorkManager
import androidx.work.WorkerParameters
import com.securepay.customer.data.remote.DeviceTokenManager
import com.securepay.customer.data.repository.DeviceRepository
import java.util.concurrent.TimeUnit

class HeartbeatWorker(
    context: Context,
    params: WorkerParameters
) : CoroutineWorker(context, params) {

    override suspend fun doWork(): Result {
        val tokenManager = DeviceTokenManager(applicationContext)
        val accountId = tokenManager.accountId
        val imei = tokenManager.imei

        if (accountId == null || imei == null) {
            Log.w(TAG, "Not registered, skipping heartbeat")
            return Result.success()
        }

        return try {
            val app = applicationContext as? com.securepay.customer.SecurePayApplication
            val repository = app?.deviceRepository ?: run {
                val api = com.securepay.customer.data.remote.ApiModule.provideApi(imei)
                DeviceRepository(api, tokenManager)
            }
            repository.heartbeat()
            Log.i(TAG, "Heartbeat successful")
            Result.success()
        } catch (e: Exception) {
            Log.e(TAG, "Heartbeat failed, will retry", e)
            Result.retry()
        }
    }

    companion object {
        private const val TAG = "HeartbeatWorker"
        private const val WORK_NAME = "securepay_heartbeat"

        fun schedule(context: Context) {
            val request = PeriodicWorkRequestBuilder<HeartbeatWorker>(
                4, TimeUnit.HOURS,
                30, TimeUnit.MINUTES
            )
                .setConstraints(
                    androidx.work.Constraints.Builder()
                        .setRequiredNetworkType(androidx.work.NetworkType.CONNECTED)
                        .build()
                )
                .build()

            WorkManager.getInstance(context).enqueueUniquePeriodicWork(
                WORK_NAME,
                ExistingPeriodicWorkPolicy.KEEP,
                request
            )
        }
    }
}