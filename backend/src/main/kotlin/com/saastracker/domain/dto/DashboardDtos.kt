package com.saastracker.domain.dto

import com.saastracker.domain.model.HealthScore
import kotlinx.serialization.Serializable
import java.math.BigDecimal
import java.time.LocalDate
import java.time.YearMonth
import java.util.UUID

@Serializable
data class SubscriptionRenewalDto(
    val subscriptionId: String,
    val vendorName: String,
    val amountUsd: String,
    val renewalDate: String,
    val daysLeft: Int,
    val alertType: String
)

@Serializable
data class MonthlySpendDto(
    val month: String,
    val amountUsd: String
)

@Serializable
data class DuplicateWarningDto(
    val type: String,
    val key: String,
    val subscriptionIds: List<String>,
    val estimatedSavingsUsd: String
)

@Serializable
data class TopCostDriverDto(
    val vendorName: String,
    val monthlySpendUsd: String,
    val subscriptionCount: Int
)

@Serializable
data class DashboardStats(
    val totalMonthlySpend: String,
    val totalAnnualSpend: String,
    val subscriptionCount: Int,
    val renewingIn30Days: List<SubscriptionRenewalDto>,
    val totalRenewal30DaysAmount: String,
    val spendByCategory: Map<String, String>,
    val monthlyTrend: List<MonthlySpendDto>,
    val topCostDrivers: List<TopCostDriverDto>,
    val duplicateWarnings: List<DuplicateWarningDto>,
    val potentialSavings: String
)

data class DashboardStatsInternal(
    val totalMonthlySpend: BigDecimal,
    val totalAnnualSpend: BigDecimal,
    val subscriptionCount: Int,
    val renewingIn30Days: List<SubscriptionRenewalInternal>,
    val totalRenewal30DaysAmount: BigDecimal,
    val spendByCategory: Map<String, BigDecimal>,
    val monthlyTrend: List<MonthlySpendInternal>,
    val topCostDrivers: List<TopCostDriverInternal>,
    val duplicateWarnings: List<DuplicateWarningInternal>,
    val potentialSavings: BigDecimal
)

data class SubscriptionRenewalInternal(
    val subscriptionId: UUID,
    val vendorName: String,
    val amountUsd: BigDecimal,
    val renewalDate: LocalDate,
    val daysLeft: Int
)

data class MonthlySpendInternal(
    val month: YearMonth,
    val amountUsd: BigDecimal
)

data class DuplicateWarningInternal(
    val type: String,
    val key: String,
    val subscriptionIds: List<UUID>,
    val estimatedSavingsUsd: BigDecimal
)

data class TopCostDriverInternal(
    val vendorName: String,
    val monthlySpendUsd: BigDecimal,
    val subscriptionCount: Int
)

@Serializable
data class YoYSpendComparisonDto(
    val dataAvailable: Boolean,
    val currentYear: Int? = null,
    val previousYear: Int? = null,
    val currentYearUsd: String? = null,
    val previousYearUsd: String? = null,
    val growthPercent: String? = null
)

@Serializable
data class GrowingSubscriptionDto(
    val subscriptionId: String,
    val vendorName: String,
    val previousMonthlyUsd: String,
    val currentMonthlyUsd: String,
    val growthPercent: String
)

@Serializable
data class BudgetGaugeDto(
    val budgetUsd: String,
    val actualUsd: String,
    val utilizationPercent: String,
    val overBudget: Boolean
)

@Serializable
data class CostPerEmployeeDto(
    val employeeCount: Int,
    val monthlyUsd: String,
    val annualUsd: String
)

@Serializable
data class AnalyticsResponse(
    val yoySpendComparison: YoYSpendComparisonDto,
    val fastestGrowingSubscriptions: List<GrowingSubscriptionDto>,
    val budgetGauge: BudgetGaugeDto?,
    val costPerEmployee: CostPerEmployeeDto,
    val healthScore: HealthScore
)
