package com.saastracker.domain.service

import com.saastracker.domain.dto.AnalyticsResponse
import com.saastracker.domain.dto.BudgetGaugeDto
import com.saastracker.domain.dto.CostPerEmployeeDto
import com.saastracker.domain.dto.GrowingSubscriptionDto
import com.saastracker.domain.dto.YoYSpendComparisonDto
import com.saastracker.domain.model.HealthScore
import com.saastracker.domain.model.SubscriptionStatus
import com.saastracker.persistence.repository.AuditLogRepository
import com.saastracker.persistence.repository.CompanyRepository
import com.saastracker.persistence.repository.SpendSnapshotRepository
import com.saastracker.persistence.repository.SubscriptionRepository
import com.saastracker.persistence.repository.UserRepository
import com.saastracker.util.toMoneyString
import java.math.BigDecimal
import java.math.RoundingMode
import java.time.LocalDate
import java.util.UUID

class AnalyticsService(
    private val subscriptionRepository: SubscriptionRepository,
    private val userRepository: UserRepository,
    private val companyRepository: CompanyRepository,
    private val auditLogRepository: AuditLogRepository,
    private val subscriptionService: SubscriptionService,
    private val currencyService: CurrencyService,
    private val spendSnapshotRepository: SpendSnapshotRepository
) {
    fun getAnalytics(companyId: UUID): AnalyticsResponse {
        val activeSubscriptions = subscriptionRepository.listByCompany(companyId)
            .filter { it.status == SubscriptionStatus.ACTIVE }

        val currentYear = LocalDate.now().year
        val previousYear = currentYear - 1
        val currentYearSnapshots = spendSnapshotRepository.findByCompanyAndYear(companyId, currentYear)
        val prevYearSnapshots = spendSnapshotRepository.findByCompanyAndYear(companyId, previousYear)

        val yoyComparison = if (prevYearSnapshots.isNotEmpty()) {
            val currentYearAnnual = currentYearSnapshots.sumOf { it.totalMonthlyUsd }
                .multiply(BigDecimal("12")).setScale(2, RoundingMode.HALF_UP)
            val previousYearAnnual = prevYearSnapshots.sumOf { it.totalMonthlyUsd }
                .multiply(BigDecimal("12")).setScale(2, RoundingMode.HALF_UP)
            val growthPercent = if (previousYearAnnual.compareTo(BigDecimal.ZERO) == 0) {
                BigDecimal("100.00")
            } else {
                currentYearAnnual.subtract(previousYearAnnual)
                    .divide(previousYearAnnual, 4, RoundingMode.HALF_UP)
                    .multiply(BigDecimal("100"))
                    .setScale(2, RoundingMode.HALF_UP)
            }
            YoYSpendComparisonDto(
                dataAvailable = true,
                currentYear = currentYear,
                previousYear = previousYear,
                currentYearUsd = currentYearAnnual.toMoneyString(),
                previousYearUsd = previousYearAnnual.toMoneyString(),
                growthPercent = growthPercent.toPlainString()
            )
        } else {
            YoYSpendComparisonDto(dataAvailable = false)
        }

        val auditLog = auditLogRepository.listByCompany(companyId)
        val fastestGrowing = activeSubscriptions.mapNotNull { subscription ->
            val latestOldAmount = auditLog
                .filter { it.entityId == subscription.id && it.oldValue?.contains("amount") == true }
                .maxByOrNull { it.createdAt }
                ?.oldValue
                ?.let(::extractAmount)
                ?: return@mapNotNull null
            val currentMonthlyUsd = subscriptionService.normalizedMonthlyUsd(subscription)
            val previousMonthlyUsd = currencyService.toUsd(latestOldAmount, subscription.currency)
            val growth = if (previousMonthlyUsd.compareTo(BigDecimal.ZERO) == 0) {
                BigDecimal("100.00")
            } else {
                currentMonthlyUsd.subtract(previousMonthlyUsd)
                    .divide(previousMonthlyUsd, 4, RoundingMode.HALF_UP)
                    .multiply(BigDecimal("100"))
                    .setScale(2, RoundingMode.HALF_UP)
            }
            Triple(subscription, previousMonthlyUsd, growth)
        }.sortedByDescending { it.third }
            .take(5)
            .map { (subscription, previousMonthlyUsd, growth) ->
                GrowingSubscriptionDto(
                    subscriptionId = subscription.id.toString(),
                    vendorName = subscription.vendorName,
                    previousMonthlyUsd = previousMonthlyUsd.toMoneyString(),
                    currentMonthlyUsd = subscriptionService.normalizedMonthlyUsd(subscription).toMoneyString(),
                    growthPercent = growth.toMoneyString()
                )
            }

        val company = companyRepository.findById(companyId)
        val monthlyTotal = activeSubscriptions.fold(BigDecimal.ZERO) { acc, sub ->
            acc + subscriptionService.normalizedMonthlyUsd(sub)
        }
        val budgetGauge = company?.monthlyBudget?.let { budget ->
            val utilization = if (budget.compareTo(BigDecimal.ZERO) == 0) {
                BigDecimal.ZERO
            } else {
                monthlyTotal.divide(budget, 4, RoundingMode.HALF_UP)
                    .multiply(BigDecimal("100"))
                    .setScale(2, RoundingMode.HALF_UP)
            }
            BudgetGaugeDto(
                budgetUsd = budget.toMoneyString(),
                actualUsd = monthlyTotal.toMoneyString(),
                utilizationPercent = utilization.toMoneyString(),
                overBudget = monthlyTotal > budget
            )
        }

        val teamSize = company?.employeeCount
            ?: userRepository.listByCompany(companyId).count { it.isActive }
                .coerceAtLeast(1)
        val annualTotal = monthlyTotal.multiply(BigDecimal("12")).setScale(2, RoundingMode.HALF_UP)
        val costPerEmployee = CostPerEmployeeDto(
            employeeCount = teamSize,
            monthlyUsd = monthlyTotal.divide(BigDecimal(teamSize), 2, RoundingMode.HALF_UP).toMoneyString(),
            annualUsd = annualTotal.divide(BigDecimal(teamSize), 2, RoundingMode.HALF_UP).toMoneyString()
        )

        val criticalCount = activeSubscriptions.count { subscriptionService.calculateHealthScore(it) == HealthScore.CRITICAL }
        val healthScore = when {
            criticalCount == 0 -> HealthScore.GOOD
            criticalCount <= (activeSubscriptions.size / 3).coerceAtLeast(1) -> HealthScore.WARNING
            else -> HealthScore.CRITICAL
        }

        return AnalyticsResponse(
            yoySpendComparison = yoyComparison,
            fastestGrowingSubscriptions = fastestGrowing,
            budgetGauge = budgetGauge,
            costPerEmployee = costPerEmployee,
            healthScore = healthScore
        )
    }

    private fun extractAmount(json: String): BigDecimal {
        val regex = """"amount"\s*:\s*"?(?<amount>[0-9]+(?:\.[0-9]+)?)"?""".toRegex()
        val match = regex.find(json) ?: return BigDecimal.ZERO
        return match.groups["amount"]?.value?.toBigDecimalOrNull() ?: BigDecimal.ZERO
    }
}

