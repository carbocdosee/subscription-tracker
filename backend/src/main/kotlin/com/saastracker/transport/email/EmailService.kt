package com.saastracker.transport.email

import com.saastracker.config.ResendConfig
import com.saastracker.domain.model.Company
import com.saastracker.domain.model.Subscription
import io.ktor.client.HttpClient
import io.ktor.client.call.body
import io.ktor.client.request.header
import io.ktor.client.request.post
import io.ktor.client.request.setBody
import io.ktor.http.ContentType
import io.ktor.http.contentType
import kotlinx.serialization.Serializable
import kotlinx.coroutines.runBlocking
import kotlinx.serialization.json.Json
import org.slf4j.LoggerFactory
import java.math.BigDecimal

data class SubscriptionWithDaysLeft(
    val subscription: Subscription,
    val daysLeft: Int
)

data class EmailContent(
    val subject: String,
    val html: String
)

enum class EmailDeliveryStatus {
    SENT,
    SKIPPED_NOT_CONFIGURED,
    FAILED
}

data class EmailDeliveryResult(
    val status: EmailDeliveryStatus,
    val message: String? = null,
    val providerMessageId: String? = null,
    val providerStatusCode: Int? = null,
    val providerResponse: String? = null
)

interface EmailService {
    fun sendRenewalDigest(
        company: Company,
        recipients: List<String>,
        subscriptions: List<SubscriptionWithDaysLeft>
    )

    fun sendTeamInviteEmail(email: String, token: String): EmailDeliveryResult

    fun sendBudgetAlertEmail(
        to: String,
        companyName: String,
        thresholdPercent: Int,
        currentSpend: BigDecimal,
        budget: BigDecimal
    )

    fun sendPasswordResetEmail(email: String, token: String): EmailDeliveryResult

    fun sendWeeklyDigest(
        company: Company,
        recipients: List<String>,
        renewals: List<SubscriptionWithDaysLeft>,
        zombies: List<Subscription>,
        monthlySpend: BigDecimal
    )
}

class ResendEmailService(
    private val config: ResendConfig,
    private val httpClient: HttpClient
) : EmailService {
    private val logger = LoggerFactory.getLogger(javaClass)
    private val json = Json { ignoreUnknownKeys = true }

    override fun sendRenewalDigest(
        company: Company,
        recipients: List<String>,
        subscriptions: List<SubscriptionWithDaysLeft>
    ) {
        if (recipients.isEmpty() || subscriptions.isEmpty()) return
        val content = buildRenewalDigestEmail(company, subscriptions)
        sendEmail(
            recipients = recipients,
            subject = content.subject,
            html = content.html
        )
    }

    override fun sendTeamInviteEmail(email: String, token: String): EmailDeliveryResult {
        val inviteLink = "${config.frontendBaseUrl}/accept-invite?token=$token"
        val html = """
            <html>
              <body style="font-family:Arial,sans-serif;background:#f7f9fc;padding:24px;">
                <div style="max-width:620px;margin:0 auto;background:#fff;padding:24px;border-radius:12px;">
                  <h2 style="margin:0 0 12px 0;color:#0f172a;">You've been invited to SaaS Subscription Tracker</h2>
                  <p style="color:#334155;">Click the button below to join your company workspace.</p>
                  <a href="$inviteLink" style="display:inline-block;background:#0ea5e9;color:#fff;padding:12px 18px;border-radius:8px;text-decoration:none;">
                    Accept invite
                  </a>
                  <p style="font-size:12px;color:#64748b;margin-top:18px;">This invitation expires in 2 days.</p>
                </div>
              </body>
            </html>
        """.trimIndent()
        return sendEmail(
            recipients = listOf(email),
            subject = "Team invitation",
            html = html
        )
    }

    override fun sendBudgetAlertEmail(
        to: String,
        companyName: String,
        thresholdPercent: Int,
        currentSpend: BigDecimal,
        budget: BigDecimal
    ) {
        val html = buildBudgetAlertEmail(companyName, thresholdPercent, currentSpend, budget)
        sendEmail(
            recipients = listOf(to),
            subject = "⚠️ Budget Alert: ${companyName.sanitizeEmailSubject()} has reached $thresholdPercent% of monthly budget",
            html = html
        )
    }

    override fun sendPasswordResetEmail(email: String, token: String): EmailDeliveryResult {
        val resetLink = "${config.frontendBaseUrl}/reset-password?token=$token"
        val html = buildPasswordResetEmail(resetLink)
        return sendEmail(
            recipients = listOf(email),
            subject = "Reset your password",
            html = html
        )
    }

    override fun sendWeeklyDigest(
        company: Company,
        recipients: List<String>,
        renewals: List<SubscriptionWithDaysLeft>,
        zombies: List<Subscription>,
        monthlySpend: BigDecimal
    ) {
        if (recipients.isEmpty()) return
        val content = buildWeeklyDigestEmail(company, renewals, zombies, monthlySpend)
        sendEmail(recipients = recipients, subject = content.subject, html = content.html)
    }

    private fun sendEmail(recipients: List<String>, subject: String, html: String): EmailDeliveryResult {
        if (config.apiKey.isNullOrBlank()) {
            logger.warn("RESEND_API_KEY is empty, email skipped subject={}", subject)
            return EmailDeliveryResult(
                status = EmailDeliveryStatus.SKIPPED_NOT_CONFIGURED,
                message = "RESEND_API_KEY is empty",
                providerResponse = "Email provider configuration is missing"
            )
        }
        val request = ResendEmailRequest(
            from = config.fromEmail,
            to = recipients,
            subject = subject,
            html = html
        )
        return runBlocking {
            runCatching {
                val response = httpClient.post("${config.baseUrl}/emails") {
                    contentType(ContentType.Application.Json)
                    header("Authorization", "Bearer ${config.apiKey}")
                    setBody(request)
                }
                val responseBody = response.body<String>()
                if (response.status.value !in 200..299) {
                    logger.error("Resend API failed status={} body={}", response.status.value, responseBody)
                    EmailDeliveryResult(
                        status = EmailDeliveryStatus.FAILED,
                        message = "Resend returned ${response.status.value}",
                        providerStatusCode = response.status.value,
                        providerResponse = responseBody
                    )
                } else {
                    val messageId = runCatching {
                        json.decodeFromString<ResendEmailResponse>(responseBody).id
                    }.getOrNull()
                    EmailDeliveryResult(
                        status = EmailDeliveryStatus.SENT,
                        message = "Delivered by Resend",
                        providerMessageId = messageId,
                        providerStatusCode = response.status.value,
                        providerResponse = responseBody
                    )
                }
            }.onFailure {
                logger.error("Failed to send email via Resend", it)
            }.getOrElse { ex ->
                EmailDeliveryResult(
                    status = EmailDeliveryStatus.FAILED,
                    message = ex.message ?: "Unknown provider error",
                    providerResponse = "Email delivery error"
                )
            }
        }
    }
}

/**
 * Builds renewal digest HTML with grouped cards and actions.
 */
fun buildRenewalDigestEmail(
    company: Company,
    subscriptions: List<SubscriptionWithDaysLeft>
): EmailContent {
    val totalDue = subscriptions.fold(BigDecimal.ZERO) { acc, item -> acc + item.subscription.amount }
    val rows = subscriptions.joinToString(separator = "") { item ->
        val subscription = item.subscription
        val reviewLink = "https://app.example.com/subscriptions/${subscription.id}"
        val reminderLink = "$reviewLink/reminder"
        val cancelLink = "$reviewLink/cancel"
        """
            <tr>
              <td style="padding:14px;border-bottom:1px solid #e2e8f0;">
                <!-- Vendor logo only shown when a URL is stored; no external tracking calls -->
                ${if (subscription.vendorLogoUrl != null) """<img src="${subscription.vendorLogoUrl.escapeHtml()}" alt="${subscription.vendorName.escapeHtml()}" style="width:24px;height:24px;border-radius:4px;vertical-align:middle;margin-right:8px;"/>""" else ""}
                <strong>${subscription.vendorName.escapeHtml()}</strong>
              </td>
              <td style="padding:14px;border-bottom:1px solid #e2e8f0;">${subscription.currency} ${subscription.amount}</td>
              <td style="padding:14px;border-bottom:1px solid #e2e8f0;">${subscription.renewalDate}</td>
              <td style="padding:14px;border-bottom:1px solid #e2e8f0;">${item.daysLeft} days</td>
              <td style="padding:14px;border-bottom:1px solid #e2e8f0;">
                <a href="$reviewLink" style="color:#0284c7;text-decoration:none;margin-right:8px;">Review</a>
                <a href="$reminderLink" style="color:#0284c7;text-decoration:none;margin-right:8px;">Set Reminder</a>
                <a href="$cancelLink" style="color:#dc2626;text-decoration:none;">Cancel</a>
              </td>
            </tr>
        """.trimIndent()
    }
    val html = """
        <!doctype html>
        <html lang="en">
          <head>
            <meta charset="utf-8" />
            <meta name="viewport" content="width=device-width, initial-scale=1" />
            <title>Renewal digest</title>
          </head>
          <body style="margin:0;padding:24px;background:#eef2ff;font-family:'Segoe UI',Arial,sans-serif;">
            <div style="max-width:760px;margin:0 auto;background:white;border-radius:16px;padding:24px;">
              <div style="display:flex;align-items:center;justify-content:space-between;">
                <div>
                  <h1 style="margin:0;font-size:24px;color:#0f172a;">Renewal Digest</h1>
                  <p style="margin:6px 0 0 0;color:#475569;">${company.name.escapeHtml()}</p>
                </div>
                <img src="https://dummyimage.com/120x36/0ea5e9/ffffff&text=${company.name.take(12).escapeHtml()}" alt="${company.name.escapeHtml()}"/>
              </div>
              <div style="margin-top:18px;padding:14px;background:#ecfeff;border-radius:10px;color:#0c4a6e;">
                ${subscriptions.size} subscriptions renewing soon, total due <strong>USD $totalDue</strong>
              </div>
              <table style="width:100%;margin-top:16px;border-collapse:collapse;font-size:14px;">
                <thead>
                  <tr style="background:#f8fafc;color:#334155;text-align:left;">
                    <th style="padding:12px;">Vendor</th>
                    <th style="padding:12px;">Amount</th>
                    <th style="padding:12px;">Renewal Date</th>
                    <th style="padding:12px;">Days Left</th>
                    <th style="padding:12px;">Actions</th>
                  </tr>
                </thead>
                <tbody>
                  $rows
                </tbody>
              </table>
              <p style="margin-top:20px;font-size:12px;color:#64748b;">
                You are receiving this email because renewal alerts are enabled.
                <a href="https://app.example.com/settings/notifications/unsubscribe" style="color:#0284c7;">Unsubscribe</a>
              </p>
            </div>
          </body>
        </html>
    """.trimIndent()
    return EmailContent(
        subject = "Renewal alert: ${subscriptions.size} subscription(s) pending",
        html = html
    )
}

fun buildWeeklyDigestEmail(
    company: Company,
    renewals: List<SubscriptionWithDaysLeft>,
    zombies: List<Subscription>,
    monthlySpend: BigDecimal
): EmailContent {
    val renewalRows = renewals.joinToString("") { item ->
        val s = item.subscription
        val logoHtml = if (s.vendorLogoUrl != null)
            """<img src="${s.vendorLogoUrl.escapeHtml()}" alt="" style="width:20px;height:20px;border-radius:4px;vertical-align:middle;margin-right:6px;"/>"""
        else ""
        """<tr>
          <td style="padding:10px 12px;border-bottom:1px solid #e2e8f0;">$logoHtml${s.vendorName.escapeHtml()}</td>
          <td style="padding:10px 12px;border-bottom:1px solid #e2e8f0;">${s.currency} ${s.amount}</td>
          <td style="padding:10px 12px;border-bottom:1px solid #e2e8f0;">${s.renewalDate}</td>
          <td style="padding:10px 12px;border-bottom:1px solid #e2e8f0;color:#0ea5e9;font-weight:600;">${item.daysLeft}d</td>
        </tr>"""
    }
    val zombieRows = zombies.joinToString("") { s ->
        val logoHtml = if (s.vendorLogoUrl != null)
            """<img src="${s.vendorLogoUrl.escapeHtml()}" alt="" style="width:20px;height:20px;border-radius:4px;vertical-align:middle;margin-right:6px;"/>"""
        else ""
        """<tr>
          <td style="padding:10px 12px;border-bottom:1px solid #e2e8f0;">$logoHtml${s.vendorName.escapeHtml()}</td>
          <td style="padding:10px 12px;border-bottom:1px solid #e2e8f0;">${s.currency} ${s.amount}</td>
          <td style="padding:10px 12px;border-bottom:1px solid #e2e8f0;color:#64748b;">${s.category.escapeHtml()}</td>
        </tr>"""
    }
    val renewalSection = if (renewals.isNotEmpty()) """
        <h2 style="font-size:16px;color:#0f172a;margin:24px 0 10px;">Upcoming renewals (${renewals.size})</h2>
        <table style="width:100%;border-collapse:collapse;font-size:13px;">
          <thead><tr style="background:#f8fafc;color:#334155;text-align:left;">
            <th style="padding:10px 12px;">Vendor</th>
            <th style="padding:10px 12px;">Amount</th>
            <th style="padding:10px 12px;">Renewal</th>
            <th style="padding:10px 12px;">Days left</th>
          </tr></thead>
          <tbody>$renewalRows</tbody>
        </table>""" else ""
    val zombieSection = if (zombies.isNotEmpty()) """
        <h2 style="font-size:16px;color:#0f172a;margin:24px 0 10px;">Zombie subscriptions — review &amp; cancel (${zombies.size})</h2>
        <table style="width:100%;border-collapse:collapse;font-size:13px;">
          <thead><tr style="background:#fef9f0;color:#334155;text-align:left;">
            <th style="padding:10px 12px;">Vendor</th>
            <th style="padding:10px 12px;">Amount</th>
            <th style="padding:10px 12px;">Category</th>
          </tr></thead>
          <tbody>$zombieRows</tbody>
        </table>""" else ""
    val html = """<!doctype html>
<html lang="en">
  <head><meta charset="utf-8"/><meta name="viewport" content="width=device-width,initial-scale=1"/><title>Weekly SaaS Digest</title></head>
  <body style="margin:0;padding:24px;background:#eef2ff;font-family:'Segoe UI',Arial,sans-serif;">
    <div style="max-width:760px;margin:0 auto;background:#fff;border-radius:16px;padding:28px;">
      <h1 style="margin:0 0 4px;font-size:22px;color:#0f172a;">Weekly SaaS Digest</h1>
      <p style="margin:0 0 20px;color:#64748b;">${company.name.escapeHtml()}</p>
      <div style="display:flex;gap:16px;flex-wrap:wrap;margin-bottom:8px;">
        <div style="flex:1;min-width:140px;background:#f8fafc;border-radius:10px;padding:16px;">
          <div style="font-size:12px;color:#64748b;margin-bottom:4px;">Monthly spend</div>
          <div style="font-size:20px;font-weight:700;color:#0f172a;">USD ${monthlySpend}</div>
        </div>
        <div style="flex:1;min-width:140px;background:#fef2f2;border-radius:10px;padding:16px;">
          <div style="font-size:12px;color:#64748b;margin-bottom:4px;">Zombie tools</div>
          <div style="font-size:20px;font-weight:700;color:#dc2626;">${zombies.size}</div>
        </div>
        <div style="flex:1;min-width:140px;background:#f0fdf4;border-radius:10px;padding:16px;">
          <div style="font-size:12px;color:#64748b;margin-bottom:4px;">Renewing soon</div>
          <div style="font-size:20px;font-weight:700;color:#16a34a;">${renewals.size}</div>
        </div>
      </div>
      $renewalSection
      $zombieSection
      <p style="margin-top:24px;font-size:12px;color:#94a3b8;">
        You receive this digest because weekly summaries are enabled for your workspace.
        To unsubscribe, go to <strong>Settings → Account → Notifications</strong>.
      </p>
    </div>
  </body>
</html>"""
    val subject = buildString {
        append("[${company.name.sanitizeEmailSubject()}] Weekly digest")
        if (zombies.isNotEmpty()) append(" · ${zombies.size} zombie tool${if (zombies.size > 1) "s" else ""}")
        if (renewals.isNotEmpty()) append(" · ${renewals.size} renewing soon")
    }
    return EmailContent(subject = subject, html = html)
}

fun buildBudgetAlertEmail(
    companyName: String,
    thresholdPercent: Int,
    currentSpend: BigDecimal,
    budget: BigDecimal
): String {
    val color = if (thresholdPercent >= 100) "#dc2626" else "#f59e0b"
    val badge = if (thresholdPercent >= 100) "OVER BUDGET" else "BUDGET WARNING"
    return """
        <!doctype html>
        <html lang="en">
          <head>
            <meta charset="utf-8" />
            <meta name="viewport" content="width=device-width, initial-scale=1" />
            <title>Budget Alert</title>
          </head>
          <body style="margin:0;padding:24px;background:#eef2ff;font-family:'Segoe UI',Arial,sans-serif;">
            <div style="max-width:620px;margin:0 auto;background:white;border-radius:16px;padding:24px;">
              <div style="display:flex;align-items:center;gap:12px;margin-bottom:16px;">
                <span style="background:$color;color:#fff;font-size:12px;font-weight:700;padding:4px 10px;border-radius:6px;">$badge</span>
                <h1 style="margin:0;font-size:20px;color:#0f172a;">Budget Alert</h1>
              </div>
              <p style="color:#334155;margin:0 0 16px 0;">
                <strong>${companyName.escapeHtml()}</strong> has reached <strong>$thresholdPercent%</strong> of the monthly subscription budget.
              </p>
              <table style="width:100%;border-collapse:collapse;font-size:14px;margin-bottom:16px;">
                <tr style="background:#f8fafc;">
                  <td style="padding:12px;color:#64748b;">Current Monthly Spend</td>
                  <td style="padding:12px;font-weight:600;color:#0f172a;">USD $currentSpend</td>
                </tr>
                <tr>
                  <td style="padding:12px;color:#64748b;">Monthly Budget</td>
                  <td style="padding:12px;font-weight:600;color:#0f172a;">USD $budget</td>
                </tr>
                <tr style="background:#f8fafc;">
                  <td style="padding:12px;color:#64748b;">Utilization</td>
                  <td style="padding:12px;font-weight:700;color:$color;">$thresholdPercent%</td>
                </tr>
              </table>
              <p style="font-size:12px;color:#64748b;margin:0;">
                Review your subscriptions to manage spending.
              </p>
            </div>
          </body>
        </html>
    """.trimIndent()
}

fun buildPasswordResetEmail(resetLink: String): String = """
    <!doctype html>
    <html lang="en">
      <head>
        <meta charset="utf-8" />
        <meta name="viewport" content="width=device-width, initial-scale=1" />
        <title>Reset your password</title>
      </head>
      <body style="margin:0;padding:24px;background:#eef2ff;font-family:'Segoe UI',Arial,sans-serif;">
        <div style="max-width:620px;margin:0 auto;background:white;border-radius:16px;padding:24px;">
          <h1 style="margin:0 0 12px 0;font-size:20px;color:#0f172a;">Reset your password</h1>
          <p style="color:#334155;margin:0 0 20px 0;">
            We received a request to reset the password for your SaaS Subscription Tracker account.
            Click the button below to choose a new password. This link expires in 1 hour.
          </p>
          <a href="$resetLink" style="display:inline-block;background:#0ea5e9;color:#fff;padding:12px 20px;border-radius:8px;text-decoration:none;font-weight:600;">
            Reset password
          </a>
          <p style="font-size:12px;color:#64748b;margin-top:20px;">
            If you did not request a password reset, you can safely ignore this email.
            Your password will not change.
          </p>
        </div>
      </body>
    </html>
""".trimIndent()

// Escapes HTML special characters to prevent injection in email HTML bodies.
internal fun String.escapeHtml(): String =
    replace("&", "&amp;")
        .replace("<", "&lt;")
        .replace(">", "&gt;")
        .replace("\"", "&quot;")

// Strips CRLF characters to prevent email header injection in subject lines.
internal fun String.sanitizeEmailSubject(): String = replace(Regex("[\r\n]"), "")

@Serializable
private data class ResendEmailRequest(
    val from: String,
    val to: List<String>,
    val subject: String,
    val html: String
)

@Serializable
private data class ResendEmailResponse(
    val id: String? = null
)
