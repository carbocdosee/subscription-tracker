package com.saastracker.transport.http.routes

import com.saastracker.domain.service.AuthService
import com.saastracker.domain.service.InviteMemberResult
import com.saastracker.domain.validation.validateAcceptInvite
import com.saastracker.domain.validation.validateInvite
import com.saastracker.domain.validation.validateLogin
import com.saastracker.domain.validation.validateRegister
import com.saastracker.persistence.repository.CompanyRepository
import com.saastracker.persistence.repository.UserRepository
import com.saastracker.transport.payment.StripeBillingService
import com.saastracker.transport.http.request.AcceptInviteRequest
import com.saastracker.transport.http.request.InviteMemberRequest
import com.saastracker.transport.http.request.LoginRequest
import com.saastracker.transport.http.request.RegisterRequest
import com.saastracker.transport.http.request.UpdateUserRoleRequest
import com.saastracker.transport.http.response.AuthResponse
import com.saastracker.transport.http.response.EmailDeliveryResponse
import com.saastracker.transport.http.response.EmailDeliveryStatusResponse
import com.saastracker.transport.http.response.TeamInvitationResponse
import com.saastracker.transport.http.response.TeamInviteResponse
import com.saastracker.transport.http.response.UserSummaryResponse
import com.saastracker.transport.security.authenticatedRoute
import io.ktor.http.Cookie
import io.ktor.http.HttpStatusCode
import io.ktor.server.application.call
import io.ktor.server.request.receive
import io.ktor.server.response.respond
import io.ktor.server.routing.Route
import io.ktor.server.routing.delete
import io.ktor.server.routing.post
import io.ktor.server.routing.put
import io.ktor.server.routing.route

private const val REFRESH_COOKIE = "refresh_token"
private const val REFRESH_MAX_AGE = 30 * 24 * 3600

fun Route.authRoutes(
    authService: AuthService,
    userRepository: UserRepository,
    companyRepository: CompanyRepository,
    stripeBillingService: StripeBillingService
) {
    route("/auth") {
        post("/register") {
            val request = call.receive<RegisterRequest>()
            validateRegister(request)
            when (val result = authService.register(request)) {
                is com.saastracker.domain.error.AppResult.Success -> {
                    call.response.cookies.append(refreshCookie(result.value.refreshToken))
                    call.respond(
                        HttpStatusCode.Created,
                        AuthResponse(
                            accessToken = result.value.accessToken,
                            user = result.value.user.toUserSummary()
                        )
                    )
                }
                is com.saastracker.domain.error.AppResult.Failure -> call.respondAppResult(result)
            }
        }

        post("/login") {
            val request = call.receive<LoginRequest>()
            validateLogin(request)
            when (val result = authService.login(request)) {
                is com.saastracker.domain.error.AppResult.Success -> {
                    call.response.cookies.append(refreshCookie(result.value.refreshToken))
                    call.respond(
                        HttpStatusCode.OK,
                        AuthResponse(
                            accessToken = result.value.accessToken,
                            user = result.value.user.toUserSummary()
                        )
                    )
                }
                is com.saastracker.domain.error.AppResult.Failure -> call.respondAppResult(result)
            }
        }

        post("/accept-invite") {
            val request = call.receive<AcceptInviteRequest>()
            validateAcceptInvite(request)
            when (val result = authService.acceptInvite(request)) {
                is com.saastracker.domain.error.AppResult.Success -> {
                    call.response.cookies.append(refreshCookie(result.value.refreshToken))
                    call.respond(
                        HttpStatusCode.OK,
                        AuthResponse(
                            accessToken = result.value.accessToken,
                            user = result.value.user.toUserSummary()
                        )
                    )
                }
                is com.saastracker.domain.error.AppResult.Failure -> call.respondAppResult(result)
            }
        }

        post("/refresh") {
            val rawToken = call.request.cookies[REFRESH_COOKIE]
                ?: return@post call.respond(HttpStatusCode.Unauthorized, mapOf("message" to "No refresh token"))
            when (val result = authService.refresh(rawToken)) {
                is com.saastracker.domain.error.AppResult.Success -> {
                    call.response.cookies.append(refreshCookie(result.value.refreshToken))
                    call.respond(
                        HttpStatusCode.OK,
                        AuthResponse(
                            accessToken = result.value.accessToken,
                            user = result.value.user.toUserSummary()
                        )
                    )
                }
                is com.saastracker.domain.error.AppResult.Failure -> call.respondAppResult(result)
            }
        }

        post("/logout") {
            val rawToken = call.request.cookies[REFRESH_COOKIE] ?: ""
            authService.logout(rawToken)
            call.response.cookies.append(Cookie(REFRESH_COOKIE, "", maxAge = 0, path = "/"))
            call.respond(HttpStatusCode.NoContent)
        }
    }

    authenticatedRoute("/api/v1/team", com.saastracker.domain.model.UserRole.ADMIN) {
        post("/invite") {
            val user = call.requireCurrentUser(userRepository) ?: return@post
            if (!call.ensureBillingAccess(user, companyRepository, stripeBillingService)) return@post
            val request = call.receive<InviteMemberRequest>()
            validateInvite(request)
            when (val result = authService.inviteMember(user, request)) {
                is com.saastracker.domain.error.AppResult.Success -> {
                    val invite = result.value.toTeamInviteResponse(call)
                    call.respond(
                        if (invite.reusedExisting) HttpStatusCode.OK else HttpStatusCode.Created,
                        invite
                    )
                }
                is com.saastracker.domain.error.AppResult.Failure -> call.respondAppResult(result)
            }
        }

        post("/invitations/{id}/resend") {
            val user = call.requireCurrentUser(userRepository) ?: return@post
            if (!call.ensureBillingAccess(user, companyRepository, stripeBillingService)) return@post
            val invitationId = call.requireUuidParam("id") ?: return@post
            when (val result = authService.resendInvitation(user, invitationId)) {
                is com.saastracker.domain.error.AppResult.Success -> {
                    val invite = result.value.toTeamInviteResponse(call)
                    call.respond(HttpStatusCode.OK, invite)
                }
                is com.saastracker.domain.error.AppResult.Failure -> call.respondAppResult(result)
            }
        }

        delete("/invitations/{id}") {
            val user = call.requireCurrentUser(userRepository) ?: return@delete
            if (!call.ensureBillingAccess(user, companyRepository, stripeBillingService)) return@delete
            val invitationId = call.requireUuidParam("id") ?: return@delete
            call.respondAppResult(authService.cancelInvitation(user, invitationId))
        }

        put("/users/{id}/role") {
            val user = call.requireCurrentUser(userRepository) ?: return@put
            if (!call.ensureBillingAccess(user, companyRepository, stripeBillingService)) return@put
            val userId = call.requireUuidParam("id") ?: return@put
            val request = call.receive<UpdateUserRoleRequest>()
            when (val result = authService.changeUserRole(user, userId, request.role)) {
                is com.saastracker.domain.error.AppResult.Success ->
                    call.respond(HttpStatusCode.OK, result.value.toUserSummary())
                is com.saastracker.domain.error.AppResult.Failure ->
                    call.respondAppResult(result)
            }
        }
    }
}

private fun refreshCookie(token: String) = Cookie(
    name = REFRESH_COOKIE,
    value = token,
    httpOnly = true,
    secure = true,
    path = "/",
    maxAge = REFRESH_MAX_AGE,
    extensions = mapOf("SameSite" to "Strict")
)

private fun com.saastracker.domain.model.User.toUserSummary(): UserSummaryResponse = UserSummaryResponse(
    id = id.toString(),
    companyId = companyId.toString(),
    email = email,
    name = name,
    role = role
)

private fun com.saastracker.domain.model.TeamInvitation.toTeamInvitationResponse(
    acceptInviteUrl: String? = null
): TeamInvitationResponse =
    TeamInvitationResponse(
        id = id.toString(),
        email = email,
        role = role,
        expiresAt = expiresAt.toString(),
        acceptedAt = acceptedAt?.toString(),
        createdAt = createdAt.toString(),
        acceptInviteUrl = acceptInviteUrl
    )

private fun InviteMemberResult.toTeamInviteResponse(call: io.ktor.server.application.ApplicationCall): TeamInviteResponse {
    val origin = call.publicOrigin()
    val acceptInviteUrl = "$origin/accept-invite?token=${invitation.token}"
    return TeamInviteResponse(
        invitation = invitation.toTeamInvitationResponse(acceptInviteUrl = acceptInviteUrl),
        reusedExisting = reusedExisting,
        acceptInviteUrl = acceptInviteUrl,
        emailDelivery = EmailDeliveryResponse(
            status = when (emailDelivery.status) {
                com.saastracker.transport.email.EmailDeliveryStatus.SENT -> EmailDeliveryStatusResponse.SENT
                com.saastracker.transport.email.EmailDeliveryStatus.SKIPPED_NOT_CONFIGURED -> EmailDeliveryStatusResponse.SKIPPED_NOT_CONFIGURED
                com.saastracker.transport.email.EmailDeliveryStatus.FAILED -> EmailDeliveryStatusResponse.FAILED
            },
            message = emailDelivery.message,
            providerMessageId = emailDelivery.providerMessageId,
            providerStatusCode = emailDelivery.providerStatusCode
        )
    )
}

private fun io.ktor.server.application.ApplicationCall.publicOrigin(): String {
    val scheme = request.headers["X-Forwarded-Proto"] ?: "http"
    val host = request.headers["X-Forwarded-Host"]
        ?: request.headers["Host"]
        ?: "localhost"
    return "$scheme://$host"
}
