package com.saastracker.transport.http.response

import com.saastracker.domain.model.BillingCycle
import com.saastracker.domain.model.PaymentMode
import com.saastracker.domain.model.PaymentStatus
import com.saastracker.domain.model.SubscriptionStatus
import kotlinx.serialization.Serializable

@Serializable
data class SubscriptionResponse(
    val id: String,
    val vendorName: String,
    val vendorUrl: String? = null,
    val vendorLogoUrl: String? = null,
    val category: String,
    val description: String? = null,
    val amount: String,
    val currency: String,
    val billingCycle: BillingCycle,
    val renewalDate: String,
    val contractStartDate: String? = null,
    val autoRenews: Boolean,
    val paymentMode: PaymentMode,
    val paymentStatus: PaymentStatus,
    val lastPaidAt: String? = null,
    val nextPaymentDate: String? = null,
    val status: SubscriptionStatus,
    val tags: List<String>,
    val ownerId: String? = null,
    val notes: String? = null,
    val documentUrl: String? = null,
    val healthScore: String,
    val duplicateWarnings: List<String>,
    val lastUsedAt: String? = null,
    val isZombie: Boolean = false,
    val createdAt: String,
    val updatedAt: String
)

@Serializable
data class SubscriptionListResponse(
    val items: List<SubscriptionResponse>
)

@Serializable
data class PagedSubscriptionListResponse(
    val items: List<SubscriptionResponse>,
    val total: Long,
    val page: Int,
    val size: Int,
    val totalPages: Int
)

@Serializable
data class CsvImportResultResponse(
    val imported: Int,
    val skipped: Int,
    val errors: List<String>
)

@Serializable
data class CommentResponse(
    val id: String,
    val subscriptionId: String,
    val userId: String,
    val body: String,
    val createdAt: String
)

@Serializable
data class CategoriesResponse(
    val predefined: List<String>,
    val custom: List<String>
)

@Serializable
data class BatchCreateResponse(
    val created: Int,
    val skipped: Int,
    val reason: String? = null,
    val requiredPlan: String? = null,
    val subscriptions: List<SubscriptionResponse>
)
