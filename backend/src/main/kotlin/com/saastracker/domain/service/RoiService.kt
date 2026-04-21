package com.saastracker.domain.service

import com.saastracker.domain.model.SavingsEventType
import com.saastracker.persistence.repository.SavingsEventRepository
import java.math.BigDecimal
import java.math.RoundingMode
import java.util.UUID

data class RoiStats(
    val totalSavedUsd: BigDecimal,
    val eventCount: Int,
    val zombieArchivedCount: Int
)

class RoiService(
    private val savingsEventRepository: SavingsEventRepository,
    private val currencyService: CurrencyService
) {
    fun getRoiStats(companyId: UUID): RoiStats {
        val events = savingsEventRepository.listByCompany(companyId)
        if (events.isEmpty()) return RoiStats(BigDecimal.ZERO, 0, 0)

        val totalUsd = events.fold(BigDecimal.ZERO) { acc, ev ->
            val usd = try {
                currencyService.toUsd(ev.amount, ev.currency)
            } catch (_: Exception) {
                ev.amount
            }
            acc + usd
        }.setScale(2, RoundingMode.HALF_UP)

        val zombieCount = events.count { it.eventType == SavingsEventType.ZOMBIE_ARCHIVED }
        return RoiStats(
            totalSavedUsd = totalUsd,
            eventCount = events.size,
            zombieArchivedCount = zombieCount
        )
    }
}
