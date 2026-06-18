package com.touchbase.agent.data.model

import kotlinx.serialization.Serializable

@Serializable
data class LedgerEntry(
    val id: String = "",
    val accountId: String = "",
    val customerName: String = "",
    val imei: String = "",
    val amount: Int = 0,
    val dateEpochMillis: Long = 0L,
    val method: String = "",
    val reference: String = ""
)
