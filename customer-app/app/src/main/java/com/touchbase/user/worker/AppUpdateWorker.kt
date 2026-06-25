package com.touchbase.user.worker

import android.content.Context
import androidx.work.Constraints
import androidx.work.CoroutineWorker
import androidx.work.ExistingPeriodicWorkPolicy
import androidx.work.NetworkType
import androidx.work.PeriodicWorkRequestBuilder
import androidx.work.WorkManager
import androidx.work.WorkerParameters
import com.touchbase.user.SecurePayApplication
import com.touchbase.user.data.remote.ApiModule
import com.touchbase.user.data.remote.DeviceTokenManager
import com.touchbase.user.data.repository.DeviceRepository
import com.touchbase.user.util.SecureLog
import java.util.concurrent.TimeUnit

class AppUpdateWorker(
    context: Context,
    params: WorkerParameters
) : CoroutineWorker(context, params) {

    override suspend fun doWork(): Result {
        val tokenManager = DeviceTokenManager(applicationContext)
        if (!tokenManager.isRegistered) return Result.success()

        return try {
            val app = applicationContext as? SecurePayApplication
            val repository = app?.deviceRepository ?: DeviceRepository(
                ApiModule.provideApi(
                    tokenManager.apiSecret ?: com.touchbase.user.BuildConfig.HMAC_SECRET,
                    tokenManager.accountId ?: tokenManager.imei.orEmpty()
                ),
                tokenManager
            )
            val update = repository.checkForAppUpdate().getOrNull() ?: return Result.retry()
            if (!update.available || update.url.isBlank() || update.sha256Base64.isBlank()) {
                return Result.success()
            }
            SecureLog.i(TAG, "Installing customer app update ${update.versionName} (${update.versionCode})")
            val committed = AppUpdateInstaller.downloadVerifyAndInstall(
                applicationContext,
                update.url,
                update.sha256Base64
            )
            if (committed) Result.success() else Result.retry()
        } catch (e: Exception) {
            SecureLog.e(TAG, "Update check failed", e)
            Result.retry()
        }
    }

    companion object {
        private const val TAG = "AppUpdateWorker"
        private const val WORK_NAME = "securepay_app_update"

        fun schedule(context: Context) {
            val request = PeriodicWorkRequestBuilder<AppUpdateWorker>(
                12, TimeUnit.HOURS,
                1, TimeUnit.HOURS
            ).setConstraints(
                Constraints.Builder()
                    .setRequiredNetworkType(NetworkType.CONNECTED)
                    .build()
            ).build()

            WorkManager.getInstance(context).enqueueUniquePeriodicWork(
                WORK_NAME,
                ExistingPeriodicWorkPolicy.UPDATE,
                request
            )
        }
    }
}
