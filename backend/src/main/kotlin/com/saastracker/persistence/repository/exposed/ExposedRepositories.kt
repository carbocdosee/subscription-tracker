package com.saastracker.persistence.repository.exposed

import com.saastracker.domain.dto.Page
import com.saastracker.domain.dto.PageRequest
import com.saastracker.domain.dto.SubscriptionFilter
import com.saastracker.domain.model.AuditLogEntry
import com.saastracker.domain.model.Company
import com.saastracker.domain.model.EmailDeliveryLog
import com.saastracker.domain.model.RenewalAlert
import com.saastracker.domain.model.SpendSnapshot
import com.saastracker.domain.model.Subscription
import com.saastracker.domain.model.SubscriptionComment
import com.saastracker.domain.model.SubscriptionPayment
import com.saastracker.domain.model.TeamInvitation
import com.saastracker.domain.model.User
import com.saastracker.persistence.repository.AuditLogRepository
import com.saastracker.persistence.repository.BudgetAlertRepository
import com.saastracker.persistence.repository.CompanyRepository
import com.saastracker.persistence.repository.EmailDeliveryRepository
import com.saastracker.persistence.repository.NotificationReadRepository
import com.saastracker.persistence.repository.RefreshTokenRecord
import com.saastracker.persistence.repository.RefreshTokenRepository
import com.saastracker.persistence.repository.RenewalAlertRepository
import com.saastracker.persistence.repository.SpendSnapshotRepository
import com.saastracker.persistence.repository.SubscriptionCommentRepository
import com.saastracker.persistence.repository.SubscriptionPaymentRepository
import com.saastracker.persistence.repository.SubscriptionRepository
import com.saastracker.persistence.repository.TeamInvitationRepository
import com.saastracker.persistence.repository.UserRepository
import com.saastracker.persistence.table.AuditLog
import com.saastracker.persistence.table.BudgetAlertLog
import com.saastracker.persistence.table.Companies
import com.saastracker.persistence.table.EmailDeliveries
import com.saastracker.persistence.table.RefreshTokens
import com.saastracker.persistence.table.RenewalAlerts
import com.saastracker.persistence.table.SpendSnapshots
import com.saastracker.persistence.table.SubscriptionComments
import com.saastracker.persistence.table.SubscriptionPayments
import com.saastracker.persistence.table.Subscriptions
import com.saastracker.persistence.table.TeamInvitations
import com.saastracker.persistence.table.UserNotificationReads
import com.saastracker.persistence.table.Users
import org.jetbrains.exposed.dao.id.EntityID
import org.jetbrains.exposed.dao.id.IdTable
import org.jetbrains.exposed.sql.Expression
import org.jetbrains.exposed.sql.SortOrder
import org.jetbrains.exposed.sql.SqlExpressionBuilder.eq
import org.jetbrains.exposed.sql.SqlExpressionBuilder.greaterEq
import org.jetbrains.exposed.sql.SqlExpressionBuilder.inList
import org.jetbrains.exposed.sql.SqlExpressionBuilder.less
import org.jetbrains.exposed.sql.SqlExpressionBuilder.lessEq
import org.jetbrains.exposed.sql.and
import org.jetbrains.exposed.sql.andWhere
import org.jetbrains.exposed.sql.lowerCase
import org.jetbrains.exposed.sql.deleteWhere
import org.jetbrains.exposed.sql.insert
import org.jetbrains.exposed.sql.selectAll
import org.jetbrains.exposed.sql.transactions.transaction
import org.jetbrains.exposed.sql.update
import org.jetbrains.exposed.sql.upsert
import java.time.Instant
import java.time.LocalDate
import java.util.UUID

private fun <T : IdTable<UUID>> entity(uuid: UUID, table: T): EntityID<UUID> = EntityID(uuid, table)

class ExposedCompanyRepository : CompanyRepository {
    override fun create(company: Company): Company = transaction {
        Companies.insert {
            it[id] = entity(company.id, Companies)
            it[name] = company.name
            it[domain] = company.domain
            it[stripeCustomerId] = company.stripeCustomerId
            it[subscriptionStatus] = company.subscriptionStatus
            it[trialEndsAt] = company.trialEndsAt
            it[monthlyBudget] = company.monthlyBudget
            it[employeeCount] = company.employeeCount
            it[settings] = company.settings
            it[createdAt] = company.createdAt
            it[updatedAt] = company.updatedAt
        }
        company
    }

    override fun update(company: Company): Company = transaction {
        Companies.update({ Companies.id eq company.id }) {
            it[name] = company.name
            it[domain] = company.domain
            it[stripeCustomerId] = company.stripeCustomerId
            it[subscriptionStatus] = company.subscriptionStatus
            it[trialEndsAt] = company.trialEndsAt
            it[monthlyBudget] = company.monthlyBudget
            it[employeeCount] = company.employeeCount
            it[settings] = company.settings
            it[updatedAt] = company.updatedAt
        }
        company
    }

    override fun findById(id: UUID): Company? = transaction {
        Companies.selectAll().where { Companies.id eq id }.singleOrNull()?.toCompany()
    }

    override fun findByDomain(domain: String): Company? = transaction {
        Companies.selectAll().where { Companies.domain eq domain.lowercase() }.singleOrNull()?.toCompany()
    }

    override fun findByStripeCustomerId(stripeCustomerId: String): Company? = transaction {
        Companies.selectAll().where { Companies.stripeCustomerId eq stripeCustomerId }.singleOrNull()?.toCompany()
    }

    override fun listAll(): List<Company> = transaction {
        Companies.selectAll().map { it.toCompany() }
    }
}

class ExposedUserRepository : UserRepository {
    override fun create(user: User): User = transaction {
        Users.insert {
            it[id] = entity(user.id, Users)
            it[companyId] = entity(user.companyId, Companies)
            it[email] = user.email
            it[name] = user.name
            it[passwordHash] = user.passwordHash
            it[role] = user.role
            it[isActive] = user.isActive
            it[lastLoginAt] = user.lastLoginAt
            it[createdAt] = user.createdAt
        }
        user
    }

    override fun update(user: User): User = transaction {
        Users.update({ Users.id eq user.id }) {
            it[companyId] = entity(user.companyId, Companies)
            it[email] = user.email
            it[name] = user.name
            it[passwordHash] = user.passwordHash
            it[role] = user.role
            it[isActive] = user.isActive
            it[lastLoginAt] = user.lastLoginAt
        }
        user
    }

    override fun findById(id: UUID): User? = transaction {
        Users.selectAll().where { Users.id eq id }.singleOrNull()?.toUser()
    }

    override fun findByEmail(email: String): User? = transaction {
        Users.selectAll().where { Users.email eq email.lowercase() }.singleOrNull()?.toUser()
    }

    override fun listByCompany(companyId: UUID): List<User> = transaction {
        Users.selectAll().where { Users.companyId eq companyId }.map { it.toUser() }
    }
}

class ExposedSubscriptionRepository : SubscriptionRepository {
    override fun create(subscription: Subscription): Subscription = transaction {
        Subscriptions.insert {
            it[id] = entity(subscription.id, Subscriptions)
            it[companyId] = entity(subscription.companyId, Companies)
            it[createdById] = entity(subscription.createdById, Users)
            it[vendorName] = subscription.vendorName
            it[vendorUrl] = subscription.vendorUrl
            it[vendorLogoUrl] = subscription.vendorLogoUrl
            it[category] = subscription.category
            it[description] = subscription.description
            it[amount] = subscription.amount
            it[currency] = subscription.currency
            it[billingCycle] = subscription.billingCycle
            it[renewalDate] = subscription.renewalDate
            it[contractStartDate] = subscription.contractStartDate
            it[autoRenews] = subscription.autoRenews
            it[paymentMode] = subscription.paymentMode
            it[paymentStatus] = subscription.paymentStatus
            it[lastPaidAt] = subscription.lastPaidAt
            it[nextPaymentDate] = subscription.nextPaymentDate
            it[status] = subscription.status
            it[tags] = serializeTags(subscription.tags)
            it[ownerId] = subscription.ownerId?.let { owner -> entity(owner, Users) }
            it[notes] = subscription.notes
            it[documentUrl] = subscription.documentUrl
            it[createdAt] = subscription.createdAt
            it[updatedAt] = subscription.updatedAt
        }
        subscription
    }

    override fun update(subscription: Subscription): Subscription = transaction {
        Subscriptions.update({ (Subscriptions.id eq subscription.id) and (Subscriptions.companyId eq subscription.companyId) }) {
            it[vendorName] = subscription.vendorName
            it[vendorUrl] = subscription.vendorUrl
            it[vendorLogoUrl] = subscription.vendorLogoUrl
            it[category] = subscription.category
            it[description] = subscription.description
            it[amount] = subscription.amount
            it[currency] = subscription.currency
            it[billingCycle] = subscription.billingCycle
            it[renewalDate] = subscription.renewalDate
            it[contractStartDate] = subscription.contractStartDate
            it[autoRenews] = subscription.autoRenews
            it[paymentMode] = subscription.paymentMode
            it[paymentStatus] = subscription.paymentStatus
            it[lastPaidAt] = subscription.lastPaidAt
            it[nextPaymentDate] = subscription.nextPaymentDate
            it[status] = subscription.status
            it[tags] = serializeTags(subscription.tags)
            it[ownerId] = subscription.ownerId?.let { owner -> entity(owner, Users) }
            it[notes] = subscription.notes
            it[documentUrl] = subscription.documentUrl
            it[updatedAt] = subscription.updatedAt
            it[archivedAt] = subscription.archivedAt
            it[archivedById] = subscription.archivedById?.let { id -> entity(id, Users) }
        }
        subscription
    }

    override fun findById(id: UUID, companyId: UUID): Subscription? = transaction {
        Subscriptions.selectAll()
            .where { (Subscriptions.id eq id) and (Subscriptions.companyId eq companyId) }
            .singleOrNull()
            ?.toSubscription()
    }

    override fun listByCompany(companyId: UUID): List<Subscription> = transaction {
        Subscriptions.selectAll()
            .where { (Subscriptions.companyId eq companyId) and (Subscriptions.archivedAt.isNull()) }
            .map { it.toSubscription() }
    }

    override fun listArchivedByCompany(companyId: UUID): List<Subscription> = transaction {
        Subscriptions.selectAll()
            .where { (Subscriptions.companyId eq companyId) and (Subscriptions.archivedAt.isNotNull()) }
            .orderBy(Subscriptions.archivedAt, SortOrder.DESC)
            .map { it.toSubscription() }
    }

    override fun listRenewingBetween(companyId: UUID, from: LocalDate, to: LocalDate): List<Subscription> = transaction {
        Subscriptions.selectAll()
            .where {
                (Subscriptions.companyId eq companyId) and
                    (Subscriptions.archivedAt.isNull()) and
                    (Subscriptions.renewalDate greaterEq from) and
                    (Subscriptions.renewalDate lessEq to)
            }
            .map { it.toSubscription() }
    }

    override fun listActiveByCompany(companyId: UUID): List<Subscription> = transaction {
        Subscriptions.selectAll()
            .where {
                (Subscriptions.companyId eq companyId) and
                    (Subscriptions.archivedAt.isNull()) and
                    (Subscriptions.status eq com.saastracker.domain.model.SubscriptionStatus.ACTIVE)
            }
            .map { it.toSubscription() }
    }

    override fun listByCompanyPaged(
        companyId: UUID,
        filter: SubscriptionFilter,
        pageRequest: PageRequest
    ): Page<Subscription> = transaction {
        val query = Subscriptions.selectAll()
            .where { (Subscriptions.companyId eq companyId) and Subscriptions.archivedAt.isNull() }

        val filterVendor = filter.vendorName
        if (filterVendor != null) {
            query.andWhere { Subscriptions.vendorName.lowerCase() like "%${filterVendor.lowercase()}%" }
        }
        val filterCategory = filter.category
        if (filterCategory != null) {
            query.andWhere { Subscriptions.category.lowerCase() like "%${filterCategory.lowercase()}%" }
        }
        val filterStatus = filter.status
        if (filterStatus != null) {
            query.andWhere { Subscriptions.status eq filterStatus }
        }
        val filterPaymentMode = filter.paymentMode
        if (filterPaymentMode != null) {
            query.andWhere { Subscriptions.paymentMode eq filterPaymentMode }
        }
        val filterMinAmount = filter.minAmount
        if (filterMinAmount != null) {
            query.andWhere { Subscriptions.amount greaterEq filterMinAmount }
        }
        val filterMaxAmount = filter.maxAmount
        if (filterMaxAmount != null) {
            query.andWhere { Subscriptions.amount lessEq filterMaxAmount }
        }

        val total = query.count()
        val offset = ((pageRequest.page - 1) * pageRequest.size).toLong()
        val sortExpr: Expression<*> = when (pageRequest.sortBy) {
            "amount" -> Subscriptions.amount
            "vendor_name" -> Subscriptions.vendorName
            "created_at" -> Subscriptions.createdAt
            else -> Subscriptions.renewalDate
        }
        val sortOrder = if (pageRequest.sortDir == "desc") SortOrder.DESC else SortOrder.ASC

        val items = query
            .orderBy(sortExpr, sortOrder)
            .limit(pageRequest.size, offset)
            .map { it.toSubscription() }

        Page(items = items, total = total, page = pageRequest.page, size = pageRequest.size)
    }
}

class ExposedRenewalAlertRepository : RenewalAlertRepository {
    override fun create(alert: RenewalAlert): RenewalAlert = transaction {
        RenewalAlerts.insert {
            it[id] = entity(alert.id, RenewalAlerts)
            it[subscriptionId] = entity(alert.subscriptionId, Subscriptions)
            it[companyId] = entity(alert.companyId, Companies)
            it[alertType] = alert.alertType
            it[alertWindowDays] = alert.alertWindowDays
            it[renewalDateSnapshot] = alert.renewalDateSnapshot
            it[deliveryStatus] = alert.deliveryStatus
            it[sentAt] = alert.sentAt
            it[failureReason] = alert.failureReason
            it[emailRecipients] = serializeEmails(alert.emailRecipients)
        }
        alert
    }

    override fun update(alert: RenewalAlert): RenewalAlert = transaction {
        RenewalAlerts.update({ RenewalAlerts.id eq alert.id }) {
            it[alertType] = alert.alertType
            it[alertWindowDays] = alert.alertWindowDays
            it[renewalDateSnapshot] = alert.renewalDateSnapshot
            it[deliveryStatus] = alert.deliveryStatus
            it[sentAt] = alert.sentAt
            it[failureReason] = alert.failureReason
            it[emailRecipients] = serializeEmails(alert.emailRecipients)
        }
        alert
    }

    override fun findForThreshold(subscriptionId: UUID, thresholdDays: Int, renewalDateSnapshot: LocalDate): RenewalAlert? = transaction {
        RenewalAlerts.selectAll()
            .where {
                (RenewalAlerts.subscriptionId eq subscriptionId) and
                    (RenewalAlerts.alertWindowDays eq thresholdDays) and
                    (RenewalAlerts.renewalDateSnapshot eq renewalDateSnapshot)
            }
            .singleOrNull()
            ?.toRenewalAlert()
    }

    override fun listByCompany(companyId: UUID): List<RenewalAlert> = transaction {
        RenewalAlerts.selectAll()
            .where { RenewalAlerts.companyId eq companyId }
            .map { it.toRenewalAlert() }
    }
}

class ExposedAuditLogRepository : AuditLogRepository {
    override fun append(entry: AuditLogEntry): AuditLogEntry = transaction {
        AuditLog.insert {
            it[id] = entity(entry.id, AuditLog)
            it[companyId] = entity(entry.companyId, Companies)
            it[userId] = entity(entry.userId, Users)
            it[action] = entry.action
            it[entityType] = entry.entityType
            it[entityId] = entry.entityId
            it[oldValue] = entry.oldValue
            it[newValue] = entry.newValue
            it[createdAt] = entry.createdAt
        }
        entry
    }

    override fun listByCompany(companyId: UUID): List<AuditLogEntry> = transaction {
        AuditLog.selectAll()
            .where { AuditLog.companyId eq companyId }
            .orderBy(AuditLog.createdAt, org.jetbrains.exposed.sql.SortOrder.DESC)
            .map { it.toAuditLogEntry() }
    }
}

class ExposedTeamInvitationRepository : TeamInvitationRepository {
    override fun create(invitation: TeamInvitation): TeamInvitation = transaction {
        TeamInvitations.insert {
            it[id] = entity(invitation.id, TeamInvitations)
            it[companyId] = entity(invitation.companyId, Companies)
            it[invitedByUserId] = entity(invitation.invitedByUserId, Users)
            it[email] = invitation.email
            it[role] = invitation.role
            it[token] = invitation.token
            it[expiresAt] = invitation.expiresAt
            it[acceptedAt] = invitation.acceptedAt
            it[createdAt] = invitation.createdAt
        }
        invitation
    }

    override fun update(invitation: TeamInvitation): TeamInvitation = transaction {
        TeamInvitations.update({ TeamInvitations.id eq invitation.id }) {
            it[invitedByUserId] = entity(invitation.invitedByUserId, Users)
            it[email] = invitation.email
            it[role] = invitation.role
            it[token] = invitation.token
            it[expiresAt] = invitation.expiresAt
            it[acceptedAt] = invitation.acceptedAt
            it[createdAt] = invitation.createdAt
        }
        invitation
    }

    override fun findById(id: UUID): TeamInvitation? = transaction {
        TeamInvitations.selectAll().where { TeamInvitations.id eq id }.singleOrNull()?.toInvitation()
    }

    override fun findByToken(token: String): TeamInvitation? = transaction {
        TeamInvitations.selectAll().where { TeamInvitations.token eq token }.singleOrNull()?.toInvitation()
    }

    override fun findByCompanyAndEmail(companyId: UUID, email: String): TeamInvitation? = transaction {
        TeamInvitations.selectAll()
            .where { (TeamInvitations.companyId eq companyId) and (TeamInvitations.email eq email.lowercase()) }
            .singleOrNull()
            ?.toInvitation()
    }

    override fun listActiveByCompany(companyId: UUID, now: java.time.Instant): List<TeamInvitation> = transaction {
        TeamInvitations.selectAll()
            .where {
                (TeamInvitations.companyId eq companyId) and
                    (TeamInvitations.acceptedAt eq null) and
                    (TeamInvitations.expiresAt greaterEq now)
            }
            .orderBy(TeamInvitations.createdAt, org.jetbrains.exposed.sql.SortOrder.DESC)
            .map { it.toInvitation() }
    }
}

class ExposedEmailDeliveryRepository : EmailDeliveryRepository {
    override fun create(entry: EmailDeliveryLog): EmailDeliveryLog = transaction {
        EmailDeliveries.insert {
            it[id] = entity(entry.id, EmailDeliveries)
            it[companyId] = entity(entry.companyId, Companies)
            it[invitationId] = entry.invitationId?.let { invitation -> entity(invitation, TeamInvitations) }
            it[recipientEmail] = entry.recipientEmail
            it[templateType] = entry.templateType
            it[status] = entry.status
            it[providerMessageId] = entry.providerMessageId
            it[providerStatusCode] = entry.providerStatusCode
            it[providerResponse] = entry.providerResponse
            it[errorMessage] = entry.errorMessage
            it[createdAt] = entry.createdAt
        }
        entry
    }

    override fun listByInvitation(invitationId: UUID, limit: Int): List<EmailDeliveryLog> = transaction {
        EmailDeliveries.selectAll()
            .where { EmailDeliveries.invitationId eq invitationId }
            .orderBy(EmailDeliveries.createdAt, org.jetbrains.exposed.sql.SortOrder.DESC)
            .limit(limit)
            .map { it.toEmailDeliveryLog() }
    }

    override fun listByCompany(companyId: UUID, limit: Int): List<EmailDeliveryLog> = transaction {
        EmailDeliveries.selectAll()
            .where { EmailDeliveries.companyId eq companyId }
            .orderBy(EmailDeliveries.createdAt, org.jetbrains.exposed.sql.SortOrder.DESC)
            .limit(limit)
            .map { it.toEmailDeliveryLog() }
    }
}

class ExposedNotificationReadRepository : NotificationReadRepository {
    override fun listReadKeysByUser(userId: UUID): Set<String> = transaction {
        UserNotificationReads
            .selectAll()
            .where { UserNotificationReads.userId eq userId }
            .map { it[UserNotificationReads.notificationKey] }
            .toSet()
    }

    override fun markRead(userId: UUID, keys: List<String>, readAt: java.time.Instant) {
        val normalizedKeys = keys.map { it.trim() }.filter { it.isNotBlank() }.distinct()
        if (normalizedKeys.isEmpty()) return

        transaction {
            UserNotificationReads.deleteWhere {
                (UserNotificationReads.userId eq userId) and
                    (UserNotificationReads.notificationKey inList normalizedKeys)
            }
            normalizedKeys.forEach { key ->
                UserNotificationReads.insert {
                    it[UserNotificationReads.userId] = entity(userId, Users)
                    it[UserNotificationReads.notificationKey] = key
                    it[UserNotificationReads.readAt] = readAt
                }
            }
        }
    }

    override fun clearForUser(userId: UUID) {
        transaction {
            UserNotificationReads.deleteWhere { UserNotificationReads.userId eq userId }
        }
    }
}

class ExposedSubscriptionCommentRepository : SubscriptionCommentRepository {
    override fun create(comment: SubscriptionComment): SubscriptionComment = transaction {
        SubscriptionComments.insert {
            it[id] = entity(comment.id, SubscriptionComments)
            it[subscriptionId] = entity(comment.subscriptionId, Subscriptions)
            it[companyId] = entity(comment.companyId, Companies)
            it[userId] = entity(comment.userId, Users)
            it[body] = comment.body
            it[createdAt] = comment.createdAt
        }
        comment
    }

    override fun listBySubscription(companyId: UUID, subscriptionId: UUID): List<SubscriptionComment> = transaction {
        SubscriptionComments.selectAll()
            .where {
                (SubscriptionComments.companyId eq companyId) and
                    (SubscriptionComments.subscriptionId eq subscriptionId)
            }
            .orderBy(SubscriptionComments.createdAt, org.jetbrains.exposed.sql.SortOrder.DESC)
            .map { it.toComment() }
    }
}

class ExposedSubscriptionPaymentRepository : SubscriptionPaymentRepository {
    override fun create(payment: SubscriptionPayment): SubscriptionPayment = transaction {
        SubscriptionPayments.insert {
            it[id] = entity(payment.id, SubscriptionPayments)
            it[subscriptionId] = entity(payment.subscriptionId, Subscriptions)
            it[companyId] = entity(payment.companyId, Companies)
            it[recordedByUserId] = entity(payment.recordedByUserId, Users)
            it[amount] = payment.amount
            it[currency] = payment.currency
            it[paidAt] = payment.paidAt
            it[paymentReference] = payment.paymentReference
            it[note] = payment.note
            it[createdAt] = payment.createdAt
        }
        payment
    }

    override fun listBySubscription(companyId: UUID, subscriptionId: UUID): List<SubscriptionPayment> = transaction {
        SubscriptionPayments.selectAll()
            .where {
                (SubscriptionPayments.companyId eq companyId) and
                    (SubscriptionPayments.subscriptionId eq subscriptionId)
            }
            .orderBy(SubscriptionPayments.paidAt, org.jetbrains.exposed.sql.SortOrder.DESC)
            .map { it.toSubscriptionPayment() }
    }
}

class ExposedSpendSnapshotRepository : SpendSnapshotRepository {
    override fun upsert(snapshot: SpendSnapshot): Unit = transaction {
        SpendSnapshots.upsert(SpendSnapshots.companyId, SpendSnapshots.year, SpendSnapshots.month) {
            it[id] = entity(snapshot.id, SpendSnapshots)
            it[companyId] = entity(snapshot.companyId, Companies)
            it[year] = snapshot.year.toShort()
            it[month] = snapshot.month.toShort()
            it[totalMonthlyUsd] = snapshot.totalMonthlyUsd
            it[subscriptionCount] = snapshot.subscriptionCount
            it[createdAt] = snapshot.createdAt
            it[updatedAt] = Instant.now()
        }
    }

    override fun findByCompanyAndYearMonth(companyId: UUID, year: Int, month: Int): SpendSnapshot? = transaction {
        SpendSnapshots.selectAll()
            .where {
                (SpendSnapshots.companyId eq companyId) and
                    (SpendSnapshots.year eq year.toShort()) and
                    (SpendSnapshots.month eq month.toShort())
            }
            .singleOrNull()
            ?.toSpendSnapshot()
    }

    override fun findByCompanyAndYear(companyId: UUID, year: Int): List<SpendSnapshot> = transaction {
        SpendSnapshots.selectAll()
            .where {
                (SpendSnapshots.companyId eq companyId) and
                    (SpendSnapshots.year eq year.toShort())
            }
            .orderBy(SpendSnapshots.month, SortOrder.ASC)
            .map { it.toSpendSnapshot() }
    }

    override fun findLastNMonths(companyId: UUID, months: Int): List<SpendSnapshot> = transaction {
        SpendSnapshots.selectAll()
            .where { SpendSnapshots.companyId eq companyId }
            .orderBy(SpendSnapshots.year, SortOrder.DESC)
            .orderBy(SpendSnapshots.month, SortOrder.DESC)
            .limit(months)
            .map { it.toSpendSnapshot() }
    }
}

class ExposedBudgetAlertRepository : BudgetAlertRepository {
    override fun exists(companyId: UUID, year: Int, month: Int, thresholdPercent: Int): Boolean = transaction {
        BudgetAlertLog.selectAll()
            .where {
                (BudgetAlertLog.companyId eq companyId) and
                    (BudgetAlertLog.year eq year.toShort()) and
                    (BudgetAlertLog.month eq month.toShort()) and
                    (BudgetAlertLog.thresholdPercent eq thresholdPercent.toShort())
            }
            .count() > 0
    }

    override fun record(companyId: UUID, year: Int, month: Int, thresholdPercent: Int): Unit = transaction {
        BudgetAlertLog.insert {
            it[id] = entity(UUID.randomUUID(), BudgetAlertLog)
            it[BudgetAlertLog.companyId] = entity(companyId, Companies)
            it[BudgetAlertLog.year] = year.toShort()
            it[BudgetAlertLog.month] = month.toShort()
            it[BudgetAlertLog.thresholdPercent] = thresholdPercent.toShort()
            it[sentAt] = Instant.now()
        }
    }
}

class ExposedRefreshTokenRepository : RefreshTokenRepository {
    override fun create(userId: UUID, tokenHash: String, expiresAt: Instant): UUID = transaction {
        val newId = UUID.randomUUID()
        RefreshTokens.insert {
            it[id] = entity(newId, RefreshTokens)
            it[RefreshTokens.userId] = entity(userId, Users)
            it[RefreshTokens.tokenHash] = tokenHash
            it[RefreshTokens.expiresAt] = expiresAt
            it[revokedAt] = null
            it[createdAt] = Instant.now()
        }
        newId
    }

    override fun findByHash(tokenHash: String): RefreshTokenRecord? = transaction {
        RefreshTokens.selectAll()
            .where { RefreshTokens.tokenHash eq tokenHash }
            .singleOrNull()
            ?.let {
                RefreshTokenRecord(
                    id = it[RefreshTokens.id].value,
                    userId = it[RefreshTokens.userId].value,
                    tokenHash = it[RefreshTokens.tokenHash],
                    expiresAt = it[RefreshTokens.expiresAt],
                    revokedAt = it[RefreshTokens.revokedAt]
                )
            }
    }

    override fun revoke(id: UUID): Unit = transaction {
        RefreshTokens.update({ RefreshTokens.id eq id }) {
            it[revokedAt] = Instant.now()
        }
    }

    override fun revokeAllForUser(userId: UUID): Unit = transaction {
        RefreshTokens.update({ (RefreshTokens.userId eq userId) and (RefreshTokens.revokedAt eq null) }) {
            it[revokedAt] = Instant.now()
        }
    }

    override fun deleteExpired(): Unit = transaction {
        RefreshTokens.deleteWhere { expiresAt less Instant.now() }
    }
}
