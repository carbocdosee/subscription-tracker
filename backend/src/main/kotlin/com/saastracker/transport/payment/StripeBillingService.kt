package com.saastracker.transport.payment

import com.saastracker.config.StripeConfig
import com.saastracker.domain.error.AppError
import com.saastracker.domain.error.AppResult
import com.saastracker.domain.model.Company
import com.saastracker.domain.model.CompanySubscriptionStatus
import com.saastracker.domain.model.PlanTier
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

    val proPriceId: String? get() = config.proPriceId
    val enterprisePriceId: String? get() = config.enterprisePriceId

    init {
        if (!config.apiKey.isNullOrBlank()) {
            Stripe.apiKey = config.apiKey
        }
    }

    // Legacy check kept for backward compat on billing portal endpoint.
    // New code should use ensurePlanFeature / ensurePlanQuota in RouteSupport.
    fun enforceAccess(company: Company): AppResult<Unit> = AppResult.Success(Unit)

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

    fun createCheckoutSession(company: Company, planTier: PlanTier): AppResult<String> {
        if (config.apiKey.isNullOrBlank()) {
            return AppResult.Failure(AppError.ExternalService("Stripe not configured"))
        }
        val priceId = when (planTier) {
            PlanTier.PRO -> config.proPriceId
            PlanTier.ENTERPRISE -> config.enterprisePriceId
            PlanTier.FREE -> return AppResult.Failure(AppError.Validation("Cannot checkout for Free plan"))
        } ?: return AppResult.Failure(AppError.ExternalService("Price ID not configured for plan: ${planTier.name}"))

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
            .putMetadata("planId", planTier.name)
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
            "checkout.session.completed" -> handleCheckoutCompleted(event)

            "customer.subscription.created",
            "customer.subscription.updated" -> updateCompanyStatus(event, CompanySubscriptionStatus.ACTIVE)

            "customer.subscription.deleted" -> handleSubscriptionDeleted(event)
            "invoice.payment_failed" -> updateCompanyStatus(event, CompanySubscriptionStatus.PAST_DUE)
        }
        return AppResult.Success(Unit)
    }

    private fun handleCheckoutCompleted(event: Event) {
        val session = event.dataObjectDeserializer.deserializeUnsafe() as? Session ?: run {
            logger.warn("Failed to deserialize checkout.session.completed")
            return
        }
        val customerId = session.customer ?: return
        val company = companyRepository.findByStripeCustomerId(customerId) ?: return

        // Read planId from metadata; fall back to PRO if not present (legacy sessions)
        val planTier = session.metadata?.get("planId")
            ?.let { runCatching { PlanTier.valueOf(it) }.getOrNull() }
            ?: PlanTier.PRO

        companyRepository.update(
            company.copy(
                subscriptionStatus = CompanySubscriptionStatus.ACTIVE,
                planTier = planTier,
                updatedAt = clockProvider.nowInstant()
            )
        )
        logger.info("Company {} upgraded to plan {}", company.id, planTier)
    }

    private fun handleSubscriptionDeleted(event: Event) {
        val stripeObject = event.dataObjectDeserializer.deserializeUnsafe() ?: run {
            logger.warn("Failed to deserialize customer.subscription.deleted")
            return
        }
        val customerId = when (stripeObject) {
            is Subscription -> stripeObject.customer
            else -> return
        }
        if (customerId.isNullOrBlank()) return
        val company = companyRepository.findByStripeCustomerId(customerId) ?: return
        companyRepository.update(
            company.copy(
                subscriptionStatus = CompanySubscriptionStatus.CANCELED,
                planTier = PlanTier.FREE,
                updatedAt = clockProvider.nowInstant()
            )
        )
        logger.info("Company {} downgraded to FREE after subscription cancellation", company.id)
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
