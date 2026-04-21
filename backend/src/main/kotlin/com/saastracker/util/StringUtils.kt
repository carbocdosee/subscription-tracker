package com.saastracker.util

private val LOG_UNSAFE = Regex("""[\r\n\t\u0000-\u001F\u007F]""")
private const val LOG_MAX_LEN = 512

/**
 * Sanitizes a string for safe inclusion in log output.
 * Replaces control characters (including CRLF) with escape sequences
 * to prevent log injection, and truncates to [LOG_MAX_LEN] chars.
 */
fun String.sanitizeForLog(): String {
    val truncated = if (length > LOG_MAX_LEN) take(LOG_MAX_LEN) + "…" else this
    return LOG_UNSAFE.replace(truncated) { match ->
        when (match.value) {
            "\r" -> "\\r"
            "\n" -> "\\n"
            "\t" -> "\\t"
            else -> "\\x${match.value[0].code.toString(16).padStart(2, '0')}"
        }
    }
}

fun levenshteinDistance(a: String, b: String): Int {
    val la = a.length
    val lb = b.length
    if (la == 0) return lb
    if (lb == 0) return la

    val dp = Array(la + 1) { IntArray(lb + 1) }
    for (i in 0..la) dp[i][0] = i
    for (j in 0..lb) dp[0][j] = j

    for (i in 1..la) {
        for (j in 1..lb) {
            val cost = if (a[i - 1] == b[j - 1]) 0 else 1
            dp[i][j] = minOf(
                dp[i - 1][j] + 1,
                dp[i][j - 1] + 1,
                dp[i - 1][j - 1] + cost
            )
        }
    }
    return dp[la][lb]
}

fun levenshteinSimilarity(a: String, b: String): Double {
    val maxLen = maxOf(a.length, b.length)
    if (maxLen == 0) return 1.0
    return 1.0 - levenshteinDistance(a, b).toDouble() / maxLen
}
