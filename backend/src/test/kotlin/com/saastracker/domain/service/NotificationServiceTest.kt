package com.saastracker.domain.service

import com.saastracker.domain.model.AlertDeliveryStatus
import com.saastracker.domain.model.AlertType
import com.saastracker.domain.model.BillingCycle
import com.saastracker.domain.model.EmailDeliveryLog
import com.saastracker.domain.model.EmailDeliveryState
import com.saastracker.domain.model.EmailTemplateType
import com.saastracker.domain.model.NotificationType
import com.saastracker.domain.model.PaymentMode
import com.saastracker.domain.model.PaymentStatus
import com.saastracker.domain.model.RenewalAlert
import com.saastracker.domain.model.Subscription
import com.saastracker.domain.model.SubscriptionStatus
import com.saastracker.domain.model.TeamInvitation
import com.saastracker.domain.model.UserRole
import com.saastracker.persistence.repository.ClockProvider
import com.saastracker.persistence.repository.EmailDeliveryRepository
import com.saastracker.persistence.repository.NotificationReadRepository
import com.saastracker.persistence.repository.RenewalAlertRepository
import com.saastracker.persistence.repository.SubscriptionPaymentRepository
import com.saastracker.persistence.repository.SubscriptionRepository
import com.saastracker.persistence.repository.TeamInvitationRepository
import io.mockk.every
import io.mockk.mockk
import io.mockk.verify
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.Test
import java.math.BigDecimal
import java.time.Instant
import java.time.LocalDate
import java.util.UUID

class NotificationServiceTest {
    private val subscriptionRepository = mockk<SubscriptionRepository>()
    private val invitationRepository = mockk<TeamInvitationRepository>()
    private val emailDeliveryRepository = mockk<EmailDeliveryRepository>()
    private val renewalAlertRepository = mockk<RenewalAlertRepository>()
    private val notificationReadRepository = mockk<NotificationReadRepository>(relaxed = true)
    private val clockProvider = mockk<ClockProvider>()
    private val paymentRepository = mockk<SubscriptionPaymentRepository>(relaxed = true)

    private val service = NotificationService(
        subscriptionRepository = subscriptionRepository,
        invitationRepository = invitationRepository,
        emailDeliveryRepository = emailDeliveryRepository,
        renewalAlertRepository = renewalAlertRepository,
        notificationReadRepository = notificationReadRepository,
        clock = clockProvider,
        paymentRepository = paymentRepository
    )

    @Test
    fun `should build feed and unread count from multiple sources`() {
        val companyId = UUID.randomUUID()
        val nowDate = LocalDate.parse("2026-02-16")
        val nowInstant = Instant.parse("2026-02-16T09:00:00Z")
        val renewalSubscription = sampleSubscription(companyId, "Slack", nowDate.plusDays(7))
        val manualSubscription = sampleSubscription(companyId, "Notion", nowDate.plusDays(90)).copy(
            paymentMode = PaymentMode.MANUAL,
            paymentStatus = PaymentStatus.OVERDUE,
            nextPaymentDate = nowDate.minusDays(2)
        )
        val invitation = TeamInvitation(
            id = UUID.randomUUID(),
            companyId = companyId,
            invitedByUserId = UUID.randomUUID(),
            email = "new.member@acme.io",
            role = UserRole.VIEWER,
            token = "tok",
            expiresAt = nowInstant.plusSeconds(12 * 3600),
            acceptedAt = null,
            createdAt = nowInstant.minusSeconds(3600)
        )
        val deliveryIssue = EmailDeliveryLog(
            id = UUID.randomUUID(),
            companyId = companyId,
            invitationId = invitation.id,
            recipientEmail = invitation.email,
            templateType = EmailTemplateType.TEAM_INVITE,
            status = EmailDeliveryState.FAILED,
            providerMessageId = null,
            providerStatusCode = 500,
            providerResponse = "provider failure",
            errorMessage = "SMTP timeout",
            createdAt = nowInstant.minusSeconds(120)
        )
        val renewalFailure = RenewalAlert(
            id = UUID.randomUUID(),
            subscriptionId = renewalSubscription.id,
            companyId = companyId,
            alertType = AlertType.DAYS_7,
            alertWindowDays = 7,
            renewalDateSnapshot = nowDate.plusDays(7),
            deliveryStatus = AlertDeliveryStatus.FAILED,
            sentAt = nowInstant.minusSeconds(240),
            failureReason = "Resend timeout",
            emailRecipients = listOf("ops@acme.io")
        )

        every { clockProvider.nowDate() } returns nowDate
        every { clockProvider.nowInstant() } returns nowInstant
        every { subscriptionRepository.listByCompany(companyId) } returns listOf(renewalSubscription, manualSubscription)
        every { invitationRepository.listActiveByCompany(companyId, nowInstant) } returns listOf(invitation)
        every { emailDeliveryRepository.listByCompany(companyId, 120) } returns listOf(deliveryIssue)
        every { renewalAlertRepository.listByCompany(companyId) } returns listOf(renewalFailure)
        every { notificationReadRepository.listReadKeysByUser(any()) } returns setOf(
            "renewal_due:${renewalSubscription.id}:${renewalSubscription.renewalDate}"
        )

        val feed = service.getFeed(companyId = companyId, userId = UUID.randomUUID(), limit = 20)

        assertEquals(4, feed.unreadCount)
        assertEquals(5, feed.items.size)
        assertTrue(feed.items.any { it.type == NotificationType.MANUAL_PAYMENT_OVERDUE })
        assertTrue(feed.items.any { it.type == NotificationType.RENEWAL_ALERT_FAILED })
        assertTrue(feed.items.any { it.type == NotificationType.INVITATION_EMAIL_ISSUE })
    }

    @Test
    fun `should mark all unread notifications as read`() {
        val companyId = UUID.randomUUID()
        val userId = UUID.randomUUID()
        val nowDate = LocalDate.parse("2026-02-16")
        val nowInstant = Instant.parse("2026-02-16T09:00:00Z")
        val manualSubscription = sampleSubscription(companyId, "Notion", nowDate.plusDays(90)).copy(
            paymentMode = PaymentMode.MANUAL,
            nextPaymentDate = nowDate.minusDays(1)
        )
        val renewalSubscription = sampleSubscription(companyId, "Slack", nowDate.plusDays(5))

        every { clockProvider.nowDate() } returns nowDate
        every { clockProvider.nowInstant() } returns nowInstant
        every { subscriptionRepository.listByCompany(companyId) } returns listOf(manualSubscription, renewalSubscription)
        every { invitationRepository.listActiveByCompany(companyId, nowInstant) } returns emptyList()
        every { emailDeliveryRepository.listByCompany(companyId, 120) } returns emptyList()
        every { renewalAlertRepository.listByCompany(companyId) } returns emptyList()
        every { notificationReadRepository.listReadKeysByUser(userId) } returns emptySet()

        var capturedKeys: List<String> = emptyList()
        every { notificationReadRepository.markRead(userId, any(), nowInstant) } answers {
            capturedKeys = secondArg()
        }

        val marked = service.markAllAsRead(companyId = companyId, userId = userId)

        assertEquals(2, marked)
        assertEquals(2, capturedKeys.size)
        verify(exactly = 1) { notificationReadRepository.markRead(userId, any(), nowInstant) }
    }

    @Test
    fun `should support filtering by notification type`() {
        val companyId = UUID.randomUUID()
        val userId = UUID.randomUUID()
        val nowDate = LocalDate.parse("2026-02-16")
        val nowInstant = Instant.parse("2026-02-16T09:00:00Z")
        val renewalSubscription = sampleSubscription(companyId, "Slack", nowDate.plusDays(3))
        val manualSubscription = sampleSubscription(companyId, "Notion", nowDate.plusDays(20)).copy(
            paymentMode = PaymentMode.MANUAL,
            nextPaymentDate = nowDate.minusDays(1)
        )

        every { clockProvider.nowDate() } returns nowDate
        every { clockProvider.nowInstant() } returns nowInstant
        every { subscriptionRepository.listByCompany(companyId) } returns listOf(renewalSubscription, manualSubscription)
        every { invitationRepository.listActiveByCompany(companyId, nowInstant) } returns emptyList()
        every { emailDeliveryRepository.listByCompany(companyId, 120) } returns emptyList()
        every { renewalAlertRepository.listByCompany(companyId) } returns emptyList()
        every { notificationReadRepository.listReadKeysByUser(userId) } returns emptySet()

        val feed = service.getFeed(
            companyId = companyId,
            userId = userId,
            typeFilter = setOf(NotificationType.MANUAL_PAYMENT_OVERDUE)
        )

        assertEquals(1, feed.items.size)
        assertEquals(NotificationType.MANUAL_PAYMENT_OVERDUE, feed.items.first().type)
        assertEquals(1, feed.unreadCount)
    }

    private fun sampleSubscription(companyId: UUID, vendorName: String, renewalDate: LocalDate): Subscription = Subscription(
        id = UUID.randomUUID(),
        companyId = companyId,
        createdById = UUID.randomUUID(),
        vendorName = vendorName,
        vendorUrl = null,
        vendorLogoUrl = null,
        category = "tools",
        description = null,
        amount = BigDecimal("120.00"),
        currency = "USD",
        billingCycle = BillingCycle.MONTHLY,
        renewalDate = renewalDate,
        contractStartDate = renewalDate.minusMonths(8),
        autoRenews = true,
        paymentMode = PaymentMode.AUTO,
        paymentStatus = PaymentStatus.PAID,
        lastPaidAt = renewalDate.minusMonths(1),
        nextPaymentDate = null,
        status = SubscriptionStatus.ACTIVE,
        tags = emptyList(),
        ownerId = null,
        notes = null,
        documentUrl = null,
        createdAt = Instant.parse("2025-01-01T00:00:00Z"),
        updatedAt = Instant.parse("2026-02-01T00:00:00Z")
    )
}
