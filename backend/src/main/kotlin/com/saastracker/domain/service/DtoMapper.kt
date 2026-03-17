package com.saastracker.domain.service

import com.saastracker.domain.dto.DashboardStats
import com.saastracker.domain.dto.DashboardStatsInternal
import com.saastracker.domain.dto.DuplicateWarningDto
import com.saastracker.domain.dto.MonthlySpendDto
import com.saastracker.domain.dto.SubscriptionRenewalDto
import com.saastracker.domain.dto.TopCostDriverDto
import com.saastracker.util.toMoneyString

fun DashboardStatsInternal.toApiDto(): DashboardStats = DashboardStats(
    totalMonthlySpend = totalMonthlySpend.toMoneyString(),
    totalAnnualSpend = totalAnnualSpend.toMoneyString(),
    subscriptionCount = subscriptionCount,
    renewingIn30Days = renewingIn30Days.map {
        SubscriptionRenewalDto(
            subscriptionId = it.subscriptionId.toString(),
            vendorName = it.vendorName,
            amountUsd = it.amountUsd.toMoneyString(),
            renewalDate = it.renewalDate.toString(),
            daysLeft = it.daysLeft,
            alertType = "DAYS_${it.daysLeft}"
        )
    },
    totalRenewal30DaysAmount = totalRenewal30DaysAmount.toMoneyString(),
    spendByCategory = spendByCategory.mapValues { (_, amount) -> amount.toMoneyString() },
    monthlyTrend = monthlyTrend.map {
        MonthlySpendDto(month = it.month.toString(), amountUsd = it.amountUsd.toMoneyString())
    },
    topCostDrivers = topCostDrivers.map {
        TopCostDriverDto(
            vendorName = it.vendorName,
            monthlySpendUsd = it.monthlySpendUsd.toMoneyString(),
            subscriptionCount = it.subscriptionCount
        )
    },
    duplicateWarnings = duplicateWarnings.map {
        DuplicateWarningDto(
            type = it.type,
            key = it.key,
            subscriptionIds = it.subscriptionIds.map { id -> id.toString() },
            estimatedSavingsUsd = it.estimatedSavingsUsd.toMoneyString()
        )
    },
    potentialSavings = potentialSavings.toMoneyString()
)
