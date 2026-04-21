package com.saastracker.transport.email

import com.saastracker.config.SmtpConfig
import com.saastracker.domain.model.Company
import com.saastracker.domain.model.Subscription
import jakarta.mail.Authenticator
import java.math.BigDecimal
import jakarta.mail.Message
import jakarta.mail.MessagingException
import jakarta.mail.PasswordAuthentication
import jakarta.mail.Session
import jakarta.mail.Transport
import jakarta.mail.internet.InternetAddress
import jakarta.mail.internet.MimeBodyPart
import jakarta.mail.internet.MimeMessage
import jakarta.mail.internet.MimeMultipart
import org.slf4j.LoggerFactory
import java.util.Properties

class SmtpEmailService(
    private val config: SmtpConfig
) : EmailService {
    private val logger = LoggerFactory.getLogger(javaClass)

    override fun sendRenewalDigest(
        company: Company,
        recipients: List<String>,
        subscriptions: List<SubscriptionWithDaysLeft>
    ) {
        if (recipients.isEmpty() || subscriptions.isEmpty()) {
            return
        }
        val content = buildRenewalDigestEmail(company, subscriptions)
        sendEmail(
            recipients = recipients,
            subject = content.subject,
            html = content.html
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
        return sendEmail(
            recipients = listOf(email),
            subject = "Reset your password",
            html = buildPasswordResetEmail(resetLink)
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

    private fun sendEmail(recipients: List<String>, subject: String, html: String): EmailDeliveryResult {
        val missing = missingConfiguration()
        if (missing.isNotEmpty()) {
            val message = "SMTP configuration missing: ${missing.joinToString(", ")}"
            logger.warn("{} subject={}", message, subject)
            return EmailDeliveryResult(
                status = EmailDeliveryStatus.SKIPPED_NOT_CONFIGURED,
                message = message,
                providerResponse = "Email provider configuration is missing"
            )
        }

        return runCatching {
            val session = createSession()
            val message = MimeMessage(session).apply {
                val from = if (config.fromName.isBlank()) {
                    InternetAddress(config.fromEmail)
                } else {
                    InternetAddress(config.fromEmail, config.fromName)
                }
                setFrom(from)
                setRecipients(
                    Message.RecipientType.TO,
                    recipients.map { InternetAddress(it) }.toTypedArray()
                )
                setSubject(subject, Charsets.UTF_8.name())
                setContent(buildMultipartBody(html))
            }
            Transport.send(message)
            EmailDeliveryResult(
                status = EmailDeliveryStatus.SENT,
                message = "Delivered by SMTP",
                providerMessageId = message.messageID,
                providerStatusCode = 250
            )
        }.onFailure {
            logger.error(
                "Failed to send email via SMTP host={} port={} recipients={}",
                config.host,
                config.port,
                recipients.size,
                it
            )
        }.getOrElse { ex ->
            EmailDeliveryResult(
                status = EmailDeliveryStatus.FAILED,
                message = ex.message ?: "SMTP provider error",
                providerResponse = if (ex is MessagingException) ex.nextException?.message ?: "SMTP error"
                                   else "Email delivery error"
            )
        }
    }

    private fun createSession(): Session {
        val props = Properties().apply {
            put("mail.transport.protocol", "smtp")
            put("mail.smtp.host", config.host)
            put("mail.smtp.port", config.port.toString())
            put("mail.smtp.connectiontimeout", config.timeoutMs.toString())
            put("mail.smtp.timeout", config.timeoutMs.toString())
            put("mail.smtp.writetimeout", config.timeoutMs.toString())
            put("mail.smtp.auth", config.auth.toString())
            put("mail.smtp.starttls.enable", config.startTls.toString())
            put("mail.smtp.starttls.required", config.startTls.toString())
            if (config.tlsTrustAll) {
                put("mail.smtp.ssl.trust", "*")
                put("mail.smtp.ssl.checkserveridentity", "false")
            }
        }

        val authenticator = if (config.auth) {
            object : Authenticator() {
                override fun getPasswordAuthentication(): PasswordAuthentication = PasswordAuthentication(
                    config.username,
                    config.password
                )
            }
        } else {
            null
        }
        return Session.getInstance(props, authenticator)
    }

    private fun buildMultipartBody(html: String): MimeMultipart =
        MimeMultipart("alternative").apply {
            addBodyPart(
                MimeBodyPart().apply {
                    setText(htmlToPlainText(html), Charsets.UTF_8.name())
                }
            )
            addBodyPart(
                MimeBodyPart().apply {
                    setContent(html, "text/html; charset=${Charsets.UTF_8.name()}")
                }
            )
        }

    private fun missingConfiguration(): List<String> {
        val missing = mutableListOf<String>()
        if (config.host.isBlank()) {
            missing += "SMTP_HOST"
        }
        if (config.fromEmail.isBlank()) {
            missing += "SMTP_FROM_EMAIL"
        }
        if (config.auth) {
            if (config.username.isNullOrBlank()) {
                missing += "SMTP_USERNAME"
            }
            if (config.password.isNullOrBlank()) {
                missing += "SMTP_PASSWORD"
            }
        }
        return missing
    }
}

private fun htmlToPlainText(html: String): String =
    html
        .replace(Regex("<style.*?>.*?</style>", setOf(RegexOption.IGNORE_CASE, RegexOption.DOT_MATCHES_ALL)), "")
        .replace(Regex("<script.*?>.*?</script>", setOf(RegexOption.IGNORE_CASE, RegexOption.DOT_MATCHES_ALL)), "")
        .replace(Regex("<[^>]+>"), " ")
        .replace("&nbsp;", " ")
        .replace(Regex("\\s+"), " ")
        .trim()
