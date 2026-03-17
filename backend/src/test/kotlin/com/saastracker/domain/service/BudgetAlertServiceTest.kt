package com.saastracker.domain.service

import com.saastracker.domain.model.BillingCycle
import com.saastracker.domain.model.Company
import com.saastracker.domain.model.CompanySubscriptionStatus
import com.saastracker.domain.model.Subscription
import com.saastracker.domain.model.SubscriptionStatus
import com.saastracker.domain.model.User
import com.saastracker.domain.model.UserRole
import com.saastracker.persistence.repository.BudgetAlertRepository
import com.saastracker.persistence.repository.ClockProvider
import com.saastracker.persistence.repository.CompanyRepository
import com.saastracker.persistence.repository.SubscriptionRepository
import com.saastracker.persistence.repository.UserRepository
import com.saastracker.transport.email.EmailService
import io.mockk.every
import io.mockk.mockk
import io.mockk.verify
import org.junit.jupiter.api.Test
import java.math.BigDecimal
import java.time.Instant
import java.time.LocalDate
import java.util.UUID

class BudgetAlertServiceTest {
    private val companyRepository = mockk<CompanyRepository>()
    private val subscriptionRepository = mockk<SubscriptionRepository>()
    private val subscriptionService = mockk<SubscriptionService>()
    private val userRepository = mockk<UserRepository>()
    private val emailService = mockk<EmailService>(relaxed = true)
    private val budgetAlertRepository = mockk<BudgetAlertRepository>(relaxed = true)
    private val clock = mockk<ClockProvider>()

    private val service = BudgetAlertService(
        companyRepository = companyRepository,
        subscriptionRepository = subscriptionRepository,
        subscriptionService = subscriptionService,
        userRepository = userRepository,
        emailService = emailService,
        budgetAlertRepository = budgetAlertRepository,
        clock = clock
    )

    @Test
    fun `should not send alert when utilization is below 80 percent`() {
        val company = sampleCompany(budget = BigDecimal("1000.00"))
        val sub = sampleSubscription(company.id)
        every { subscriptionRepository.listByCompany(company.id) } returns listOf(sub)
        every { subscriptionService.normalizedMonthlyUsd(sub) } returns BigDecimal("790.00")
        every { userRepository.listByCompany(company.id) } returns listOf(sampleAdmin(company.id))

        service.checkCompany(company, 2026, 3)

        verify(exactly = 0) { emailService.sendBudgetAlertEmail(any(), any(), any(), any(), any()) }
        verify(exactly = 0) { budgetAlertRepository.record(any(), any(), any(), any()) }
    }

    @Test
    fun `should send 80 percent alert when utilization reaches 80 percent`() {
        val company = sampleCompany(budget = BigDecimal("1000.00"))
        val sub = sampleSubscription(company.id)
        every { subscriptionRepository.listByCompany(company.id) } returns listOf(sub)
        every { subscriptionService.normalizedMonthlyUsd(sub) } returns BigDecimal("800.00")
        every { userRepository.listByCompany(company.id) } returns listOf(sampleAdmin(company.id))
        every { budgetAlertRepository.exists(company.id, 2026, 3, 80) } returns false
        every { budgetAlertRepository.exists(company.id, 2026, 3, 100) } returns false

        service.checkCompany(company, 2026, 3)

        verify(exactly = 1) { emailService.sendBudgetAlertEmail(any(), any(), 80, any(), any()) }
        verify(exactly = 0) { emailService.sendBudgetAlertEmail(any(), any(), 100, any(), any()) }
        verify(exactly = 1) { budgetAlertRepository.record(company.id, 2026, 3, 80) }
    }

    @Test
    fun `should send both alerts when utilization reaches 100 percent`() {
        val company = sampleCompany(budget = BigDecimal("1000.00"))
        val sub = sampleSubscription(company.id)
        every { subscriptionRepository.listByCompany(company.id) } returns listOf(sub)
        every { subscriptionService.normalizedMonthlyUsd(sub) } returns BigDecimal("1000.00")
        every { userRepository.listByCompany(company.id) } returns listOf(sampleAdmin(company.id))
        every { budgetAlertRepository.exists(company.id, 2026, 3, 80) } returns false
        every { budgetAlertRepository.exists(company.id, 2026, 3, 100) } returns false

        service.checkCompany(company, 2026, 3)

        verify(exactly = 1) { emailService.sendBudgetAlertEmail(any(), any(), 80, any(), any()) }
        verify(exactly = 1) { emailService.sendBudgetAlertEmail(any(), any(), 100, any(), any()) }
        verify(exactly = 1) { budgetAlertRepository.record(company.id, 2026, 3, 80) }
        verify(exactly = 1) { budgetAlertRepository.record(company.id, 2026, 3, 100) }
    }

    @Test
    fun `should not send duplicate alert when already recorded this month`() {
        val company = sampleCompany(budget = BigDecimal("1000.00"))
        val sub = sampleSubscription(company.id)
        every { subscriptionRepository.listByCompany(company.id) } returns listOf(sub)
        every { subscriptionService.normalizedMonthlyUsd(sub) } returns BigDecimal("800.00")
        every { userRepository.listByCompany(company.id) } returns listOf(sampleAdmin(company.id))
        every { budgetAlertRepository.exists(company.id, 2026, 3, 80) } returns true
        every { budgetAlertRepository.exists(company.id, 2026, 3, 100) } returns false

        service.checkCompany(company, 2026, 3)

        verify(exactly = 0) { emailService.sendBudgetAlertEmail(any(), any(), any(), any(), any()) }
        verify(exactly = 0) { budgetAlertRepository.record(any(), any(), any(), any()) }
    }

    @Test
    fun `should skip company with no monthly budget`() {
        val company = sampleCompany(budget = null)
        every { subscriptionRepository.listByCompany(company.id) } returns emptyList()

        service.checkCompany(company, 2026, 3)

        verify(exactly = 0) { subscriptionRepository.listByCompany(any()) }
        verify(exactly = 0) { emailService.sendBudgetAlertEmail(any(), any(), any(), any(), any()) }
    }

    private fun sampleCompany(budget: BigDecimal?): Company = Company(
        id = UUID.randomUUID(),
        name = "Acme",
        domain = "acme.io",
        stripeCustomerId = null,
        subscriptionStatus = CompanySubscriptionStatus.ACTIVE,
        trialEndsAt = Instant.now().plusSeconds(86_400),
        monthlyBudget = budget,
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

    private fun sampleSubscription(companyId: UUID): Subscription = Subscription(
        id = UUID.randomUUID(),
        companyId = companyId,
        createdById = UUID.randomUUID(),
        vendorName = "Slack",
        vendorUrl = null,
        vendorLogoUrl = null,
        category = "communication",
        description = null,
        amount = BigDecimal("800.00"),
        currency = "USD",
        billingCycle = BillingCycle.MONTHLY,
        renewalDate = LocalDate.now().plusDays(30),
        contractStartDate = null,
        autoRenews = true,
        status = SubscriptionStatus.ACTIVE,
        tags = emptyList(),
        ownerId = null,
        notes = null,
        documentUrl = null,
        createdAt = Instant.now(),
        updatedAt = Instant.now()
    )
}
