package com.saastracker.transport.http.request

import kotlinx.serialization.Serializable

@Serializable
data class MarkNotificationsReadRequest(
    val keys: List<String>
)
