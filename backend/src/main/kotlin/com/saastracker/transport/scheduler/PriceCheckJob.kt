package com.saastracker.transport.scheduler

import com.saastracker.domain.service.InsightsService
import com.saastracker.persistence.repository.CompanyRepository
import org.quartz.Job
import org.quartz.JobExecutionContext
import org.slf4j.LoggerFactory

class PriceCheckJob : Job {
    private val logger = LoggerFactory.getLogger(javaClass)

    override fun execute(context: JobExecutionContext) {
        val companyRepository = PriceCheckJobRuntime.companyRepository
        val insightsService = PriceCheckJobRuntime.insightsService
        if (companyRepository == null || insightsService == null) {
            logger.error("PriceCheckJob runtime is not initialized")
            return
        }
        runCatching {
            @Suppress("DEPRECATION")
            val companies = companyRepository.listAll()
            var totalIncreases = 0
            for (company in companies) {
                val insights = insightsService.getWeeklyInsights(company.id)
                totalIncreases += insights.priceIncreases.size
            }
            logger.info("PriceCheckJob complete: companies={} priceIncreases={}", companies.size, totalIncreases)
        }.onFailure {
            logger.error("PriceCheckJob failed", it)
        }
    }
}

object PriceCheckJobRuntime {
    @Volatile
    var companyRepository: CompanyRepository? = null

    @Volatile
    var insightsService: InsightsService? = null
}
