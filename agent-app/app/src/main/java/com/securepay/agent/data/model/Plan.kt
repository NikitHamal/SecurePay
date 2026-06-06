package com.securepay.agent.data.model

import kotlinx.serialization.Serializable

@Serializable
data class Plan(
    val id: String,
    val name: String,
    val termDays: Int,
    val totalAmount: Int,
    val dailyRate: Int,
    val minDownPayment: Int,
    val createdAt: Long = 0L
)