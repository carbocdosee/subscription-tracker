package com.saastracker.transport.scheduler

import com.saastracker.domain.service.AlertService
import com.saastracker.domain.service.BudgetAlertService
import com.saastracker.transport.metrics.AppMetrics
import org.quartz.Job
import org.quartz.JobExecutionContext
import org.slf4j.LoggerFactory

class RenewalCheckJob : Job {
    private val logger = LoggerFactory.getLogger(javaClass)

    override fun execute(context: JobExecutionContext) {
        val alertService = RenewalJobRuntime.alertService
        val metrics = RenewalJobRuntime.metrics
        if (alertService == null || metrics == null) {
            logger.error("RenewalCheckJob runtime is not initialized")
            return
        }

        runCatching {
            val summary = alertService.runDailyRenewalCheck()
            logger.info(
                "Renewal check completed companies={} alerts={} emails={} skipped={}",
                summary.companiesProcessed,
                summary.alertsCreated,
                summary.emailsSent,
                summary.skippedDuplicates
            )
            metrics.incrementJobExecution("renewal_check", "success")
        }.onFailure {
            logger.error("RenewalCheckJob failed", it)
            metrics.incrementJobExecution("renewal_check", "error")
        }

        runCatching {
            RenewalJobRuntime.budgetAlertService?.checkAllCompanies()
        }.onFailure {
            logger.error("BudgetAlertService.checkAllCompanies failed", it)
        }
    }
}

object RenewalJobRuntime {
    @Volatile
    var alertService: AlertService? = null

    @Volatile
    var metrics: AppMetrics? = null

    @Volatile
    var budgetAlertService: BudgetAlertService? = null
}

