package com.saastracker.config

import com.saastracker.transport.scheduler.DataRetentionJob
import com.saastracker.transport.scheduler.PriceCheckJob
import com.saastracker.transport.scheduler.RenewalCheckJob
import com.saastracker.transport.scheduler.SpendSnapshotJob
import com.saastracker.transport.scheduler.WeeklyDigestJob
import com.saastracker.transport.scheduler.ZombieCheckJob
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

        val retentionJobDetail = JobBuilder.newJob(DataRetentionJob::class.java)
            .withIdentity("data-retention-job")
            .build()

        val retentionTrigger = TriggerBuilder.newTrigger()
            .withIdentity("data-retention-trigger")
            .withSchedule(CronScheduleBuilder.cronSchedule("0 0 3 * * ?")) // daily at 03:00
            .build()

        if (checkExists(retentionJobDetail.key).not()) {
            scheduleJob(retentionJobDetail, retentionTrigger)
        }

        val digestJobDetail = JobBuilder.newJob(WeeklyDigestJob::class.java)
            .withIdentity("weekly-digest-job")
            .build()

        val digestTrigger = TriggerBuilder.newTrigger()
            .withIdentity("weekly-digest-trigger")
            .withSchedule(CronScheduleBuilder.cronSchedule("0 0 * * * ?")) // every hour
            .build()

        if (checkExists(digestJobDetail.key).not()) {
            scheduleJob(digestJobDetail, digestTrigger)
        }

        val zombieJobDetail = JobBuilder.newJob(ZombieCheckJob::class.java)
            .withIdentity("zombie-check-job")
            .build()

        val zombieTrigger = TriggerBuilder.newTrigger()
            .withIdentity("zombie-check-trigger")
            .withSchedule(CronScheduleBuilder.cronSchedule("0 0 10 * * ?")) // daily at 10:00
            .build()

        if (checkExists(zombieJobDetail.key).not()) {
            scheduleJob(zombieJobDetail, zombieTrigger)
        }

        val priceCheckJobDetail = JobBuilder.newJob(PriceCheckJob::class.java)
            .withIdentity("price-check-job")
            .build()

        val priceCheckTrigger = TriggerBuilder.newTrigger()
            .withIdentity("price-check-trigger")
            .withSchedule(CronScheduleBuilder.cronSchedule("0 30 9 * * ?")) // daily at 09:30
            .build()

        if (checkExists(priceCheckJobDetail.key).not()) {
            scheduleJob(priceCheckJobDetail, priceCheckTrigger)
        }
    }
}

