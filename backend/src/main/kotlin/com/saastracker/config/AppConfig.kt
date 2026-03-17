package com.saastracker.config

import io.ktor.server.config.ApplicationConfig

data class AppConfig(
    val env: String,
    val database: DatabaseConfig,
    val redis: RedisConfig,
    val jwt: JwtConfig,
    val email: EmailConfig,
    val smtp: SmtpConfig,
    val resend: ResendConfig,
    val stripe: StripeConfig,
    val scheduler: SchedulerConfig,
    val rateLimit: RateLimitConfig,
    val cors: CorsConfig
)

enum class EmailProvider {
    SMTP,
    RESEND
}

data class EmailConfig(
    val provider: EmailProvider
)

data class DatabaseConfig(
    val jdbcUrl: String,
    val username: String,
    val password: String,
    val maxPoolSize: Int
)

data class RedisConfig(
    val uri: String,
    val cacheTtlSeconds: Long
)

data class JwtConfig(
    val issuer: String,
    val audience: String,
    val secret: String,
    val accessTokenTtlMinutes: Long,
    val refreshTokenTtlDays: Long = 30
)

data class SmtpConfig(
    val host: String,
    val port: Int,
    val username: String?,
    val password: String?,
    val fromEmail: String,
    val fromName: String,
    val auth: Boolean,
    val startTls: Boolean,
    val tlsTrustAll: Boolean,
    val timeoutMs: Int,
    val frontendBaseUrl: String
)

data class ResendConfig(
    val apiKey: String?,
    val fromEmail: String,
    val baseUrl: String,
    val frontendBaseUrl: String
)

data class StripeConfig(
    val apiKey: String?,
    val webhookSecret: String?,
    val monthlyPriceId: String?,
    val annualPriceId: String?,
    val successUrl: String,
    val cancelUrl: String
)

data class SchedulerConfig(
    val renewalCron: String
)

data class RateLimitConfig(
    val requestsPerMinute: Int,
    val authRequestsPerMinute: Int
)

data class CorsConfig(
    val allowedOrigins: List<String>
)

fun ApplicationConfig.toAppConfig(): AppConfig = AppConfig(
    env = propertyOrNull("app.env")?.getString() ?: "local",
    database = DatabaseConfig(
        jdbcUrl = property("app.database.jdbcUrl").getString(),
        username = property("app.database.username").getString(),
        password = property("app.database.password").getString(),
        maxPoolSize = property("app.database.maxPoolSize").getString().toInt()
    ),
    redis = RedisConfig(
        uri = property("app.redis.uri").getString(),
        cacheTtlSeconds = property("app.redis.cacheTtlSeconds").getString().toLong()
    ),
    jwt = JwtConfig(
        issuer = property("app.jwt.issuer").getString(),
        audience = property("app.jwt.audience").getString(),
        secret = property("app.jwt.secret").getString().also { secret ->
            require(secret.toByteArray(Charsets.UTF_8).size >= 32) {
                "JWT secret must be at least 32 bytes for HS256 (current: ${secret.toByteArray(Charsets.UTF_8).size} bytes)"
            }
        },
        accessTokenTtlMinutes = property("app.jwt.accessTokenTtlMinutes").getString().toLong(),
        refreshTokenTtlDays = propertyOrNull("app.jwt.refreshTokenTtlDays")?.getString()?.toLong() ?: 30
    ),
    email = EmailConfig(
        provider = propertyOrNull("app.email.provider")
            ?.getString()
            ?.trim()
            ?.uppercase()
            ?.let {
                runCatching { EmailProvider.valueOf(it) }.getOrDefault(EmailProvider.SMTP)
            }
            ?: EmailProvider.SMTP
    ),
    smtp = SmtpConfig(
        host = property("app.smtp.host").getString(),
        port = property("app.smtp.port").getString().toInt(),
        username = propertyOrNull("app.smtp.username")?.getString(),
        password = propertyOrNull("app.smtp.password")?.getString(),
        fromEmail = property("app.smtp.fromEmail").getString(),
        fromName = property("app.smtp.fromName").getString(),
        auth = property("app.smtp.auth").getString().toBoolean(),
        startTls = property("app.smtp.startTls").getString().toBoolean(),
        tlsTrustAll = property("app.smtp.tlsTrustAll").getString().toBoolean(),
        timeoutMs = property("app.smtp.timeoutMs").getString().toInt(),
        frontendBaseUrl = propertyOrNull("app.frontend.baseUrl")?.getString()?.trimEnd('/') ?: "http://localhost"
    ),
    resend = ResendConfig(
        apiKey = propertyOrNull("app.resend.apiKey")?.getString(),
        fromEmail = property("app.resend.fromEmail").getString(),
        baseUrl = property("app.resend.baseUrl").getString(),
        frontendBaseUrl = propertyOrNull("app.frontend.baseUrl")?.getString()?.trimEnd('/') ?: "http://localhost"
    ),
    stripe = StripeConfig(
        apiKey = propertyOrNull("app.stripe.apiKey")?.getString(),
        webhookSecret = propertyOrNull("app.stripe.webhookSecret")?.getString(),
        monthlyPriceId = propertyOrNull("app.stripe.monthlyPriceId")?.getString(),
        annualPriceId = propertyOrNull("app.stripe.annualPriceId")?.getString(),
        successUrl = propertyOrNull("app.stripe.successUrl")?.getString() ?: "http://localhost/dashboard?upgraded=true",
        cancelUrl = propertyOrNull("app.stripe.cancelUrl")?.getString() ?: "http://localhost/billing/upgrade"
    ),
    scheduler = SchedulerConfig(
        renewalCron = property("app.scheduler.renewalCron").getString()
    ),
    rateLimit = RateLimitConfig(
        requestsPerMinute = property("app.rateLimit.requestsPerMinute").getString().toInt(),
        authRequestsPerMinute = property("app.rateLimit.authRequestsPerMinute").getString().toInt()
    ),
    cors = CorsConfig(
        allowedOrigins = property("app.cors.allowedOrigins").getString()
            .split(",")
            .map(String::trim)
            .filter(String::isNotBlank)
    )
)
