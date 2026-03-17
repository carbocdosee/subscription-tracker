package com.saastracker.util

import com.saastracker.domain.model.BillingCycle
import java.math.BigDecimal
import java.math.RoundingMode

/**
 * Calculates the normalized monthly cost for a subscription,
 * converting annual/quarterly billing cycles to monthly equivalents.
 *
 * @param amount The billed amount per cycle
 * @param cycle The billing cycle (MONTHLY, ANNUAL, QUARTERLY)
 * @return Monthly equivalent cost rounded to 2 decimal places
 *
 * Example:
 * normalizeToMonthly(1200.00, ANNUAL) returns 100.00
 * normalizeToMonthly(90.00, QUARTERLY) returns 30.00
 */
fun normalizeToMonthly(amount: BigDecimal, cycle: BillingCycle): BigDecimal {
    val divisor = when (cycle) {
        BillingCycle.MONTHLY -> BigDecimal.ONE
        BillingCycle.QUARTERLY -> BigDecimal("3")
        BillingCycle.ANNUAL -> BigDecimal("12")
    }
    return amount.divide(divisor, 2, RoundingMode.HALF_UP)
}

fun normalizeToAnnual(amount: BigDecimal, cycle: BillingCycle): BigDecimal {
    val multiplier = when (cycle) {
        BillingCycle.MONTHLY -> BigDecimal("12")
        BillingCycle.QUARTERLY -> BigDecimal("4")
        BillingCycle.ANNUAL -> BigDecimal.ONE
    }
    return amount.multiply(multiplier).setScale(2, RoundingMode.HALF_UP)
}

fun BigDecimal.toMoneyString(): String = setScale(2, RoundingMode.HALF_UP).toPlainString()

