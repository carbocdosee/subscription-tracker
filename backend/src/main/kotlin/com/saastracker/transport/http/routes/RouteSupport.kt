package com.saastracker.transport.http.routes

import com.saastracker.domain.error.AppError
import com.saastracker.domain.error.AppResult
import com.saastracker.domain.model.User
import com.saastracker.persistence.repository.CompanyRepository
import com.saastracker.persistence.repository.UserRepository
import com.saastracker.transport.payment.StripeBillingService
import com.saastracker.transport.security.AppPrincipal
import io.ktor.http.HttpStatusCode
import io.ktor.server.application.ApplicationCall
import io.ktor.server.auth.principal
import io.ktor.server.response.respond
import java.util.UUID

suspend fun ApplicationCall.requireCurrentUser(userRepository: UserRepository): User? {
    val principal = principal<AppPrincipal>()
    if (principal == null) {
        respond(HttpStatusCode.Unauthorized, mapOf("message" to "Authentication required"))
        return null
    }
    val user = userRepository.findById(principal.userId)
    if (user == null || !user.isActive) {
        respond(HttpStatusCode.Unauthorized, mapOf("message" to "User not found or inactive"))
        return null
    }
    return user
}

suspend fun ApplicationCall.ensureBillingAccess(
    user: User,
    companyRepository: CompanyRepository,
    stripeBillingService: StripeBillingService
): Boolean {
    val company = companyRepository.findById(user.companyId)
    if (company == null) {
        respond(HttpStatusCode.NotFound, mapOf("message" to "Company not found"))
        return false
    }
    return when (val result = stripeBillingService.enforceAccess(company)) {
        is AppResult.Success -> true
        is AppResult.Failure -> {
            respond(HttpStatusCode.PaymentRequired, mapOf("message" to result.error.message))
            false
        }
    }
}

suspend fun ApplicationCall.requireUuidParam(name: String): UUID? {
    val uuid = parameters[name]?.let { runCatching { UUID.fromString(it) }.getOrNull() }
    if (uuid == null) respond(HttpStatusCode.BadRequest, mapOf("message" to "Invalid or missing id"))
    return uuid
}

suspend fun <T> ApplicationCall.respondAppResult(
    result: AppResult<T>,
    onSuccessStatus: HttpStatusCode = HttpStatusCode.OK
) {
    when (result) {
        is AppResult.Success -> {
            if (result.value is Unit) {
                respond(onSuccessStatus, mapOf("ok" to true))
            } else {
                respond(onSuccessStatus, result.value as Any)
            }
        }
        is AppResult.Failure -> {
            val status = when (result.error) {
                is AppError.Validation -> HttpStatusCode.BadRequest
                is AppError.Unauthorized -> HttpStatusCode.Unauthorized
                is AppError.Forbidden -> HttpStatusCode.Forbidden
                is AppError.NotFound -> HttpStatusCode.NotFound
                is AppError.Conflict -> HttpStatusCode.Conflict
                is AppError.RateLimited -> HttpStatusCode.TooManyRequests
                is AppError.ExternalService -> HttpStatusCode.BadGateway
                is AppError.Internal -> HttpStatusCode.InternalServerError
            }
            respond(status, mapOf("message" to result.error.message))
        }
    }
}
