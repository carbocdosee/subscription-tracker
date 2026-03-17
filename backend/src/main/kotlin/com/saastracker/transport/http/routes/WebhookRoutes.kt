package com.saastracker.transport.http.routes

import com.saastracker.transport.payment.StripeBillingService
import io.ktor.http.HttpStatusCode
import io.ktor.server.application.call
import io.ktor.server.request.receiveText
import io.ktor.server.response.respond
import io.ktor.server.routing.Route
import io.ktor.server.routing.post
import io.ktor.server.routing.route

fun Route.webhookRoutes(stripeBillingService: StripeBillingService) {
    route("/webhooks") {
        post("/stripe") {
            val signature = call.request.headers["Stripe-Signature"]
                ?: throw IllegalArgumentException("Missing Stripe-Signature header")
            val payload = call.receiveText()
            call.respondAppResult(
                stripeBillingService.handleWebhook(payload, signature),
                onSuccessStatus = HttpStatusCode.OK
            )
        }
    }
}
