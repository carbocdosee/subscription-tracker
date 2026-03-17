package com.saastracker.domain.service

import com.saastracker.persistence.repository.CurrencyRateRepository
import java.math.BigDecimal
import java.math.RoundingMode

class CurrencyService(
    private val currencyRateRepository: CurrencyRateRepository
) {
    fun toUsd(amount: BigDecimal, currency: String): BigDecimal {
        val normalizedCurrency = currency.uppercase()
        val rate = currencyRateRepository.getRateToUsd(normalizedCurrency)
            ?: throw IllegalArgumentException("Unsupported currency: $normalizedCurrency")
        return amount.multiply(BigDecimal.valueOf(rate)).setScale(2, RoundingMode.HALF_UP)
    }
}

