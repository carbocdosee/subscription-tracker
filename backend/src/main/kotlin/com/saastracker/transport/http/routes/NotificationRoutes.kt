package com.saastracker.transport.http.routes

import com.saastracker.domain.model.NotificationType
import com.saastracker.domain.model.UserRole
import com.saastracker.domain.service.NotificationFeed
import com.saastracker.domain.service.NotificationFeedItem
import com.saastracker.domain.service.NotificationService
import com.saastracker.persistence.repository.ClockProvider
import com.saastracker.persistence.repository.CompanyRepository
import com.saastracker.persistence.repository.UserRepository
import com.saastracker.transport.http.request.MarkNotificationsReadRequest
import com.saastracker.transport.http.response.MarkAllNotificationsReadResponse
import com.saastracker.transport.http.response.NotificationFeedResponse
import com.saastracker.transport.http.response.NotificationItemResponse
import com.saastracker.transport.http.response.NotificationUnreadCountResponse
import com.saastracker.transport.payment.StripeBillingService
import com.saastracker.transport.security.authenticatedRoute
import io.ktor.http.HttpStatusCode
import io.ktor.server.application.call
import io.ktor.server.request.receive
import io.ktor.server.response.respond
import io.ktor.server.routing.Route
import io.ktor.server.routing.get
import io.ktor.server.routing.patch
import io.ktor.server.routing.post
import kotlinx.serialization.Serializable

@Serializable
private data class DigestSettingsResponse(
    val weeklyDigestEnabled: Boolean,
    val timezone: String,
    val zombieThresholdDays: Int
)

@Serializable
private data class UpdateDigestSettingsRequest(
    val weeklyDigestEnabled: Boolean? = null,
    val timezone: String? = null,
    val zombieThresholdDays: Int? = null
)

fun Route.notificationRoutes(
    notificationService: NotificationService,
    userRepository: UserRepository,
    companyRepository: CompanyRepository,
    stripeBillingService: StripeBillingService,
    clock: ClockProvider
) {
    authenticatedRoute("/api/v1/notifications/digest-settings", UserRole.VIEWER) {
        get {
            val user = call.requireCurrentUser(userRepository) ?: return@get
            val company = companyRepository.findById(user.companyId)
                ?: return@get call.respond(HttpStatusCode.NotFound)
            call.respond(DigestSettingsResponse(
                weeklyDigestEnabled = company.weeklyDigestEnabled,
                timezone = company.timezone,
                zombieThresholdDays = company.zombieThresholdDays
            ))
        }

        patch {
            val user = call.requireCurrentUser(userRepository) ?: return@patch
            if (user.role != UserRole.ADMIN) {
                call.respond(HttpStatusCode.Forbidden, mapOf("message" to "Admin role required"))
                return@patch
            }
            val company = companyRepository.findById(user.companyId)
                ?: return@patch call.respond(HttpStatusCode.NotFound)
            val req = call.receive<UpdateDigestSettingsRequest>()
            val updated = companyRepository.update(
                company.copy(
                    weeklyDigestEnabled = req.weeklyDigestEnabled ?: company.weeklyDigestEnabled,
                    timezone = req.timezone?.trim()?.ifBlank { null } ?: company.timezone,
                    zombieThresholdDays = req.zombieThresholdDays?.coerceIn(1, 365) ?: company.zombieThresholdDays,
                    updatedAt = clock.nowInstant()
                )
            )
            call.respond(DigestSettingsResponse(
                weeklyDigestEnabled = updated.weeklyDigestEnabled,
                timezone = updated.timezone,
                zombieThresholdDays = updated.zombieThresholdDays
            ))
        }
    }


    authenticatedRoute("/api/v1/notifications", UserRole.VIEWER) {
        get {
            val user = call.requireCurrentUser(userRepository) ?: return@get
            val limit = call.request.queryParameters["limit"]?.toIntOrNull() ?: 20
            require(limit in 1..100) { "limit must be between 1 and 100" }
            val typeFilter = parseTypeFilter(call.request.queryParameters["types"])

            val feed = notificationService.getFeed(
                companyId = user.companyId,
                userId = user.id,
                limit = limit,
                typeFilter = typeFilter
            )
            call.respond(feed.toResponse())
        }

        get("/unread-count") {
            val user = call.requireCurrentUser(userRepository) ?: return@get
            val typeFilter = parseTypeFilter(call.request.queryParameters["types"])
            val unread = notificationService.unreadCount(
                companyId = user.companyId,
                userId = user.id,
                typeFilter = typeFilter
            )
            call.respond(NotificationUnreadCountResponse(unreadCount = unread))
        }

        post("/read") {
            val user = call.requireCurrentUser(userRepository) ?: return@post
            // No plan gate — notification reads are always permitted
            val request = call.receive<MarkNotificationsReadRequest>()
            require(request.keys.isNotEmpty()) { "keys must not be empty" }
            notificationService.markRead(user.id, request.keys)
            call.respond(mapOf("ok" to true))
        }

        post("/read-all") {
            val user = call.requireCurrentUser(userRepository) ?: return@post
            // No plan gate — notification reads are always permitted
            val typeFilter = parseTypeFilter(call.request.queryParameters["types"])
            val marked = notificationService.markAllAsRead(
                companyId = user.companyId,
                userId = user.id,
                typeFilter = typeFilter
            )
            call.respond(MarkAllNotificationsReadResponse(ok = true, marked = marked))
        }
    }
}

private fun parseTypeFilter(raw: String?): Set<NotificationType> {
    if (raw.isNullOrBlank()) return emptySet()
    return raw
        .split(",")
        .map { token ->
            val normalized = token.trim().uppercase()
            require(normalized.isNotBlank()) { "types contains empty value" }
            try {
                NotificationType.valueOf(normalized)
            } catch (_: IllegalArgumentException) {
                throw IllegalArgumentException("Unknown notification type: $normalized")
            }
        }
        .toSet()
}

private fun NotificationFeed.toResponse(): NotificationFeedResponse = NotificationFeedResponse(
    items = items.map { it.toResponse() },
    unreadCount = unreadCount
)

private fun NotificationFeedItem.toResponse(): NotificationItemResponse = NotificationItemResponse(
    key = key,
    type = type,
    severity = severity,
    title = title,
    message = message,
    createdAt = createdAt.toString(),
    actionPath = actionPath,
    actionLabel = actionLabel,
    read = read
)
