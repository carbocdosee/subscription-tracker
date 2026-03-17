package com.saastracker.domain.service

import com.saastracker.domain.dto.DashboardStatsInternal
import com.saastracker.domain.dto.DuplicateWarningInternal
import com.saastracker.domain.dto.MonthlySpendInternal
import com.saastracker.domain.dto.SubscriptionRenewalInternal
import com.saastracker.domain.dto.TopCostDriverInternal
import com.saastracker.domain.model.SubscriptionStatus
import com.saastracker.persistence.repository.ClockProvider
import com.saastracker.persistence.repository.SubscriptionRepository
import com.saastracker.util.daysUntil
import com.saastracker.util.normalizeToAnnual
import java.math.BigDecimal
import java.time.YearMonth
import java.util.UUID

class DashboardService(
    private val subscriptionRepository: SubscriptionRepository,
    private val subscriptionService: SubscriptionService,
    private val currencyService: CurrencyService,
    private val clockProvider: ClockProvider
) {
    fun getDashboardStats(companyId: UUID): DashboardStatsInternal {
        val subscriptions = subscriptionRepository.listByCompany(companyId)
            .filter { it.status == SubscriptionStatus.ACTIVE }

        val totalMonthly = subscriptions.fold(BigDecimal.ZERO) { acc, subscription ->
            acc + subscriptionService.normalizedMonthlyUsd(subscription)
        }
        val totalAnnual = subscriptions.fold(BigDecimal.ZERO) { acc, subscription ->
            acc + currencyService.toUsd(normalizeToAnnual(subscription.amount, subscription.billingCycle), subscription.currency)
        }

        val renewing30 = subscriptions.mapNotNull { subscription ->
            val days = daysUntil(subscription.renewalDate)
            if (days in 0..30) {
                SubscriptionRenewalInternal(
                    subscriptionId = subscription.id,
                    vendorName = subscription.vendorName,
                    amountUsd = currencyService.toUsd(subscription.amount, subscription.currency),
                    renewalDate = subscription.renewalDate,
                    daysLeft = days
                )
            } else {
                null
            }
        }.sortedBy { it.daysLeft }

        val spendByCategory = subscriptions.groupBy { it.category.lowercase() }.mapValues { (_, items) ->
            items.fold(BigDecimal.ZERO) { acc, subscription ->
                acc + subscriptionService.normalizedMonthlyUsd(subscription)
            }
        }

        val monthlyTrend = buildMonthlyTrend(subscriptions)
        val topCostDrivers = buildTopCostDrivers(subscriptions)
        val duplicateDetection = subscriptionService.detectDuplicates(companyId)
        val duplicateWarnings = buildDuplicateWarnings(subscriptions)

        return DashboardStatsInternal(
            totalMonthlySpend = totalMonthly,
            totalAnnualSpend = totalAnnual,
            subscriptionCount = subscriptions.size,
            renewingIn30Days = renewing30,
            totalRenewal30DaysAmount = renewing30.fold(BigDecimal.ZERO) { acc, dto -> acc + dto.amountUsd },
            spendByCategory = spendByCategory,
            monthlyTrend = monthlyTrend,
            topCostDrivers = topCostDrivers,
            duplicateWarnings = duplicateWarnings,
            potentialSavings = duplicateDetection.potentialSavingsUsd
        )
    }

    private fun buildMonthlyTrend(subscriptions: List<com.saastracker.domain.model.Subscription>): List<MonthlySpendInternal> {
        val current = YearMonth.from(clockProvider.nowDate())
        return (11 downTo 0).map { shift ->
            val month = current.minusMonths(shift.toLong())
            val monthlyValue = subscriptions
                .filter { subscription ->
                    val startMonth = YearMonth.from(subscription.contractStartDate ?: subscription.createdAt.atZone(java.time.ZoneOffset.UTC).toLocalDate())
                    startMonth <= month
                }
                .fold(BigDecimal.ZERO) { acc, subscription ->
                    acc + subscriptionService.normalizedMonthlyUsd(subscription)
                }
            MonthlySpendInternal(month = month, amountUsd = monthlyValue)
        }
    }

    private fun buildDuplicateWarnings(subscriptions: List<com.saastracker.domain.model.Subscription>): List<DuplicateWarningInternal> {
        val byVendor = subscriptions.groupBy { it.vendorName.lowercase() }
            .filterValues { it.size > 1 }
            .map { (key, items) ->
                DuplicateWarningInternal(
                    type = "vendor",
                    key = key,
                    subscriptionIds = items.map { it.id },
                    estimatedSavingsUsd = items.minOfOrNull { currencyService.toUsd(it.amount, it.currency) } ?: BigDecimal.ZERO
                )
            }
        val byCategory = subscriptions.groupBy { it.category.lowercase() }
            .filterValues { it.size > 1 }
            .map { (key, items) ->
                DuplicateWarningInternal(
                    type = "category",
                    key = key,
                    subscriptionIds = items.map { it.id },
                    estimatedSavingsUsd = items.minOfOrNull { currencyService.toUsd(it.amount, it.currency) } ?: BigDecimal.ZERO
                )
            }
        return (byVendor + byCategory).sortedByDescending { it.estimatedSavingsUsd }
    }

    private fun buildTopCostDrivers(subscriptions: List<com.saastracker.domain.model.Subscription>): List<TopCostDriverInternal> =
        subscriptions
            .groupBy { it.vendorName }
            .map { (vendorName, items) ->
                TopCostDriverInternal(
                    vendorName = vendorName,
                    monthlySpendUsd = items.fold(BigDecimal.ZERO) { acc, subscription ->
                        acc + subscriptionService.normalizedMonthlyUsd(subscription)
                    },
                    subscriptionCount = items.size
                )
            }
            .sortedByDescending { it.monthlySpendUsd }
            .take(5)
}
