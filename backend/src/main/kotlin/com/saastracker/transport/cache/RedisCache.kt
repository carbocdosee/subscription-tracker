package com.saastracker.transport.cache

import io.lettuce.core.SetArgs
import io.lettuce.core.api.StatefulRedisConnection
import kotlinx.serialization.encodeToString
import kotlinx.serialization.json.Json
import org.slf4j.LoggerFactory
import java.time.Duration

class RedisCache(
    private val connection: StatefulRedisConnection<String, String>,
    private val defaultTtl: Duration
) {
    private val logger = LoggerFactory.getLogger(javaClass)
    private val commands get() = connection.sync()

    fun get(key: String): String? = runCatching { commands.get(key) }
        .onFailure { logger.warn("Redis get failed for key={}", key, it) }
        .getOrNull()

    fun <T> getJson(key: String, serializer: kotlinx.serialization.KSerializer<T>): T? {
        val raw = get(key) ?: return null
        return runCatching { Json.decodeFromString(serializer, raw) }.getOrNull()
    }

    fun set(key: String, value: String, ttl: Duration = defaultTtl) {
        runCatching {
            commands.set(key, value, SetArgs.Builder.ex(ttl))
        }.onFailure {
            logger.warn("Redis set failed for key={}", key, it)
        }
    }

    fun <T> setJson(
        key: String,
        serializer: kotlinx.serialization.KSerializer<T>,
        value: T,
        ttl: Duration = defaultTtl
    ) {
        val payload = Json.encodeToString(serializer, value)
        set(key, payload, ttl)
    }

    fun delete(key: String) {
        runCatching { commands.del(key) }
            .onFailure { logger.warn("Redis delete failed for key={}", key, it) }
    }
}

