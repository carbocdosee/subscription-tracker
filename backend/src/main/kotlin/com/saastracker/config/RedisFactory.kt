package com.saastracker.config

import io.lettuce.core.RedisClient
import io.lettuce.core.api.StatefulRedisConnection

object RedisFactory {
    fun createClient(config: RedisConfig): RedisClient = RedisClient.create(config.uri)

    fun connect(client: RedisClient): StatefulRedisConnection<String, String> = client.connect()
}

