package com.saastracker.transport.payment

import com.saastracker.config.StripeConfig
import com.saastracker.domain.error.AppError
import com.saastracker.domain.error.AppResult
import com.saastracker.domain.model.Company
import com.saastracker.domain.model.CompanySubscriptionStatus
import com.saastracker.domain.model.UserRole
import com.saastracker.persistence.repository.ClockProvider
import com.saastracker.persistence.repository.CompanyRepository
import com.saastracker.persistence.repository.UserRepository
import com.stripe.Stripe
import com.stripe.exception.SignatureVerificationException
import com.stripe.model.Customer
import com.stripe.model.Event
import com.stripe.model.Invoice
import com.stripe.model.Subscription
import com.stripe.model.checkout.Session
import com.stripe.net.Webhook
import com.stripe.param.CustomerCreateParams
import com.stripe.param.checkout.SessionCreateParams
import org.slf4j.LoggerFactory
import java.util.UUID

class StripeBillingService(
    private val config: StripeConfig,
    private val companyRepository: CompanyRepository,
    private val clockProvider: ClockProvider,
    private val userRepository: UserRepository
) {
    private val logger = LoggerFactory.getLogger(javaClass)

    init {
        if (!config.apiKey.isNullOrBlank()) {
            Stripe.apiKey = config.apiKey
        }
    }

    fun enforceAccess(company: Company): AppResult<Unit> {
        val now = clockProvider.nowInstant()
        val trialExpired = company.trialEndsAt.isBefore(now)
        val blocked = trialExpired && company.subscriptionStatus != CompanySubscriptionStatus.ACTIVE
        return if (blocked) {
            AppResult.Failure(AppError.Forbidden("Trial expired. Activate billing to continue."))
        } else {
            AppResult.Success(Unit)
        }
    }

    fun createPortalSession(company: Company): AppResult<String> {
        if (config.apiKey.isNullOrBlank())
            return AppResult.Failure(AppError.ExternalService("Stripe not configured"))
        val customerId = company.stripeCustomerId
            ?: return AppResult.Failure(AppError.Validation("No active billing subscription"))

        val params = com.stripe.param.billingportal.SessionCreateParams.builder()
            .setCustomer(customerId)
            .setReturnUrl(config.cancelUrl.substringBefore("/billing") + "/dashboard")
            .build()
        val session = com.stripe.model.billingportal.Session.create(params)
        return AppResult.Success(session.url)
    }

    fun createCheckoutSession(company: Company, plan: String = "monthly"): AppResult<String> {
        if (config.apiKey.isNullOrBlank()) {
            return AppResult.Failure(AppError.ExternalService("Stripe not configured"))
        }
        val priceId = (if (plan == "annual") config.annualPriceId else config.monthlyPriceId)
            ?: return AppResult.Failure(AppError.ExternalService("Price ID not configured for plan: $plan"))

        val customerId = company.stripeCustomerId ?: run {
            val adminEmail = userRepository.listByCompany(company.id)
                .firstOrNull { it.role == UserRole.ADMIN }
                ?.email
                ?: return AppResult.Failure(AppError.Internal("No admin user found for company"))

            val customer = Customer.create(
                CustomerCreateParams.builder()
                    .setEmail(adminEmail)
                    .setName(company.name)
                    .build()
            )
            companyRepository.update(company.copy(stripeCustomerId = customer.id, updatedAt = clockProvider.nowInstant()))
            customer.id
        }

        val params = SessionCreateParams.builder()
            .setCustomer(customerId)
            .setMode(SessionCreateParams.Mode.SUBSCRIPTION)
            .addLineItem(
                SessionCreateParams.LineItem.builder()
                    .setPrice(priceId)
                    .setQuantity(1L)
                    .build()
            )
            .setSuccessUrl(config.successUrl)
            .setCancelUrl(config.cancelUrl)
            .build()

        val session = Session.create(params)
        return AppResult.Success(session.url)
    }

    fun handleWebhook(payload: String, signature: String): AppResult<Unit> {
        val webhookSecret = config.webhookSecret
            ?: return AppResult.Failure(AppError.ExternalService("Stripe webhook secret is not configured"))

        val event = try {
            Webhook.constructEvent(payload, signature, webhookSecret)
        } catch (ex: SignatureVerificationException) {
            logger.warn("Stripe webhook signature invalid", ex)
            return AppResult.Failure(AppError.Unauthorized("Invalid stripe signature"))
        }

        when (event.type) {
            "customer.subscription.created",
            "customer.subscription.updated" -> updateCompanyStatus(event, CompanySubscriptionStatus.ACTIVE)

            "customer.subscription.deleted" -> updateCompanyStatus(event, CompanySubscriptionStatus.CANCELED)
            "invoice.payment_failed" -> updateCompanyStatus(event, CompanySubscriptionStatus.PAST_DUE)
        }
        return AppResult.Success(Unit)
    }

    private fun updateCompanyStatus(event: Event, status: CompanySubscriptionStatus) {
        val stripeObject = event.dataObjectDeserializer.deserializeUnsafe() ?: run {
            logger.warn("Failed to deserialize Stripe event data for type={}", event.type)
            return
        }
        val customerId = when (stripeObject) {
            is Subscription -> stripeObject.customer
            is Invoice -> stripeObject.customer
            else -> {
                logger.warn("Unexpected Stripe object type={} for event type={}", stripeObject.javaClass.name, event.type)
                return
            }
        }
        if (customerId.isNullOrBlank()) return

        val company = companyRepository.findByStripeCustomerId(customerId) ?: return
        companyRepository.update(
            company.copy(
                subscriptionStatus = status,
                updatedAt = clockProvider.nowInstant()
            )
        )
    }
}
