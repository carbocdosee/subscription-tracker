package com.saastracker.transport.scheduler

import com.saastracker.persistence.repository.AuditLogRepository
import com.saastracker.persistence.repository.EmailDeliveryRepository
import com.saastracker.persistence.repository.PasswordResetRepository
import com.saastracker.persistence.repository.RefreshTokenRepository
import org.quartz.Job
import org.quartz.JobExecutionContext
import org.slf4j.LoggerFactory
import java.time.Instant
import java.time.temporal.ChronoUnit

private const val EMAIL_DELIVERY_RETENTION_DAYS = 90L
private const val AUDIT_LOG_RETENTION_DAYS = 730L // 2 years

class DataRetentionJob : Job {
    private val logger = LoggerFactory.getLogger(javaClass)

    override fun execute(context: JobExecutionContext) {
        purge("refresh tokens") {
            DataRetentionJobRuntime.refreshTokenRepository!!.deleteExpired()
        }
        purge("email delivery logs older than $EMAIL_DELIVERY_RETENTION_DAYS days") {
            val cutoff = Instant.now().minus(EMAIL_DELIVERY_RETENTION_DAYS, ChronoUnit.DAYS)
            DataRetentionJobRuntime.emailDeliveryRepository!!.deleteOlderThan(cutoff)
        }
        purge("audit log entries older than $AUDIT_LOG_RETENTION_DAYS days") {
            val cutoff = Instant.now().minus(AUDIT_LOG_RETENTION_DAYS, ChronoUnit.DAYS)
            DataRetentionJobRuntime.auditLogRepository!!.deleteOlderThan(cutoff)
        }
        purge("expired password reset tokens") {
            DataRetentionJobRuntime.passwordResetRepository!!.deleteAllExpired()
        }
    }

    private fun purge(description: String, block: () -> Unit) {
        if (!DataRetentionJobRuntime.isInitialized()) {
            logger.error("DataRetentionJob runtime is not initialized")
            return
        }
        runCatching {
            block()
            logger.info("DataRetentionJob: purged {}", description)
        }.onFailure {
            logger.error("DataRetentionJob: failed to purge {}", description, it)
        }
    }
}

object DataRetentionJobRuntime {
    @Volatile var refreshTokenRepository: RefreshTokenRepository? = null
    @Volatile var emailDeliveryRepository: EmailDeliveryRepository? = null
    @Volatile var auditLogRepository: AuditLogRepository? = null
    @Volatile var passwordResetRepository: PasswordResetRepository? = null

    fun isInitialized() =
        refreshTokenRepository != null &&
            emailDeliveryRepository != null &&
            auditLogRepository != null &&
            passwordResetRepository != null
}
