package com.saastracker.domain.service

import com.saastracker.domain.model.Company
import com.saastracker.domain.model.EmailDeliveryLog
import com.saastracker.domain.model.EmailDeliveryState
import com.saastracker.domain.model.EmailTemplateType
import com.saastracker.persistence.repository.ClockProvider
import com.saastracker.persistence.repository.CompanyRepository
import com.saastracker.persistence.repository.EmailDeliveryRepository
import com.saastracker.persistence.repository.IdentityProvider
import com.saastracker.persistence.repository.SubscriptionRepository
import com.saastracker.persistence.repository.UserRepository
import com.saastracker.transport.email.EmailService
import com.saastracker.transport.email.SubscriptionWithDaysLeft
import org.slf4j.LoggerFactory
import java.math.BigDecimal
import java.time.LocalDate
import java.time.ZoneId
import java.time.ZonedDateTime

class DigestService(
    private val companyRepository: CompanyRepository,
    private val subscriptionRepository: SubscriptionRepository,
    private val userRepository: UserRepository,
    private val emailDeliveryRepository: EmailDeliveryRepository,
    private val emailService: EmailService,
    private val idProvider: IdentityProvider,
    private val clock: ClockProvider
) {
    private val logger = LoggerFactory.getLogger(javaClass)

    /** Entry point called hourly by WeeklyDigestJob.
     *  Only sends to companies whose Monday 09:00 falls within a ±30-minute window of [now]. */
    fun runWeeklyDigest(now: ZonedDateTime) {
        val companies = companyRepository.findAllWithDigestEnabled()
        logger.info("WeeklyDigest: evaluating {} companies", companies.size)
        var sent = 0
        var skipped = 0
        for (company in companies) {
            val zoneId = runCatching { ZoneId.of(company.timezone) }.getOrElse { ZoneId.of("UTC") }
            val companyNow = now.withZoneSameInstant(zoneId)
            // Send only on Monday, between 08:30 and 09:30 (local time)
            if (companyNow.dayOfWeek != java.time.DayOfWeek.MONDAY) {
                skipped++
                continue
            }
            val hour = companyNow.hour
            val minute = companyNow.minute
            val totalMinutes = hour * 60 + minute
            if (totalMinutes !in (8 * 60 + 30)..(9 * 60 + 30)) {
                skipped++
                continue
            }
            runCatching { sendWeeklyDigest(company) }.onFailure {
                logger.error("WeeklyDigest failed for company={}", company.id, it)
            }.onSuccess { wasSent ->
                if (wasSent) sent++ else skipped++
            }
        }
        logger.info("WeeklyDigest complete: sent={} skipped={}", sent, skipped)
    }

    /** Sends digest for a single company. Returns true if email was dispatched. */
    fun sendWeeklyDigest(company: Company): Boolean {
        if (emailDeliveryRepository.existsSentThisWeek(company.id, EmailTemplateType.WEEKLY_DIGEST)) {
            logger.debug("WeeklyDigest already sent this week for company={}", company.id)
            return false
        }

        val subscriptions = subscriptionRepository.listActiveByCompany(company.id)
        val today = clock.nowDate()
        val cutoff = today.plusDays(30)

        val renewals = subscriptions
            .filter { it.renewalDate in today..cutoff }
            .map { SubscriptionWithDaysLeft(it, java.time.temporal.ChronoUnit.DAYS.between(today, it.renewalDate).toInt()) }
            .sortedBy { it.daysLeft }

        val zombies = subscriptions.filter { it.isZombie }

        val monthlySpend = subscriptions.fold(BigDecimal.ZERO) { acc, s ->
            acc + when (s.billingCycle) {
                com.saastracker.domain.model.BillingCycle.MONTHLY -> s.amount
                com.saastracker.domain.model.BillingCycle.QUARTERLY -> s.amount.divide(BigDecimal("3"), 2, java.math.RoundingMode.HALF_UP)
                com.saastracker.domain.model.BillingCycle.ANNUAL -> s.amount.divide(BigDecimal("12"), 2, java.math.RoundingMode.HALF_UP)
                else -> s.amount
            }
        }

        val recipients = userRepository.listByCompany(company.id)
            .filter { it.isActive && it.role in setOf(
                com.saastracker.domain.model.UserRole.ADMIN,
                com.saastracker.domain.model.UserRole.EDITOR
            )}
            .map { it.email }

        if (recipients.isEmpty()) {
            logger.debug("WeeklyDigest: no eligible recipients for company={}", company.id)
            return false
        }

        val now = clock.nowInstant()
        val deliveryLog = EmailDeliveryLog(
            id = idProvider.newId(),
            companyId = company.id,
            invitationId = null,
            templateType = EmailTemplateType.WEEKLY_DIGEST,
            recipientEmail = recipients.first(),
            status = EmailDeliveryState.SENT,
            providerMessageId = null,
            providerStatusCode = null,
            providerResponse = null,
            errorMessage = null,
            createdAt = now
        )

        return runCatching {
            emailService.sendWeeklyDigest(company, recipients, renewals, zombies, monthlySpend)
            emailDeliveryRepository.create(deliveryLog)
            logger.info("WeeklyDigest sent company={} recipients={} renewals={} zombies={}",
                company.id, recipients.size, renewals.size, zombies.size)
            true
        }.onFailure {
            emailDeliveryRepository.create(deliveryLog.copy(
                status = EmailDeliveryState.FAILED,
                errorMessage = it.message?.take(500)
            ))
            logger.error("WeeklyDigest email failed for company={}", company.id, it)
        }.getOrElse { false }
    }
}
