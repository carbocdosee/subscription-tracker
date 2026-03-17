package com.saastracker.domain.dto

import com.saastracker.domain.model.PaymentMode
import com.saastracker.domain.model.SubscriptionStatus
import java.math.BigDecimal

data class SubscriptionFilter(
    val vendorName: String? = null,
    val category: String? = null,
    val status: SubscriptionStatus? = null,
    val paymentMode: PaymentMode? = null,
    val minAmount: BigDecimal? = null,
    val maxAmount: BigDecimal? = null
)

data class PageRequest(
    val page: Int = 1,
    val size: Int = 25,
    val sortBy: String = "renewal_date",
    val sortDir: String = "asc"
)

data class Page<T>(
    val items: List<T>,
    val total: Long,
    val page: Int,
    val size: Int
) {
    val totalPages: Int get() = if (size <= 0) 0 else ((total + size - 1) / size).toInt()
}
