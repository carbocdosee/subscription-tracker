package com.saastracker.transport.http.response

import com.saastracker.domain.model.NotificationSeverity
import com.saastracker.domain.model.NotificationType
import kotlinx.serialization.Serializable

@Serializable
data class NotificationItemResponse(
    val key: String,
    val type: NotificationType,
    val severity: NotificationSeverity,
    val title: String,
    val message: String,
    val createdAt: String,
    val actionPath: String? = null,
    val actionLabel: String? = null,
    val read: Boolean
)

@Serializable
data class NotificationFeedResponse(
    val items: List<NotificationItemResponse>,
    val unreadCount: Int
)

@Serializable
data class NotificationUnreadCountResponse(
    val unreadCount: Int
)

@Serializable
data class MarkAllNotificationsReadResponse(
    val ok: Boolean,
    val marked: Int
)
