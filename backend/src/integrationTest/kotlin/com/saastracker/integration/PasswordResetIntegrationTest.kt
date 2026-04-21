package com.saastracker.integration

import com.saastracker.config.DatabaseConfig
import com.saastracker.config.DatabaseFactory
import com.saastracker.domain.model.Company
import com.saastracker.domain.model.CompanySubscriptionStatus
import com.saastracker.domain.model.User
import com.saastracker.domain.model.UserRole
import com.saastracker.persistence.repository.exposed.ExposedCompanyRepository
import com.saastracker.persistence.repository.exposed.ExposedPasswordResetRepository
import com.saastracker.persistence.repository.exposed.ExposedUserRepository
import org.junit.jupiter.api.BeforeAll
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.TestInstance
import org.testcontainers.containers.PostgreSQLContainer
import org.testcontainers.junit.jupiter.Container
import org.testcontainers.junit.jupiter.Testcontainers
import java.math.BigDecimal
import java.security.MessageDigest
import java.time.Instant
import java.util.UUID
import kotlin.test.assertEquals
import kotlin.test.assertNotNull
import kotlin.test.assertNull
import kotlin.test.assertTrue

@Testcontainers(disabledWithoutDocker = true)
@TestInstance(TestInstance.Lifecycle.PER_CLASS)
class PasswordResetIntegrationTest {

    companion object {
        @Container
        val postgres = PostgreSQLContainer("postgres:16")
            .withDatabaseName("saas_tracker_it")
            .withUsername("saas")
            .withPassword("saas")
    }

    private lateinit var companyRepository: ExposedCompanyRepository
    private lateinit var userRepository: ExposedUserRepository
    private lateinit var passwordResetRepository: ExposedPasswordResetRepository

    @BeforeAll
    fun setup() {
        val dbConfig = DatabaseConfig(
            jdbcUrl = postgres.jdbcUrl,
            username = postgres.username,
            password = postgres.password,
            maxPoolSize = 4
        )
        val dataSource = DatabaseFactory.createDataSource(dbConfig)
        DatabaseFactory.connect(dataSource)
        DatabaseFactory.migrate(dbConfig)

        companyRepository = ExposedCompanyRepository()
        userRepository = ExposedUserRepository()
        passwordResetRepository = ExposedPasswordResetRepository()
    }

    @Test
    fun `create and find token by hash`() {
        val user = seedUser()
        val rawToken = "testtoken-${UUID.randomUUID()}"
        val tokenHash = sha256Hex(rawToken)
        val expiresAt = Instant.now().plusSeconds(3600)

        val tokenId = passwordResetRepository.create(user.id, tokenHash, expiresAt)
        val found = passwordResetRepository.findByHash(tokenHash)

        assertNotNull(found)
        assertEquals(tokenId, found.id)
        assertEquals(user.id, found.userId)
        assertEquals(tokenHash, found.tokenHash)
        assertNull(found.usedAt)
        assertTrue(found.expiresAt.isAfter(Instant.now()))
    }

    @Test
    fun `findByHash returns null for unknown hash`() {
        val result = passwordResetRepository.findByHash("0".repeat(64))

        assertNull(result)
    }

    @Test
    fun `markUsed sets usedAt timestamp`() {
        val user = seedUser()
        val tokenHash = sha256Hex("markused-${UUID.randomUUID()}")
        val tokenId = passwordResetRepository.create(user.id, tokenHash, Instant.now().plusSeconds(3600))

        passwordResetRepository.markUsed(tokenId)
        val updated = passwordResetRepository.findByHash(tokenHash)

        assertNotNull(updated)
        assertNotNull(updated.usedAt)
    }

    @Test
    fun `deleteExpiredForUser removes expired tokens for a specific user`() {
        val user = seedUser()
        val expiredHash = sha256Hex("expired-${UUID.randomUUID()}")
        val validHash = sha256Hex("valid-${UUID.randomUUID()}")

        passwordResetRepository.create(user.id, expiredHash, Instant.now().minusSeconds(1))
        passwordResetRepository.create(user.id, validHash, Instant.now().plusSeconds(3600))

        passwordResetRepository.deleteExpiredForUser(user.id)

        assertNull(passwordResetRepository.findByHash(expiredHash))
        assertNotNull(passwordResetRepository.findByHash(validHash))
    }

    @Test
    fun `deleteAllExpired removes all expired tokens across users`() {
        val userA = seedUser()
        val userB = seedUser()
        val expiredHashA = sha256Hex("expiredA-${UUID.randomUUID()}")
        val expiredHashB = sha256Hex("expiredB-${UUID.randomUUID()}")
        val validHash = sha256Hex("validC-${UUID.randomUUID()}")

        passwordResetRepository.create(userA.id, expiredHashA, Instant.now().minusSeconds(10))
        passwordResetRepository.create(userB.id, expiredHashB, Instant.now().minusSeconds(10))
        passwordResetRepository.create(userA.id, validHash, Instant.now().plusSeconds(3600))

        passwordResetRepository.deleteAllExpired()

        assertNull(passwordResetRepository.findByHash(expiredHashA))
        assertNull(passwordResetRepository.findByHash(expiredHashB))
        assertNotNull(passwordResetRepository.findByHash(validHash))
    }

    private fun seedUser(): User {
        val now = Instant.now()
        val company = companyRepository.create(
            Company(
                id = UUID.randomUUID(),
                name = "Test Co ${UUID.randomUUID()}",
                domain = "test-${UUID.randomUUID()}.io",
                stripeCustomerId = null,
                subscriptionStatus = CompanySubscriptionStatus.TRIAL,
                trialEndsAt = now.plusSeconds(86_400),
                monthlyBudget = BigDecimal("1000.00"),
                employeeCount = null,
                settings = "{}",
                createdAt = now,
                updatedAt = now
            )
        )
        return userRepository.create(
            User(
                id = UUID.randomUUID(),
                companyId = company.id,
                email = "user-${UUID.randomUUID()}@test.io",
                name = "Test User",
                passwordHash = "hash",
                role = UserRole.ADMIN,
                isActive = true,
                lastLoginAt = now,
                createdAt = now
            )
        )
    }

    private fun sha256Hex(input: String): String =
        MessageDigest.getInstance("SHA-256")
            .digest(input.toByteArray(Charsets.UTF_8))
            .joinToString("") { "%02x".format(it) }
}
