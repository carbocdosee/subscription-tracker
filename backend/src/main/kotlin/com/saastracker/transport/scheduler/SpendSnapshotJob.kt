package com.saastracker.transport.scheduler

import com.saastracker.domain.service.SpendSnapshotService
import org.quartz.Job
import org.quartz.JobExecutionContext
import org.slf4j.LoggerFactory

class SpendSnapshotJob : Job {
    private val logger = LoggerFactory.getLogger(javaClass)

    override fun execute(context: JobExecutionContext) {
        val service = SpendSnapshotJobRuntime.snapshotService
        if (service == null) {
            logger.error("SpendSnapshotJob runtime is not initialized")
            return
        }

        runCatching {
            service.captureAllCompanies()
            logger.info("SpendSnapshot capture completed")
        }.onFailure {
            logger.error("SpendSnapshotJob failed", it)
        }
    }
}

object SpendSnapshotJobRuntime {
    @Volatile
    var snapshotService: SpendSnapshotService? = null
}
