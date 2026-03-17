package com.saastracker.transport.http.response

import com.saastracker.domain.model.UserRole
import kotlinx.serialization.Serializable

@Serializable
enum class EmailDeliveryStatusResponse {
    SENT,
    SKIPPED_NOT_CONFIGURED,
    FAILED
}

@Serializable
data class EmailDeliveryResponse(
    val status: EmailDeliveryStatusResponse,
    val message: String? = null,
    val providerMessageId: String? = null,
    val providerStatusCode: Int? = null
)

@Serializable
data class TeamMemberResponse(
    val id: String,
    val email: String,
    val name: String,
    val role: UserRole,
    val isActive: Boolean
)

@Serializable
data class TeamMembersResponse(
    val items: List<TeamMemberResponse>
)

@Serializable
data class TeamInviteResponse(
    val invitation: TeamInvitationResponse,
    val reusedExisting: Boolean,
    val acceptInviteUrl: String,
    val emailDelivery: EmailDeliveryResponse
)

@Serializable
data class TeamInvitationResponse(
    val id: String,
    val email: String,
    val role: UserRole,
    val expiresAt: String,
    val acceptedAt: String?,
    val createdAt: String,
    val acceptInviteUrl: String? = null
)

@Serializable
data class TeamAuditLogItemResponse(
    val id: String,
    val action: String,
    val entityType: String,
    val entityId: String,
    val userId: String,
    val oldValue: String?,
    val newValue: String?,
    val createdAt: String
)

@Serializable
data class TeamAuditLogResponse(
    val items: List<TeamAuditLogItemResponse>
)

@Serializable
data class TeamInvitationsResponse(
    val items: List<TeamInvitationResponse>
)

@Serializable
data class InvitationDeliveryEntryResponse(
    val id: String,
    val invitationId: String?,
    val recipientEmail: String,
    val status: EmailDeliveryStatusResponse,
    val providerMessageId: String?,
    val providerStatusCode: Int?,
    val message: String?,
    val createdAt: String
)

@Serializable
data class InvitationDeliveryHistoryResponse(
    val items: List<InvitationDeliveryEntryResponse>
)

@Serializable
data class OnboardingSettingsResponse(
    val completed: Boolean,
    val completedSteps: List<String>,
    val startedAt: String? = null
)

@Serializable
data class CompanySettingsResponse(
    val monthlyBudget: String?,
    val employeeCount: Int?,
    val onboarding: OnboardingSettingsResponse? = null
)
