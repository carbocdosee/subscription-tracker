package com.saastracker.transport.http.request

import com.saastracker.domain.model.UserRole
import kotlinx.serialization.Serializable

@Serializable
data class RegisterRequest(
    val companyName: String,
    val companyDomain: String,
    val fullName: String,
    val email: String,
    val password: String,
    val monthlyBudget: String? = null
)

@Serializable
data class LoginRequest(
    val email: String,
    val password: String
)

@Serializable
data class InviteMemberRequest(
    val email: String,
    val role: UserRole
)

@Serializable
data class AcceptInviteRequest(
    val token: String,
    val fullName: String,
    val password: String
)

@Serializable
data class UpdateUserRoleRequest(
    val role: UserRole
)

@Serializable
data class OnboardingSettings(
    val completed: Boolean? = null,
    val completedSteps: List<String>? = null,
    val startedAt: String? = null
)

@Serializable
data class UpdateCompanyRequest(
    val monthlyBudget: String? = null,
    val employeeCount: Int? = null,
    val onboarding: OnboardingSettings? = null
)

