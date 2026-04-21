package com.saastracker.transport.http.routes

import com.saastracker.domain.service.InsightsService
import com.saastracker.domain.service.PriceIncreaseItem
import com.saastracker.domain.service.RenewalInsightItem
import com.saastracker.domain.service.WeeklyInsights
import com.saastracker.domain.service.ZombieAlertItem
import com.saastracker.persistence.repository.CompanyRepository
import com.saastracker.persistence.repository.UserRepository
import com.saastracker.transport.cache.RedisCache
import com.saastracker.transport.payment.StripeBillingService
import com.saastracker.transport.security.authenticatedRoute
import io.ktor.server.application.call
import io.ktor.server.response.respond
import io.ktor.server.routing.Route
import io.ktor.server.routing.get
import kotlinx.serialization.Serializable
import kotlinx.serialization.json.Json
import java.time.Duration

private val insightsTtl = Duration.ofSeconds(900)

@Serializable
private data class ZombieAlertResponse(
    val id: String,
    val vendor: String,
    val daysSinceUsed: Int,
    val monthlyCost: String,
    val currency: String
)

@Serializable
private data class RenewalInsightResponse(
    val id: String,
    val vendor: String,
    val daysLeft: Int,
    val amountUsd: String
)

@Serializable
private data class PriceIncreaseResponse(
    val id: String,
    val vendor: String,
    val oldAmount: String,
    val newAmount: String,
    val changePercent: String
)

@Serializable
private data class WeeklyInsightsResponse(
    val totalActions: Int,
    val zombieAlerts: List<ZombieAlertResponse>,
    val renewalsThisWeek: List<RenewalInsightResponse>,
    val priceIncreases: List<PriceIncreaseResponse>
)

private fun WeeklyInsights.toResponse() = WeeklyInsightsResponse(
    totalActions = totalActions,
    zombieAlerts = zombieAlerts.map { it.toResponse() },
    renewalsThisWeek = renewalsThisWeek.map { it.toResponse() },
    priceIncreases = priceIncreases.map { it.toResponse() }
)

private fun ZombieAlertItem.toResponse() = ZombieAlertResponse(
    id = id.toString(),
    vendor = vendor,
    daysSinceUsed = daysSinceUsed,
    monthlyCost = monthlyCost.toPlainString(),
    currency = currency
)

private fun RenewalInsightItem.toResponse() = RenewalInsightResponse(
    id = id.toString(),
    vendor = vendor,
    daysLeft = daysLeft,
    amountUsd = amountUsd.toPlainString()
)

private fun PriceIncreaseItem.toResponse() = PriceIncreaseResponse(
    id = id.toString(),
    vendor = vendor,
    oldAmount = oldAmount.toPlainString(),
    newAmount = newAmount.toPlainString(),
    changePercent = changePercent.toPlainString()
)

fun Route.insightsRoutes(
    insightsService: InsightsService,
    userRepository: UserRepository,
    companyRepository: CompanyRepository,
    stripeBillingService: StripeBillingService,
    redisCache: RedisCache
) {
    authenticatedRoute("/api/v1/insights/weekly") {
        get {
            val user = call.requireCurrentUser(userRepository) ?: return@get

            val cacheKey = "insights:weekly:${user.companyId}"
            val cached = redisCache.get(cacheKey)
            if (cached != null) {
                val response = runCatching {
                    Json.decodeFromString(WeeklyInsightsResponse.serializer(), cached)
                }.getOrNull()
                if (response != null) {
                    call.respond(response)
                    return@get
                }
            }

            val insights = insightsService.getWeeklyInsights(user.companyId)
            val response = insights.toResponse()
            redisCache.setJson(cacheKey, WeeklyInsightsResponse.serializer(), response, insightsTtl)
            call.respond(response)
        }
    }
}
