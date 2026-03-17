package com.saastracker.domain.service

import com.saastracker.domain.model.AlertType
import com.saastracker.domain.model.AlertDeliveryStatus
import com.saastracker.domain.model.Company
import com.saastracker.domain.model.RenewalAlert
import com.saastracker.domain.model.SubscriptionStatus
import com.saastracker.domain.model.UserRole
import com.saastracker.persistence.repository.ClockProvider
import com.saastracker.persistence.repository.CompanyRepository
import com.saastracker.persistence.repository.IdentityProvider
import com.saastracker.persistence.repository.RenewalAlertRepository
import com.saastracker.persistence.repository.SubscriptionRepository
import com.saastracker.persistence.repository.UserRepository
import com.saastracker.transport.email.EmailService
import com.saastracker.transport.email.SubscriptionWithDaysLeft
import com.saastracker.transport.metrics.AppMetrics
import com.saastracker.util.daysUntil
import org.slf4j.LoggerFactory

data class RenewalCheckSummary(
    val companiesProcessed: Int,
    val alertsCreated: Int,
    val emailsSent: Int,
    val skippedDuplicates: Int
)

class AlertService(
    private val companyRepository: CompanyRepository,
    private val userRepository: UserRepository,
    private val subscriptionRepository: SubscriptionRepository,
    private val renewalAlertRepository: RenewalAlertRepository,
    private val emailService: EmailService,
    private val metrics: AppMetrics,
    private val idProvider: IdentityProvider,
    private val clock: ClockProvider
) {
    private val logger = LoggerFactory.getLogger(javaClass)
    private val thresholds = listOf(90, 60, 30, 7, 1)

    fun runDailyRenewalCheck(): RenewalCheckSummary {
        var alertsCreated = 0
        var emailsSent = 0
        var skippedDuplicates = 0
        val companies = companyRepository.listAll()

        companies.forEach { company ->
            val createdForCompany = processCompany(company)
            alertsCreated += createdForCompany.createdAlerts
            skippedDuplicates += createdForCompany.skippedDuplicates
            if (createdForCompany.emailSent) {
                emailsSent += 1
            }
        }

        return RenewalCheckSummary(
            companiesProcessed = companies.size,
            alertsCreated = alertsCreated,
            emailsSent = emailsSent,
            skippedDuplicates = skippedDuplicates
        )
    }

    private fun processCompany(company: Company): CompanyResult {
        val subscriptions = subscriptionRepository.listActiveByCompany(company.id)
            .filter { it.status == SubscriptionStatus.ACTIVE }

        if (subscriptions.isEmpty()) return CompanyResult()

        val recipients = userRepository.listByCompany(company.id)
            .filter { it.isActive && it.role in setOf(UserRole.ADMIN, UserRole.EDITOR) }
            .map { it.email }
            .distinct()

        if (recipients.isEmpty()) return CompanyResult()

        val toEmail = mutableListOf<SubscriptionWithDaysLeft>()
        val pendingAlerts = mutableListOf<RenewalAlert>()
        var createdAlerts = 0
        var skippedDuplicates = 0

        subscriptions.forEach { subscription ->
            val daysLeft = daysUntil(subscription.renewalDate)
            if (daysLeft !in thresholds) return@forEach
            val alertType = AlertType.fromDays(daysLeft) ?: return@forEach
            val existingAlert = renewalAlertRepository.findForThreshold(
                subscriptionId = subscription.id,
                thresholdDays = daysLeft,
                renewalDateSnapshot = subscription.renewalDate
            )
            if (existingAlert?.deliveryStatus == AlertDeliveryStatus.SENT) {
                skippedDuplicates += 1
                return@forEach
            }

            val alert = existingAlert ?: renewalAlertRepository.create(
                RenewalAlert(
                    id = idProvider.newId(),
                    subscriptionId = subscription.id,
                    companyId = company.id,
                    alertType = alertType,
                    alertWindowDays = daysLeft,
                    renewalDateSnapshot = subscription.renewalDate,
                    deliveryStatus = AlertDeliveryStatus.PENDING,
                    sentAt = null,
                    failureReason = null,
                    emailRecipients = emptyList()
                )
            )
            if (existingAlert == null) {
                createdAlerts += 1
            }
            pendingAlerts += alert
            toEmail += SubscriptionWithDaysLeft(subscription, daysLeft)
        }

        if (toEmail.isNotEmpty()) {
            val sendResult = runCatching {
                emailService.sendRenewalDigest(company, recipients, toEmail.sortedBy { it.daysLeft })
                logger.info("Renewal digest sent company_id={} subscriptions={}", company.id, toEmail.size)
            }
            if (sendResult.isSuccess) {
                val sentAt = clock.nowInstant()
                pendingAlerts.forEach { alert ->
                    renewalAlertRepository.update(
                        alert.copy(
                            deliveryStatus = AlertDeliveryStatus.SENT,
                            sentAt = sentAt,
                            failureReason = null,
                            emailRecipients = recipients
                        )
                    )
                    metrics.incrementAlertSent(alert.alertType.name)
                }
            } else {
                val reason = sendResult.exceptionOrNull()?.message ?: "Unknown delivery failure"
                logger.error("Renewal digest send failed company_id={}", company.id, sendResult.exceptionOrNull())
                pendingAlerts.forEach { alert ->
                    renewalAlertRepository.update(
                        alert.copy(
                            deliveryStatus = AlertDeliveryStatus.FAILED,
                            sentAt = null,
                            failureReason = reason,
                            emailRecipients = recipients
                        )
                    )
                }
            }
            return CompanyResult(
                createdAlerts = createdAlerts,
                skippedDuplicates = skippedDuplicates,
                emailSent = sendResult.isSuccess
            )
        }
        return CompanyResult(
            createdAlerts = createdAlerts,
            skippedDuplicates = skippedDuplicates,
            emailSent = false
        )
    }

    data class CompanyResult(
        val createdAlerts: Int = 0,
        val skippedDuplicates: Int = 0,
        val emailSent: Boolean = false
    )
}
