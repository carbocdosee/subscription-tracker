package com.saastracker.domain.service

import com.saastracker.domain.model.BillingCycle
import com.saastracker.domain.model.SubscriptionStatus
import com.saastracker.persistence.repository.ClockProvider
import com.saastracker.persistence.repository.SubscriptionPaymentRepository
import com.saastracker.persistence.repository.SubscriptionRepository
import com.saastracker.util.daysUntil
import java.math.BigDecimal
import java.math.RoundingMode
import java.time.Instant
import java.time.temporal.ChronoUnit
import java.util.UUID

data class ZombieAlertItem(
    val id: UUID,
    val vendor: String,
    val daysSinceUsed: Int,
    val monthlyCost: BigDecimal,
    val currency: String
)

data class RenewalInsightItem(
    val id: UUID,
    val vendor: String,
    val daysLeft: Int,
    val amountUsd: BigDecimal
)

data class PriceIncreaseItem(
    val id: UUID,
    val vendor: String,
    val oldAmount: BigDecimal,
    val newAmount: BigDecimal,
    val changePercent: BigDecimal
)

data class WeeklyInsights(
    val totalActions: Int,
    val zombieAlerts: List<ZombieAlertItem>,
    val renewalsThisWeek: List<RenewalInsightItem>,
    val priceIncreases: List<PriceIncreaseItem>
)

class InsightsService(
    private val subscriptionRepository: SubscriptionRepository,
    private val currencyService: CurrencyService,
    private val clock: ClockProvider,
    private val paymentRepository: SubscriptionPaymentRepository
) {
    fun getWeeklyInsights(companyId: UUID): WeeklyInsights {
        val now: Instant = clock.nowInstant()
        val today = clock.nowDate()
        val subscriptions = subscriptionRepository.listByCompany(companyId)
            .filter { it.status == SubscriptionStatus.ACTIVE }

        val zombieAlerts = subscriptions
            .filter { it.isZombie }
            .map { sub ->
                val lastActivity = sub.lastUsedAt ?: sub.createdAt
                val daysSince = ChronoUnit.DAYS.between(lastActivity, now).toInt()
                val monthlyCost = when (sub.billingCycle) {
                    BillingCycle.ANNUAL -> sub.amount.divide(BigDecimal("12"), 2, RoundingMode.HALF_UP)
                    BillingCycle.QUARTERLY -> sub.amount.divide(BigDecimal("3"), 2, RoundingMode.HALF_UP)
                    else -> sub.amount
                }
                ZombieAlertItem(
                    id = sub.id,
                    vendor = sub.vendorName,
                    daysSinceUsed = daysSince,
                    monthlyCost = monthlyCost,
                    currency = sub.currency
                )
            }
            .sortedByDescending { it.daysSinceUsed }

        val renewalsThisWeek = subscriptions
            .mapNotNull { sub ->
                val daysLeft = daysUntil(sub.renewalDate)
                if (daysLeft in 0..7) {
                    RenewalInsightItem(
                        id = sub.id,
                        vendor = sub.vendorName,
                        daysLeft = daysLeft,
                        amountUsd = currencyService.toUsd(sub.amount, sub.currency)
                    )
                } else null
            }
            .sortedBy { it.daysLeft }

        val recentPayments = paymentRepository.listRecentByCompany(companyId)
        val paymentsBySubscription = recentPayments.groupBy { it.subscriptionId }
        val priceIncreases = subscriptions.mapNotNull { sub ->
            val payments = paymentsBySubscription[sub.id]?.sortedByDescending { it.paidAt } ?: return@mapNotNull null
            if (payments.size < 2) return@mapNotNull null
            val newest = payments[0]
            val previous = payments[1]
            if (previous.amount <= BigDecimal.ZERO) return@mapNotNull null
            val changePercent = (newest.amount - previous.amount)
                .divide(previous.amount, 4, RoundingMode.HALF_UP)
                .multiply(BigDecimal("100"))
            if (changePercent <= BigDecimal.ZERO) return@mapNotNull null
            PriceIncreaseItem(
                id = sub.id,
                vendor = sub.vendorName,
                oldAmount = previous.amount,
                newAmount = newest.amount,
                changePercent = changePercent.setScale(1, RoundingMode.HALF_UP)
            )
        }

        val totalActions = zombieAlerts.size + renewalsThisWeek.size + priceIncreases.size
        return WeeklyInsights(
            totalActions = totalActions,
            zombieAlerts = zombieAlerts,
            renewalsThisWeek = renewalsThisWeek,
            priceIncreases = priceIncreases
        )
    }
}
