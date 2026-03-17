package com.saastracker.domain.service

import java.net.URI

class VendorLogoService {
    fun resolveLogoUrl(vendorName: String, vendorUrl: String?): String? {
        val domain = vendorUrl?.let(::extractDomain)
            ?: vendorName.lowercase().replace(Regex("[^a-z0-9]"), "")
                .takeIf { it.isNotBlank() }
                ?.let { "$it.com" }
        return domain?.let { "https://logo.clearbit.com/$it" }
    }

    private fun extractDomain(url: String): String? = runCatching {
        val normalized = if (url.startsWith("http")) url else "https://$url"
        URI(normalized).host?.removePrefix("www.")
    }.getOrNull()
}

