package com.saastracker.config

import com.saastracker.domain.service.AlertService
import com.saastracker.domain.service.AnalyticsService
import com.saastracker.domain.service.AuthService
import com.saastracker.domain.service.BudgetAlertService
import com.saastracker.domain.service.CurrencyService
import com.saastracker.domain.service.DashboardService
import com.saastracker.domain.service.DigestService
import com.saastracker.domain.service.InsightsService
import com.saastracker.domain.service.RoiService
import com.saastracker.domain.service.HealthService
import com.saastracker.domain.service.NotificationService
import com.saastracker.domain.service.PasswordService
import com.saastracker.domain.service.SpendSnapshotService
import com.saastracker.domain.service.SubscriptionService
import com.saastracker.domain.service.VendorLogoService
import com.saastracker.persistence.repository.AuditLogRepository
import com.saastracker.persistence.repository.ClockProvider
import com.saastracker.persistence.repository.CompanyRepository
import com.saastracker.persistence.repository.CurrencyRateRepository
import com.saastracker.persistence.repository.EmailDeliveryRepository
import com.saastracker.persistence.repository.IdentityProvider
import com.saastracker.persistence.repository.NotificationReadRepository
import com.saastracker.persistence.repository.RenewalAlertRepository
import com.saastracker.persistence.repository.SavingsEventRepository
import com.saastracker.persistence.repository.SpendSnapshotRepository
import com.saastracker.persistence.repository.SubscriptionCommentRepository
import com.saastracker.persistence.repository.BudgetAlertRepository
import com.saastracker.persistence.repository.PasswordResetRepository
import com.saastracker.persistence.repository.RefreshTokenRepository
import com.saastracker.persistence.repository.SubscriptionPaymentRepository
import com.saastracker.persistence.repository.SubscriptionRepository
import com.saastracker.persistence.repository.TeamInvitationRepository
import com.saastracker.persistence.repository.UserRepository
import com.saastracker.persistence.repository.exposed.ExposedAuditLogRepository
import com.saastracker.persistence.repository.exposed.ExposedBudgetAlertRepository
import com.saastracker.persistence.repository.exposed.ExposedPasswordResetRepository
import com.saastracker.persistence.repository.exposed.ExposedRefreshTokenRepository
import com.saastracker.persistence.repository.exposed.ExposedCompanyRepository
import com.saastracker.persistence.repository.exposed.ExposedEmailDeliveryRepository
import com.saastracker.persistence.repository.exposed.ExposedNotificationReadRepository
import com.saastracker.persistence.repository.exposed.ExposedRenewalAlertRepository
import com.saastracker.persistence.repository.exposed.ExposedSavingsEventRepository
import com.saastracker.persistence.repository.exposed.ExposedSpendSnapshotRepository
import com.saastracker.persistence.repository.exposed.ExposedSubscriptionCommentRepository
import com.saastracker.persistence.repository.exposed.ExposedSubscriptionPaymentRepository
import com.saastracker.persistence.repository.exposed.ExposedSubscriptionRepository
import com.saastracker.persistence.repository.exposed.ExposedTeamInvitationRepository
import com.saastracker.persistence.repository.exposed.ExposedUserRepository
import com.saastracker.persistence.repository.exposed.InMemoryCurrencyRateRepository
import com.saastracker.persistence.repository.exposed.SystemClockProvider
import com.saastracker.persistence.repository.exposed.UuidIdentityProvider
import com.saastracker.transport.cache.RedisCache
import com.saastracker.transport.email.EmailService
import com.saastracker.transport.email.ResendEmailService
import com.saastracker.transport.email.SmtpEmailService
import com.saastracker.transport.metrics.AppMetrics
import com.saastracker.transport.payment.StripeBillingService
import com.saastracker.transport.security.JwtService
import io.ktor.client.HttpClient
import io.ktor.client.engine.cio.CIO
import io.ktor.client.plugins.contentnegotiation.ContentNegotiation
import io.ktor.serialization.kotlinx.json.json
import io.lettuce.core.RedisClient
import io.lettuce.core.api.StatefulRedisConnection
import io.micrometer.core.instrument.MeterRegistry
import io.micrometer.prometheusmetrics.PrometheusConfig
import io.micrometer.prometheusmetrics.PrometheusMeterRegistry
import kotlinx.serialization.json.Json
import org.koin.core.module.Module
import org.koin.dsl.bind
import org.koin.dsl.module
import org.quartz.Scheduler
import javax.sql.DataSource

fun appModule(appConfig: AppConfig): Module = module {
    single { appConfig }
    single<DataSource> {
        DatabaseFactory.createDataSource(get<AppConfig>().database).also {
            DatabaseFactory.connect(it)
            DatabaseFactory.migrate(get<AppConfig>().database)
        }
    }

    single<RedisClient> { RedisFactory.createClient(get<AppConfig>().redis) }
    single<StatefulRedisConnection<String, String>> { RedisFactory.connect(get()) }
    single {
        RedisCache(
            connection = get(),
            defaultTtl = java.time.Duration.ofSeconds(get<AppConfig>().redis.cacheTtlSeconds)
        )
    }

    single {
        HttpClient(CIO) {
            install(ContentNegotiation) {
                json(Json { ignoreUnknownKeys = true })
            }
        }
    }

    single { PrometheusMeterRegistry(PrometheusConfig.DEFAULT) } bind MeterRegistry::class
    single { AppMetrics(get()) }

    single<Scheduler> {
        QuartzFactory.buildScheduler(get<AppConfig>().scheduler.renewalCron).apply {
            start()
        }
    }

    single { JwtService(get<AppConfig>().jwt) }
    single { PasswordService() }
    single { VendorLogoService() }

    single { UuidIdentityProvider() } bind IdentityProvider::class
    single { SystemClockProvider() } bind ClockProvider::class

    single { InMemoryCurrencyRateRepository() } bind CurrencyRateRepository::class

    single { ExposedCompanyRepository() } bind CompanyRepository::class
    single { ExposedUserRepository() } bind UserRepository::class
    single { ExposedSubscriptionRepository() } bind SubscriptionRepository::class
    single { ExposedRenewalAlertRepository() } bind RenewalAlertRepository::class
    single { ExposedAuditLogRepository() } bind AuditLogRepository::class
    single { ExposedTeamInvitationRepository() } bind TeamInvitationRepository::class
    single { ExposedEmailDeliveryRepository() } bind EmailDeliveryRepository::class
    single { ExposedNotificationReadRepository() } bind NotificationReadRepository::class
    single { ExposedSubscriptionCommentRepository() } bind SubscriptionCommentRepository::class
    single { ExposedSubscriptionPaymentRepository() } bind SubscriptionPaymentRepository::class
    single { ExposedSpendSnapshotRepository() } bind SpendSnapshotRepository::class
    single { ExposedBudgetAlertRepository() } bind BudgetAlertRepository::class
    single { ExposedRefreshTokenRepository() } bind RefreshTokenRepository::class
    single { ExposedPasswordResetRepository() } bind PasswordResetRepository::class
    single { ExposedSavingsEventRepository() } bind SavingsEventRepository::class

    single<EmailService> {
        val config = get<AppConfig>()
        when (config.email.provider) {
            EmailProvider.SMTP -> SmtpEmailService(config.smtp)
            EmailProvider.RESEND -> ResendEmailService(config.resend, get())
        }
    }
    single { StripeBillingService(get<AppConfig>().stripe, get(), get(), get()) }
    single { CurrencyService(get()) }
    single { SubscriptionService(get(), get(), get(), get(), get(), get(), get(), get(), get()) }
    single { DashboardService(get(), get(), get(), get()) }
    single { SpendSnapshotService(get(), get(), get(), get(), get(), get()) }
    single { AnalyticsService(get(), get(), get(), get(), get(), get(), get()) }
    single { AuthService(get(), get(), get(), get(), get(), get(), get(), get(), get(), get(), get(), get(), get(), get()) }
    single { AlertService(get(), get(), get(), get(), get(), get(), get(), get()) }
    single { NotificationService(get(), get(), get(), get(), get(), get(), get()) }
    single { HealthService(get(), get(), get()) }
    single { BudgetAlertService(get(), get(), get(), get(), get(), get(), get()) }
    single { DigestService(get(), get(), get(), get(), get(), get(), get()) }
    single { InsightsService(get(), get(), get(), get()) }
    single { RoiService(get(), get()) }
}
