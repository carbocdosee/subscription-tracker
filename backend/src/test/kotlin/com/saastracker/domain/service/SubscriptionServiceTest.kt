package com.saastracker.domain.service

import com.saastracker.domain.dto.Page
import com.saastracker.domain.dto.PageRequest
import com.saastracker.domain.dto.SubscriptionFilter
import com.saastracker.domain.model.BillingCycle
import com.saastracker.domain.model.PaymentMode
import com.saastracker.domain.model.PaymentStatus
import com.saastracker.domain.model.Subscription
import com.saastracker.domain.model.SubscriptionStatus
import com.saastracker.domain.model.User
import com.saastracker.domain.model.UserRole
import com.saastracker.persistence.repository.AuditLogRepository
import com.saastracker.persistence.repository.ClockProvider
import com.saastracker.persistence.repository.CurrencyRateRepository
import com.saastracker.persistence.repository.IdentityProvider
import com.saastracker.persistence.repository.SavingsEventRepository
import com.saastracker.persistence.repository.SubscriptionCommentRepository
import com.saastracker.persistence.repository.SubscriptionPaymentRepository
import com.saastracker.persistence.repository.SubscriptionRepository
import com.saastracker.util.daysUntil
import com.saastracker.util.normalizeToMonthly
import io.mockk.every
import io.mockk.mockk
import io.mockk.verify
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Test
import java.math.BigDecimal
import java.time.Instant
import java.time.LocalDate
import java.time.temporal.ChronoUnit
import java.util.UUID
import com.saastracker.transport.http.request.MarkSubscriptionPaidRequest
import com.saastracker.domain.error.AppResult

class SubscriptionServiceTest {
    private val subscriptionRepository = mockk<SubscriptionRepository>()
    private val commentRepository = mockk<SubscriptionCommentRepository>(relaxed = true)
    private val paymentRepository = mockk<SubscriptionPaymentRepository>(relaxed = true)
    private val auditLogRepository = mockk<AuditLogRepository>(relaxed = true)
    private val currencyService = CurrencyService(mockk<CurrencyRateRepository>(relaxed = true).also {
        every { it.getRateToUsd("USD") } returns 1.0
        every { it.getRateToUsd("EUR") } returns 1.1
    })
    private val vendorLogoService = VendorLogoService()
    private val idProvider = mockk<IdentityProvider>(relaxed = true)
    private val clockProvider = mockk<ClockProvider>(relaxed = true)
    private val savingsEventRepository = mockk<SavingsEventRepository>(relaxed = true)

    private val service = SubscriptionService(
        subscriptionRepository = subscriptionRepository,
        commentRepository = commentRepository,
        paymentRepository = paymentRepository,
        auditLogRepository = auditLogRepository,
        currencyService = currencyService,
        vendorLogoService = vendorLogoService,
        idProvider = idProvider,
        clockProvider = clockProvider,
        savingsEventRepository = savingsEventRepository
    )

    init {
        every { clockProvider.nowDate() } returns LocalDate.now()
    }

    @Test
    fun `should detect duplicate vendor`() {
        val companyId = UUID.randomUUID()
        val sharedVendor = "Notion"
        every { subscriptionRepository.listActiveByCompany(companyId) } returns listOf(
            sampleSubscription(companyId, sharedVendor, "docs"),
            sampleSubscription(companyId, sharedVendor, "knowledge")
        )

        val duplicate = service.detectDuplicates(companyId)

        assertTrue(duplicate.warnings.any { it.contains("Duplicate vendor detected", ignoreCase = true) })
    }

    @Test
    fun `should normalize monthly spend from annual billing`() {
        val normalized = normalizeToMonthly(BigDecimal("1200.00"), BillingCycle.ANNUAL)
        assertEquals(BigDecimal("100.00"), normalized)
    }

    @Test
    fun `should calculate correct days until renewal`() {
        val renewalDate = LocalDate.now().plusDays(30)
        val days = daysUntil(renewalDate)
        assertEquals(30, days)
    }

    @Test
    fun `should not send duplicate alerts`() {
        val companyId = UUID.randomUUID()
        every { subscriptionRepository.listActiveByCompany(companyId) } returns emptyList()
        val detection = service.detectDuplicates(companyId)
        assertTrue(detection.warnings.isEmpty())
    }

    @Test
    fun `should calculate total spend in USD when currencies mixed`() {
        val companyId = UUID.randomUUID()
        val usdSubscription = sampleSubscription(companyId, "Slack", "communication", "100.00", "USD", BillingCycle.MONTHLY)
        val eurSubscription = sampleSubscription(companyId, "Figma", "design", "100.00", "EUR", BillingCycle.MONTHLY)
        every { subscriptionRepository.listByCompany(companyId) } returns listOf(usdSubscription, eurSubscription)
        every { subscriptionRepository.listActiveByCompany(companyId) } returns listOf(usdSubscription, eurSubscription)
        val dashboardService = DashboardService(subscriptionRepository, service, currencyService, clockProvider)

        val stats = dashboardService.getDashboardStats(companyId)

        assertEquals(BigDecimal("210.00"), stats.totalMonthlySpend)
        assertEquals(2, stats.topCostDrivers.size)
        assertEquals("Figma", stats.topCostDrivers.first().vendorName)
    }

    @Test
    fun `should mark manual subscription as paid and recalculate next payment date`() {
        val companyId = UUID.randomUUID()
        val nowDate = LocalDate.parse("2026-02-15")
        val nowInstant = Instant.parse("2026-02-15T12:00:00Z")
        val subscription = sampleSubscription(
            companyId = companyId,
            vendorName = "Notion",
            category = "docs"
        ).copy(
            paymentMode = PaymentMode.MANUAL,
            paymentStatus = PaymentStatus.OVERDUE,
            nextPaymentDate = LocalDate.parse("2026-02-10")
        )
        val user = sampleUser(companyId)

        every { clockProvider.nowDate() } returns nowDate
        every { clockProvider.nowInstant() } returns nowInstant
        every { subscriptionRepository.findById(subscription.id, companyId) } returns subscription
        every { subscriptionRepository.update(any()) } answers { firstArg() }
        every { paymentRepository.create(any()) } answers { firstArg() }
        every { idProvider.newId() } returnsMany listOf(UUID.randomUUID(), UUID.randomUUID())

        val result = service.markAsPaid(user, subscription.id, MarkSubscriptionPaidRequest())

        assertTrue(result is AppResult.Success)
        val updated = (result as AppResult.Success).value
        assertEquals(PaymentStatus.PENDING, updated.paymentStatus)
        assertEquals(LocalDate.parse("2026-02-15"), updated.lastPaidAt)
        assertEquals(LocalDate.parse("2026-03-15"), updated.nextPaymentDate)
        verify(exactly = 1) { paymentRepository.create(any()) }
    }

    @Test
    fun `listPaged should return correct page and total`() {
        val companyId = UUID.randomUUID()
        val allSubs = (1..50).map { i -> sampleSubscription(companyId, "Vendor $i", "category") }
        val pageRequest = PageRequest(page = 2, size = 10)
        val expectedPage = Page(items = allSubs.drop(10).take(10), total = 50L, page = 2, size = 10)

        every {
            subscriptionRepository.listByCompanyPaged(companyId, SubscriptionFilter(), pageRequest)
        } returns expectedPage

        val result = service.listPaged(companyId, SubscriptionFilter(), pageRequest)

        assertEquals(50L, result.total)
        assertEquals(2, result.page)
        assertEquals(10, result.size)
        assertEquals(5, result.totalPages)
        assertEquals(10, result.items.size)
    }

    @Test
    fun `listPaged should pass filter to repository`() {
        val companyId = UUID.randomUUID()
        val filter = SubscriptionFilter(vendorName = "Slack", status = SubscriptionStatus.ACTIVE)
        val pageRequest = PageRequest(page = 1, size = 25)
        val matching = listOf(sampleSubscription(companyId, "Slack", "communication"))
        val expectedPage = Page(items = matching, total = 1L, page = 1, size = 25)

        every { subscriptionRepository.listByCompanyPaged(companyId, filter, pageRequest) } returns expectedPage

        val result = service.listPaged(companyId, filter, pageRequest)

        assertEquals(1L, result.total)
        assertEquals(1, result.items.size)
        assertEquals("Slack", result.items.first().vendorName)
        verify(exactly = 1) { subscriptionRepository.listByCompanyPaged(companyId, filter, pageRequest) }
    }

    @Test
    fun `Page totalPages calculation should handle edge cases`() {
        assertEquals(0, Page<Any>(emptyList(), 0L, 1, 25).totalPages)
        assertEquals(1, Page<Any>(emptyList(), 1L, 1, 25).totalPages)
        assertEquals(1, Page<Any>(emptyList(), 25L, 1, 25).totalPages)
        assertEquals(2, Page<Any>(emptyList(), 26L, 1, 25).totalPages)
        assertEquals(4, Page<Any>(emptyList(), 100L, 1, 25).totalPages)
    }

    @Test
    fun `should reject mark as paid for auto subscriptions`() {
        val companyId = UUID.randomUUID()
        val subscription = sampleSubscription(
            companyId = companyId,
            vendorName = "Slack",
            category = "communication"
        ).copy(paymentMode = PaymentMode.AUTO)
        val user = sampleUser(companyId)

        every { subscriptionRepository.findById(subscription.id, companyId) } returns subscription

        val result = service.markAsPaid(user, subscription.id, MarkSubscriptionPaidRequest())

        assertTrue(result is AppResult.Failure)
        verify(exactly = 0) { paymentRepository.create(any()) }
    }

    private fun sampleSubscription(
        companyId: UUID,
        vendorName: String,
        category: String,
        amount: String = "100.00",
        currency: String = "USD",
        billingCycle: BillingCycle = BillingCycle.MONTHLY
    ): Subscription = Subscription(
        id = UUID.randomUUID(),
        companyId = companyId,
        createdById = UUID.randomUUID(),
        vendorName = vendorName,
        vendorUrl = null,
        vendorLogoUrl = null,
        category = category,
        description = null,
        amount = BigDecimal(amount),
        currency = currency,
        billingCycle = billingCycle,
        renewalDate = LocalDate.now().plus(30, ChronoUnit.DAYS),
        contractStartDate = LocalDate.now().minusMonths(3),
        autoRenews = true,
        status = SubscriptionStatus.ACTIVE,
        tags = emptyList(),
        ownerId = UUID.randomUUID(),
        notes = "owner assigned",
        documentUrl = null,
        createdAt = Instant.now(),
        updatedAt = Instant.now()
    )

    private fun sampleUser(companyId: UUID): User = User(
        id = UUID.randomUUID(),
        companyId = companyId,
        email = "editor@acme.io",
        name = "Editor",
        passwordHash = "hash",
        role = UserRole.EDITOR,
        isActive = true,
        lastLoginAt = Instant.now(),
        createdAt = Instant.now()
    )
}
