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
    BUDGET_THRESHOLD
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
    RENEWAL_DIGEST
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
