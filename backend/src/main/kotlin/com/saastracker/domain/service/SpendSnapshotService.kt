package com.saastracker.domain.service

import com.saastracker.domain.model.SpendSnapshot
import com.saastracker.domain.model.SubscriptionStatus
import com.saastracker.persistence.repository.ClockProvider
import com.saastracker.persistence.repository.CompanyRepository
import com.saastracker.persistence.repository.IdentityProvider
import com.saastracker.persistence.repository.SpendSnapshotRepository
import com.saastracker.persistence.repository.SubscriptionRepository
import java.math.BigDecimal
import java.util.UUID

class SpendSnapshotService(
    private val companyRepository: CompanyRepository,
    private val subscriptionRepository: SubscriptionRepository,
    private val subscriptionService: SubscriptionService,
    private val spendSnapshotRepository: SpendSnapshotRepository,
    private val idProvider: IdentityProvider,
    private val clock: ClockProvider
) {
    fun captureAllCompanies() {
        val now = clock.nowDate()
        companyRepository.listAll().forEach { company ->
            captureCompany(company.id, now.year, now.monthValue)
        }
    }

    fun captureCompany(companyId: UUID, year: Int, month: Int) {
        val subs = subscriptionRepository.listByCompany(companyId)
            .filter { it.status == SubscriptionStatus.ACTIVE }
        val total = subs.fold(BigDecimal.ZERO) { acc, s ->
            acc + subscriptionService.normalizedMonthlyUsd(s)
        }
        val now = clock.nowInstant()
        spendSnapshotRepository.upsert(
            SpendSnapshot(
                id = idProvider.newId(),
                companyId = companyId,
                year = year,
                month = month,
                totalMonthlyUsd = total,
                subscriptionCount = subs.size,
                createdAt = now,
                updatedAt = now
            )
        )
    }
}
