package com.touchbase.agent.data.model

import kotlinx.serialization.Serializable

@Serializable
data class KpiSummary(
    val activeCount: Int = 0,
    val lockedCount: Int = 0,
    val warningCount: Int = 0,
    val paidCount: Int = 0,
    val totalOutstanding: Int = 0,
    val collectedToday: Int = 0,
    val totalAccounts: Int = 0,
    val collectionHistory: List<Int> = emptyList(),
    val outstandingHistory: List<Int> = emptyList()
)
