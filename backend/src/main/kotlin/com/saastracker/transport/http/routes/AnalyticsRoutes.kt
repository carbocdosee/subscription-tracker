package com.saastracker.transport.http.routes

import com.saastracker.domain.model.UserRole
import com.saastracker.domain.service.AnalyticsService
import com.saastracker.domain.service.SpendSnapshotService
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

fun Route.analyticsRoutes(
    analyticsService: AnalyticsService,
    spendSnapshotService: SpendSnapshotService,
    userRepository: UserRepository,
    companyRepository: CompanyRepository,
    stripeBillingService: StripeBillingService
) {
    authenticatedRoute("/api/v1/analytics") {
        get {
            val user = call.requireCurrentUser(userRepository) ?: return@get
            if (!call.ensureBillingAccess(user, companyRepository, stripeBillingService)) return@get
            val analytics = analyticsService.getAnalytics(user.companyId)
            call.respond(analytics)
        }
    }

    authenticatedRoute("/api/v1/admin/snapshots/capture", UserRole.ADMIN) {
        post {
            call.requireCurrentUser(userRepository) ?: return@post
            spendSnapshotService.captureAllCompanies()
            call.respond(HttpStatusCode.OK, mapOf("ok" to true))
        }
    }
}
