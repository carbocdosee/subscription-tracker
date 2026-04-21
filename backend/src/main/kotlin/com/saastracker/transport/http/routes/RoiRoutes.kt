package com.saastracker.transport.http.routes

import com.saastracker.domain.service.RoiService
import com.saastracker.persistence.repository.CompanyRepository
import com.saastracker.persistence.repository.UserRepository
import com.saastracker.transport.payment.StripeBillingService
import com.saastracker.transport.security.authenticatedRoute
import io.ktor.server.application.call
import io.ktor.server.response.respond
import io.ktor.server.routing.Route
import io.ktor.server.routing.get
import kotlinx.serialization.Serializable

@Serializable
data class RoiStatsResponse(
    val totalSavedUsd: String,
    val eventCount: Int,
    val zombieArchivedCount: Int
)

fun Route.roiRoutes(
    roiService: RoiService,
    userRepository: UserRepository,
    companyRepository: CompanyRepository,
    stripeBillingService: StripeBillingService
) {
    authenticatedRoute("/api/v1/insights") {
        get("/roi") {
            val user = call.requireCurrentUser(userRepository) ?: return@get
            val stats = roiService.getRoiStats(user.companyId)
            call.respond(
                RoiStatsResponse(
                    totalSavedUsd = stats.totalSavedUsd.toPlainString(),
                    eventCount = stats.eventCount,
                    zombieArchivedCount = stats.zombieArchivedCount
                )
            )
        }
    }
}
