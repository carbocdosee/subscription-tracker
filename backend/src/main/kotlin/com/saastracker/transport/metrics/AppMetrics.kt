package com.saastracker.transport.metrics

import io.micrometer.core.instrument.MeterRegistry
import io.micrometer.core.instrument.Timer
import java.time.Duration

class AppMetrics(private val registry: MeterRegistry) {
    fun incrementRequest(path: String, method: String, status: Int) {
        registry.counter(
            "http_requests_total",
            "path", sanitizePath(path),
            "method", method,
            "status", status.toString()
        ).increment()
    }

    fun recordRequestLatency(path: String, method: String, duration: Duration) {
        Timer.builder("http_request_latency")
            .tag("path", sanitizePath(path))
            .tag("method", method)
            .publishPercentileHistogram()
            .register(registry)
            .record(duration)
    }

    fun incrementJobExecution(jobName: String, status: String) {
        registry.counter("job_executions_total", "job", jobName, "status", status).increment()
    }

    fun incrementAlertSent(alertType: String) {
        registry.counter("renewal_alerts_sent_total", "type", alertType).increment()
    }

    private fun sanitizePath(path: String): String = path.replace(Regex("/[0-9a-fA-F-]{36}"), "/:id")
}

