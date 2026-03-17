package com.saastracker.domain.service

import io.lettuce.core.api.StatefulRedisConnection
import kotlinx.serialization.Serializable
import org.quartz.Scheduler
import javax.sql.DataSource

@Serializable
data class DependencyHealth(
    val status: String,
    val details: String
)

@Serializable
data class HealthResponseInternal(
    val status: String,
    val dependencies: Map<String, DependencyHealth>
)

class HealthService(
    private val dataSource: DataSource,
    private val redisConnection: StatefulRedisConnection<String, String>,
    private val scheduler: Scheduler
) {
    fun check(): HealthResponseInternal {
        val dbHealth = runCatching {
            dataSource.connection.use { connection ->
                if (connection.isValid(2)) {
                    DependencyHealth("UP", "PostgreSQL connection is valid")
                } else {
                    DependencyHealth("DOWN", "PostgreSQL validation failed")
                }
            }
        }.getOrElse { DependencyHealth("DOWN", it.message ?: "Unknown DB error") }

        val redisHealth = runCatching {
            val pong = redisConnection.sync().ping()
            if (pong.equals("PONG", ignoreCase = true)) {
                DependencyHealth("UP", "Redis ping succeeded")
            } else {
                DependencyHealth("DOWN", "Redis ping failed")
            }
        }.getOrElse { DependencyHealth("DOWN", it.message ?: "Unknown Redis error") }

        val schedulerHealth = runCatching {
            if (scheduler.isStarted && !scheduler.isShutdown) {
                DependencyHealth("UP", "Quartz scheduler is running")
            } else {
                DependencyHealth("DOWN", "Quartz scheduler is not running")
            }
        }.getOrElse { DependencyHealth("DOWN", it.message ?: "Unknown scheduler error") }

        val dependencies = mapOf(
            "database" to dbHealth,
            "redis" to redisHealth,
            "scheduler" to schedulerHealth
        )
        val overall = if (dependencies.values.all { it.status == "UP" }) "UP" else "DOWN"
        return HealthResponseInternal(status = overall, dependencies = dependencies)
    }
}
