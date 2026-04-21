package com.saastracker.persistence.repository.exposed

import com.saastracker.domain.model.AuditLogEntry
import com.saastracker.domain.model.Company
import com.saastracker.domain.model.EmailDeliveryLog
import com.saastracker.domain.model.RenewalAlert
import com.saastracker.domain.model.SavingsEvent
import com.saastracker.domain.model.SpendSnapshot
import com.saastracker.domain.model.Subscription
import com.saastracker.domain.model.SubscriptionComment
import com.saastracker.domain.model.SubscriptionPayment
import com.saastracker.domain.model.TeamInvitation
import com.saastracker.domain.model.User
import com.saastracker.persistence.table.AuditLog
import com.saastracker.persistence.table.Companies
import com.saastracker.persistence.table.EmailDeliveries
import com.saastracker.persistence.table.RenewalAlerts
import com.saastracker.persistence.table.SavingsEvents
import com.saastracker.persistence.table.SpendSnapshots
import com.saastracker.persistence.table.SubscriptionComments
import com.saastracker.persistence.table.SubscriptionPayments
import com.saastracker.persistence.table.Subscriptions
import com.saastracker.persistence.table.TeamInvitations
import com.saastracker.persistence.table.Users
import kotlinx.serialization.encodeToString
import kotlinx.serialization.json.Json
import org.jetbrains.exposed.sql.ResultRow

internal fun ResultRow.toCompany(): Company = Company(
    id = this[Companies.id].value,
    name = this[Companies.name],
    domain = this[Companies.domain],
    stripeCustomerId = this[Companies.stripeCustomerId],
    subscriptionStatus = this[Companies.subscriptionStatus],
    trialEndsAt = this[Companies.trialEndsAt],
    monthlyBudget = this[Companies.monthlyBudget],
    employeeCount = this[Companies.employeeCount],
    settings = this[Companies.settings],
    planTier = this[Companies.planTier],
    weeklyDigestEnabled = this[Companies.weeklyDigestEnabled],
    timezone = this[Companies.timezone],
    zombieThresholdDays = this[Companies.zombieThresholdDays],
    createdAt = this[Companies.createdAt],
    updatedAt = this[Companies.updatedAt]
)

internal fun ResultRow.toUser(): User = User(
    id = this[Users.id].value,
    companyId = this[Users.companyId].value,
    email = this[Users.email],
    name = this[Users.name],
    passwordHash = this[Users.passwordHash],
    role = this[Users.role],
    isActive = this[Users.isActive],
    lastLoginAt = this[Users.lastLoginAt],
    createdAt = this[Users.createdAt]
)

internal fun ResultRow.toSubscription(): Subscription = Subscription(
    id = this[Subscriptions.id].value,
    companyId = this[Subscriptions.companyId].value,
    createdById = this[Subscriptions.createdById].value,
    vendorName = this[Subscriptions.vendorName],
    vendorUrl = this[Subscriptions.vendorUrl],
    vendorLogoUrl = this[Subscriptions.vendorLogoUrl],
    category = this[Subscriptions.category],
    description = this[Subscriptions.description],
    amount = this[Subscriptions.amount],
    currency = this[Subscriptions.currency],
    billingCycle = this[Subscriptions.billingCycle],
    renewalDate = this[Subscriptions.renewalDate],
    contractStartDate = this[Subscriptions.contractStartDate],
    autoRenews = this[Subscriptions.autoRenews],
    paymentMode = this[Subscriptions.paymentMode],
    paymentStatus = this[Subscriptions.paymentStatus],
    lastPaidAt = this[Subscriptions.lastPaidAt],
    nextPaymentDate = this[Subscriptions.nextPaymentDate],
    status = this[Subscriptions.status],
    tags = this[Subscriptions.tags]?.let { Json.decodeFromString<List<String>>(it) },
    ownerId = this[Subscriptions.ownerId]?.value,
    notes = this[Subscriptions.notes],
    documentUrl = this[Subscriptions.documentUrl],
    createdAt = this[Subscriptions.createdAt],
    updatedAt = this[Subscriptions.updatedAt],
    archivedAt = this[Subscriptions.archivedAt],
    archivedById = this[Subscriptions.archivedById]?.value,
    lastUsedAt = this[Subscriptions.lastUsedAt],
    isZombie = this[Subscriptions.isZombie]
)

internal fun serializeTags(tags: List<String>?): String? = tags?.let { Json.encodeToString(it) }

internal fun ResultRow.toRenewalAlert(): RenewalAlert = RenewalAlert(
    id = this[RenewalAlerts.id].value,
    subscriptionId = this[RenewalAlerts.subscriptionId].value,
    companyId = this[RenewalAlerts.companyId].value,
    alertType = this[RenewalAlerts.alertType],
    alertWindowDays = this[RenewalAlerts.alertWindowDays],
    renewalDateSnapshot = this[RenewalAlerts.renewalDateSnapshot],
    deliveryStatus = this[RenewalAlerts.deliveryStatus],
    sentAt = this[RenewalAlerts.sentAt],
    failureReason = this[RenewalAlerts.failureReason],
    emailRecipients = Json.decodeFromString<List<String>>(this[RenewalAlerts.emailRecipients])
)

internal fun serializeEmails(emailRecipients: List<String>): String = Json.encodeToString(emailRecipients)

internal fun ResultRow.toAuditLogEntry(): AuditLogEntry = AuditLogEntry(
    id = this[AuditLog.id].value,
    companyId = this[AuditLog.companyId].value,
    userId = this[AuditLog.userId].value,
    action = this[AuditLog.action],
    entityType = this[AuditLog.entityType],
    entityId = this[AuditLog.entityId],
    oldValue = this[AuditLog.oldValue],
    newValue = this[AuditLog.newValue],
    createdAt = this[AuditLog.createdAt]
)

internal fun ResultRow.toInvitation(): TeamInvitation = TeamInvitation(
    id = this[TeamInvitations.id].value,
    companyId = this[TeamInvitations.companyId].value,
    invitedByUserId = this[TeamInvitations.invitedByUserId].value,
    email = this[TeamInvitations.email],
    role = this[TeamInvitations.role],
    token = this[TeamInvitations.token],
    expiresAt = this[TeamInvitations.expiresAt],
    acceptedAt = this[TeamInvitations.acceptedAt],
    createdAt = this[TeamInvitations.createdAt]
)

internal fun ResultRow.toComment(): SubscriptionComment = SubscriptionComment(
    id = this[SubscriptionComments.id].value,
    subscriptionId = this[SubscriptionComments.subscriptionId].value,
    companyId = this[SubscriptionComments.companyId].value,
    userId = this[SubscriptionComments.userId].value,
    body = this[SubscriptionComments.body],
    createdAt = this[SubscriptionComments.createdAt]
)

internal fun ResultRow.toSubscriptionPayment(): SubscriptionPayment = SubscriptionPayment(
    id = this[SubscriptionPayments.id].value,
    subscriptionId = this[SubscriptionPayments.subscriptionId].value,
    companyId = this[SubscriptionPayments.companyId].value,
    recordedByUserId = this[SubscriptionPayments.recordedByUserId].value,
    amount = this[SubscriptionPayments.amount],
    currency = this[SubscriptionPayments.currency],
    paidAt = this[SubscriptionPayments.paidAt],
    paymentReference = this[SubscriptionPayments.paymentReference],
    note = this[SubscriptionPayments.note],
    createdAt = this[SubscriptionPayments.createdAt]
)

internal fun ResultRow.toEmailDeliveryLog(): EmailDeliveryLog = EmailDeliveryLog(
    id = this[EmailDeliveries.id].value,
    companyId = this[EmailDeliveries.companyId].value,
    invitationId = this[EmailDeliveries.invitationId]?.value,
    recipientEmail = this[EmailDeliveries.recipientEmail],
    templateType = this[EmailDeliveries.templateType],
    status = this[EmailDeliveries.status],
    providerMessageId = this[EmailDeliveries.providerMessageId],
    providerStatusCode = this[EmailDeliveries.providerStatusCode],
    providerResponse = this[EmailDeliveries.providerResponse],
    errorMessage = this[EmailDeliveries.errorMessage],
    createdAt = this[EmailDeliveries.createdAt]
)

internal fun ResultRow.toSpendSnapshot(): SpendSnapshot = SpendSnapshot(
    id = this[SpendSnapshots.id].value,
    companyId = this[SpendSnapshots.companyId].value,
    year = this[SpendSnapshots.year].toInt(),
    month = this[SpendSnapshots.month].toInt(),
    totalMonthlyUsd = this[SpendSnapshots.totalMonthlyUsd],
    subscriptionCount = this[SpendSnapshots.subscriptionCount],
    createdAt = this[SpendSnapshots.createdAt],
    updatedAt = this[SpendSnapshots.updatedAt]
)

internal fun ResultRow.toSavingsEvent(): SavingsEvent = SavingsEvent(
    id = this[SavingsEvents.id].value,
    companyId = this[SavingsEvents.companyId].value,
    subscriptionId = this[SavingsEvents.subscriptionId]?.value,
    eventType = this[SavingsEvents.eventType],
    vendorName = this[SavingsEvents.vendorName],
    amount = this[SavingsEvents.amount],
    currency = this[SavingsEvents.currency],
    savedAt = this[SavingsEvents.savedAt]
)
