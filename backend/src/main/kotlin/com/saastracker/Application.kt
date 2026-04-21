package com.saastracker

import com.saastracker.config.appModule
import com.saastracker.config.toAppConfig
import com.saastracker.transport.http.configureHttp
import com.saastracker.transport.http.routes.analyticsRoutes
import com.saastracker.transport.http.routes.authRoutes
import com.saastracker.transport.http.routes.billingRoutes
import com.saastracker.transport.http.routes.dashboardRoutes
import com.saastracker.transport.http.routes.healthRoutes
import com.saastracker.transport.http.routes.insightsRoutes
import com.saastracker.transport.http.routes.roiRoutes
import com.saastracker.transport.http.routes.notificationRoutes
import com.saastracker.transport.http.routes.subscriptionRoutes
import com.saastracker.transport.http.routes.teamRoutes
import com.saastracker.transport.http.routes.webhookRoutes
import com.saastracker.transport.scheduler.DataRetentionJobRuntime
import com.saastracker.transport.scheduler.DigestJobRuntime
import com.saastracker.transport.scheduler.PriceCheckJobRuntime
import com.saastracker.transport.scheduler.RenewalJobRuntime
import com.saastracker.transport.scheduler.SpendSnapshotJobRuntime
import com.saastracker.transport.scheduler.ZombieJobRuntime
import com.saastracker.transport.security.configureAuth
import com.saastracker.transport.security.configureRateLimiting
import io.ktor.server.application.Application
import io.ktor.server.application.install
import io.ktor.server.netty.EngineMain
import io.ktor.server.routing.routing
import org.koin.ktor.plugin.Koin
import org.koin.ktor.ext.inject
import org.quartz.Scheduler

fun main(args: Array<String>): Unit = EngineMain.main(args)

fun Application.module() {
    val appConfig = environment.config.toAppConfig()

    install(Koin) {
        modules(appModule(appConfig))
    }

    val metrics by inject<com.saastracker.transport.metrics.AppMetrics>()
    configureHttp(appConfig, metrics)
    val rateLimiter = configureRateLimiting(appConfig.rateLimit)

    val jwtService by inject<com.saastracker.transport.security.JwtService>()
    configureAuth(jwtService)

    val scheduler by inject<Scheduler>()
    val alertService by inject<com.saastracker.domain.service.AlertService>()
    val authService by inject<com.saastracker.domain.service.AuthService>()
    val companyRepository by inject<com.saastracker.persistence.repository.CompanyRepository>()
    val userRepository by inject<com.saastracker.persistence.repository.UserRepository>()
    val subscriptionService by inject<com.saastracker.domain.service.SubscriptionService>()
    val subscriptionRepository by inject<com.saastracker.persistence.repository.SubscriptionRepository>()
    val dashboardService by inject<com.saastracker.domain.service.DashboardService>()
    val analyticsService by inject<com.saastracker.domain.service.AnalyticsService>()
    val spendSnapshotService by inject<com.saastracker.domain.service.SpendSnapshotService>()
    val healthService by inject<com.saastracker.domain.service.HealthService>()
    val notificationService by inject<com.saastracker.domain.service.NotificationService>()
    val meterRegistry by inject<io.micrometer.prometheusmetrics.PrometheusMeterRegistry>()
    val stripeBillingService by inject<com.saastracker.transport.payment.StripeBillingService>()
    val auditLogRepository by inject<com.saastracker.persistence.repository.AuditLogRepository>()
    val invitationRepository by inject<com.saastracker.persistence.repository.TeamInvitationRepository>()
    val emailDeliveryRepository by inject<com.saastracker.persistence.repository.EmailDeliveryRepository>()
    val clockProvider by inject<com.saastracker.persistence.repository.ClockProvider>()

    val budgetAlertService by inject<com.saastracker.domain.service.BudgetAlertService>()
    val insightsService by inject<com.saastracker.domain.service.InsightsService>()
    val roiService by inject<com.saastracker.domain.service.RoiService>()
    val redisCache by inject<com.saastracker.transport.cache.RedisCache>()

    RenewalJobRuntime.alertService = alertService
    RenewalJobRuntime.metrics = metrics
    RenewalJobRuntime.budgetAlertService = budgetAlertService
    SpendSnapshotJobRuntime.snapshotService = spendSnapshotService
    DataRetentionJobRuntime.refreshTokenRepository = inject<com.saastracker.persistence.repository.RefreshTokenRepository>().value
    DataRetentionJobRuntime.emailDeliveryRepository = emailDeliveryRepository
    DataRetentionJobRuntime.auditLogRepository = auditLogRepository
    DataRetentionJobRuntime.passwordResetRepository = inject<com.saastracker.persistence.repository.PasswordResetRepository>().value
    DigestJobRuntime.digestService = inject<com.saastracker.domain.service.DigestService>().value
    ZombieJobRuntime.companyRepository = companyRepository
    ZombieJobRuntime.subscriptionRepository = subscriptionRepository
    ZombieJobRuntime.clock = clockProvider
    PriceCheckJobRuntime.companyRepository = companyRepository
    PriceCheckJobRuntime.insightsService = insightsService

    routing {
        healthRoutes(healthService, meterRegistry)
        authRoutes(authService, userRepository, companyRepository, stripeBillingService)
        subscriptionRoutes(subscriptionService, userRepository, companyRepository, subscriptionRepository, stripeBillingService, rateLimiter)
        dashboardRoutes(dashboardService, userRepository, companyRepository, stripeBillingService)
        notificationRoutes(notificationService, userRepository, companyRepository, stripeBillingService, clockProvider)
        analyticsRoutes(analyticsService, spendSnapshotService, userRepository, companyRepository, stripeBillingService)
        teamRoutes(
            userRepository = userRepository,
            auditLogRepository = auditLogRepository,
            invitationRepository = invitationRepository,
            emailDeliveryRepository = emailDeliveryRepository,
            clock = clockProvider,
            companyRepository = companyRepository,
            stripeBillingService = stripeBillingService,
            subscriptionRepository = subscriptionRepository,
            savingsEventRepository = inject<com.saastracker.persistence.repository.SavingsEventRepository>().value,
            idProvider = inject<com.saastracker.persistence.repository.IdentityProvider>().value
        )
        insightsRoutes(insightsService, userRepository, companyRepository, stripeBillingService, redisCache)
        roiRoutes(roiService, userRepository, companyRepository, stripeBillingService)
        billingRoutes(stripeBillingService, userRepository, companyRepository)
        webhookRoutes(stripeBillingService)
    }

    environment.monitor.subscribe(io.ktor.server.application.ApplicationStopped) {
        if (!scheduler.isShutdown) {
            scheduler.shutdown(true)
        }
    }
}
