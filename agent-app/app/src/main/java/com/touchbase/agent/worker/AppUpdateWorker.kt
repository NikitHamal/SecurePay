package com.touchbase.agent.worker

import android.content.Context
import androidx.work.Constraints
import androidx.work.CoroutineWorker
import androidx.work.ExistingPeriodicWorkPolicy
import androidx.work.ExistingWorkPolicy
import androidx.work.OneTimeWorkRequestBuilder
import androidx.work.NetworkType
import androidx.work.PeriodicWorkRequestBuilder
import androidx.work.WorkManager
import androidx.work.WorkerParameters
import com.touchbase.agent.BuildConfig
import com.touchbase.agent.admin.SecureLog
import com.touchbase.agent.data.remote.ApiModule
import com.touchbase.agent.data.remote.SecurePayRepository
import com.touchbase.agent.data.remote.TokenManager
import java.util.concurrent.TimeUnit

class AppUpdateWorker(
    context: Context,
    params: WorkerParameters
) : CoroutineWorker(context, params) {

    override suspend fun doWork(): Result {
        val tokenManager = TokenManager(applicationContext)
        if (!tokenManager.isLoggedIn) return Result.success()

        return try {
            val repository = SecurePayRepository(
                ApiModule.provideApi(tokenManager),
                tokenManager
            )
            val update = repository.checkForAppUpdate().getOrNull() ?: return Result.retry()
            if (!update.available || update.url.isBlank() || update.sha256Base64.isBlank()) {
                return Result.success()
            }
            SecureLog.i(TAG, "Installing agent app update ${update.versionName} (${update.versionCode})")
            val committed = AppUpdateInstaller.downloadVerifyAndInstall(
                applicationContext,
                update.url,
                update.sha256Base64,
                update.signatureChecksumBase64
            )
            if (committed) Result.success() else Result.retry()
        } catch (e: Exception) {
            SecureLog.e(TAG, "Update check failed", e)
            Result.retry()
        }
    }

    companion object {
        private const val TAG = "AgentAppUpdateWorker"
        private const val WORK_NAME = "agent_app_update"

        fun runNow(context: Context) {
            val request = OneTimeWorkRequestBuilder<AppUpdateWorker>()
                .setConstraints(networkConstraints())
                .build()
            WorkManager.getInstance(context).enqueueUniqueWork(
                "${WORK_NAME}_immediate",
                ExistingWorkPolicy.REPLACE,
                request
            )
        }

        fun schedule(context: Context) {
            val request = PeriodicWorkRequestBuilder<AppUpdateWorker>(
                12, TimeUnit.HOURS,
                1, TimeUnit.HOURS
            ).setConstraints(networkConstraints()).build()

            WorkManager.getInstance(context).enqueueUniquePeriodicWork(
                WORK_NAME,
                ExistingPeriodicWorkPolicy.UPDATE,
                request
            )
        }

        private fun networkConstraints(): Constraints = Constraints.Builder()
            .setRequiredNetworkType(NetworkType.CONNECTED)
            .build()
    }
}
