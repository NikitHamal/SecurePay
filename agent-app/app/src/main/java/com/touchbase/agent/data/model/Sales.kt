package com.touchbase.agent.data.model

import kotlinx.serialization.Serializable

@Serializable
data class SaleItem(
    val id: String,
    val customerName: String,
    val imei: String,
    val deviceModel: String,
    val planName: String? = null,
    val totalLoanAmount: Int = 0,
    val amountPaid: Int = 0,
    val remainingBalance: Int = 0,
    val dailyRate: Int = 0,
    val nextPaymentDueEpochMillis: Long = 0L,
    val status: String = "ACTIVE",
    val downPayment: Int = 0,
    val releaseApproved: Boolean = false,
    val releaseApprovedAt: Long? = null,
    val releasedAt: Long? = null
)
