package com.saastracker.persistence.table

import com.saastracker.domain.model.AlertType
import com.saastracker.domain.model.AlertDeliveryStatus
import com.saastracker.domain.model.AuditAction
import com.saastracker.domain.model.AuditEntityType
import com.saastracker.domain.model.BillingCycle
import com.saastracker.domain.model.CompanySubscriptionStatus
import com.saastracker.domain.model.EmailDeliveryState
import com.saastracker.domain.model.EmailTemplateType
import com.saastracker.domain.model.PaymentMode
import com.saastracker.domain.model.PaymentStatus
import com.saastracker.domain.model.SubscriptionStatus
import com.saastracker.domain.model.UserRole
import org.jetbrains.exposed.dao.id.UUIDTable
import org.jetbrains.exposed.sql.Table
import org.jetbrains.exposed.sql.javatime.date
import org.jetbrains.exposed.sql.javatime.timestamp
import org.jetbrains.exposed.sql.json.jsonb

object Companies : UUIDTable("companies") {
    val name = varchar("name", 255)
    val domain = varchar("domain", 255).uniqueIndex()
    val stripeCustomerId = varchar("stripe_customer_id", 255).nullable()
    val subscriptionStatus = enumerationByName("subscription_status", 50, CompanySubscriptionStatus::class)
        .default(CompanySubscriptionStatus.TRIAL)
    val trialEndsAt = timestamp("trial_ends_at")
    val monthlyBudget = decimal("monthly_budget", 10, 2).nullable()
    val employeeCount = integer("employee_count").nullable()
    val settings = jsonb("settings", { it }, { it }).default("{}")
    val createdAt = timestamp("created_at")
    val updatedAt = timestamp("updated_at")
}

object Users : UUIDTable("users") {
    val companyId = reference("company_id", Companies, onDelete = org.jetbrains.exposed.sql.ReferenceOption.CASCADE)
    val email = varchar("email", 255).uniqueIndex()
    val name = varchar("name", 255)
    val passwordHash = varchar("password_hash", 255)
    val role = enumerationByName("role", 50, UserRole::class).default(UserRole.VIEWER)
    val isActive = bool("is_active").default(true)
    val lastLoginAt = timestamp("last_login_at").nullable()
    val createdAt = timestamp("created_at")
}

object UserNotificationReads : Table("user_notification_reads") {
    val userId = reference("user_id", Users, onDelete = org.jetbrains.exposed.sql.ReferenceOption.CASCADE)
    val notificationKey = varchar("notification_key", 255)
    val readAt = timestamp("read_at")

    override val primaryKey = PrimaryKey(userId, notificationKey)
}

object Subscriptions : UUIDTable("subscriptions") {
    val companyId = reference("company_id", Companies, onDelete = org.jetbrains.exposed.sql.ReferenceOption.CASCADE)
    val createdById = reference("created_by_id", Users)
    val vendorName = varchar("vendor_name", 255)
    val vendorUrl = varchar("vendor_url", 255).nullable()
    val vendorLogoUrl = varchar("vendor_logo_url", 500).nullable()
    val category = varchar("category", 100)
    val description = text("description").nullable()
    val amount = decimal("amount", 10, 2)
    val currency = char("currency", 3).default("USD")
    val billingCycle = enumerationByName("billing_cycle", 20, BillingCycle::class)
    val renewalDate = date("renewal_date")
    val contractStartDate = date("contract_start_date").nullable()
    val autoRenews = bool("auto_renews").default(true)
    val paymentMode = enumerationByName("payment_mode", 20, PaymentMode::class).default(PaymentMode.AUTO)
    val paymentStatus = enumerationByName("payment_status", 20, PaymentStatus::class).default(PaymentStatus.PAID)
    val lastPaidAt = date("last_paid_at").nullable()
    val nextPaymentDate = date("next_payment_date").nullable()
    val status = enumerationByName("status", 50, SubscriptionStatus::class).default(SubscriptionStatus.ACTIVE)
    val tags = text("tags").nullable()
    val ownerId = reference("owner_id", Users).nullable()
    val notes = text("notes").nullable()
    val documentUrl = varchar("document_url", 500).nullable()
    val archivedAt = timestamp("archived_at").nullable()
    val archivedById = reference("archived_by_id", Users).nullable()
    val createdAt = timestamp("created_at")
    val updatedAt = timestamp("updated_at")
}

object RenewalAlerts : UUIDTable("renewal_alerts") {
    val subscriptionId = reference("subscription_id", Subscriptions, onDelete = org.jetbrains.exposed.sql.ReferenceOption.CASCADE)
    val companyId = reference("company_id", Companies)
    val alertType = enumerationByName("alert_type", 20, AlertType::class)
    val alertWindowDays = integer("alert_window_days")
    val renewalDateSnapshot = date("renewal_date_snapshot")
    val deliveryStatus = enumerationByName("delivery_status", 20, AlertDeliveryStatus::class)
        .default(AlertDeliveryStatus.PENDING)
    val sentAt = timestamp("sent_at").nullable()
    val failureReason = text("failure_reason").nullable()
    val emailRecipients = text("email_recipients")

    init {
        uniqueIndex(subscriptionId, alertWindowDays, renewalDateSnapshot)
    }
}

object EmailDeliveries : UUIDTable("email_deliveries") {
    val companyId = reference("company_id", Companies, onDelete = org.jetbrains.exposed.sql.ReferenceOption.CASCADE)
    val invitationId = reference("invitation_id", TeamInvitations, onDelete = org.jetbrains.exposed.sql.ReferenceOption.CASCADE).nullable()
    val recipientEmail = varchar("recipient_email", 255)
    val templateType = enumerationByName("template_type", 50, EmailTemplateType::class)
    val status = enumerationByName("status", 50, EmailDeliveryState::class)
    val providerMessageId = varchar("provider_message_id", 255).nullable()
    val providerStatusCode = integer("provider_status_code").nullable()
    val providerResponse = text("provider_response").nullable()
    val errorMessage = text("error_message").nullable()
    val createdAt = timestamp("created_at")
}

object AuditLog : UUIDTable("audit_log") {
    val companyId = reference("company_id", Companies)
    val userId = reference("user_id", Users)
    val action = enumerationByName("action", 100, AuditAction::class)
    val entityType = enumerationByName("entity_type", 100, AuditEntityType::class)
    val entityId = uuid("entity_id")
    val oldValue = jsonb("old_value", { it }, { it }).nullable()
    val newValue = jsonb("new_value", { it }, { it }).nullable()
    val createdAt = timestamp("created_at")
}

object TeamInvitations : UUIDTable("team_invitations") {
    val companyId = reference("company_id", Companies, onDelete = org.jetbrains.exposed.sql.ReferenceOption.CASCADE)
    val invitedByUserId = reference("invited_by_user_id", Users)
    val email = varchar("email", 255)
    val role = enumerationByName("role", 50, UserRole::class)
    val token = varchar("token", 255).uniqueIndex()
    val expiresAt = timestamp("expires_at")
    val acceptedAt = timestamp("accepted_at").nullable()
    val createdAt = timestamp("created_at")

    init {
        uniqueIndex(companyId, email)
    }
}

object SubscriptionComments : UUIDTable("subscription_comments") {
    val subscriptionId = reference("subscription_id", Subscriptions, onDelete = org.jetbrains.exposed.sql.ReferenceOption.CASCADE)
    val companyId = reference("company_id", Companies, onDelete = org.jetbrains.exposed.sql.ReferenceOption.CASCADE)
    val userId = reference("user_id", Users)
    val body = text("body")
    val createdAt = timestamp("created_at")
}

object SubscriptionPayments : UUIDTable("subscription_payments") {
    val subscriptionId = reference("subscription_id", Subscriptions, onDelete = org.jetbrains.exposed.sql.ReferenceOption.CASCADE)
    val companyId = reference("company_id", Companies, onDelete = org.jetbrains.exposed.sql.ReferenceOption.CASCADE)
    val recordedByUserId = reference("recorded_by_user_id", Users)
    val amount = decimal("amount", 10, 2)
    val currency = char("currency", 3).default("USD")
    val paidAt = date("paid_at")
    val paymentReference = varchar("payment_reference", 255).nullable()
    val note = text("note").nullable()
    val createdAt = timestamp("created_at")
}

object BudgetAlertLog : UUIDTable("budget_alert_log") {
    val companyId = reference("company_id", Companies, onDelete = org.jetbrains.exposed.sql.ReferenceOption.CASCADE)
    val year = short("year")
    val month = short("month")
    val thresholdPercent = short("threshold_percent")
    val sentAt = timestamp("sent_at")

    init {
        uniqueIndex(companyId, year, month, thresholdPercent)
    }
}

object RefreshTokens : UUIDTable("refresh_tokens") {
    val userId = reference("user_id", Users, onDelete = org.jetbrains.exposed.sql.ReferenceOption.CASCADE)
    val tokenHash = varchar("token_hash", 255).uniqueIndex()
    val expiresAt = timestamp("expires_at")
    val revokedAt = timestamp("revoked_at").nullable()
    val createdAt = timestamp("created_at")
}

object SpendSnapshots : UUIDTable("spend_snapshots") {
    val companyId = reference("company_id", Companies, onDelete = org.jetbrains.exposed.sql.ReferenceOption.CASCADE)
    val year = short("year")
    val month = short("month")
    val totalMonthlyUsd = decimal("total_monthly_usd", 14, 2)
    val subscriptionCount = integer("subscription_count").default(0)
    val createdAt = timestamp("created_at")
    val updatedAt = timestamp("updated_at")

    init {
        uniqueIndex(companyId, year, month)
    }
}

val managedTables: Array<Table> = arrayOf(
    Companies,
    Users,
    UserNotificationReads,
    Subscriptions,
    SubscriptionPayments,
    RenewalAlerts,
    EmailDeliveries,
    AuditLog,
    TeamInvitations,
    SubscriptionComments,
    SpendSnapshots,
    BudgetAlertLog,
    RefreshTokens
)
