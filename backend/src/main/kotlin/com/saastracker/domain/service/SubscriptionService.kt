package com.saastracker.domain.service

import com.saastracker.domain.dto.Page
import com.saastracker.domain.dto.PageRequest
import com.saastracker.domain.dto.SubscriptionFilter
import com.saastracker.domain.error.AppError
import com.saastracker.domain.error.AppResult
import com.saastracker.domain.model.AuditAction
import com.saastracker.domain.model.AuditEntityType
import com.saastracker.domain.model.AuditLogEntry
import com.saastracker.domain.model.HealthScore
import com.saastracker.domain.model.PaymentMode
import com.saastracker.domain.model.PaymentStatus
import com.saastracker.domain.model.Subscription
import com.saastracker.domain.model.SubscriptionComment
import com.saastracker.domain.model.SubscriptionPayment
import com.saastracker.domain.model.SubscriptionStatus
import com.saastracker.domain.model.User
import com.saastracker.persistence.repository.AuditLogRepository
import com.saastracker.persistence.repository.ClockProvider
import com.saastracker.persistence.repository.IdentityProvider
import com.saastracker.persistence.repository.SubscriptionCommentRepository
import com.saastracker.persistence.repository.SubscriptionPaymentRepository
import com.saastracker.persistence.repository.SubscriptionRepository
import com.saastracker.transport.http.request.CreateSubscriptionRequest
import com.saastracker.transport.http.request.MarkSubscriptionPaidRequest
import com.saastracker.transport.http.request.UpdateSubscriptionRequest
import com.saastracker.transport.http.response.CategoriesResponse
import com.saastracker.util.csvEscape
import com.saastracker.util.daysUntil
import com.saastracker.util.normalizeToMonthly
import com.saastracker.util.parseCsv
import org.apache.pdfbox.pdmodel.PDDocument
import org.apache.pdfbox.pdmodel.PDPage
import org.apache.pdfbox.pdmodel.PDPageContentStream
import org.apache.pdfbox.pdmodel.common.PDRectangle
import org.apache.pdfbox.pdmodel.font.PDType1Font
import org.apache.pdfbox.pdmodel.font.Standard14Fonts
import java.io.ByteArrayOutputStream
import java.io.Reader
import java.math.BigDecimal
import java.time.Clock
import java.time.LocalDate
import java.util.UUID

data class DuplicateDetection(
    val warnings: List<String>,
    val potentialSavingsUsd: BigDecimal
)

data class CsvImportResult(
    val imported: Int,
    val skipped: Int,
    val errors: List<String>
)

class SubscriptionService(
    private val subscriptionRepository: SubscriptionRepository,
    private val commentRepository: SubscriptionCommentRepository,
    private val paymentRepository: SubscriptionPaymentRepository,
    private val auditLogRepository: AuditLogRepository,
    private val currencyService: CurrencyService,
    private val vendorLogoService: VendorLogoService,
    private val idProvider: IdentityProvider,
    private val clockProvider: ClockProvider
) {
    companion object {
        val PREDEFINED_CATEGORIES = listOf(
            "analytics", "communication", "design", "devtools",
            "finance", "hr", "infrastructure", "legal",
            "marketing", "other", "productivity", "security"
        )
    }

    fun list(companyId: UUID): List<Subscription> = subscriptionRepository.listByCompany(companyId)

    fun listPaged(companyId: UUID, filter: SubscriptionFilter, pageRequest: PageRequest): Page<Subscription> =
        subscriptionRepository.listByCompanyPaged(companyId, filter, pageRequest)

    fun create(currentUser: User, request: CreateSubscriptionRequest): AppResult<Subscription> {
        if (!currentUser.role.canEdit()) {
            return AppResult.Failure(AppError.Forbidden("User has read-only access"))
        }
        val renewalDate = LocalDate.parse(request.renewalDate)
        val parsedNextPaymentDate = request.nextPaymentDate?.let(LocalDate::parse)
        val resolvedNextPaymentDate = when (request.paymentMode) {
            PaymentMode.MANUAL -> parsedNextPaymentDate ?: renewalDate
            PaymentMode.AUTO -> parsedNextPaymentDate
        }
        val now = clockProvider.nowInstant()
        val subscription = Subscription(
            id = idProvider.newId(),
            companyId = currentUser.companyId,
            createdById = currentUser.id,
            vendorName = request.vendorName.trim(),
            vendorUrl = request.vendorUrl?.trim(),
            vendorLogoUrl = vendorLogoService.resolveLogoUrl(request.vendorName, request.vendorUrl),
            category = request.category.trim().lowercase(),
            description = request.description?.trim(),
            amount = request.amount.toBigDecimal(),
            currency = request.currency.uppercase(),
            billingCycle = request.billingCycle,
            renewalDate = renewalDate,
            contractStartDate = request.contractStartDate?.let(LocalDate::parse),
            autoRenews = request.autoRenews,
            paymentMode = request.paymentMode,
            paymentStatus = resolvePaymentStatus(request.paymentMode, resolvedNextPaymentDate, clockProvider.nowDate()),
            lastPaidAt = null,
            nextPaymentDate = resolvedNextPaymentDate,
            status = request.status,
            tags = request.tags.filter { it.isNotBlank() }.map { it.trim().lowercase() },
            ownerId = request.ownerId?.let(UUID::fromString),
            notes = request.notes?.trim(),
            documentUrl = request.documentUrl?.trim(),
            createdAt = now,
            updatedAt = now
        )
        val created = subscriptionRepository.create(subscription)
        recordAudit(
            currentUser = currentUser,
            action = AuditAction.CREATED,
            entityId = created.id,
            oldValue = null,
            newValue = """{"vendor":"${created.vendorName}","amount":"${created.amount}","currency":"${created.currency}"}"""
        )
        return AppResult.Success(created)
    }

    fun update(currentUser: User, subscriptionId: UUID, request: UpdateSubscriptionRequest): AppResult<Subscription> {
        if (!currentUser.role.canEdit()) {
            return AppResult.Failure(AppError.Forbidden("User has read-only access"))
        }
        val existing = subscriptionRepository.findById(subscriptionId, currentUser.companyId)
            ?: return AppResult.Failure(AppError.NotFound("Subscription not found"))

        val renewalDate = LocalDate.parse(request.renewalDate)
        val parsedNextPaymentDate = request.nextPaymentDate?.let(LocalDate::parse)
        val resolvedNextPaymentDate = when (request.paymentMode) {
            PaymentMode.MANUAL -> parsedNextPaymentDate ?: existing.nextPaymentDate ?: renewalDate
            PaymentMode.AUTO -> parsedNextPaymentDate
        }
        val updated = existing.copy(
            vendorName = request.vendorName.trim(),
            vendorUrl = request.vendorUrl?.trim(),
            vendorLogoUrl = vendorLogoService.resolveLogoUrl(request.vendorName, request.vendorUrl),
            category = request.category.trim().lowercase(),
            description = request.description?.trim(),
            amount = request.amount.toBigDecimal(),
            currency = request.currency.uppercase(),
            billingCycle = request.billingCycle,
            renewalDate = renewalDate,
            contractStartDate = request.contractStartDate?.let(LocalDate::parse),
            autoRenews = request.autoRenews,
            paymentMode = request.paymentMode,
            paymentStatus = when (request.paymentMode) {
                PaymentMode.AUTO -> PaymentStatus.PAID
                PaymentMode.MANUAL -> resolvePaymentStatus(PaymentMode.MANUAL, resolvedNextPaymentDate, clockProvider.nowDate())
            },
            nextPaymentDate = resolvedNextPaymentDate,
            status = request.status,
            tags = request.tags.filter { it.isNotBlank() }.map { it.trim().lowercase() },
            ownerId = request.ownerId?.let(UUID::fromString),
            notes = request.notes?.trim(),
            documentUrl = request.documentUrl?.trim(),
            updatedAt = clockProvider.nowInstant()
        )
        subscriptionRepository.update(updated)
        recordAudit(
            currentUser = currentUser,
            action = AuditAction.UPDATED,
            entityId = updated.id,
            oldValue = """{"vendor":"${existing.vendorName}","amount":"${existing.amount}"}""",
            newValue = """{"vendor":"${updated.vendorName}","amount":"${updated.amount}"}"""
        )
        return AppResult.Success(updated)
    }

    fun markAsPaid(
        currentUser: User,
        subscriptionId: UUID,
        request: MarkSubscriptionPaidRequest
    ): AppResult<Subscription> {
        if (!currentUser.role.canEdit()) {
            return AppResult.Failure(AppError.Forbidden("User has read-only access"))
        }
        val existing = subscriptionRepository.findById(subscriptionId, currentUser.companyId)
            ?: return AppResult.Failure(AppError.NotFound("Subscription not found"))
        if (existing.paymentMode != PaymentMode.MANUAL) {
            return AppResult.Failure(AppError.Validation("Only manual subscriptions can be marked as paid"))
        }

        val paidAt = request.paidAt?.let(LocalDate::parse) ?: clockProvider.nowDate()
        val paidAmount = request.amount?.toBigDecimalOrNull() ?: existing.amount
        if (paidAmount <= BigDecimal.ZERO) {
            return AppResult.Failure(AppError.Validation("Amount must be positive"))
        }

        val nextPaymentDate = incrementByBillingCycle(paidAt, existing.billingCycle)
        val updated = existing.copy(
            paymentStatus = resolvePaymentStatus(PaymentMode.MANUAL, nextPaymentDate, clockProvider.nowDate()),
            lastPaidAt = paidAt,
            nextPaymentDate = nextPaymentDate,
            updatedAt = clockProvider.nowInstant()
        )
        subscriptionRepository.update(updated)

        paymentRepository.create(
            SubscriptionPayment(
                id = idProvider.newId(),
                subscriptionId = existing.id,
                companyId = currentUser.companyId,
                recordedByUserId = currentUser.id,
                amount = paidAmount,
                currency = existing.currency,
                paidAt = paidAt,
                paymentReference = request.paymentReference?.trim()?.ifBlank { null },
                note = request.note?.trim()?.ifBlank { null },
                createdAt = clockProvider.nowInstant()
            )
        )

        recordAudit(
            currentUser = currentUser,
            action = AuditAction.MARKED_PAID,
            entityId = updated.id,
            oldValue = """{"paymentStatus":"${existing.paymentStatus}","nextPaymentDate":"${existing.nextPaymentDate}"}""",
            newValue = """{"paymentStatus":"${updated.paymentStatus}","paidAt":"$paidAt","nextPaymentDate":"$nextPaymentDate"}"""
        )
        return AppResult.Success(updated)
    }

    fun archive(currentUser: User, subscriptionId: UUID): AppResult<Unit> {
        if (!currentUser.role.canEdit()) {
            return AppResult.Failure(AppError.Forbidden("User has read-only access"))
        }
        val existing = subscriptionRepository.findById(subscriptionId, currentUser.companyId)
            ?: return AppResult.Failure(AppError.NotFound("Subscription not found"))
        val now = clockProvider.nowInstant()
        subscriptionRepository.update(existing.copy(archivedAt = now, archivedById = currentUser.id, updatedAt = now))
        recordAudit(
            currentUser = currentUser,
            action = AuditAction.ARCHIVED,
            entityId = existing.id,
            oldValue = """{"status":"${existing.status}","vendorName":"${existing.vendorName}"}""",
            newValue = """{"archivedAt":"$now"}"""
        )
        return AppResult.Success(Unit)
    }

    fun listArchived(companyId: UUID): List<Subscription> = subscriptionRepository.listArchivedByCompany(companyId)

    fun getCategories(companyId: UUID): CategoriesResponse {
        val custom = subscriptionRepository.listByCompany(companyId)
            .map { it.category.lowercase().trim() }
            .distinct()
            .filter { it !in PREDEFINED_CATEGORIES }
            .sorted()
        return CategoriesResponse(predefined = PREDEFINED_CATEGORIES, custom = custom)
    }

    fun detectDuplicates(companyId: UUID): DuplicateDetection {
        val subscriptions = subscriptionRepository.listActiveByCompany(companyId)
        val warnings = mutableListOf<String>()
        var potentialSavings = BigDecimal.ZERO

        subscriptions.groupBy { it.vendorName.lowercase() }
            .filterValues { it.size > 1 }
            .forEach { (vendor, duplicates) ->
                warnings += "Duplicate vendor detected: $vendor (${duplicates.size} subscriptions)"
                potentialSavings += duplicates.minOfOrNull { it.amount } ?: BigDecimal.ZERO
            }

        subscriptions.groupBy { it.category.lowercase() }
            .filterValues { it.size > 1 }
            .forEach { (category, duplicates) ->
                warnings += "Multiple tools in category '$category' (${duplicates.size})"
                potentialSavings += duplicates.minOfOrNull { it.amount } ?: BigDecimal.ZERO
            }

        return DuplicateDetection(warnings.distinct(), potentialSavings)
    }

    fun calculateHealthScore(subscription: Subscription, viewerClock: Clock = Clock.systemUTC()): HealthScore {
        if (subscription.status != SubscriptionStatus.ACTIVE) return HealthScore.CRITICAL
        val daysLeft = daysUntil(subscription.renewalDate, viewerClock)
        val hasOwner = subscription.ownerId != null
        val hasNotes = !subscription.notes.isNullOrBlank()
        val hasTags = !subscription.tags.isNullOrEmpty()
        val score = listOf(daysLeft > 7, hasOwner, hasNotes, hasTags, subscription.autoRenews).count { it }
        if (subscription.paymentMode == PaymentMode.MANUAL && subscription.paymentStatus == PaymentStatus.OVERDUE) {
            return HealthScore.CRITICAL
        }
        return when {
            score >= 4 -> HealthScore.GOOD
            score >= 2 -> HealthScore.WARNING
            else -> HealthScore.CRITICAL
        }
    }

    fun importCsv(currentUser: User, reader: Reader): AppResult<CsvImportResult> {
        if (!currentUser.role.canEdit()) {
            return AppResult.Failure(AppError.Forbidden("User has read-only access"))
        }
        val parser = parseCsv(reader)
        var imported = 0
        var skipped = 0
        val errors = mutableListOf<String>()

        parser.records.forEachIndexed { index, row ->
            runCatching {
                val vendorName = row.get("vendor_name")
                val category = row.get("category")
                val amount = row.get("amount")
                val currency = row.get("currency")
                val billingCycle = row.get("billing_cycle")
                val renewalDate = row.get("renewal_date")
                val request = CreateSubscriptionRequest(
                    vendorName = vendorName,
                    vendorUrl = row.get("vendor_url").ifBlank { null },
                    category = category,
                    amount = amount,
                    currency = currency.ifBlank { "USD" },
                    billingCycle = com.saastracker.domain.model.BillingCycle.valueOf(billingCycle.uppercase()),
                    renewalDate = renewalDate,
                    description = row.get("description").ifBlank { null },
                    contractStartDate = row.get("contract_start_date").ifBlank { null },
                    autoRenews = row.get("auto_renews").ifBlank { "true" }.toBoolean(),
                    paymentMode = runCatching { row.get("payment_mode") }.getOrNull().orEmpty().ifBlank { "AUTO" }
                        .uppercase()
                        .let(PaymentMode::valueOf),
                    nextPaymentDate = runCatching { row.get("next_payment_date") }.getOrNull().orEmpty().ifBlank { null },
                    status = row.get("status").ifBlank { "ACTIVE" }
                        .uppercase()
                        .let(com.saastracker.domain.model.SubscriptionStatus::valueOf),
                    tags = row.get("tags").split("|").map(String::trim).filter(String::isNotBlank),
                    ownerId = row.get("owner_id").ifBlank { null },
                    notes = row.get("notes").ifBlank { null },
                    documentUrl = row.get("document_url").ifBlank { null }
                )
                when (val result = create(currentUser, request)) {
                    is AppResult.Success -> imported += 1
                    is AppResult.Failure -> {
                        skipped += 1
                        errors += "Row ${index + 2}: ${result.error.message}"
                    }
                }
            }.onFailure {
                skipped += 1
                errors += "Row ${index + 2}: ${it.message ?: "Invalid data"}"
            }
        }
        return AppResult.Success(CsvImportResult(imported = imported, skipped = skipped, errors = errors))
    }

    fun addComment(currentUser: User, subscriptionId: UUID, body: String): AppResult<SubscriptionComment> {
        if (body.isBlank()) return AppResult.Failure(AppError.Validation("Comment body is empty"))
        val subscription = subscriptionRepository.findById(subscriptionId, currentUser.companyId)
            ?: return AppResult.Failure(AppError.NotFound("Subscription not found"))
        val comment = SubscriptionComment(
            id = idProvider.newId(),
            subscriptionId = subscription.id,
            companyId = currentUser.companyId,
            userId = currentUser.id,
            body = body.trim(),
            createdAt = clockProvider.nowInstant()
        )
        commentRepository.create(comment)
        recordAudit(
            currentUser = currentUser,
            action = AuditAction.CREATED,
            entityId = comment.id,
            entityType = AuditEntityType.COMMENT,
            oldValue = null,
            newValue = """{"subscription_id":"${subscription.id}","body":"${comment.body}"}"""
        )
        return AppResult.Success(comment)
    }

    fun listComments(currentUser: User, subscriptionId: UUID): AppResult<List<SubscriptionComment>> {
        val subscription = subscriptionRepository.findById(subscriptionId, currentUser.companyId)
            ?: return AppResult.Failure(AppError.NotFound("Subscription not found"))
        return AppResult.Success(commentRepository.listBySubscription(currentUser.companyId, subscription.id))
    }

    fun normalizedMonthlyUsd(subscription: Subscription): BigDecimal {
        val monthlyLocal = normalizeToMonthly(subscription.amount, subscription.billingCycle)
        return currencyService.toUsd(monthlyLocal, subscription.currency)
    }

    fun exportCsv(companyId: UUID, filter: SubscriptionFilter): String {
        val subscriptions = subscriptionRepository.listByCompany(companyId).applyFilter(filter)
        val header = "vendor_name,vendor_url,category,amount,currency,billing_cycle,renewal_date," +
            "description,contract_start_date,auto_renews,payment_mode,next_payment_date,status,tags,notes,document_url"
        val rows = subscriptions.joinToString("\n") { sub ->
            listOf(
                sub.vendorName.csvEscape(),
                (sub.vendorUrl ?: "").csvEscape(),
                sub.category.csvEscape(),
                sub.amount.toPlainString(),
                sub.currency,
                sub.billingCycle.name,
                sub.renewalDate.toString(),
                (sub.description ?: "").csvEscape(),
                (sub.contractStartDate?.toString() ?: ""),
                sub.autoRenews.toString(),
                sub.paymentMode.name,
                (sub.nextPaymentDate?.toString() ?: ""),
                sub.status.name,
                sub.tags.orEmpty().joinToString("|").csvEscape(),
                (sub.notes ?: "").csvEscape(),
                (sub.documentUrl ?: "").csvEscape()
            ).joinToString(",")
        }
        return if (rows.isEmpty()) header else "$header\n$rows"
    }

    fun exportPdf(companyId: UUID, companyName: String, filter: SubscriptionFilter): ByteArray {
        val subscriptions = subscriptionRepository.listByCompany(companyId).applyFilter(filter)
        val doc = PDDocument()
        val boldFont = PDType1Font(Standard14Fonts.FontName.HELVETICA_BOLD)
        val normalFont = PDType1Font(Standard14Fonts.FontName.HELVETICA)

        // Fixed X positions for each column — proportional fonts require absolute coords
        val colX = listOf(50f, 200f, 308f, 358f, 428f, 500f)

        fun PDPageContentStream.cell(text: String, x: Float, y: Float) {
            beginText()
            newLineAtOffset(x, y)
            showText(text)
            endText()
        }

        fun newPage(): PDPageContentStream {
            val page = PDPage(PDRectangle.A4)
            doc.addPage(page)
            return PDPageContentStream(doc, page)
        }

        var cs = newPage()

        cs.setFont(boldFont, 16f)
        cs.cell("$companyName — Subscription Report", 50f, 780f)

        cs.setFont(normalFont, 10f)
        cs.cell("Generated: ${LocalDate.now()}", 50f, 760f)

        cs.setFont(boldFont, 9f)
        listOf("Vendor", "Category", "Currency", "Amount", "Renewal", "Status")
            .forEachIndexed { i, header -> cs.cell(header, colX[i], 730f) }

        var y = 710f
        cs.setFont(normalFont, 9f)

        for (sub in subscriptions) {
            if (y < 50f) {
                cs.close()
                cs = newPage()
                cs.setFont(normalFont, 9f)
                y = 780f
            }
            listOf(
                sub.vendorName.take(26),
                sub.category.take(14),
                sub.currency,
                sub.amount.toPlainString(),
                sub.renewalDate.toString(),
                sub.status.name
            ).forEachIndexed { i, cell -> cs.cell(cell, colX[i], y) }
            y -= 18f
        }

        cs.close()

        val out = ByteArrayOutputStream()
        doc.save(out)
        doc.close()
        return out.toByteArray()
    }

    private fun List<Subscription>.applyFilter(f: SubscriptionFilter): List<Subscription> =
        filter { sub ->
            (f.category == null || sub.category.equals(f.category, ignoreCase = true)) &&
            (f.status == null || sub.status == f.status) &&
            (f.vendorName == null || sub.vendorName.contains(f.vendorName, ignoreCase = true)) &&
            (f.paymentMode == null || sub.paymentMode == f.paymentMode) &&
            (f.minAmount == null || sub.amount >= f.minAmount) &&
            (f.maxAmount == null || sub.amount <= f.maxAmount)
        }

    private fun recordAudit(
        currentUser: User,
        action: AuditAction,
        entityId: UUID,
        oldValue: String?,
        newValue: String?,
        entityType: AuditEntityType = AuditEntityType.SUBSCRIPTION
    ) {
        auditLogRepository.append(
            AuditLogEntry(
                id = idProvider.newId(),
                companyId = currentUser.companyId,
                userId = currentUser.id,
                action = action,
                entityType = entityType,
                entityId = entityId,
                oldValue = oldValue,
                newValue = newValue,
                createdAt = clockProvider.nowInstant()
            )
        )
    }

    private fun resolvePaymentStatus(paymentMode: PaymentMode, nextPaymentDate: LocalDate?, today: LocalDate): PaymentStatus =
        when (paymentMode) {
            PaymentMode.AUTO -> PaymentStatus.PAID
            PaymentMode.MANUAL -> if (nextPaymentDate != null && nextPaymentDate.isBefore(today)) PaymentStatus.OVERDUE else PaymentStatus.PENDING
        }

    private fun incrementByBillingCycle(start: LocalDate, cycle: com.saastracker.domain.model.BillingCycle): LocalDate =
        when (cycle) {
            com.saastracker.domain.model.BillingCycle.MONTHLY -> start.plusMonths(1)
            com.saastracker.domain.model.BillingCycle.QUARTERLY -> start.plusMonths(3)
            com.saastracker.domain.model.BillingCycle.ANNUAL -> start.plusYears(1)
        }
}
