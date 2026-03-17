package com.saastracker.integration

import com.saastracker.config.DatabaseConfig
import com.saastracker.config.DatabaseFactory
import com.saastracker.domain.model.BillingCycle
import com.saastracker.domain.model.Company
import com.saastracker.domain.model.CompanySubscriptionStatus
import com.saastracker.domain.model.Subscription
import com.saastracker.domain.model.SubscriptionStatus
import com.saastracker.domain.model.User
import com.saastracker.domain.model.UserRole
import com.saastracker.domain.service.AlertService
import com.saastracker.persistence.repository.exposed.ExposedCompanyRepository
import com.saastracker.persistence.repository.exposed.ExposedRenewalAlertRepository
import com.saastracker.persistence.repository.exposed.ExposedSubscriptionRepository
import com.saastracker.persistence.repository.exposed.ExposedUserRepository
import com.saastracker.persistence.repository.exposed.SystemClockProvider
import com.saastracker.persistence.repository.exposed.UuidIdentityProvider
import com.saastracker.transport.email.EmailService
import com.saastracker.transport.email.EmailDeliveryResult
import com.saastracker.transport.email.EmailDeliveryStatus
import com.saastracker.transport.email.SubscriptionWithDaysLeft
import com.saastracker.transport.metrics.AppMetrics
import io.micrometer.prometheusmetrics.PrometheusConfig
import io.micrometer.prometheusmetrics.PrometheusMeterRegistry
import org.junit.jupiter.api.BeforeAll
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.TestInstance
import org.testcontainers.containers.PostgreSQLContainer
import org.testcontainers.junit.jupiter.Container
import org.testcontainers.junit.jupiter.Testcontainers
import java.math.BigDecimal
import java.time.Instant
import java.time.LocalDate
import java.util.UUID
import kotlin.test.assertEquals

@Testcontainers(disabledWithoutDocker = true)
@TestInstance(TestInstance.Lifecycle.PER_CLASS)
class SubscriptionIntegrationTest {

    companion object {
        @Container
        val postgres = PostgreSQLContainer("postgres:16")
            .withDatabaseName("saas_tracker_it")
            .withUsername("saas")
            .withPassword("saas")
    }

    private lateinit var companyRepository: ExposedCompanyRepository
    private lateinit var userRepository: ExposedUserRepository
    private lateinit var subscriptionRepository: ExposedSubscriptionRepository
    private lateinit var renewalAlertRepository: ExposedRenewalAlertRepository

    @BeforeAll
    fun setup() {
        val dbConfig = DatabaseConfig(
            jdbcUrl = postgres.jdbcUrl,
            username = postgres.username,
            password = postgres.password,
            maxPoolSize = 4
        )
        val dataSource = DatabaseFactory.createDataSource(dbConfig)
        DatabaseFactory.connect(dataSource)
        DatabaseFactory.migrate(dbConfig)

        companyRepository = ExposedCompanyRepository()
        userRepository = ExposedUserRepository()
        subscriptionRepository = ExposedSubscriptionRepository()
        renewalAlertRepository = ExposedRenewalAlertRepository()
    }

    @Test
    fun `full lifecycle - create subscription and receive renewal alert`() {
        val now = Instant.now()
        val company = companyRepository.create(
            Company(
                id = UUID.randomUUID(),
                name = "Acme",
                domain = "acme.io",
                stripeCustomerId = null,
                subscriptionStatus = CompanySubscriptionStatus.TRIAL,
                trialEndsAt = now.plusSeconds(86_400),
                monthlyBudget = BigDecimal("5000.00"),
                employeeCount = null,
                settings = "{}",
                createdAt = now,
                updatedAt = now
            )
        )
        val user = userRepository.create(
            User(
                id = UUID.randomUUID(),
                companyId = company.id,
                email = "admin@acme.io",
                name = "Admin",
                passwordHash = "hash",
                role = UserRole.ADMIN,
                isActive = true,
                lastLoginAt = now,
                createdAt = now
            )
        )
        val subscription = subscriptionRepository.create(
            Subscription(
                id = UUID.randomUUID(),
                companyId = company.id,
                createdById = user.id,
                vendorName = "Slack",
                vendorUrl = "https://slack.com",
                vendorLogoUrl = null,
                category = "communication",
                description = null,
                amount = BigDecimal("250.00"),
                currency = "USD",
                billingCycle = BillingCycle.MONTHLY,
                renewalDate = LocalDate.now().plusDays(30),
                contractStartDate = LocalDate.now().minusMonths(2),
                autoRenews = true,
                status = SubscriptionStatus.ACTIVE,
                tags = emptyList(),
                ownerId = user.id,
                notes = "Owned by Ops",
                documentUrl = null,
                createdAt = now,
                updatedAt = now
            )
        )

        var emailsSent = 0
        val emailService = object : EmailService {
            override fun sendRenewalDigest(company: Company, recipients: List<String>, subscriptions: List<SubscriptionWithDaysLeft>) {
                emailsSent += 1
                assertEquals(1, subscriptions.size)
            }

            override fun sendTeamInviteEmail(email: String, token: String): EmailDeliveryResult =
                EmailDeliveryResult(status = EmailDeliveryStatus.SENT, message = "stub")

            override fun sendBudgetAlertEmail(
                to: String,
                companyName: String,
                thresholdPercent: Int,
                currentSpend: BigDecimal,
                budget: BigDecimal
            ) { /* stub */ }
        }
        val alertService = AlertService(
            companyRepository = companyRepository,
            userRepository = userRepository,
            subscriptionRepository = subscriptionRepository,
            renewalAlertRepository = renewalAlertRepository,
            emailService = emailService,
            metrics = AppMetrics(PrometheusMeterRegistry(PrometheusConfig.DEFAULT)),
            idProvider = UuidIdentityProvider(),
            clock = SystemClockProvider()
        )

        val firstRun = alertService.runDailyRenewalCheck()
        val alertsAfterFirstRun = renewalAlertRepository.listByCompany(company.id)
        val secondRun = alertService.runDailyRenewalCheck()
        val alertsAfterSecondRun = renewalAlertRepository.listByCompany(company.id)

        assertEquals(1, firstRun.alertsCreated)
        assertEquals(1, emailsSent)
        assertEquals(1, alertsAfterFirstRun.size)
        assertEquals(0, secondRun.alertsCreated)
        assertEquals(1, alertsAfterSecondRun.size)
        assertEquals(subscription.id, alertsAfterSecondRun.first().subscriptionId)
    }
}
