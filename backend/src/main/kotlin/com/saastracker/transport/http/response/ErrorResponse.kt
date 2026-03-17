package com.saastracker.transport.http.response

import kotlinx.serialization.Serializable

@Serializable
data class ErrorResponse(
    val message: String,
    val requestId: String? = null
)

