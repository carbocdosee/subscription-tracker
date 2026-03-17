package com.saastracker.transport.security

import com.saastracker.domain.model.UserRole
import io.ktor.server.application.Application
import io.ktor.server.application.call
import io.ktor.server.application.install
import io.ktor.server.auth.Authentication
import io.ktor.server.auth.AuthenticationConfig
import io.ktor.server.auth.AuthenticationProvider
import io.ktor.server.auth.Principal
import io.ktor.server.auth.authenticate
import io.ktor.server.auth.principal
import io.ktor.server.response.respond
import io.ktor.server.routing.Route
import io.ktor.server.routing.route
import io.ktor.http.HttpStatusCode
import java.util.UUID

data class AppPrincipal(
    val userId: UUID,
    val companyId: UUID,
    val email: String,
    val role: UserRole
) : Principal

class BearerAuthProvider internal constructor(
    private val jwtService: JwtService
) : AuthenticationProvider(Config(name = "bearer")) {
    class Config(name: String?) : AuthenticationProvider.Config(name)

    override suspend fun onAuthenticate(context: io.ktor.server.auth.AuthenticationContext) {
        val authorization = context.call.request.headers["Authorization"] ?: return
        if (!authorization.startsWith("Bearer ")) return
        val token = authorization.removePrefix("Bearer ").trim()
        val principalUser = jwtService.parseToken(token) ?: return
        context.principal(
            AppPrincipal(
                userId = principalUser.userId,
                companyId = principalUser.companyId,
                email = principalUser.email,
                role = principalUser.role
            )
        )
    }
}

fun AuthenticationConfig.bearerAuth(jwtService: JwtService) {
    register(BearerAuthProvider(jwtService))
}

fun Application.configureAuth(jwtService: JwtService) {
    install(Authentication) {
        bearerAuth(jwtService)
    }
}

fun Route.authenticatedRoute(
    path: String,
    minimumRole: UserRole = UserRole.VIEWER,
    build: Route.() -> Unit
) {
    authenticate("bearer") {
        route(path) {
            intercept(io.ktor.server.application.ApplicationCallPipeline.Call) {
                val principal = call.principal<AppPrincipal>()
                if (principal == null) {
                    call.respond(HttpStatusCode.Unauthorized, mapOf("message" to "Authentication required"))
                    finish()
                    return@intercept
                }
                if (!hasRole(principal.role, minimumRole)) {
                    call.respond(HttpStatusCode.Forbidden, mapOf("message" to "Insufficient role"))
                    finish()
                    return@intercept
                }
            }
            build()
        }
    }
}

private fun hasRole(actual: UserRole, required: UserRole): Boolean = when (required) {
    UserRole.ADMIN -> actual == UserRole.ADMIN
    UserRole.EDITOR -> actual == UserRole.ADMIN || actual == UserRole.EDITOR
    UserRole.VIEWER -> true
}
