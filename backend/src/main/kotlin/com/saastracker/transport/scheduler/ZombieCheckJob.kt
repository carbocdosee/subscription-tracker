package com.saastracker.transport.scheduler

import com.saastracker.domain.model.SubscriptionStatus
import com.saastracker.persistence.repository.ClockProvider
import com.saastracker.persistence.repository.CompanyRepository
import com.saastracker.persistence.repository.SubscriptionRepository
import org.quartz.Job
import org.quartz.JobExecutionContext
import org.slf4j.LoggerFactory
import java.time.Instant
import java.time.temporal.ChronoUnit

class ZombieCheckJob : Job {
    private val logger = LoggerFactory.getLogger(javaClass)

    override fun execute(context: JobExecutionContext) {
        val companyRepository = ZombieJobRuntime.companyRepository
        val subscriptionRepository = ZombieJobRuntime.subscriptionRepository
        val clock = ZombieJobRuntime.clock
        if (companyRepository == null || subscriptionRepository == null || clock == null) {
            logger.error("ZombieCheckJob runtime is not initialized")
            return
        }
        runCatching {
            val summary = runZombieCheck(companyRepository, subscriptionRepository, clock.nowInstant())
            logger.info("ZombieCheck complete: companies={} marked={} cleared={}",
                summary.companiesProcessed, summary.markedZombie, summary.clearedZombie)
        }.onFailure {
            logger.error("ZombieCheckJob failed", it)
        }
    }

    private data class ZombieSummary(
        val companiesProcessed: Int,
        val markedZombie: Int,
        val clearedZombie: Int
    )

    private fun runZombieCheck(
        companyRepository: CompanyRepository,
        subscriptionRepository: SubscriptionRepository,
        now: Instant
    ): ZombieSummary {
        @Suppress("DEPRECATION")
        val companies = companyRepository.listAll()
        var markedZombie = 0
        var clearedZombie = 0

        for (company in companies) {
            val thresholdDays = company.zombieThresholdDays.toLong()
            val cutoff = now.minus(thresholdDays, ChronoUnit.DAYS)
            val subscriptions = subscriptionRepository.listActiveByCompany(company.id)
                .filter { it.status == SubscriptionStatus.ACTIVE }

            for (sub in subscriptions) {
                val lastActivity: Instant = sub.lastUsedAt ?: sub.createdAt
                val isStale = lastActivity.isBefore(cutoff)

                if (isStale && !sub.isZombie) {
                    // Mark as zombie — only when transitioning from non-zombie to zombie (dedup)
                    subscriptionRepository.markZombie(sub.id, true, now)
                    markedZombie++
                } else if (!isStale && sub.isZombie) {
                    // Clear zombie flag if subscription has been used recently
                    subscriptionRepository.markZombie(sub.id, false, now)
                    clearedZombie++
                }
            }
        }
        return ZombieSummary(
            companiesProcessed = companies.size,
            markedZombie = markedZombie,
            clearedZombie = clearedZombie
        )
    }
}

object ZombieJobRuntime {
    @Volatile
    var companyRepository: CompanyRepository? = null

    @Volatile
    var subscriptionRepository: SubscriptionRepository? = null

    @Volatile
    var clock: ClockProvider? = null
}
