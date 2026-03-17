package com.saastracker.transport.http.routes

import com.saastracker.domain.model.UserRole
import com.saastracker.domain.service.HealthService
import com.saastracker.transport.security.authenticatedRoute
import io.micrometer.prometheusmetrics.PrometheusMeterRegistry
import io.ktor.http.HttpStatusCode
import io.ktor.server.application.call
import io.ktor.server.response.respond
import io.ktor.server.response.respondText
import io.ktor.server.routing.Route
import io.ktor.server.routing.get
import io.ktor.server.routing.route

fun Route.healthRoutes(
    healthService: HealthService,
    meterRegistry: PrometheusMeterRegistry
) {
    route("/health") {
        get {
            val health = healthService.check()
            val status = if (health.status == "UP") HttpStatusCode.OK else HttpStatusCode.ServiceUnavailable
            call.respond(status, health)
        }
    }

    authenticatedRoute("/metrics", UserRole.ADMIN) {
        get {
            call.respondText(meterRegistry.scrape())
        }
    }
}
