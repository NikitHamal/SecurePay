package com.touchbase.user.ui.lock

import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.lifecycle.lifecycleScope
import com.touchbase.user.SecurePayApplication
import com.touchbase.user.admin.DevicePolicyController
import com.touchbase.user.data.model.DeviceStatus
import com.touchbase.user.domain.RemainingTime
import com.touchbase.user.ui.theme.SecurePayTheme
import com.touchbase.user.util.SecureLog
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.collectLatest
import kotlinx.coroutines.launch

class LockTaskActivity : ComponentActivity() {

    private lateinit var policyController: DevicePolicyController

    private val uiState = MutableStateFlow(LockTaskUiState())
    private var isSyncing = false

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        enableEdgeToEdge()

        policyController = DevicePolicyController(this)

        SecureLog.w(TAG, "LockTaskActivity created — entering pinned lock mode")
        policyController.startLockTask(this)
        policyController.enforceLock()

        val app = application as SecurePayApplication
        val repository = app.deviceRepository

        setContent {
            SecurePayTheme {
                val state by uiState.collectAsState()

                val trustedNow = remember { mutableStateOf(repository.trustedTime) }
                LaunchedEffect(Unit) {
                    while (true) {
                        trustedNow.value = repository.trustedTime
                        updateRemaining(repository, trustedNow.value)
                        delay(1_000)
                    }
                }

                LaunchedEffect(repository) {
                    repository.account.collectLatest { account ->
                        if (account != null) {
                            val now = repository.trustedTime
                            val status = DeviceStatus.evaluate(
                                account.nextPaymentDueEpochMillis,
                                account.lockedByDealer,
                                now
                            )
                            uiState.value = uiState.value.copy(
                                account = account,
                                remaining = RemainingTime.until(account.nextPaymentDueEpochMillis, now)
                            )
                            if (status != DeviceStatus.LOCKED) {
                                SecureLog.i(TAG, "Status no longer LOCKED — releasing lock")
                                releaseAndFinish()
                            }
                        }
                    }
                }

                LockTaskScreen(
                    state = state,
                    onSync = {
                        if (!isSyncing) {
                            isSyncing = true
                            uiState.value = uiState.value.copy(isSyncing = true, message = "Syncing with server…")
                            lifecycleScope.launch {
                                runCatching { repository.heartbeat() }
                                    .onSuccess {
                                        val now = repository.trustedTime
                                        updateRemaining(repository, now)
                                        uiState.value = uiState.value.copy(
                                            isSyncing = false,
                                            message = "Synced. If you've paid, your device will unlock shortly."
                                        )
                                    }
                                    .onFailure {
                                        uiState.value = uiState.value.copy(
                                            isSyncing = false,
                                            message = "Sync failed. Please check your connection and retry."
                                        )
                                    }
                                isSyncing = false
                            }
                        }
                    }
                )
            }
        }
    }

    private fun updateRemaining(
        repository: com.touchbase.user.data.repository.DeviceRepository,
        now: Long
    ) {
        val account = repository.account.value
        val due = account?.nextPaymentDueEpochMillis ?: repository.cachedNextPaymentDue
        if (due > 0L) {
            uiState.value = uiState.value.copy(remaining = RemainingTime.until(due, now))
        }
    }

    private fun releaseAndFinish() {
        policyController.releaseRestrictions()
        policyController.stopLockTask(this)
    }

    override fun onDestroy() {
        super.onDestroy()
        SecureLog.w(TAG, "LockTaskActivity destroyed")
    }

    companion object {
        private const val TAG = "LockTaskActivity"
    }
}
