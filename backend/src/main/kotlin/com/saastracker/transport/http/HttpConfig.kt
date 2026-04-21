package com.saastracker.transport.http

import com.saastracker.config.AppConfig
import com.saastracker.transport.http.response.ErrorResponse
import com.saastracker.transport.metrics.AppMetrics
import com.saastracker.util.sanitizeForLog
import io.ktor.http.HttpStatusCode
import io.ktor.http.HttpHeaders
import io.ktor.serialization.kotlinx.json.json
import io.ktor.server.application.Application
import io.ktor.server.application.ApplicationCallPipeline
import io.ktor.server.application.call
import io.ktor.server.application.install
import io.ktor.server.application.log
import io.ktor.server.plugins.callid.CallId
import io.ktor.server.plugins.callid.callId
import io.ktor.server.plugins.callloging.CallLogging
import io.ktor.server.plugins.compression.Compression
import io.ktor.server.plugins.contentnegotiation.ContentNegotiation
import io.ktor.server.plugins.cors.routing.CORS
import io.ktor.server.plugins.defaultheaders.DefaultHeaders
import io.ktor.server.plugins.forwardedheaders.ForwardedHeaders
import io.ktor.server.plugins.statuspages.StatusPages
import io.ktor.server.request.httpMethod
import io.ktor.server.request.path
import io.ktor.server.request.uri
import io.ktor.server.request.userAgent
import io.ktor.server.response.respond
import kotlinx.serialization.json.Json
import org.valiktor.ConstraintViolationException
import java.time.Duration
import java.time.Instant
import java.util.UUID

fun Application.configureHttp(appConfig: AppConfig, appMetrics: AppMetrics) {
    install(DefaultHeaders) {
        header("X-Content-Type-Options", "nosniff")
        header("X-Frame-Options", "DENY")
        header("X-XSS-Protection", "1; mode=block")
        header("Referrer-Policy", "strict-origin-when-cross-origin")
        header("Strict-Transport-Security", "max-age=31536000; includeSubDomains")
        header("Content-Security-Policy", "default-src 'none'; frame-ancestors 'none'")
    }
    install(ForwardedHeaders)
    install(Compression)
    install(ContentNegotiation) {
        json(
            Json {
                ignoreUnknownKeys = true
                encodeDefaults = true
                prettyPrint = false
            }
        )
    }
    install(CallId) {
        header(HttpHeaders.XRequestId)
        generate {
            UUID.randomUUID().toString()
        }
        verify { it.isNotBlank() }
        replyToHeader(HttpHeaders.XRequestId)
    }
    install(CallLogging) {
        level = org.slf4j.event.Level.INFO
        mdc("request_id") { call -> call.callId }
        format { call ->
            val status = call.response.status() ?: HttpStatusCode.Processing
            val method = call.request.httpMethod.value
            val uri = call.request.uri.sanitizeForLog()
            val ip = (call.request.headers["X-Forwarded-For"] ?: "unknown").sanitizeForLog()
            val ua = (call.request.userAgent() ?: "-").sanitizeForLog()
            "${status.value} ${status.description}: $method $uri ip=$ip ua=$ua"
        }
    }

    // Log every incoming request immediately upon arrival (before processing).
    // This captures requests that may crash mid-pipeline and produce no response log.
    intercept(ApplicationCallPipeline.Setup) {
        val method = call.request.httpMethod.value
        val uri = call.request.uri.sanitizeForLog()
        val ip = (call.request.headers["X-Forwarded-For"] ?: "unknown").sanitizeForLog()
        this@configureHttp.log.info("→ $method $uri ip=$ip rid=${call.callId}")
        proceed()
    }
    install(CORS) {
        allowHeader(HttpHeaders.Authorization)
        allowHeader(HttpHeaders.ContentType)
        allowHeader(HttpHeaders.XRequestId)
        allowCredentials = true
        allowMethod(io.ktor.http.HttpMethod.Get)
        allowMethod(io.ktor.http.HttpMethod.Post)
        allowMethod(io.ktor.http.HttpMethod.Put)
        allowMethod(io.ktor.http.HttpMethod.Patch)
        allowMethod(io.ktor.http.HttpMethod.Delete)
        appConfig.cors.allowedOrigins.forEach { origin ->
            val isLocalhost = origin.contains("localhost") || origin.contains("127.0.0.1")
            val schemes = if (isLocalhost) listOf("http", "https") else listOf("https")
            allowHost(origin.removePrefix("http://").removePrefix("https://"), schemes = schemes)
        }
    }
    install(StatusPages) {
        exception<ConstraintViolationException> { call, cause ->
            val details = cause.constraintViolations
                .joinToString("; ") { violation -> "${violation.property}: ${violation.constraint.name}" }
                .ifBlank { "Validation failed" }
            respondError(call, HttpStatusCode.BadRequest, details)
        }
        exception<IllegalArgumentException> { call, cause ->
            respondError(call, HttpStatusCode.BadRequest, cause.message ?: "Invalid input")
        }
        exception<NoSuchElementException> { call, cause ->
            respondError(call, HttpStatusCode.NotFound, cause.message ?: "Not found")
        }
        exception<io.ktor.server.plugins.BadRequestException> { call, cause ->
            respondError(call, HttpStatusCode.BadRequest, cause.message ?: "Invalid request body")
        }
        exception<Throwable> { call, cause ->
            val method = call.request.httpMethod.value
            val uri = call.request.uri.sanitizeForLog()
            val rid = call.callId ?: "?"
            this@configureHttp.log.error("Unhandled error rid=$rid $method $uri", cause)
            respondError(call, HttpStatusCode.InternalServerError, "Internal server error")
        }
    }

    // Request-level metrics and request_id response header.
    intercept(ApplicationCallPipeline.Monitoring) {
        val startedAt = Instant.now()
        try {
            proceed()
        } finally {
            val duration = Duration.between(startedAt, Instant.now())
            appMetrics.recordRequestLatency(call.request.path(), call.request.httpMethod.value, duration)
            appMetrics.incrementRequest(call.request.path(), call.request.httpMethod.value, call.response.status()?.value ?: 200)
        }
    }
}

private suspend fun respondError(
    call: io.ktor.server.application.ApplicationCall,
    status: HttpStatusCode,
    message: String
) {
    call.respond(
        status,
        ErrorResponse(message = message, requestId = call.callId)
    )
}
