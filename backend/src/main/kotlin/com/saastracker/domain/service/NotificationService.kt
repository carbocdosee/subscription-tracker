package com.saastracker.domain.service

import com.saastracker.domain.model.EmailDeliveryState
import com.saastracker.domain.model.EmailTemplateType
import com.saastracker.domain.model.NotificationSeverity
import com.saastracker.domain.model.NotificationType
import com.saastracker.domain.model.PaymentMode
import com.saastracker.domain.model.SubscriptionStatus
import com.saastracker.domain.model.AlertDeliveryStatus
import com.saastracker.persistence.repository.ClockProvider
import com.saastracker.persistence.repository.EmailDeliveryRepository
import com.saastracker.persistence.repository.NotificationReadRepository
import com.saastracker.persistence.repository.RenewalAlertRepository
import com.saastracker.persistence.repository.SubscriptionRepository
import com.saastracker.persistence.repository.TeamInvitationRepository
import java.math.BigDecimal
import java.time.Duration
import java.time.Instant
import java.time.ZoneOffset
import java.time.temporal.ChronoUnit
import java.util.UUID
import kotlin.math.ceil

data class NotificationFeedItem(
    val key: String,
    val type: NotificationType,
    val severity: NotificationSeverity,
    val title: String,
    val message: String,
    val createdAt: Instant,
    val actionPath: String?,
    val actionLabel: String?,
    val read: Boolean
)

data class NotificationFeed(
    val items: List<NotificationFeedItem>,
    val unreadCount: Int
)

class NotificationService(
    private val subscriptionRepository: SubscriptionRepository,
    private val invitationRepository: TeamInvitationRepository,
    private val emailDeliveryRepository: EmailDeliveryRepository,
    private val renewalAlertRepository: RenewalAlertRepository,
    private val notificationReadRepository: NotificationReadRepository,
    private val clock: ClockProvider
) {
    fun getFeed(
        companyId: UUID,
        userId: UUID,
        limit: Int = 20,
        typeFilter: Set<NotificationType> = emptySet()
    ): NotificationFeed {
        val boundedLimit = limit.coerceIn(1, 100)
        val notifications = buildNotifications(companyId, typeFilter)
        val readKeys = notificationReadRepository.listReadKeysByUser(userId)
        val unreadCount = notifications.count { it.key !in readKeys }
        val items = notifications
            .take(boundedLimit)
            .map { notification ->
                NotificationFeedItem(
                    key = notification.key,
                    type = notification.type,
                    severity = notification.severity,
                    title = notification.title,
                    message = notification.message,
                    createdAt = notification.createdAt,
                    actionPath = notification.actionPath,
                    actionLabel = notification.actionLabel,
                    read = notification.key in readKeys
                )
            }
        return NotificationFeed(items = items, unreadCount = unreadCount)
    }

    fun unreadCount(
        companyId: UUID,
        userId: UUID,
        typeFilter: Set<NotificationType> = emptySet()
    ): Int {
        val notifications = buildNotifications(companyId, typeFilter)
        val readKeys = notificationReadRepository.listReadKeysByUser(userId)
        return notifications.count { it.key !in readKeys }
    }

    fun markRead(userId: UUID, keys: List<String>) {
        notificationReadRepository.markRead(userId, keys, clock.nowInstant())
    }

    fun markAllAsRead(
        companyId: UUID,
        userId: UUID,
        typeFilter: Set<NotificationType> = emptySet()
    ): Int {
        val notifications = buildNotifications(companyId, typeFilter)
        val readKeys = notificationReadRepository.listReadKeysByUser(userId)
        val unreadKeys = notifications
            .asSequence()
            .map { it.key }
            .filterNot { it in readKeys }
            .toList()
        if (unreadKeys.isNotEmpty()) {
            notificationReadRepository.markRead(userId, unreadKeys, clock.nowInstant())
        }
        return unreadKeys.size
    }

    private fun buildNotifications(companyId: UUID, typeFilter: Set<NotificationType>): List<NotificationCandidate> {
        val nowDate = clock.nowDate()
        val nowInstant = clock.nowInstant()

        val subscriptions = subscriptionRepository.listByCompany(companyId)
            .filter { it.status == SubscriptionStatus.ACTIVE }
        val subscriptionsById = subscriptions.associateBy { it.id }

        val renewalDue = subscriptions.mapNotNull { subscription ->
            val daysLeft = ChronoUnit.DAYS.between(nowDate, subscription.renewalDate).toInt()
            if (daysLeft !in 0..30) return@mapNotNull null

            val severity = when {
                daysLeft <= 1 -> NotificationSeverity.DANGER
                daysLeft <= 7 -> NotificationSeverity.WARNING
                else -> NotificationSeverity.INFO
            }
            val title = when {
                daysLeft == 0 -> "${subscription.vendorName} renews today"
                daysLeft == 1 -> "${subscription.vendorName} renews tomorrow"
                else -> "${subscription.vendorName} renews in $daysLeft days"
            }

            NotificationCandidate(
                key = "renewal_due:${subscription.id}:${subscription.renewalDate}",
                type = NotificationType.RENEWAL_DUE,
                severity = severity,
                title = title,
                message = "Renewal date ${subscription.renewalDate} - ${formatAmount(subscription.amount, subscription.currency)}",
                createdAt = subscription.renewalDate.atStartOfDay().toInstant(ZoneOffset.UTC),
                sortAt = nowInstant.minus(Duration.ofHours(daysLeft.toLong())),
                actionPath = "/subscriptions",
                actionLabel = "Review"
            )
        }

        val manualPayments = subscriptions.mapNotNull { subscription ->
            if (subscription.paymentMode != PaymentMode.MANUAL || subscription.nextPaymentDate == null) return@mapNotNull null
            val dueDate = subscription.nextPaymentDate
            val daysToDue = ChronoUnit.DAYS.between(nowDate, dueDate).toInt()

            when {
                daysToDue < 0 -> NotificationCandidate(
                    key = "manual_payment_overdue:${subscription.id}:$dueDate",
                    type = NotificationType.MANUAL_PAYMENT_OVERDUE,
                    severity = NotificationSeverity.DANGER,
                    title = "Manual payment overdue: ${subscription.vendorName}",
                    message = "Was due on $dueDate - ${formatAmount(subscription.amount, subscription.currency)}",
                    createdAt = dueDate.atStartOfDay().toInstant(ZoneOffset.UTC),
                    sortAt = nowInstant.plus(Duration.ofHours((-daysToDue).toLong())),
                    actionPath = "/subscriptions",
                    actionLabel = "Mark as paid"
                )
                daysToDue in 0..7 -> NotificationCandidate(
                    key = "manual_payment_due:${subscription.id}:$dueDate",
                    type = NotificationType.MANUAL_PAYMENT_DUE,
                    severity = if (daysToDue <= 1) NotificationSeverity.WARNING else NotificationSeverity.INFO,
                    title = if (daysToDue == 0) {
                        "Manual payment due today: ${subscription.vendorName}"
                    } else {
                        "Manual payment due in $daysToDue days: ${subscription.vendorName}"
                    },
                    message = "Next payment date $dueDate - ${formatAmount(subscription.amount, subscription.currency)}",
                    createdAt = dueDate.atStartOfDay().toInstant(ZoneOffset.UTC),
                    sortAt = nowInstant.minus(Duration.ofHours(daysToDue.toLong())),
                    actionPath = "/subscriptions",
                    actionLabel = "Mark as paid"
                )
                else -> null
            }
        }

        val invitationExpiringSoon = invitationRepository.listActiveByCompany(companyId, nowInstant)
            .mapNotNull { invitation ->
                val hoursLeft = Duration.between(nowInstant, invitation.expiresAt).toHours()
                if (hoursLeft > 48) return@mapNotNull null
                val severity = if (hoursLeft <= 24) NotificationSeverity.DANGER else NotificationSeverity.WARNING
                val label = when {
                    hoursLeft <= 1 -> "within 1 hour"
                    hoursLeft < 24 -> "in $hoursLeft hours"
                    else -> "in ${ceil(hoursLeft / 24.0).toInt()} days"
                }

                NotificationCandidate(
                    key = "invitation_expiring:${invitation.id}:${invitation.expiresAt.epochSecond}",
                    type = NotificationType.INVITATION_EXPIRING,
                    severity = severity,
                    title = "Invitation expiring soon",
                    message = "${invitation.email} expires $label",
                    createdAt = invitation.expiresAt,
                    sortAt = nowInstant.minus(Duration.ofHours(hoursLeft.coerceAtLeast(0))),
                    actionPath = "/team",
                    actionLabel = "Review invite"
                )
            }

        val inviteDeliveryIssues = emailDeliveryRepository.listByCompany(companyId, limit = 120)
            .asSequence()
            .filter { it.templateType == EmailTemplateType.TEAM_INVITE }
            .filter {
                it.status == EmailDeliveryState.FAILED || it.status == EmailDeliveryState.SKIPPED_NOT_CONFIGURED
            }
            .filter { it.createdAt.isAfter(nowInstant.minus(30, ChronoUnit.DAYS)) }
            .map { delivery ->
                val isFailure = delivery.status == EmailDeliveryState.FAILED
                NotificationCandidate(
                    key = "invitation_delivery_issue:${delivery.id}",
                    type = NotificationType.INVITATION_EMAIL_ISSUE,
                    severity = if (isFailure) NotificationSeverity.DANGER else NotificationSeverity.WARNING,
                    title = if (isFailure) "Invitation email failed" else "Invitation email skipped",
                    message = buildString {
                        append(delivery.recipientEmail)
                        delivery.errorMessage?.takeIf { it.isNotBlank() }?.let {
                            append(" - ")
                            append(it)
                        }
                    },
                    createdAt = delivery.createdAt,
                    sortAt = delivery.createdAt,
                    actionPath = "/team",
                    actionLabel = "Open team"
                )
            }
            .toList()

        val renewalDeliveryIssues = renewalAlertRepository.listByCompany(companyId)
            .asSequence()
            .filter { it.deliveryStatus == AlertDeliveryStatus.FAILED }
            .filter { it.renewalDateSnapshot.isAfter(nowDate.minusDays(31)) || it.renewalDateSnapshot == nowDate.minusDays(31) }
            .map { alert ->
                val subscription = subscriptionsById[alert.subscriptionId]
                val vendorName = subscription?.vendorName ?: "Subscription ${alert.subscriptionId}"
                val details = alert.failureReason?.takeIf { it.isNotBlank() } ?: "Unknown delivery failure"
                NotificationCandidate(
                    key = "renewal_alert_failed:${alert.id}",
                    type = NotificationType.RENEWAL_ALERT_FAILED,
                    severity = NotificationSeverity.DANGER,
                    title = "Renewal alert email failed",
                    message = "$vendorName (${alert.alertWindowDays}d window) - $details",
                    createdAt = alert.sentAt ?: alert.renewalDateSnapshot.atStartOfDay().toInstant(ZoneOffset.UTC),
                    sortAt = alert.sentAt ?: nowInstant,
                    actionPath = "/dashboard",
                    actionLabel = "Open dashboard"
                )
            }
            .toList()

        return (renewalDue + manualPayments + invitationExpiringSoon + inviteDeliveryIssues + renewalDeliveryIssues)
            .asSequence()
            .filter { typeFilter.isEmpty() || it.type in typeFilter }
            .distinctBy { it.key }
            .sortedWith(
                compareByDescending<NotificationCandidate> { severityWeight(it.severity) }
                    .thenByDescending { it.sortAt }
                    .thenByDescending { it.createdAt }
            )
            .toList()
    }

    private fun severityWeight(severity: NotificationSeverity): Int = when (severity) {
        NotificationSeverity.DANGER -> 3
        NotificationSeverity.WARNING -> 2
        NotificationSeverity.INFO -> 1
    }

    private fun formatAmount(amount: BigDecimal, currency: String): String = "${currency.uppercase()} ${amount.stripTrailingZeros().toPlainString()}"

    private data class NotificationCandidate(
        val key: String,
        val type: NotificationType,
        val severity: NotificationSeverity,
        val title: String,
        val message: String,
        val createdAt: Instant,
        val sortAt: Instant,
        val actionPath: String?,
        val actionLabel: String?
    )
}
