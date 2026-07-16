package com.touchbase.agent.data.model

import kotlinx.serialization.Serializable

@Serializable
data class ApiNotification(
    val id: String,
    val recipientId: String,
    val type: String,
    val title: String,
    val message: String,
    val isRead: Boolean = false,
    val relatedEntityType: String? = null,
    val relatedEntityId: String? = null,
    val createdAt: Long = 0L
)

@Serializable
data class MarkReadRequest(
    val ids: List<String>
)
