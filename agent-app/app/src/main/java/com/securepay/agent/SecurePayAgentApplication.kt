package com.securepay.agent

import android.app.Application
import com.securepay.agent.admin.SecurityChecker
import com.securepay.agent.data.remote.ApiModule
import com.securepay.agent.data.remote.SecurePayRepository
import com.securepay.agent.data.remote.TokenManager

class SecurePayAgentApplication : Application() {

    val tokenManager: TokenManager by lazy { TokenManager(this) }

    val repository: SecurePayRepository by lazy {
        val api = ApiModule.provideApi(tokenManager)
        SecurePayRepository(api, tokenManager)
    }

    var securityReport: SecurityChecker.SecurityReport? = null
        private set

    override fun onCreate() {
        super.onCreate()
        securityReport = SecurityChecker.runAllChecks(this)
    }
}