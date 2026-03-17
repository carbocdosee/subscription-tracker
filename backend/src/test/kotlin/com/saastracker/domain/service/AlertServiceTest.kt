package com.saastracker.domain.service

import com.saastracker.domain.model.Company
import com.saastracker.domain.model.CompanySubscriptionStatus
import com.saastracker.domain.model.AlertDeliveryStatus
import com.saastracker.domain.model.AlertType
import com.saastracker.domain.model.RenewalAlert
import com.saastracker.domain.model.Subscription
import com.saastracker.domain.model.SubscriptionStatus
import com.saastracker.domain.model.User
import com.saastracker.domain.model.UserRole
import com.saastracker.persistence.repository.ClockProvider
import com.saastracker.persistence.repository.CompanyRepository
import com.saastracker.persistence.repository.IdentityProvider
import com.saastracker.persistence.repository.RenewalAlertRepository
import com.saastracker.persistence.repository.SubscriptionRepository
import com.saastracker.persistence.repository.UserRepository
import com.saastracker.transport.email.EmailService
import com.saastracker.transport.metrics.AppMetrics
import io.mockk.every
import io.mockk.mockk
import io.mockk.verify
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Test
import java.math.BigDecimal
import java.time.Instant
import java.time.LocalDate
import java.util.UUID

class AlertServiceTest {
    private val companyRepository = mockk<CompanyRepository>()
    private val userRepository = mockk<UserRepository>()
    private val subscriptionRepository = mockk<SubscriptionRepository>()
    private val renewalAlertRepository = mockk<RenewalAlertRepository>(relaxed = true)
    private val emailService = mockk<EmailService>(relaxed = true)
    private val metrics = mockk<AppMetrics>(relaxed = true)
    private val idProvider = mockk<IdentityProvider>()
    private val clockProvider = mockk<ClockProvider>()

    private val service = AlertService(
        companyRepository = companyRepository,
        userRepository = userRepository,
        subscriptionRepository = subscriptionRepository,
        renewalAlertRepository = renewalAlertRepository,
        emailService = emailService,
        metrics = metrics,
        idProvider = idProvider,
        clock = clockProvider
    )

    @Test
    fun `should send alert only once per threshold period`() {
        val company = sampleCompany()
        val subscription = sampleSubscription(company.id, 30)
        val sentAlert = RenewalAlert(
            id = UUID.randomUUID(),
            subscriptionId = subscription.id,
            companyId = company.id,
            alertType = AlertType.DAYS_30,
            alertWindowDays = 30,
            renewalDateSnapshot = subscription.renewalDate,
            deliveryStatus = AlertDeliveryStatus.SENT,
            sentAt = Instant.parse("2026-02-14T09:00:00Z"),
            failureReason = null,
            emailRecipients = listOf("admin@acme.io")
        )
        every { companyRepository.listAll() } returns listOf(company)
        every { userRepository.listByCompany(company.id) } returns listOf(sampleAdmin(company.id))
        every { subscriptionRepository.listActiveByCompany(company.id) } returns listOf(subscription)
        every { renewalAlertRepository.findForThreshold(subscription.id, 30, subscription.renewalDate) } returnsMany listOf(null, sentAlert)
        every { renewalAlertRepository.create(any()) } answers { firstArg() }
        every { renewalAlertRepository.update(any()) } answers { firstArg() }
        every { clockProvider.nowDate() } returns LocalDate.now()
        every { clockProvider.nowInstant() } returns Instant.now()
        every { idProvider.newId() } returnsMany listOf(UUID.randomUUID(), UUID.randomUUID())

        val firstRun = service.runDailyRenewalCheck()
        val secondRun = service.runDailyRenewalCheck()

        assertEquals(1, firstRun.alertsCreated)
        assertEquals(0, secondRun.alertsCreated)
        verify(exactly = 1) { renewalAlertRepository.create(any()) }
        verify(exactly = 1) { renewalAlertRepository.update(match { it.deliveryStatus == AlertDeliveryStatus.SENT }) }
    }

    @Test
    fun `should group alerts by company in digest`() {
        val company = sampleCompany()
        val first = sampleSubscription(company.id, 30)
        val second = sampleSubscription(company.id, 7)
        every { companyRepository.listAll() } returns listOf(company)
        every { userRepository.listByCompany(company.id) } returns listOf(sampleAdmin(company.id))
        every { subscriptionRepository.listActiveByCompany(company.id) } returns listOf(first, second)
        every { renewalAlertRepository.findForThreshold(any(), any(), any()) } returns null
        every { renewalAlertRepository.create(any()) } answers { firstArg() }
        every { renewalAlertRepository.update(any()) } answers { firstArg() }
        every { clockProvider.nowDate() } returns LocalDate.now()
        every { clockProvider.nowInstant() } returns Instant.now()
        every { idProvider.newId() } returns UUID.randomUUID()

        service.runDailyRenewalCheck()

        verify(exactly = 1) { emailService.sendRenewalDigest(eq(company), any(), match { it.size == 2 }) }
    }

    @Test
    fun `should skip inactive subscriptions`() {
        val company = sampleCompany()
        val canceled = sampleSubscription(company.id, 30).copy(status = SubscriptionStatus.CANCELED)
        every { companyRepository.listAll() } returns listOf(company)
        every { userRepository.listByCompany(company.id) } returns listOf(sampleAdmin(company.id))
        every { subscriptionRepository.listActiveByCompany(company.id) } returns listOf(canceled)
        every { clockProvider.nowDate() } returns LocalDate.now()
        every { clockProvider.nowInstant() } returns Instant.now()
        every { idProvider.newId() } returns UUID.randomUUID()

        val summary = service.runDailyRenewalCheck()

        assertEquals(0, summary.alertsCreated)
        verify(exactly = 0) { renewalAlertRepository.create(any()) }
    }

    @Test
    fun `should handle email delivery failure gracefully`() {
        val company = sampleCompany()
        val subscription = sampleSubscription(company.id, 30)
        every { companyRepository.listAll() } returns listOf(company)
        every { userRepository.listByCompany(company.id) } returns listOf(sampleAdmin(company.id))
        every { subscriptionRepository.listActiveByCompany(company.id) } returns listOf(subscription)
        every { renewalAlertRepository.findForThreshold(any(), any(), any()) } returns null
        every { renewalAlertRepository.create(any()) } answers { firstArg() }
        every { renewalAlertRepository.update(any()) } answers { firstArg() }
        every { clockProvider.nowDate() } returns LocalDate.now()
        every { clockProvider.nowInstant() } returns Instant.now()
        every { idProvider.newId() } returns UUID.randomUUID()
        every { emailService.sendRenewalDigest(any(), any(), any()) } throws RuntimeException("SMTP timeout")

        val summary = service.runDailyRenewalCheck()

        assertEquals(1, summary.alertsCreated)
        verify(exactly = 0) { metrics.incrementAlertSent(any()) }
        verify(exactly = 1) { renewalAlertRepository.update(match { it.deliveryStatus == AlertDeliveryStatus.FAILED }) }
    }

    private fun sampleCompany(): Company = Company(
        id = UUID.randomUUID(),
        name = "Acme",
        domain = "acme.io",
        stripeCustomerId = null,
        subscriptionStatus = CompanySubscriptionStatus.TRIAL,
        trialEndsAt = Instant.now().plusSeconds(86_400),
        monthlyBudget = BigDecimal("1000.00"),
        employeeCount = null,
        settings = "{}",
        createdAt = Instant.now(),
        updatedAt = Instant.now()
    )

    private fun sampleAdmin(companyId: UUID): User = User(
        id = UUID.randomUUID(),
        companyId = companyId,
        email = "admin@acme.io",
        name = "Admin",
        passwordHash = "hash",
        role = UserRole.ADMIN,
        isActive = true,
        lastLoginAt = Instant.now(),
        createdAt = Instant.now()
    )

    private fun sampleSubscription(companyId: UUID, daysUntilRenewal: Long): Subscription = Subscription(
        id = UUID.randomUUID(),
        companyId = companyId,
        createdById = UUID.randomUUID(),
        vendorName = "Slack",
        vendorUrl = null,
        vendorLogoUrl = null,
        category = "communication",
        description = null,
        amount = BigDecimal("200.00"),
        currency = "USD",
        billingCycle = com.saastracker.domain.model.BillingCycle.MONTHLY,
        renewalDate = LocalDate.now().plusDays(daysUntilRenewal),
        contractStartDate = LocalDate.now().minusMonths(4),
        autoRenews = true,
        status = SubscriptionStatus.ACTIVE,
        tags = emptyList(),
        ownerId = UUID.randomUUID(),
        notes = null,
        documentUrl = null,
        createdAt = Instant.now(),
        updatedAt = Instant.now()
    )
}
