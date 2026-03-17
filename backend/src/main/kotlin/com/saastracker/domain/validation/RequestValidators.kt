package com.saastracker.domain.validation

import com.saastracker.transport.http.request.AcceptInviteRequest
import com.saastracker.transport.http.request.CreateSubscriptionRequest
import com.saastracker.transport.http.request.InviteMemberRequest
import com.saastracker.transport.http.request.LoginRequest
import com.saastracker.transport.http.request.MarkSubscriptionPaidRequest
import com.saastracker.transport.http.request.RegisterRequest
import com.saastracker.transport.http.request.UpdateSubscriptionRequest
import org.valiktor.functions.hasSize
import org.valiktor.functions.isEmail
import org.valiktor.functions.isNotBlank
import org.valiktor.functions.isNotNull
import org.valiktor.functions.matches
import org.valiktor.validate
import java.math.BigDecimal
import java.time.LocalDate

fun validateRegister(request: RegisterRequest) {
    validate(request) {
        validate(RegisterRequest::companyName).isNotBlank().hasSize(min = 2, max = 255)
        validate(RegisterRequest::companyDomain).isNotBlank().matches(Regex("^[a-zA-Z0-9.-]+$"))
        validate(RegisterRequest::fullName).isNotBlank().hasSize(min = 2, max = 255)
        validate(RegisterRequest::email).isNotBlank().isEmail()
        validate(RegisterRequest::password).isNotBlank().hasSize(min = 10, max = 128)
    }
    validatePasswordComplexity(request.password)
}

fun validateLogin(request: LoginRequest) {
    validate(request) {
        validate(LoginRequest::email).isNotBlank().isEmail()
        validate(LoginRequest::password).isNotBlank().hasSize(min = 10, max = 128)
    }
}

fun validateInvite(request: InviteMemberRequest) {
    validate(request) {
        validate(InviteMemberRequest::email).isNotBlank().isEmail()
        validate(InviteMemberRequest::role).isNotNull()
    }
}

fun validateAcceptInvite(request: AcceptInviteRequest) {
    validate(request) {
        validate(AcceptInviteRequest::token).isNotBlank().hasSize(min = 32, max = 255)
        validate(AcceptInviteRequest::fullName).isNotBlank().hasSize(min = 2, max = 255)
        validate(AcceptInviteRequest::password).isNotBlank().hasSize(min = 10, max = 128)
    }
    validatePasswordComplexity(request.password)
}

fun validateSubscriptionRequest(request: CreateSubscriptionRequest) {
    validate(request) {
        validate(CreateSubscriptionRequest::vendorName).isNotBlank().hasSize(min = 2, max = 255)
        validate(CreateSubscriptionRequest::category).isNotBlank().hasSize(min = 2, max = 100)
        validate(CreateSubscriptionRequest::amount).isNotBlank()
        validate(CreateSubscriptionRequest::currency).isNotBlank().hasSize(min = 3, max = 3)
        validate(CreateSubscriptionRequest::renewalDate).isNotBlank()
    }
    val amount = request.amount.toBigDecimalOrNull() ?: throw IllegalArgumentException("Amount must be numeric")
    validateAmount(amount)
    request.nextPaymentDate?.let {
        runCatching { LocalDate.parse(it) }
            .getOrElse { throw IllegalArgumentException("Invalid nextPaymentDate format, expected YYYY-MM-DD") }
    }
}

fun validateSubscriptionRequest(request: UpdateSubscriptionRequest) {
    validate(request) {
        validate(UpdateSubscriptionRequest::vendorName).isNotBlank().hasSize(min = 2, max = 255)
        validate(UpdateSubscriptionRequest::category).isNotBlank().hasSize(min = 2, max = 100)
        validate(UpdateSubscriptionRequest::amount).isNotBlank()
        validate(UpdateSubscriptionRequest::currency).isNotBlank().hasSize(min = 3, max = 3)
        validate(UpdateSubscriptionRequest::renewalDate).isNotBlank()
    }
    val amount = request.amount.toBigDecimalOrNull() ?: throw IllegalArgumentException("Amount must be numeric")
    validateAmount(amount)
    request.nextPaymentDate?.let {
        runCatching { LocalDate.parse(it) }
            .getOrElse { throw IllegalArgumentException("Invalid nextPaymentDate format, expected YYYY-MM-DD") }
    }
}

fun validateMarkSubscriptionPaid(request: MarkSubscriptionPaidRequest) {
    request.paidAt?.let {
        runCatching { LocalDate.parse(it) }
            .getOrElse { throw IllegalArgumentException("Invalid paidAt format, expected YYYY-MM-DD") }
    }
    request.amount?.let {
        val amount = it.toBigDecimalOrNull() ?: throw IllegalArgumentException("Amount must be numeric")
        validateAmount(amount)
    }
}

private fun validatePasswordComplexity(password: String) {
    require(password.any { it.isUpperCase() }) { "Password must contain at least one uppercase letter" }
    require(password.any { it.isLowerCase() }) { "Password must contain at least one lowercase letter" }
    require(password.any { it.isDigit() }) { "Password must contain at least one digit" }
}

private fun validateAmount(amount: BigDecimal) {
    require(amount > BigDecimal.ZERO) { "Amount must be positive" }
    require(amount.stripTrailingZeros().scale() <= 2) { "Amount must have at most 2 decimal places" }
    require(amount <= BigDecimal("999999999.99")) { "Amount exceeds maximum allowed value" }
}
