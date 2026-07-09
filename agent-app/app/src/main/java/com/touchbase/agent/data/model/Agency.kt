package com.touchbase.agent.data.model

import kotlinx.serialization.Serializable

@Serializable
data class Agency(
    val id: String,
    val name: String,
    val ownerId: String,
    val phone: String? = null,
    val region: String? = null,
    val isActive: Boolean = true,
    val createdAt: Long = 0L
)

@Serializable
data class Branch(
    val id: String,
    val name: String,
    val agencyId: String,
    val adminId: String? = null,
    val address: String? = null,
    val phone: String? = null,
    val isActive: Boolean = true,
    val createdAt: Long = 0L
)
