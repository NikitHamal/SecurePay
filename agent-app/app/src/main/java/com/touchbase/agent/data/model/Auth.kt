package com.touchbase.agent.data.model

import kotlinx.serialization.Serializable

@Serializable
data class LoginRequest(
    val email: String,
    val password: String
)

@Serializable
data class LoginResponse(
    val token: String,
    val dealer: DealerInfo
)

@Serializable
data class DealerInfo(
    val id: String,
    val name: String,
    val email: String,
    val role: String = "AGENT",
    val agencyId: String? = null,
    val branchId: String? = null
)

@Serializable
data class RegisterAgentRequest(
    val fullName: String,
    val email: String,
    val phone: String,
    val password: String,
    val requestedBranchId: String? = null
)

@Serializable
data class RegisterAgentResponse(
    val message: String,
    val requestId: String
)

@Serializable
data class VerifyRequest(
    val vendorData: String
)

@Serializable
data class VerifyResponse(
    val url: String? = null,
    val sessionId: String? = null,
    val sessionToken: String? = null
)
