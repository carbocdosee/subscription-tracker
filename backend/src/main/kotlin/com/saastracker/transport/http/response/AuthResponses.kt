package com.saastracker.transport.http.response

import com.saastracker.domain.model.UserRole
import kotlinx.serialization.Serializable

@Serializable
data class AuthResponse(
    val accessToken: String,
    val tokenType: String = "Bearer",
    val user: UserSummaryResponse
)

@Serializable
data class UserSummaryResponse(
    val id: String,
    val companyId: String,
    val email: String,
    val name: String,
    val role: UserRole
)

