package com.saastracker.transport.http.routes

import com.saastracker.domain.model.PlanMatrix
import com.saastracker.domain.model.PlanTier
import com.saastracker.domain.model.UserRole
import com.saastracker.persistence.repository.CompanyRepository
import com.saastracker.persistence.repository.UserRepository
import com.saastracker.transport.payment.StripeBillingService
import com.saastracker.transport.security.authenticatedRoute
import io.ktor.http.HttpStatusCode
import io.ktor.server.application.call
import io.ktor.server.request.receive
import io.ktor.server.response.respond
import io.ktor.server.routing.Route
import io.ktor.server.routing.get
import io.ktor.server.routing.post
import kotlinx.serialization.Serializable

@Serializable
private data class CheckoutRequest(val planId: String)

fun Route.billingRoutes(
    stripeBillingService: StripeBillingService,
    userRepository: UserRepository,
    companyRepository: CompanyRepository
) {
    authenticatedRoute("/api/v1/billing", UserRole.ADMIN) {

        // Returns available plans and the company's current plan
        get("/plans") {
            val user = call.requireCurrentUser(userRepository) ?: return@get
            val company = companyRepository.findById(user.companyId)
                ?: return@get call.respond(HttpStatusCode.NotFound, mapOf("message" to "Company not found"))

            val plans = listOf(
                buildPlanInfo(PlanTier.FREE, company.planTier, null),
                buildPlanInfo(PlanTier.PRO, company.planTier, stripeBillingService.proPriceId),
                buildPlanInfo(PlanTier.ENTERPRISE, company.planTier, stripeBillingService.enterprisePriceId)
            )
            call.respond(mapOf("currentPlan" to company.planTier.name, "plans" to plans))
        }

        post("/checkout") {
            val user = call.requireCurrentUser(userRepository) ?: return@post
            val company = companyRepository.findById(user.companyId)
                ?: return@post call.respond(HttpStatusCode.NotFound, mapOf("message" to "Company not found"))

            val body = runCatching { call.receive<CheckoutRequest>() }.getOrNull()
            val planTier = body?.planId?.let { runCatching { PlanTier.valueOf(it.uppercase()) }.getOrNull() }
                ?: return@post call.respond(
                    HttpStatusCode.BadRequest,
                    mapOf("message" to "Invalid planId. Valid values: PRO, ENTERPRISE")
                )

            call.respondAppResult(
                stripeBillingService.createCheckoutSession(company, planTier).map { url ->
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
            call.respond(
                mapOf(
                    "subscriptionStatus" to company.subscriptionStatus.name,
                    "planTier" to company.planTier.name
                )
            )
        }
    }
}

private fun buildPlanInfo(tier: PlanTier, currentTier: PlanTier, priceId: String?): Map<String, Any?> {
    val limits = PlanMatrix.limits[tier]
    return mapOf(
        "id" to tier.name,
        "name" to tier.name.lowercase().replaceFirstChar { it.uppercase() },
        "priceMonthly" to when (tier) { PlanTier.FREE -> 0; PlanTier.PRO -> 19; PlanTier.ENTERPRISE -> 79 },
        "currency" to "USD",
        "priceId" to priceId,
        "limits" to mapOf(
            "subscriptions" to (limits?.maxSubscriptions ?: -1),
            "teamMembers" to (limits?.maxTeamMembers ?: -1)
        ),
        "isCurrent" to (tier == currentTier)
    )
}
