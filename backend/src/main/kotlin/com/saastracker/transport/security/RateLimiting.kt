package com.saastracker.transport.security

import com.saastracker.config.RateLimitConfig
import io.ktor.http.HttpStatusCode
import io.ktor.server.application.Application
import io.ktor.server.application.call
import io.ktor.server.application.createApplicationPlugin
import io.ktor.server.application.install
import io.ktor.server.request.path
import io.ktor.server.response.respond
import io.ktor.util.AttributeKey
import java.time.Instant
import java.time.temporal.ChronoUnit
import java.util.concurrent.ConcurrentHashMap
import java.util.concurrent.atomic.AtomicInteger

class InMemoryRateLimiter(private val defaultLimit: Int, val authLimit: Int = 10, val exportLimit: Int = 5) {
    private data class Window(val minuteEpoch: Long, val count: AtomicInteger)

    private val windows = ConcurrentHashMap<String, Window>()

    fun isAllowed(key: String, maxRequests: Int = defaultLimit, now: Instant = Instant.now()): Boolean {
        val currentMinute = now.truncatedTo(ChronoUnit.MINUTES).epochSecond
        // Lazy TTL cleanup: evict windows from past minutes to prevent unbounded map growth.
        windows.entries.removeIf { it.value.minuteEpoch < currentMinute }
        val window = windows.compute(key) { _, existing ->
            if (existing == null || existing.minuteEpoch != currentMinute)
                Window(currentMinute, AtomicInteger(0))
            else existing
        } ?: Window(currentMinute, AtomicInteger(0))
        return window.count.incrementAndGet() <= maxRequests
    }
}

val PublicRateLimit = createApplicationPlugin(
    name = "PublicRateLimit",
    createConfiguration = ::RateLimitPluginConfig
) {
    val limiter = pluginConfig.limiter
    val ignoredPathPrefixes = pluginConfig.ignoredPathPrefixes

    onCall { call ->
        val path = call.request.path()
        if (ignoredPathPrefixes.any(path::startsWith)) {
            return@onCall
        }

        val clientIp = call.request.headers["X-Forwarded-For"]?.substringBefore(",")
            ?: call.request.local.remoteHost

        if (!limiter.isAllowed(clientIp)) {
            call.respond(HttpStatusCode.TooManyRequests, mapOf("message" to "Rate limit exceeded. Retry in one minute."))
            return@onCall
        }

        // Stricter per-IP limit for authentication endpoints to mitigate brute-force attacks.
        if (path.startsWith("/auth/") && !limiter.isAllowed("$clientIp:auth", limiter.authLimit)) {
            call.respond(HttpStatusCode.TooManyRequests, mapOf("message" to "Too many authentication attempts. Retry in one minute."))
            return@onCall
        }
    }
}

class RateLimitPluginConfig {
    lateinit var limiter: InMemoryRateLimiter
    var ignoredPathPrefixes: Set<String> = setOf("/health")
}

fun Application.configureRateLimiting(rateLimitConfig: RateLimitConfig): InMemoryRateLimiter {
    val limiter = InMemoryRateLimiter(rateLimitConfig.requestsPerMinute, rateLimitConfig.authRequestsPerMinute, rateLimitConfig.exportRequestsPerMinute)
    install(PublicRateLimit) {
        this.limiter = limiter
    }
    attributes.put(AttributeKey("rateLimiter"), limiter)
    return limiter
}
