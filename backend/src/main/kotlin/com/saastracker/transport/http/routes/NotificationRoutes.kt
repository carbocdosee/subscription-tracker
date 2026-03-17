package com.saastracker.transport.http.routes

import com.saastracker.domain.model.NotificationType
import com.saastracker.domain.model.UserRole
import com.saastracker.domain.service.NotificationFeed
import com.saastracker.domain.service.NotificationFeedItem
import com.saastracker.domain.service.NotificationService
import com.saastracker.persistence.repository.CompanyRepository
import com.saastracker.persistence.repository.UserRepository
import com.saastracker.transport.http.request.MarkNotificationsReadRequest
import com.saastracker.transport.http.response.MarkAllNotificationsReadResponse
import com.saastracker.transport.http.response.NotificationFeedResponse
import com.saastracker.transport.http.response.NotificationItemResponse
import com.saastracker.transport.http.response.NotificationUnreadCountResponse
import com.saastracker.transport.payment.StripeBillingService
import com.saastracker.transport.security.authenticatedRoute
import io.ktor.server.application.call
import io.ktor.server.request.receive
import io.ktor.server.response.respond
import io.ktor.server.routing.Route
import io.ktor.server.routing.get
import io.ktor.server.routing.post

fun Route.notificationRoutes(
    notificationService: NotificationService,
    userRepository: UserRepository,
    companyRepository: CompanyRepository,
    stripeBillingService: StripeBillingService
) {
    authenticatedRoute("/api/v1/notifications", UserRole.VIEWER) {
        get {
            val user = call.requireCurrentUser(userRepository) ?: return@get
            if (!call.ensureBillingAccess(user, companyRepository, stripeBillingService)) return@get

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
            if (!call.ensureBillingAccess(user, companyRepository, stripeBillingService)) return@get
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
            if (!call.ensureBillingAccess(user, companyRepository, stripeBillingService)) return@post
            val request = call.receive<MarkNotificationsReadRequest>()
            require(request.keys.isNotEmpty()) { "keys must not be empty" }
            notificationService.markRead(user.id, request.keys)
            call.respond(mapOf("ok" to true))
        }

        post("/read-all") {
            val user = call.requireCurrentUser(userRepository) ?: return@post
            if (!call.ensureBillingAccess(user, companyRepository, stripeBillingService)) return@post
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
