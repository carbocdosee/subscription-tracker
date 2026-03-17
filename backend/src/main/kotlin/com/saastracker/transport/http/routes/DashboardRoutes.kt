package com.saastracker.transport.http.routes

import com.saastracker.domain.service.DashboardService
import com.saastracker.domain.service.toApiDto
import com.saastracker.persistence.repository.CompanyRepository
import com.saastracker.persistence.repository.UserRepository
import com.saastracker.transport.payment.StripeBillingService
import com.saastracker.transport.security.authenticatedRoute
import io.ktor.server.application.call
import io.ktor.server.response.respond
import io.ktor.server.routing.Route
import io.ktor.server.routing.get

fun Route.dashboardRoutes(
    dashboardService: DashboardService,
    userRepository: UserRepository,
    companyRepository: CompanyRepository,
    stripeBillingService: StripeBillingService
) {
    authenticatedRoute("/api/v1/dashboard") {
        get("/stats") {
            val user = call.requireCurrentUser(userRepository) ?: return@get
            if (!call.ensureBillingAccess(user, companyRepository, stripeBillingService)) return@get
            val stats = dashboardService.getDashboardStats(user.companyId).toApiDto()
            call.respond(stats)
        }
    }
}
