package com.saastracker.persistence.repository

import com.saastracker.domain.dto.Page
import com.saastracker.domain.dto.PageRequest
import com.saastracker.domain.dto.SubscriptionFilter
import com.saastracker.domain.model.AuditLogEntry
import com.saastracker.domain.model.Company
import com.saastracker.domain.model.EmailDeliveryLog
import com.saastracker.domain.model.EmailTemplateType
import com.saastracker.domain.model.RenewalAlert
import com.saastracker.domain.model.SavingsEvent
import com.saastracker.domain.model.SavingsEventType
import com.saastracker.domain.model.SpendSnapshot
import com.saastracker.domain.model.Subscription
import com.saastracker.domain.model.SubscriptionComment
import com.saastracker.domain.model.SubscriptionPayment
import com.saastracker.domain.model.TeamInvitation
import com.saastracker.domain.model.User
import com.saastracker.domain.model.UserRole
import java.math.BigDecimal
import java.time.Instant
import java.time.LocalDate
import java.util.UUID

interface CompanyRepository {
    fun create(company: Company): Company
    fun update(company: Company): Company
    fun findById(id: UUID): Company?
    fun findByDomain(domain: String): Company?
    fun findByStripeCustomerId(stripeCustomerId: String): Company?
    fun delete(id: UUID)
    fun findAllWithDigestEnabled(): List<Company>
    @Deprecated("Use targeted queries instead of loading all companies")
    fun listAll(): List<Company>
}

interface UserRepository {
    fun create(user: User): User
    fun update(user: User): User
    fun findById(id: UUID): User?
    fun findByEmail(email: String): User?
    fun listByCompany(companyId: UUID): List<User>
    fun deactivate(id: UUID)
}

interface SubscriptionRepository {
    fun create(subscription: Subscription): Subscription
    fun update(subscription: Subscription): Subscription
    fun findById(id: UUID, companyId: UUID): Subscription?
    fun listByCompany(companyId: UUID): List<Subscription>
    fun listArchivedByCompany(companyId: UUID): List<Subscription>
    fun listRenewingBetween(companyId: UUID, from: LocalDate, to: LocalDate): List<Subscription>
    fun listActiveByCompany(companyId: UUID): List<Subscription>
    fun listByCompanyPaged(companyId: UUID, filter: SubscriptionFilter, pageRequest: PageRequest): Page<Subscription>
    /** Sets last_used_at = [now] and is_zombie = false. Returns the updated subscription, or null if not found. */
    fun markUsed(id: UUID, companyId: UUID, now: Instant): Subscription?
    /** Sets is_zombie flag without touching last_used_at. */
    fun markZombie(id: UUID, isZombie: Boolean, now: Instant)
    /** Lists non-archived subscriptions owned by a specific user in the company. */
    fun listActiveByOwner(companyId: UUID, ownerId: UUID): List<Subscription>
}

interface RenewalAlertRepository {
    fun create(alert: RenewalAlert): RenewalAlert
    fun update(alert: RenewalAlert): RenewalAlert
    fun findForThreshold(subscriptionId: UUID, thresholdDays: Int, renewalDateSnapshot: LocalDate): RenewalAlert?
    fun listByCompany(companyId: UUID): List<RenewalAlert>
}

interface AuditLogRepository {
    fun append(entry: AuditLogEntry): AuditLogEntry
    fun listByCompany(companyId: UUID): List<AuditLogEntry>
    fun deleteOlderThan(cutoff: Instant)
}

interface TeamInvitationRepository {
    fun create(invitation: TeamInvitation): TeamInvitation
    fun update(invitation: TeamInvitation): TeamInvitation
    fun findById(id: UUID): TeamInvitation?
    fun findByToken(token: String): TeamInvitation?
    fun findByCompanyAndEmail(companyId: UUID, email: String): TeamInvitation?
    fun listActiveByCompany(companyId: UUID, now: Instant): List<TeamInvitation>
    fun countCreatedSince(companyId: UUID, since: Instant): Long
}

interface EmailDeliveryRepository {
    fun create(entry: EmailDeliveryLog): EmailDeliveryLog
    fun listByInvitation(invitationId: UUID, limit: Int = 20): List<EmailDeliveryLog>
    fun listByCompany(companyId: UUID, limit: Int = 100): List<EmailDeliveryLog>
    fun listByRecipientEmail(email: String): List<EmailDeliveryLog>
    /** Returns true if a delivery of [templateType] was recorded for this company since the start of the current ISO week (Monday 00:00 UTC). */
    fun existsSentThisWeek(companyId: UUID, templateType: EmailTemplateType): Boolean
    fun deleteOlderThan(cutoff: Instant)
}

interface NotificationReadRepository {
    fun listReadKeysByUser(userId: UUID): Set<String>
    fun markRead(userId: UUID, keys: List<String>, readAt: Instant)
    fun clearForUser(userId: UUID)
}

interface SubscriptionCommentRepository {
    fun create(comment: SubscriptionComment): SubscriptionComment
    fun listBySubscription(companyId: UUID, subscriptionId: UUID): List<SubscriptionComment>
}

interface SubscriptionPaymentRepository {
    fun create(payment: SubscriptionPayment): SubscriptionPayment
    fun listBySubscription(companyId: UUID, subscriptionId: UUID): List<SubscriptionPayment>
    /** Returns the most recent payments for the company, ordered by paidAt DESC. */
    fun listRecentByCompany(companyId: UUID, limit: Int = 400): List<SubscriptionPayment>
}

interface SpendSnapshotRepository {
    fun upsert(snapshot: SpendSnapshot)
    fun findByCompanyAndYearMonth(companyId: UUID, year: Int, month: Int): SpendSnapshot?
    fun findByCompanyAndYear(companyId: UUID, year: Int): List<SpendSnapshot>
    fun findLastNMonths(companyId: UUID, months: Int): List<SpendSnapshot>
}

interface BudgetAlertRepository {
    fun exists(companyId: UUID, year: Int, month: Int, thresholdPercent: Int): Boolean
    fun record(companyId: UUID, year: Int, month: Int, thresholdPercent: Int)
}

interface CurrencyRateRepository {
    fun getRateToUsd(currency: String): Double?
    fun upsertRateToUsd(currency: String, rate: Double)
}

data class RefreshTokenRecord(
    val id: UUID,
    val userId: UUID,
    val tokenHash: String,
    val expiresAt: Instant,
    val revokedAt: Instant?
)

interface RefreshTokenRepository {
    fun create(userId: UUID, tokenHash: String, expiresAt: Instant): UUID
    fun findByHash(tokenHash: String): RefreshTokenRecord?
    fun revoke(id: UUID)
    fun revokeAllForUser(userId: UUID)
    fun deleteExpired()
}

data class PasswordResetTokenRecord(
    val id: UUID,
    val userId: UUID,
    val tokenHash: String,
    val expiresAt: Instant,
    val usedAt: Instant?
)

interface PasswordResetRepository {
    fun create(userId: UUID, tokenHash: String, expiresAt: Instant): UUID
    fun findByHash(tokenHash: String): PasswordResetTokenRecord?
    fun markUsed(id: UUID)
    fun deleteExpiredForUser(userId: UUID)
    fun deleteAllExpired()
}

interface SavingsEventRepository {
    fun record(event: SavingsEvent)
    fun listByCompany(companyId: UUID): List<SavingsEvent>
    fun sumByCompany(companyId: UUID): Map<SavingsEventType, BigDecimal>
}

interface IdentityProvider {
    fun newId(): UUID
}

interface ClockProvider {
    fun nowInstant(): java.time.Instant
    fun nowDate(): LocalDate
}

interface RoleGuard {
    fun ensureRole(userRole: UserRole, required: UserRole)
}
