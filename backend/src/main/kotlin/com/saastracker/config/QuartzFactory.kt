package com.saastracker.config

import com.saastracker.transport.scheduler.RenewalCheckJob
import com.saastracker.transport.scheduler.SpendSnapshotJob
import org.quartz.CronScheduleBuilder
import org.quartz.JobBuilder
import org.quartz.TriggerBuilder
import org.quartz.impl.StdSchedulerFactory

object QuartzFactory {
    fun buildScheduler(cron: String) = StdSchedulerFactory().scheduler.apply {
        val renewalJobDetail = JobBuilder.newJob(RenewalCheckJob::class.java)
            .withIdentity("renewal-check-job")
            .build()

        val renewalTrigger = TriggerBuilder.newTrigger()
            .withIdentity("renewal-check-trigger")
            .withSchedule(CronScheduleBuilder.cronSchedule(cron))
            .build()

        if (checkExists(renewalJobDetail.key).not()) {
            scheduleJob(renewalJobDetail, renewalTrigger)
        }

        val snapshotJobDetail = JobBuilder.newJob(SpendSnapshotJob::class.java)
            .withIdentity("spend-snapshot-job")
            .build()

        val snapshotTrigger = TriggerBuilder.newTrigger()
            .withIdentity("spend-snapshot-trigger")
            .withSchedule(CronScheduleBuilder.cronSchedule("0 5 0 1 * ?"))
            .build()

        if (checkExists(snapshotJobDetail.key).not()) {
            scheduleJob(snapshotJobDetail, snapshotTrigger)
        }
    }
}

