package com.saastracker.persistence.repository.exposed

import com.saastracker.domain.model.UserRole
import com.saastracker.persistence.repository.ClockProvider
import com.saastracker.persistence.repository.CurrencyRateRepository
import com.saastracker.persistence.repository.IdentityProvider
import com.saastracker.persistence.repository.RoleGuard
import java.time.Instant
import java.time.LocalDate
import java.util.UUID
import java.util.concurrent.ConcurrentHashMap

class UuidIdentityProvider : IdentityProvider {
    override fun newId(): UUID = UUID.randomUUID()
}

class SystemClockProvider : ClockProvider {
    override fun nowInstant(): Instant = Instant.now()
    override fun nowDate(): LocalDate = LocalDate.now()
}

class SimpleRoleGuard : RoleGuard {
    override fun ensureRole(userRole: UserRole, required: UserRole) {
        val allowed = when (required) {
            UserRole.ADMIN -> userRole == UserRole.ADMIN
            UserRole.EDITOR -> userRole == UserRole.ADMIN || userRole == UserRole.EDITOR
            UserRole.VIEWER -> true
        }
        require(allowed) { "Insufficient role: required=$required actual=$userRole" }
    }
}

class InMemoryCurrencyRateRepository : CurrencyRateRepository {
    private val rates = ConcurrentHashMap<String, Double>(
        mapOf(
            "USD" to 1.0,
            "EUR" to 1.09,
            "GBP" to 1.27,
            "CAD" to 0.74,
            "AUD" to 0.66,
            "JPY" to 0.0068
        )
    )

    override fun getRateToUsd(currency: String): Double? = rates[currency.uppercase()]

    override fun upsertRateToUsd(currency: String, rate: Double) {
        rates[currency.uppercase()] = rate
    }
}

