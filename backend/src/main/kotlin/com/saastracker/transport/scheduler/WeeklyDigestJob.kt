package com.saastracker.transport.scheduler

import com.saastracker.domain.service.DigestService
import org.quartz.Job
import org.quartz.JobExecutionContext
import org.slf4j.LoggerFactory
import java.time.ZonedDateTime

class WeeklyDigestJob : Job {
    private val logger = LoggerFactory.getLogger(javaClass)

    override fun execute(context: JobExecutionContext) {
        val digestService = DigestJobRuntime.digestService
        if (digestService == null) {
            logger.error("WeeklyDigestJob runtime is not initialized")
            return
        }
        runCatching {
            digestService.runWeeklyDigest(ZonedDateTime.now())
        }.onFailure {
            logger.error("WeeklyDigestJob failed", it)
        }
    }
}

object DigestJobRuntime {
    @Volatile
    var digestService: DigestService? = null
}
