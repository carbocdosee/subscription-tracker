package com.saastracker.domain.model

import kotlinx.serialization.Serializable

@Serializable
enum class CompanySubscriptionStatus {
    TRIAL,
    ACTIVE,
    PAST_DUE,
    CANCELED
}

@Serializable
enum class PlanTier {
    FREE, PRO, ENTERPRISE;

    fun isAtLeast(required: PlanTier): Boolean = this.ordinal >= required.ordinal
}

@Serializable
enum class PlanFeature {
    ANALYTICS,
    EMAIL_ALERTS,
    TEAM_INVITE,
    EXPORT,
    VENDOR_SUGGEST,
    SUBSCRIPTION_CREATE
}

object PlanMatrix {
    data class PlanLimits(
        val maxSubscriptions: Int,  // -1 = unlimited
        val maxTeamMembers: Int     // -1 = unlimited
    )

    val limits = mapOf(
        PlanTier.FREE       to PlanLimits(maxSubscriptions = 5,  maxTeamMembers = 1),
        PlanTier.PRO        to PlanLimits(maxSubscriptions = 50, maxTeamMembers = 5),
        PlanTier.ENTERPRISE to PlanLimits(maxSubscriptions = -1, maxTeamMembers = -1)
    )

    private val featureMinPlan = mapOf(
        PlanFeature.ANALYTICS       to PlanTier.PRO,
        PlanFeature.EMAIL_ALERTS    to PlanTier.PRO,
        PlanFeature.TEAM_INVITE     to PlanTier.PRO,
        PlanFeature.EXPORT          to PlanTier.PRO,
        PlanFeature.VENDOR_SUGGEST  to PlanTier.PRO,
        PlanFeature.SUBSCRIPTION_CREATE to PlanTier.FREE
    )

    fun canAccess(tier: PlanTier, feature: PlanFeature): Boolean =
        tier.isAtLeast(featureMinPlan[feature] ?: PlanTier.FREE)

    fun requiredPlanFor(feature: PlanFeature): PlanTier =
        featureMinPlan[feature] ?: PlanTier.FREE
}

@Serializable
enum class UserRole {
    ADMIN,
    EDITOR,
    VIEWER;

    fun canEdit(): Boolean = this == ADMIN || this == EDITOR
    fun isAdmin(): Boolean = this == ADMIN
}

@Serializable
enum class BillingCycle {
    MONTHLY,
    QUARTERLY,
    ANNUAL
}

@Serializable
enum class SubscriptionStatus {
    ACTIVE,
    CANCELED,
    PAUSED,
    EXPIRED
}

@Serializable
enum class PaymentMode {
    AUTO,
    MANUAL
}

@Serializable
enum class PaymentStatus {
    PAID,
    PENDING,
    OVERDUE
}

@Serializable
enum class AlertType(val days: Int) {
    DAYS_90(90),
    DAYS_60(60),
    DAYS_30(30),
    DAYS_7(7),
    DAYS_1(1);

    companion object {
        private val daysMap = entries.associateBy { it.days }

        fun fromDays(days: Int): AlertType? = daysMap[days]
    }
}

@Serializable
enum class AlertDeliveryStatus {
    PENDING,
    SENT,
    FAILED
}

@Serializable
enum class NotificationType {
    RENEWAL_DUE,
    RENEWAL_ALERT_FAILED,
    MANUAL_PAYMENT_DUE,
    MANUAL_PAYMENT_OVERDUE,
    INVITATION_EXPIRING,
    INVITATION_EMAIL_ISSUE,
    BUDGET_THRESHOLD,
    ZOMBIE_DETECTED,
    PRICE_INCREASED,
    WEEKLY_DIGEST
}

@Serializable
enum class NotificationSeverity {
    INFO,
    WARNING,
    DANGER
}

@Serializable
enum class EmailTemplateType {
    TEAM_INVITE,
    RENEWAL_DIGEST,
    WEEKLY_DIGEST
}

@Serializable
enum class SavingsEventType {
    ZOMBIE_ARCHIVED
}

@Serializable
enum class EmailDeliveryState {
    SENT,
    SKIPPED_NOT_CONFIGURED,
    FAILED
}

@Serializable
enum class HealthScore {
    GOOD,
    WARNING,
    CRITICAL
}

@Serializable
enum class AuditEntityType {
    SUBSCRIPTION,
    USER,
    INVITATION,
    COMPANY,
    COMMENT
}

@Serializable
enum class AuditAction {
    CREATED,
    UPDATED,
    DELETED,
    MARKED_PAID,
    INVITED,
    ACCEPTED_INVITE,
    ROLE_CHANGED,
    ARCHIVED
}
