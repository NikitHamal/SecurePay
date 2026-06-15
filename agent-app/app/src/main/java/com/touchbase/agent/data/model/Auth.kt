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
    val email: String
)
