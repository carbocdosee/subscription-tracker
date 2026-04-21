package com.saastracker.domain.service

import com.saastracker.domain.model.Company
import com.saastracker.domain.model.CompanySubscriptionStatus
import com.saastracker.domain.model.SubscriptionStatus
import com.saastracker.domain.model.UserRole
import com.saastracker.persistence.repository.BudgetAlertRepository
import com.saastracker.persistence.repository.ClockProvider
import com.saastracker.persistence.repository.CompanyRepository
import com.saastracker.persistence.repository.SubscriptionRepository
import com.saastracker.persistence.repository.UserRepository
import com.saastracker.transport.email.EmailService
import org.slf4j.LoggerFactory
import java.math.BigDecimal
import java.math.RoundingMode

class BudgetAlertService(
    private val companyRepository: CompanyRepository,
    private val subscriptionRepository: SubscriptionRepository,
    private val subscriptionService: SubscriptionService,
    private val userRepository: UserRepository,
    private val emailService: EmailService,
    private val budgetAlertRepository: BudgetAlertRepository,
    private val clock: ClockProvider
) {
    private val logger = LoggerFactory.getLogger(javaClass)
    private val thresholds = listOf(80, 100)

    fun checkAllCompanies() {
        val now = clock.nowDate()
        companyRepository.listAll()
            .filter { it.subscriptionStatus == CompanySubscriptionStatus.TRIAL }
            .forEach { company -> checkCompany(company, now.year, now.monthValue) }
    }

    fun checkCompany(company: Company, year: Int, month: Int) {
        val budget = company.monthlyBudget ?: return

        val monthlySpend = subscriptionRepository.listByCompany(company.id)
            .filter { it.status == SubscriptionStatus.ACTIVE }
            .fold(BigDecimal.ZERO) { acc, s -> acc + subscriptionService.normalizedMonthlyUsd(s) }

        val utilization = monthlySpend
            .divide(budget, 4, RoundingMode.HALF_UP)
            .multiply(BigDecimal("100"))
            .toInt()

        thresholds.forEach { threshold ->
            if (utilization >= threshold) {
                val alreadySent = budgetAlertRepository.exists(company.id, year, month, threshold)
                if (!alreadySent) {
                    sendBudgetAlert(company, threshold, monthlySpend, budget)
                    budgetAlertRepository.record(company.id, year, month, threshold)
                }
            }
        }
    }

    private fun sendBudgetAlert(company: Company, threshold: Int, spend: BigDecimal, budget: BigDecimal) {
        userRepository.listByCompany(company.id)
            .filter { it.role == UserRole.ADMIN && it.isActive }
            .forEach { admin ->
                runCatching {
                    emailService.sendBudgetAlertEmail(admin.email, company.name, threshold, spend, budget)
                }.onFailure {
                    logger.error("Failed to send budget alert to {} for company {}", admin.email, company.id, it)
                }
            }
    }
}
