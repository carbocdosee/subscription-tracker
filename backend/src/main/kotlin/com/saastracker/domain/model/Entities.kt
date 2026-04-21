package com.saastracker.domain.model

import java.math.BigDecimal
import java.time.Instant
import java.time.LocalDate
import java.util.UUID

data class Company(
    val id: UUID,
    val name: String,
    val domain: String,
    val stripeCustomerId: String?,
    val subscriptionStatus: CompanySubscriptionStatus,
    val trialEndsAt: Instant,
    val monthlyBudget: BigDecimal?,
    val employeeCount: Int?,
    val settings: String,
    val createdAt: Instant,
    val updatedAt: Instant,
    val planTier: PlanTier = PlanTier.FREE,
    val weeklyDigestEnabled: Boolean = true,
    val timezone: String = "UTC",
    val zombieThresholdDays: Int = 60
)

data class User(
    val id: UUID,
    val companyId: UUID,
    val email: String,
    val name: String,
    val passwordHash: String,
    val role: UserRole,
    val isActive: Boolean,
    val lastLoginAt: Instant?,
    val createdAt: Instant
)

data class Subscription(
    val id: UUID,
    val companyId: UUID,
    val createdById: UUID,
    val vendorName: String,
    val vendorUrl: String?,
    val vendorLogoUrl: String?,
    val category: String,
    val description: String?,
    val amount: BigDecimal,
    val currency: String,
    val billingCycle: BillingCycle,
    val renewalDate: LocalDate,
    val contractStartDate: LocalDate?,
    val autoRenews: Boolean,
    val paymentMode: PaymentMode = PaymentMode.AUTO,
    val paymentStatus: PaymentStatus = PaymentStatus.PAID,
    val lastPaidAt: LocalDate? = null,
    val nextPaymentDate: LocalDate? = null,
    val status: SubscriptionStatus,
    val tags: List<String>?,
    val ownerId: UUID?,
    val notes: String?,
    val documentUrl: String?,
    val createdAt: Instant,
    val updatedAt: Instant,
    val archivedAt: Instant? = null,
    val archivedById: UUID? = null,
    val lastUsedAt: Instant? = null,
    val isZombie: Boolean = false
)

data class RenewalAlert(
    val id: UUID,
    val subscriptionId: UUID,
    val companyId: UUID,
    val alertType: AlertType,
    val alertWindowDays: Int,
    val renewalDateSnapshot: LocalDate,
    val deliveryStatus: AlertDeliveryStatus,
    val sentAt: Instant?,
    val failureReason: String?,
    val emailRecipients: List<String>
)

data class AuditLogEntry(
    val id: UUID,
    val companyId: UUID,
    val userId: UUID,
    val action: AuditAction,
    val entityType: AuditEntityType,
    val entityId: UUID,
    val oldValue: String?,
    val newValue: String?,
    val createdAt: Instant
)

data class TeamInvitation(
    val id: UUID,
    val companyId: UUID,
    val invitedByUserId: UUID,
    val email: String,
    val role: UserRole,
    val token: String,
    val expiresAt: Instant,
    val acceptedAt: Instant?,
    val createdAt: Instant
)

data class SubscriptionComment(
    val id: UUID,
    val subscriptionId: UUID,
    val companyId: UUID,
    val userId: UUID,
    val body: String,
    val createdAt: Instant
)

data class SubscriptionPayment(
    val id: UUID,
    val subscriptionId: UUID,
    val companyId: UUID,
    val recordedByUserId: UUID,
    val amount: BigDecimal,
    val currency: String,
    val paidAt: LocalDate,
    val paymentReference: String?,
    val note: String?,
    val createdAt: Instant
)

data class EmailDeliveryLog(
    val id: UUID,
    val companyId: UUID,
    val invitationId: UUID?,
    val recipientEmail: String,
    val templateType: EmailTemplateType,
    val status: EmailDeliveryState,
    val providerMessageId: String?,
    val providerStatusCode: Int?,
    val providerResponse: String?,
    val errorMessage: String?,
    val createdAt: Instant
)

data class UserNotificationRead(
    val userId: UUID,
    val notificationKey: String,
    val readAt: Instant
)

data class SpendSnapshot(
    val id: UUID,
    val companyId: UUID,
    val year: Int,
    val month: Int,
    val totalMonthlyUsd: BigDecimal,
    val subscriptionCount: Int,
    val createdAt: Instant,
    val updatedAt: Instant
)

data class SavingsEvent(
    val id: UUID,
    val companyId: UUID,
    val subscriptionId: UUID?,
    val eventType: SavingsEventType,
    val vendorName: String,
    val amount: BigDecimal,
    val currency: String,
    val savedAt: Instant
)
