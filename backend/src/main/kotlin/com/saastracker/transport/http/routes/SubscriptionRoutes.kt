package com.saastracker.transport.http.routes

import com.saastracker.domain.dto.PageRequest
import com.saastracker.domain.dto.SubscriptionFilter
import com.saastracker.domain.model.HealthScore
import com.saastracker.domain.model.PaymentMode
import com.saastracker.domain.model.PlanFeature
import com.saastracker.domain.model.SubscriptionStatus
import com.saastracker.domain.model.UserRole
import com.saastracker.domain.service.SubscriptionService
import com.saastracker.domain.validation.validateMarkSubscriptionPaid
import com.saastracker.domain.validation.validateSubscriptionRequest
import com.saastracker.persistence.repository.CompanyRepository
import com.saastracker.persistence.repository.SubscriptionRepository
import com.saastracker.persistence.repository.UserRepository
import com.saastracker.transport.payment.StripeBillingService
import com.saastracker.domain.model.SAAS_TEMPLATES
import com.saastracker.domain.model.PlanMatrix
import com.saastracker.transport.http.request.AddCommentRequest
import com.saastracker.transport.http.request.BatchCreateRequest
import com.saastracker.transport.http.request.CreateSubscriptionRequest
import com.saastracker.transport.http.request.MarkSubscriptionPaidRequest
import com.saastracker.transport.http.request.UpdateSubscriptionRequest
import com.saastracker.transport.http.response.BatchCreateResponse
import com.saastracker.transport.http.response.CategoriesResponse
import com.saastracker.transport.http.response.CommentResponse
import com.saastracker.transport.http.response.CsvImportResultResponse
import com.saastracker.transport.http.response.PagedSubscriptionListResponse
import com.saastracker.transport.http.response.SubscriptionListResponse
import com.saastracker.transport.http.response.SubscriptionResponse
import com.saastracker.transport.security.InMemoryRateLimiter
import com.saastracker.transport.security.authenticatedRoute
import com.saastracker.util.toMoneyString
import io.ktor.http.ContentType
import io.ktor.http.HttpHeaders
import io.ktor.http.HttpStatusCode
import io.ktor.http.content.PartData
import io.ktor.http.content.forEachPart
import io.ktor.server.application.call
import io.ktor.server.request.receive
import io.ktor.server.request.receiveMultipart
import io.ktor.server.response.respond
import io.ktor.server.response.respondBytes
import io.ktor.server.response.respondText
import io.ktor.server.routing.Route
import io.ktor.server.routing.delete
import io.ktor.server.routing.get
import io.ktor.server.routing.patch
import io.ktor.server.routing.post
import io.ktor.server.routing.put
import io.ktor.utils.io.core.readBytes


fun Route.subscriptionRoutes(
    subscriptionService: SubscriptionService,
    userRepository: UserRepository,
    companyRepository: CompanyRepository,
    subscriptionRepository: SubscriptionRepository,
    stripeBillingService: StripeBillingService,
    rateLimiter: InMemoryRateLimiter
) {
    authenticatedRoute("/api/v1/subscriptions", UserRole.VIEWER) {
        get {
            val user = call.requireCurrentUser(userRepository) ?: return@get
            val filter = SubscriptionFilter(
                vendorName = call.request.queryParameters["vendor"],
                category = call.request.queryParameters["category"],
                status = call.request.queryParameters["status"]
                    ?.let { runCatching { SubscriptionStatus.valueOf(it) }.getOrNull() },
                paymentMode = call.request.queryParameters["paymentMode"]
                    ?.let { runCatching { PaymentMode.valueOf(it) }.getOrNull() },
                minAmount = call.request.queryParameters["minAmount"]?.toBigDecimalOrNull(),
                maxAmount = call.request.queryParameters["maxAmount"]?.toBigDecimalOrNull(),
                zombie = call.request.queryParameters["zombie"]?.toBooleanStrictOrNull()
            )
            val pageRequest = PageRequest(
                page = call.request.queryParameters["page"]?.toIntOrNull() ?: 1,
                size = (call.request.queryParameters["size"]?.toIntOrNull() ?: 25).coerceIn(1, 100),
                sortBy = call.request.queryParameters["sortBy"] ?: "renewal_date",
                sortDir = call.request.queryParameters["sortDir"] ?: "asc"
            )

            val page = subscriptionService.listPaged(user.companyId, filter, pageRequest)
            val duplicateDetection = subscriptionService.detectDuplicates(user.companyId)
            call.respond(
                PagedSubscriptionListResponse(
                    items = page.items.map { subscription ->
                        val warnings = duplicateDetection.warnings.filter { warning ->
                            warning.contains(subscription.vendorName, ignoreCase = true) ||
                                warning.contains(subscription.category, ignoreCase = true)
                        }
                        subscription.toResponse(
                            healthScore = subscriptionService.calculateHealthScore(subscription),
                            duplicateWarnings = warnings
                        )
                    },
                    total = page.total,
                    page = page.page,
                    size = page.size,
                    totalPages = page.totalPages
                )
            )
        }

        get("/categories") {
            val user = call.requireCurrentUser(userRepository) ?: return@get
            call.respond(subscriptionService.getCategories(user.companyId))
        }

        // FR-002: return static SaaS template library
        get("/templates") {
            call.requireCurrentUser(userRepository) ?: return@get
            call.respond(mapOf("templates" to SAAS_TEMPLATES))
        }

        // FR-003: batch create subscriptions with partial-quota support
        post("/batch") {
            val user = call.requireCurrentUser(userRepository) ?: return@post
            if (!user.role.canEdit()) {
                call.respond(HttpStatusCode.Forbidden, mapOf("message" to "Editor or Admin role required"))
                return@post
            }
            val request = call.receive<BatchCreateRequest>()
            if (request.items.isEmpty()) {
                call.respond(HttpStatusCode.BadRequest, mapOf("error" to "items must not be empty"))
                return@post
            }
            if (request.items.size > 20) {
                call.respond(HttpStatusCode.BadRequest, mapOf("error" to "Maximum 20 items per batch"))
                return@post
            }

            // Calculate available quota slots
            val company = companyRepository.findById(user.companyId)
            val limits = company?.let { PlanMatrix.limits[it.planTier] }
            val slots = if (limits == null || limits.maxSubscriptions == -1) {
                request.items.size // unlimited
            } else {
                val current = subscriptionRepository.listActiveByCompany(user.companyId).size
                (limits.maxSubscriptions - current).coerceAtLeast(0)
            }

            if (slots == 0) {
                val planTier = company?.planTier
                call.respond(
                    HttpStatusCode.Forbidden,
                    mapOf(
                        "reason" to "PLAN_LIMIT_SUBSCRIPTIONS",
                        "requiredPlan" to "PRO",
                        "message" to "${planTier?.name ?: "Free"} plan subscription limit reached."
                    )
                )
                return@post
            }

            val toCreate = request.items.take(slots)
            val skipped = request.items.size - toCreate.size
            val created = subscriptionService.batchCreate(user, toCreate)

            call.respond(
                BatchCreateResponse(
                    created = created.size,
                    skipped = skipped,
                    reason = if (skipped > 0) "PLAN_LIMIT" else null,
                    requiredPlan = if (skipped > 0) "PRO" else null,
                    subscriptions = created.map { sub ->
                        sub.toResponse(subscriptionService.calculateHealthScore(sub), emptyList())
                    }
                )
            )
        }

        get("/vendors/suggest") {
            val user = call.requireCurrentUser(userRepository) ?: return@get
            if (!call.ensurePlanFeature(user, PlanFeature.VENDOR_SUGGEST, companyRepository)) return@get
            val q = call.request.queryParameters["q"] ?: ""
            val suggestions = subscriptionService.suggestVendors(user.companyId, q)
            call.respond(mapOf("items" to suggestions.map { s ->
                mapOf(
                    "vendorName" to s.vendorName,
                    "subscriptionId" to s.subscriptionId.toString(),
                    "category" to s.category,
                    "similarity" to s.similarity
                )
            }))
        }

        get("/{id}") {
            val user = call.requireCurrentUser(userRepository) ?: return@get
            val subscriptionId = call.requireUuidParam("id") ?: return@get
            val subscription = subscriptionService.list(user.companyId)
                .firstOrNull { it.id == subscriptionId }
                ?: throw NoSuchElementException("Subscription not found")
            call.respond(subscription.toResponse(subscriptionService.calculateHealthScore(subscription), emptyList()))
        }

        post {
            val user = call.requireCurrentUser(userRepository) ?: return@post
            if (!call.ensureSubscriptionQuota(user, companyRepository, subscriptionRepository)) return@post
            if (!user.role.canEdit()) {
                call.respond(HttpStatusCode.Forbidden, mapOf("message" to "Editor or Admin role required"))
                return@post
            }
            val request = call.receive<CreateSubscriptionRequest>()
            validateSubscriptionRequest(request)
            call.respondAppResult(
                subscriptionService.create(user, request).map { created ->
                    val warnings = subscriptionService.detectDuplicatesByVendor(user.companyId, created.vendorName)
                        .filter { !it.contains("id=${created.id}") }
                    created.toResponse(subscriptionService.calculateHealthScore(created), warnings)
                },
                HttpStatusCode.Created
            )
        }

        put("/{id}") {
            val user = call.requireCurrentUser(userRepository) ?: return@put
            if (!user.role.canEdit()) {
                call.respond(HttpStatusCode.Forbidden, mapOf("message" to "Editor or Admin role required"))
                return@put
            }
            val subscriptionId = call.requireUuidParam("id") ?: return@put
            val request = call.receive<UpdateSubscriptionRequest>()
            validateSubscriptionRequest(request)
            call.respondAppResult(
                subscriptionService.update(user, subscriptionId, request).map { updated ->
                    val warnings = subscriptionService.detectDuplicatesByVendor(user.companyId, updated.vendorName)
                        .filter { !it.contains("id=${updated.id}") }
                    updated.toResponse(subscriptionService.calculateHealthScore(updated), warnings)
                }
            )
        }

        get("/archived") {
            val user = call.requireCurrentUser(userRepository) ?: return@get
            if (!user.role.isAdmin()) {
                call.respond(HttpStatusCode.Forbidden, mapOf("message" to "Admin role required"))
                return@get
            }
            val archived = subscriptionService.listArchived(user.companyId)
            call.respond(SubscriptionListResponse(items = archived.map { it.toResponse(subscriptionService.calculateHealthScore(it), emptyList()) }))
        }

        get("/export") {
            val user = call.requireCurrentUser(userRepository) ?: return@get
            if (!call.ensurePlanFeature(user, PlanFeature.EXPORT, companyRepository)) return@get
            val format = call.request.queryParameters["format"] ?: "csv"
            if (format != "csv" && format != "pdf") {
                call.respond(HttpStatusCode.BadRequest, mapOf("message" to "Unsupported format: $format"))
                return@get
            }

            val clientIp = call.request.headers["X-Forwarded-For"]?.substringBefore(",")
                ?: call.request.local.remoteHost
            if (!rateLimiter.isAllowed("$clientIp:export", rateLimiter.exportLimit)) {
                call.respond(HttpStatusCode.TooManyRequests, mapOf("message" to "Export rate limit exceeded. Retry in one minute."))
                return@get
            }
            val filter = SubscriptionFilter(
                vendorName = call.request.queryParameters["vendor"],
                category = call.request.queryParameters["category"],
                status = call.request.queryParameters["status"]
                    ?.let { runCatching { SubscriptionStatus.valueOf(it) }.getOrNull() },
                paymentMode = call.request.queryParameters["paymentMode"]
                    ?.let { runCatching { PaymentMode.valueOf(it) }.getOrNull() },
                minAmount = call.request.queryParameters["minAmount"]?.toBigDecimalOrNull(),
                maxAmount = call.request.queryParameters["maxAmount"]?.toBigDecimalOrNull()
            )

            when (format) {
                "csv" -> {
                    val csv = subscriptionService.exportCsv(user.companyId, filter)
                    call.response.headers.append(HttpHeaders.ContentDisposition, "attachment; filename=\"subscriptions.csv\"")
                    call.respondText(csv, ContentType("text", "csv"))
                }
                else -> {
                    val companyName = companyRepository.findById(user.companyId)?.name ?: "Company"
                    val pdf = subscriptionService.exportPdf(user.companyId, companyName, filter)
                    call.response.headers.append(HttpHeaders.ContentDisposition, "attachment; filename=\"subscriptions.pdf\"")
                    call.respondBytes(pdf, ContentType("application", "pdf"))
                }
            }
        }

        delete("/{id}") {
            val user = call.requireCurrentUser(userRepository) ?: return@delete
            if (!user.role.canEdit()) {
                call.respond(HttpStatusCode.Forbidden, mapOf("message" to "Editor or Admin role required"))
                return@delete
            }
            val subscriptionId = call.requireUuidParam("id") ?: return@delete
            call.respondAppResult(subscriptionService.archive(user, subscriptionId))
        }

        post("/import/csv") {
            val user = call.requireCurrentUser(userRepository) ?: return@post
            if (!call.ensureSubscriptionQuota(user, companyRepository, subscriptionRepository)) return@post
            if (!user.role.canEdit()) {
                call.respond(HttpStatusCode.Forbidden, mapOf("message" to "Editor or Admin role required"))
                return@post
            }
            val multipart = call.receiveMultipart()
            var csvPayload: String? = null
            multipart.forEachPart { part ->
                when (part) {
                    is PartData.FileItem -> {
                        val bytes = part.provider().readBytes()
                        require(bytes.size <= 10 * 1024 * 1024) { "CSV file exceeds 10 MB limit" }
                        csvPayload = bytes.decodeToString()
                    }
                    else -> {}
                }
                part.dispose.invoke()
            }
            if (csvPayload.isNullOrBlank()) {
                throw IllegalArgumentException("CSV payload is empty")
            }
            call.respondAppResult(
                subscriptionService.importCsv(user, csvPayload!!.reader()).map {
                    CsvImportResultResponse(
                        imported = it.imported,
                        skipped = it.skipped,
                        errors = it.errors
                    )
                }
            )
        }

        post("/{id}/comments") {
            val user = call.requireCurrentUser(userRepository) ?: return@post
            val subscriptionId = call.requireUuidParam("id") ?: return@post
            val request = call.receive<AddCommentRequest>()
            call.respondAppResult(
                subscriptionService.addComment(user, subscriptionId, request.body).map {
                    CommentResponse(
                        id = it.id.toString(),
                        subscriptionId = it.subscriptionId.toString(),
                        userId = it.userId.toString(),
                        body = it.body,
                        createdAt = it.createdAt.toString()
                    )
                },
                HttpStatusCode.Created
            )
        }

        patch("/{id}/mark-used") {
            val user = call.requireCurrentUser(userRepository) ?: return@patch
            if (!user.role.canEdit()) {
                call.respond(HttpStatusCode.Forbidden, mapOf("message" to "Editor or Admin role required"))
                return@patch
            }
            val subscriptionId = call.requireUuidParam("id") ?: return@patch
            call.respondAppResult(
                subscriptionService.markUsed(user, subscriptionId).map {
                    it.toResponse(subscriptionService.calculateHealthScore(it), emptyList())
                }
            )
        }

        post("/{id}/mark-paid") {
            val user = call.requireCurrentUser(userRepository) ?: return@post
            if (!user.role.canEdit()) {
                call.respond(HttpStatusCode.Forbidden, mapOf("message" to "Editor or Admin role required"))
                return@post
            }
            val subscriptionId = call.requireUuidParam("id") ?: return@post
            val request = call.receive<MarkSubscriptionPaidRequest>()
            validateMarkSubscriptionPaid(request)
            call.respondAppResult(
                subscriptionService.markAsPaid(user, subscriptionId, request).map {
                    it.toResponse(subscriptionService.calculateHealthScore(it), emptyList())
                }
            )
        }

        get("/{id}/comments") {
            val user = call.requireCurrentUser(userRepository) ?: return@get
            val subscriptionId = call.requireUuidParam("id") ?: return@get
            call.respondAppResult(
                subscriptionService.listComments(user, subscriptionId).map { comments ->
                    comments.map {
                        CommentResponse(
                            id = it.id.toString(),
                            subscriptionId = it.subscriptionId.toString(),
                            userId = it.userId.toString(),
                            body = it.body,
                            createdAt = it.createdAt.toString()
                        )
                    }
                }
            )
        }
    }
}

private fun com.saastracker.domain.model.Subscription.toResponse(
    healthScore: HealthScore,
    duplicateWarnings: List<String>
): SubscriptionResponse = SubscriptionResponse(
    id = id.toString(),
    vendorName = vendorName,
    vendorUrl = vendorUrl,
    vendorLogoUrl = vendorLogoUrl,
    category = category,
    description = description,
    amount = amount.toMoneyString(),
    currency = currency,
    billingCycle = billingCycle,
    renewalDate = renewalDate.toString(),
    contractStartDate = contractStartDate?.toString(),
    autoRenews = autoRenews,
    paymentMode = paymentMode,
    paymentStatus = paymentStatus,
    lastPaidAt = lastPaidAt?.toString(),
    nextPaymentDate = nextPaymentDate?.toString(),
    status = status,
    tags = tags.orEmpty(),
    ownerId = ownerId?.toString(),
    notes = notes,
    documentUrl = documentUrl,
    healthScore = healthScore.name,
    duplicateWarnings = duplicateWarnings,
    lastUsedAt = lastUsedAt?.toString(),
    isZombie = isZombie,
    createdAt = createdAt.toString(),
    updatedAt = updatedAt.toString()
)
