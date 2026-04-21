package com.saastracker.transport.http.routes

import com.saastracker.domain.model.PlanFeature
import com.saastracker.domain.model.AuditAction
import com.saastracker.domain.model.SavingsEvent
import com.saastracker.domain.model.SavingsEventType
import com.saastracker.domain.model.AuditEntityType
import com.saastracker.domain.model.AuditLogEntry
import com.saastracker.domain.model.EmailDeliveryLog
import com.saastracker.domain.model.TeamInvitation
import com.saastracker.domain.model.UserRole
import com.saastracker.domain.model.User
import com.saastracker.persistence.repository.AuditLogRepository
import com.saastracker.persistence.repository.ClockProvider
import com.saastracker.persistence.repository.CompanyRepository
import com.saastracker.persistence.repository.EmailDeliveryRepository
import com.saastracker.persistence.repository.IdentityProvider
import com.saastracker.persistence.repository.SavingsEventRepository
import com.saastracker.persistence.repository.SubscriptionRepository
import com.saastracker.persistence.repository.TeamInvitationRepository
import com.saastracker.persistence.repository.UserRepository
import com.saastracker.transport.payment.StripeBillingService
import com.saastracker.transport.http.request.OnboardingSettings
import com.saastracker.transport.http.request.UpdateCompanyRequest
import com.saastracker.transport.http.response.CompanySettingsResponse
import com.saastracker.transport.http.response.EmailDeliveryStatusResponse
import com.saastracker.transport.http.response.InvitationDeliveryEntryResponse
import com.saastracker.transport.http.response.InvitationDeliveryHistoryResponse
import com.saastracker.transport.http.response.OnboardingSettingsResponse
import com.saastracker.transport.http.response.TeamAuditLogItemResponse
import com.saastracker.transport.http.response.TeamAuditLogResponse
import com.saastracker.transport.http.response.TeamInvitationResponse
import com.saastracker.transport.http.response.TeamInvitationsResponse
import com.saastracker.transport.http.response.TeamMemberResponse
import com.saastracker.transport.http.response.TeamMembersResponse
import com.saastracker.transport.security.authenticatedRoute
import io.ktor.http.HttpStatusCode
import io.ktor.server.application.call
import io.ktor.server.request.receive
import io.ktor.server.response.respond
import io.ktor.server.routing.Route
import io.ktor.server.routing.delete
import io.ktor.server.routing.get
import io.ktor.server.routing.patch
import kotlinx.serialization.Serializable
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.JsonArray
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.JsonPrimitive
import kotlinx.serialization.json.buildJsonArray
import kotlinx.serialization.json.buildJsonObject
import kotlinx.serialization.json.booleanOrNull
import kotlinx.serialization.json.jsonPrimitive
import kotlinx.serialization.json.put
import kotlinx.serialization.json.add
import java.math.BigDecimal
import java.util.UUID

@Serializable
data class OffboardingResponse(
    val userId: String,
    val archivedSubscriptions: Int
)

fun Route.teamRoutes(
    userRepository: UserRepository,
    auditLogRepository: AuditLogRepository,
    invitationRepository: TeamInvitationRepository,
    emailDeliveryRepository: EmailDeliveryRepository,
    clock: ClockProvider,
    companyRepository: CompanyRepository,
    stripeBillingService: StripeBillingService,
    subscriptionRepository: SubscriptionRepository,
    savingsEventRepository: SavingsEventRepository,
    idProvider: IdentityProvider
) {
    authenticatedRoute("/api/v1/team", UserRole.VIEWER) {
        get("/members") {
            val user = call.requireCurrentUser(userRepository) ?: return@get
            // No plan gate — team read-only access is available on all plans
            val members = userRepository.listByCompany(user.companyId).map { it.toResponse() }
            call.respond(TeamMembersResponse(items = members))
        }

        get("/audit-log") {
            val user = call.requireCurrentUser(userRepository) ?: return@get
            // No plan gate — team read-only access is available on all plans
            val logs = auditLogRepository.listByCompany(user.companyId).map { it.toResponse() }
            call.respond(TeamAuditLogResponse(items = logs))
        }

        get("/invitations") {
            val user = call.requireCurrentUser(userRepository) ?: return@get
            // No plan gate — team read-only access is available on all plans
            val origin = call.publicOrigin()
            val includeInviteLink = user.role == UserRole.ADMIN
            val invitations = invitationRepository.listActiveByCompany(user.companyId, clock.nowInstant()).map {
                it.toResponse(
                    acceptInviteUrl = if (includeInviteLink) {
                        "$origin/accept-invite?token=${it.token}"
                    } else {
                        null
                    }
                )
            }
            call.respond(TeamInvitationsResponse(items = invitations))
        }
    }

    authenticatedRoute("/api/v1/team", UserRole.ADMIN) {
        get("/invitations/{id}/delivery-history") {
            val user = call.requireCurrentUser(userRepository) ?: return@get
            // No plan gate — team read-only access is available on all plans
            val invitationId = call.requireUuidParam("id") ?: return@get
            val invitation = invitationRepository.findById(invitationId)
                ?: throw NoSuchElementException("Invitation not found")
            if (invitation.companyId != user.companyId) {
                call.respond(HttpStatusCode.Forbidden, mapOf("message" to "Cross-company access denied"))
                return@get
            }
            val items = emailDeliveryRepository.listByInvitation(invitationId, limit = 10).map { it.toDeliveryResponse() }
            call.respond(InvitationDeliveryHistoryResponse(items = items))
        }

        get("/members/{userId}/subscriptions") {
            val user = call.requireCurrentUser(userRepository) ?: return@get
            val targetId = call.requireUuidParam("userId") ?: return@get
            val target = userRepository.findById(targetId)
            if (target == null || target.companyId != user.companyId) {
                call.respond(HttpStatusCode.NotFound, mapOf("message" to "User not found"))
                return@get
            }
            val subs = subscriptionRepository.listActiveByOwner(user.companyId, targetId)
            call.respond(mapOf("subscriptions" to subs.map { mapOf("id" to it.id.toString(), "vendorName" to it.vendorName, "amount" to it.amount.toPlainString(), "currency" to it.currency, "billingCycle" to it.billingCycle.name) }))
        }

        delete("/members/{userId}") {
            val user = call.requireCurrentUser(userRepository) ?: return@delete
            val targetId = call.requireUuidParam("userId") ?: return@delete
            if (targetId == user.id) {
                call.respond(HttpStatusCode.BadRequest, mapOf("message" to "Cannot offboard yourself"))
                return@delete
            }
            val target = userRepository.findById(targetId)
            if (target == null || target.companyId != user.companyId) {
                call.respond(HttpStatusCode.NotFound, mapOf("message" to "User not found"))
                return@delete
            }
            val archiveSubscriptions = call.request.queryParameters["archiveSubscriptions"] == "true"
            val now = clock.nowInstant()
            var archivedCount = 0
            if (archiveSubscriptions) {
                val ownedSubs = subscriptionRepository.listActiveByOwner(user.companyId, targetId)
                for (sub in ownedSubs) {
                    subscriptionRepository.update(sub.copy(archivedAt = now, archivedById = user.id, updatedAt = now))
                    if (sub.isZombie) {
                        savingsEventRepository.record(
                            SavingsEvent(
                                id = idProvider.newId(),
                                companyId = sub.companyId,
                                subscriptionId = sub.id,
                                eventType = SavingsEventType.ZOMBIE_ARCHIVED,
                                vendorName = sub.vendorName,
                                amount = sub.amount,
                                currency = sub.currency,
                                savedAt = now
                            )
                        )
                    }
                    archivedCount++
                }
            }
            userRepository.deactivate(targetId)
            auditLogRepository.append(
                AuditLogEntry(
                    id = UUID.randomUUID(),
                    companyId = user.companyId,
                    userId = user.id,
                    action = AuditAction.DELETED,
                    entityType = AuditEntityType.USER,
                    entityId = targetId,
                    oldValue = null,
                    newValue = """{"offboarded":true,"archivedSubscriptions":$archivedCount}""",
                    createdAt = now
                )
            )
            call.respond(OffboardingResponse(userId = targetId.toString(), archivedSubscriptions = archivedCount))
        }
    }

    authenticatedRoute("/api/v1/company", UserRole.VIEWER) {
        get {
            val user = call.requireCurrentUser(userRepository) ?: return@get
            // No plan gate — team read-only access is available on all plans
            val company = companyRepository.findById(user.companyId)
                ?: return@get call.respond(HttpStatusCode.NotFound)
            call.respond(company.toCompanySettingsResponse())
        }
    }

    authenticatedRoute("/api/v1/company", UserRole.ADMIN) {
        patch {
            val user = call.requireCurrentUser(userRepository) ?: return@patch
            // No plan gate — company settings are accessible on all plans
            val req = call.receive<UpdateCompanyRequest>()
            val company = companyRepository.findById(user.companyId)
                ?: return@patch call.respond(HttpStatusCode.NotFound)
            val updatedSettings = req.onboarding
                ?.let { mergeOnboardingSettings(company.settings, it) }
                ?: company.settings
            val updated = companyRepository.update(
                company.copy(
                    monthlyBudget = req.monthlyBudget?.let { BigDecimal(it) } ?: company.monthlyBudget,
                    employeeCount = req.employeeCount ?: company.employeeCount,
                    settings = updatedSettings,
                    weeklyDigestEnabled = req.weeklyDigestEnabled ?: company.weeklyDigestEnabled,
                    timezone = req.timezone?.trim()?.ifBlank { null } ?: company.timezone,
                    zombieThresholdDays = req.zombieThresholdDays?.coerceIn(1, 365) ?: company.zombieThresholdDays,
                    updatedAt = clock.nowInstant()
                )
            )
            if (req.onboarding?.completed == true) {
                auditLogRepository.append(
                    AuditLogEntry(
                        id = UUID.randomUUID(),
                        companyId = user.companyId,
                        userId = user.id,
                        action = AuditAction.UPDATED,
                        entityType = AuditEntityType.COMPANY,
                        entityId = user.companyId,
                        oldValue = null,
                        newValue = """{"onboarding_completed":true}""",
                        createdAt = clock.nowInstant()
                    )
                )
            }
            call.respond(updated.toCompanySettingsResponse())
        }
    }
}

private fun mergeOnboardingSettings(currentSettings: String, onboarding: OnboardingSettings): String {
    val settingsObj = try {
        Json.parseToJsonElement(currentSettings) as? JsonObject ?: JsonObject(emptyMap())
    } catch (_: Exception) {
        JsonObject(emptyMap())
    }
    val current = settingsObj["onboarding"] as? JsonObject ?: JsonObject(emptyMap())
    val merged = buildJsonObject {
        current.entries.forEach { (k, v) -> put(k, v) }
        onboarding.completed?.let { put("completed", JsonPrimitive(it)) }
        onboarding.completedSteps?.let { steps ->
            put("completedSteps", buildJsonArray { steps.forEach { add(JsonPrimitive(it)) } })
        }
        onboarding.startedAt?.let { put("startedAt", JsonPrimitive(it)) }
    }
    return buildJsonObject {
        settingsObj.entries.forEach { (k, v) -> put(k, v) }
        put("onboarding", merged)
    }.toString()
}

private fun com.saastracker.domain.model.Company.toCompanySettingsResponse(): CompanySettingsResponse {
    val settingsObj = try {
        Json.parseToJsonElement(settings) as? JsonObject ?: JsonObject(emptyMap())
    } catch (_: Exception) {
        JsonObject(emptyMap())
    }
    val onboardingObj = settingsObj["onboarding"] as? JsonObject
    val onboarding = onboardingObj?.let {
        OnboardingSettingsResponse(
            completed = (it["completed"] as? JsonPrimitive)?.booleanOrNull ?: false,
            completedSteps = (it["completedSteps"] as? JsonArray)
                ?.map { el -> (el as JsonPrimitive).content }
                ?: emptyList(),
            startedAt = (it["startedAt"] as? JsonPrimitive)?.content
        )
    }
    return CompanySettingsResponse(
        monthlyBudget = monthlyBudget?.toPlainString(),
        employeeCount = employeeCount,
        zombieThresholdDays = zombieThresholdDays,
        onboarding = onboarding
    )
}

private fun User.toResponse(): TeamMemberResponse = TeamMemberResponse(
    id = id.toString(),
    email = email,
    name = name,
    role = role,
    isActive = isActive
)

private fun AuditLogEntry.toResponse(): TeamAuditLogItemResponse = TeamAuditLogItemResponse(
    id = id.toString(),
    action = action.name,
    entityType = entityType.name,
    entityId = entityId.toString(),
    userId = userId.toString(),
    oldValue = oldValue,
    newValue = newValue,
    createdAt = createdAt.toString()
)

private fun TeamInvitation.toResponse(acceptInviteUrl: String? = null): TeamInvitationResponse = TeamInvitationResponse(
    id = id.toString(),
    email = email,
    role = role,
    expiresAt = expiresAt.toString(),
    acceptedAt = acceptedAt?.toString(),
    createdAt = createdAt.toString(),
    acceptInviteUrl = acceptInviteUrl
)

private fun EmailDeliveryLog.toDeliveryResponse(): InvitationDeliveryEntryResponse = InvitationDeliveryEntryResponse(
    id = id.toString(),
    invitationId = invitationId?.toString(),
    recipientEmail = recipientEmail,
    status = when (status) {
        com.saastracker.domain.model.EmailDeliveryState.SENT -> EmailDeliveryStatusResponse.SENT
        com.saastracker.domain.model.EmailDeliveryState.SKIPPED_NOT_CONFIGURED -> EmailDeliveryStatusResponse.SKIPPED_NOT_CONFIGURED
        com.saastracker.domain.model.EmailDeliveryState.FAILED -> EmailDeliveryStatusResponse.FAILED
    },
    providerMessageId = providerMessageId,
    providerStatusCode = providerStatusCode,
    message = errorMessage ?: providerResponse,
    createdAt = createdAt.toString()
)

private fun io.ktor.server.application.ApplicationCall.publicOrigin(): String {
    val scheme = request.headers["X-Forwarded-Proto"] ?: "http"
    val host = request.headers["X-Forwarded-Host"]
        ?: request.headers["Host"]
        ?: "localhost"
    return "$scheme://$host"
}
