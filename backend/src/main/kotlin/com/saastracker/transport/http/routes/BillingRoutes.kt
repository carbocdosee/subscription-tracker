package com.saastracker.transport.http.routes

import com.saastracker.domain.model.UserRole
import com.saastracker.persistence.repository.CompanyRepository
import com.saastracker.persistence.repository.UserRepository
import com.saastracker.transport.payment.StripeBillingService
import com.saastracker.transport.security.authenticatedRoute
import io.ktor.http.HttpStatusCode
import io.ktor.server.application.call
import io.ktor.server.response.respond
import io.ktor.server.routing.Route
import io.ktor.server.routing.get
import io.ktor.server.routing.post

fun Route.billingRoutes(
    stripeBillingService: StripeBillingService,
    userRepository: UserRepository,
    companyRepository: CompanyRepository
) {
    authenticatedRoute("/api/v1/billing", UserRole.ADMIN) {
        post("/checkout") {
            val user = call.requireCurrentUser(userRepository) ?: return@post
            val company = companyRepository.findById(user.companyId)
                ?: return@post call.respond(HttpStatusCode.NotFound, mapOf("message" to "Company not found"))
            val plan = call.request.queryParameters["plan"] ?: "monthly"
            call.respondAppResult(
                stripeBillingService.createCheckoutSession(company, plan).map { url ->
                    mapOf("checkoutUrl" to url)
                }
            )
        }

        get("/portal") {
            val user = call.requireCurrentUser(userRepository) ?: return@get
            val company = companyRepository.findById(user.companyId)
                ?: return@get call.respond(HttpStatusCode.NotFound, mapOf("message" to "Company not found"))
            call.respondAppResult(
                stripeBillingService.createPortalSession(company).map { mapOf("portalUrl" to it) }
            )
        }

        get("/status") {
            val user = call.requireCurrentUser(userRepository) ?: return@get
            val company = companyRepository.findById(user.companyId)
                ?: return@get call.respond(HttpStatusCode.NotFound, mapOf("message" to "Company not found"))
            call.respond(mapOf("subscriptionStatus" to company.subscriptionStatus.name))
        }
    }
}
