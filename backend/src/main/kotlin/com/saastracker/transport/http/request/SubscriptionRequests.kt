package com.saastracker.transport.http.request

import com.saastracker.domain.model.BillingCycle
import com.saastracker.domain.model.PaymentMode
import com.saastracker.domain.model.SubscriptionStatus
import kotlinx.serialization.Serializable

@Serializable
data class CreateSubscriptionRequest(
    val vendorName: String,
    val vendorUrl: String? = null,
    val category: String,
    val description: String? = null,
    val amount: String,
    val currency: String = "USD",
    val billingCycle: BillingCycle,
    val renewalDate: String,
    val contractStartDate: String? = null,
    val autoRenews: Boolean = true,
    val paymentMode: PaymentMode = PaymentMode.AUTO,
    val nextPaymentDate: String? = null,
    val status: SubscriptionStatus = SubscriptionStatus.ACTIVE,
    val tags: List<String> = emptyList(),
    val ownerId: String? = null,
    val notes: String? = null,
    val documentUrl: String? = null
)

@Serializable
data class UpdateSubscriptionRequest(
    val vendorName: String,
    val vendorUrl: String? = null,
    val category: String,
    val description: String? = null,
    val amount: String,
    val currency: String = "USD",
    val billingCycle: BillingCycle,
    val renewalDate: String,
    val contractStartDate: String? = null,
    val autoRenews: Boolean = true,
    val paymentMode: PaymentMode = PaymentMode.AUTO,
    val nextPaymentDate: String? = null,
    val status: SubscriptionStatus = SubscriptionStatus.ACTIVE,
    val tags: List<String> = emptyList(),
    val ownerId: String? = null,
    val notes: String? = null,
    val documentUrl: String? = null
)

@Serializable
data class AddCommentRequest(
    val body: String
)

@Serializable
data class MarkSubscriptionPaidRequest(
    val paidAt: String? = null,
    val amount: String? = null,
    val paymentReference: String? = null,
    val note: String? = null
)

@Serializable
data class AnalyticsQueryRequest(
    val employeeCount: Int? = null
)
