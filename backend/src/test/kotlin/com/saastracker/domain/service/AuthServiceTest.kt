package com.saastracker.domain.service

import com.saastracker.domain.error.AppError
import com.saastracker.domain.error.AppResult
import com.saastracker.domain.model.AuditLogEntry
import com.saastracker.domain.model.Company
import com.saastracker.domain.model.CompanySubscriptionStatus
import com.saastracker.domain.model.TeamInvitation
import com.saastracker.domain.model.User
import com.saastracker.domain.model.UserRole
import com.saastracker.persistence.repository.AuditLogRepository
import com.saastracker.persistence.repository.ClockProvider
import com.saastracker.persistence.repository.CompanyRepository
import com.saastracker.persistence.repository.EmailDeliveryRepository
import com.saastracker.persistence.repository.IdentityProvider
import com.saastracker.persistence.repository.NotificationReadRepository
import com.saastracker.persistence.repository.PasswordResetRepository
import com.saastracker.persistence.repository.PasswordResetTokenRecord
import com.saastracker.persistence.repository.RefreshTokenRecord
import com.saastracker.persistence.repository.RefreshTokenRepository
import com.saastracker.persistence.repository.SubscriptionRepository
import com.saastracker.persistence.repository.TeamInvitationRepository
import com.saastracker.persistence.repository.UserRepository
import com.saastracker.transport.email.EmailDeliveryResult
import com.saastracker.transport.email.EmailDeliveryStatus
import com.saastracker.transport.email.EmailService
import com.saastracker.transport.http.request.InviteMemberRequest
import com.saastracker.transport.security.JwtService
import io.mockk.every
import io.mockk.mockk
import io.mockk.verify
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertNotEquals
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.Test
import java.math.BigDecimal
import java.security.MessageDigest
import java.time.Duration
import java.time.Instant
import java.util.UUID

class AuthServiceTest {
    private val companyRepository = mockk<CompanyRepository>(relaxed = true)
    private val userRepository = mockk<UserRepository>(relaxed = true)
    private val invitationRepository = mockk<TeamInvitationRepository>(relaxed = true)
    private val auditLogRepository = mockk<AuditLogRepository>(relaxed = true)
    private val emailDeliveryRepository = mockk<EmailDeliveryRepository>(relaxed = true)
    private val refreshTokenRepository = mockk<RefreshTokenRepository>(relaxed = true)
    private val notificationReadRepository = mockk<NotificationReadRepository>(relaxed = true)
    private val passwordResetRepository = mockk<PasswordResetRepository>(relaxed = true)
    private val subscriptionRepository = mockk<SubscriptionRepository>(relaxed = true)
    private val passwordService = mockk<PasswordService>(relaxed = true)
    private val jwtService = mockk<JwtService>(relaxed = true)
    private val emailService = mockk<EmailService>(relaxed = true)
    private val idProvider = mockk<IdentityProvider>(relaxed = true)
    private val clock = mockk<ClockProvider>(relaxed = true)

    private val service = AuthService(
        companyRepository = companyRepository,
        userRepository = userRepository,
        invitationRepository = invitationRepository,
        auditLogRepository = auditLogRepository,
        emailDeliveryRepository = emailDeliveryRepository,
        subscriptionRepository = subscriptionRepository,
        refreshTokenRepository = refreshTokenRepository,
        notificationReadRepository = notificationReadRepository,
        passwordResetRepository = passwordResetRepository,
        passwordService = passwordService,
        jwtService = jwtService,
        emailService = emailService,
        idProvider = idProvider,
        clock = clock
    )

    // ---- refresh() tests ----

    @Test
    fun `refresh with valid token rotates token and returns new access token`() {
        val now = Instant.parse("2026-03-10T10:00:00Z")
        val userId = UUID.randomUUID()
        val recordId = UUID.randomUUID()
        val rawToken = "valid-refresh-token"
        val tokenHash = "hashed"
        val user = activeUser(id = userId)

        every { clock.nowInstant() } returns now
        every { jwtService.hashRefreshToken(rawToken) } returns tokenHash
        every { jwtService.generateRefreshToken() } returns "new-refresh-token"
        every { jwtService.refreshTokenTtlDays } returns 30
        every { jwtService.createToken(any(), any()) } returns "new-access-token"
        every { refreshTokenRepository.findByHash(tokenHash) } returns RefreshTokenRecord(
            id = recordId,
            userId = userId,
            tokenHash = tokenHash,
            expiresAt = now.plus(Duration.ofDays(1)),
            revokedAt = null
        )
        every { userRepository.findById(userId) } returns user

        val result = service.refresh(rawToken)

        assertTrue(result is AppResult.Success)
        val payload = (result as AppResult.Success).value
        assertEquals("new-access-token", payload.accessToken)
        assertEquals("new-refresh-token", payload.refreshToken)
        verify(exactly = 1) { refreshTokenRepository.revoke(recordId) }
        verify(exactly = 1) { refreshTokenRepository.create(userId, any(), any()) }
    }

    @Test
    fun `refresh with expired token returns Unauthorized`() {
        val now = Instant.parse("2026-03-10T10:00:00Z")
        val rawToken = "expired-refresh-token"
        val tokenHash = "hashed-expired"

        every { clock.nowInstant() } returns now
        every { jwtService.hashRefreshToken(rawToken) } returns tokenHash
        every { refreshTokenRepository.findByHash(tokenHash) } returns RefreshTokenRecord(
            id = UUID.randomUUID(),
            userId = UUID.randomUUID(),
            tokenHash = tokenHash,
            expiresAt = now.minus(Duration.ofMinutes(5)),
            revokedAt = null
        )

        val result = service.refresh(rawToken)

        assertTrue(result is AppResult.Failure)
        val error = (result as AppResult.Failure).error
        assertTrue(error is AppError.Unauthorized)
        assertEquals("Refresh token expired", error.message)
    }

    @Test
    fun `refresh with revoked token returns Unauthorized`() {
        val now = Instant.parse("2026-03-10T10:00:00Z")
        val rawToken = "revoked-refresh-token"
        val tokenHash = "hashed-revoked"

        every { clock.nowInstant() } returns now
        every { jwtService.hashRefreshToken(rawToken) } returns tokenHash
        every { refreshTokenRepository.findByHash(tokenHash) } returns RefreshTokenRecord(
            id = UUID.randomUUID(),
            userId = UUID.randomUUID(),
            tokenHash = tokenHash,
            expiresAt = now.plus(Duration.ofDays(1)),
            revokedAt = now.minus(Duration.ofHours(1))
        )

        val result = service.refresh(rawToken)

        assertTrue(result is AppResult.Failure)
        val error = (result as AppResult.Failure).error
        assertTrue(error is AppError.Unauthorized)
        assertEquals("Refresh token revoked", error.message)
    }

    @Test
    fun `refresh with unknown token returns Unauthorized`() {
        val rawToken = "unknown-token"
        val tokenHash = "hashed-unknown"

        every { jwtService.hashRefreshToken(rawToken) } returns tokenHash
        every { refreshTokenRepository.findByHash(tokenHash) } returns null

        val result = service.refresh(rawToken)

        assertTrue(result is AppResult.Failure)
        val error = (result as AppResult.Failure).error
        assertTrue(error is AppError.Unauthorized)
        assertEquals("Invalid refresh token", error.message)
    }

    @Test
    fun `logout revokes token and returns success`() {
        val rawToken = "some-refresh-token"
        val tokenHash = "hashed"
        val recordId = UUID.randomUUID()

        every { jwtService.hashRefreshToken(rawToken) } returns tokenHash
        every { refreshTokenRepository.findByHash(tokenHash) } returns RefreshTokenRecord(
            id = recordId,
            userId = UUID.randomUUID(),
            tokenHash = tokenHash,
            expiresAt = Instant.now().plus(Duration.ofDays(1)),
            revokedAt = null
        )

        val result = service.logout(rawToken)

        assertTrue(result is AppResult.Success)
        verify(exactly = 1) { refreshTokenRepository.revoke(recordId) }
    }

    @Test
    fun `logout with unknown token returns success without error`() {
        val rawToken = "unknown"
        val tokenHash = "hashed-unknown"

        every { jwtService.hashRefreshToken(rawToken) } returns tokenHash
        every { refreshTokenRepository.findByHash(tokenHash) } returns null

        val result = service.logout(rawToken)

        assertTrue(result is AppResult.Success)
        verify(exactly = 0) { refreshTokenRepository.revoke(any()) }
    }

    // ---- requestPasswordReset() tests ----

    @Test
    fun `requestPasswordReset creates token and sends email for known active user`() {
        val now = Instant.parse("2026-03-18T10:00:00Z")
        val user = activeUser()

        every { clock.nowInstant() } returns now
        every { userRepository.findByEmail(user.email) } returns user
        every { emailService.sendPasswordResetEmail(any(), any()) } returns EmailDeliveryResult(
            status = EmailDeliveryStatus.SENT,
            message = "Delivered"
        )

        val result = service.requestPasswordReset(user.email)

        assertTrue(result is AppResult.Success)
        verify(exactly = 1) { passwordResetRepository.deleteExpiredForUser(user.id) }
        verify(exactly = 1) { passwordResetRepository.create(user.id, any(), any()) }
        verify(exactly = 1) { emailService.sendPasswordResetEmail(user.email, any()) }
    }

    @Test
    fun `requestPasswordReset returns success without creating token for unknown email`() {
        every { userRepository.findByEmail("nobody@company.com") } returns null

        val result = service.requestPasswordReset("nobody@company.com")

        assertTrue(result is AppResult.Success)
        verify(exactly = 0) { passwordResetRepository.create(any(), any(), any()) }
        verify(exactly = 0) { emailService.sendPasswordResetEmail(any(), any()) }
    }

    @Test
    fun `requestPasswordReset returns success without creating token for inactive user`() {
        val inactive = activeUser().copy(isActive = false)
        every { userRepository.findByEmail(inactive.email) } returns inactive

        val result = service.requestPasswordReset(inactive.email)

        assertTrue(result is AppResult.Success)
        verify(exactly = 0) { passwordResetRepository.create(any(), any(), any()) }
        verify(exactly = 0) { emailService.sendPasswordResetEmail(any(), any()) }
    }

    // ---- resetPassword() tests ----

    @Test
    fun `resetPassword with valid token updates password and revokes all sessions`() {
        val now = Instant.parse("2026-03-18T10:00:00Z")
        val user = activeUser()
        val rawToken = "validresettoken1234567890"
        val tokenHash = sha256Hex(rawToken)
        val recordId = UUID.randomUUID()

        every { clock.nowInstant() } returns now
        every { passwordResetRepository.findByHash(tokenHash) } returns PasswordResetTokenRecord(
            id = recordId,
            userId = user.id,
            tokenHash = tokenHash,
            expiresAt = now.plus(Duration.ofMinutes(30)),
            usedAt = null
        )
        every { userRepository.findById(user.id) } returns user

        val result = service.resetPassword(rawToken, "NewPassword99!")

        assertTrue(result is AppResult.Success)
        verify(exactly = 1) { userRepository.update(any()) }
        verify(exactly = 1) { passwordResetRepository.markUsed(recordId) }
        verify(exactly = 1) { refreshTokenRepository.revokeAllForUser(user.id) }
    }

    @Test
    fun `resetPassword with unknown token returns Validation failure`() {
        val rawToken = "unknowntoken1234567890"
        val tokenHash = sha256Hex(rawToken)

        every { passwordResetRepository.findByHash(tokenHash) } returns null

        val result = service.resetPassword(rawToken, "NewPassword99!")

        assertTrue(result is AppResult.Failure)
        assertTrue((result as AppResult.Failure).error is AppError.Validation)
        verify(exactly = 0) { userRepository.update(any()) }
    }

    @Test
    fun `resetPassword with already-used token returns Validation failure`() {
        val now = Instant.parse("2026-03-18T10:00:00Z")
        val rawToken = "usedtoken12345678901234"
        val tokenHash = sha256Hex(rawToken)

        every { clock.nowInstant() } returns now
        every { passwordResetRepository.findByHash(tokenHash) } returns PasswordResetTokenRecord(
            id = UUID.randomUUID(),
            userId = UUID.randomUUID(),
            tokenHash = tokenHash,
            expiresAt = now.plus(Duration.ofMinutes(30)),
            usedAt = now.minus(Duration.ofMinutes(10))
        )

        val result = service.resetPassword(rawToken, "NewPassword99!")

        assertTrue(result is AppResult.Failure)
        val error = (result as AppResult.Failure).error
        assertTrue(error is AppError.Validation)
        assertEquals("Reset token already used", error.message)
        verify(exactly = 0) { userRepository.update(any()) }
    }

    @Test
    fun `resetPassword with expired token returns Validation failure`() {
        val now = Instant.parse("2026-03-18T10:00:00Z")
        val rawToken = "expiredtoken123456789012"
        val tokenHash = sha256Hex(rawToken)

        every { clock.nowInstant() } returns now
        every { passwordResetRepository.findByHash(tokenHash) } returns PasswordResetTokenRecord(
            id = UUID.randomUUID(),
            userId = UUID.randomUUID(),
            tokenHash = tokenHash,
            expiresAt = now.minus(Duration.ofSeconds(1)),
            usedAt = null
        )

        val result = service.resetPassword(rawToken, "NewPassword99!")

        assertTrue(result is AppResult.Failure)
        val error = (result as AppResult.Failure).error
        assertTrue(error is AppError.Validation)
        assertEquals("Reset token expired", error.message)
        verify(exactly = 0) { userRepository.update(any()) }
    }

    @Test
    fun `resetPassword for inactive user returns Forbidden`() {
        val now = Instant.parse("2026-03-18T10:00:00Z")
        val inactive = activeUser().copy(isActive = false)
        val rawToken = "inactivetoken123456789012"
        val tokenHash = sha256Hex(rawToken)
        val recordId = UUID.randomUUID()

        every { clock.nowInstant() } returns now
        every { passwordResetRepository.findByHash(tokenHash) } returns PasswordResetTokenRecord(
            id = recordId,
            userId = inactive.id,
            tokenHash = tokenHash,
            expiresAt = now.plus(Duration.ofMinutes(30)),
            usedAt = null
        )
        every { userRepository.findById(inactive.id) } returns inactive

        val result = service.resetPassword(rawToken, "NewPassword99!")

        assertTrue(result is AppResult.Failure)
        assertTrue((result as AppResult.Failure).error is AppError.Forbidden)
        verify(exactly = 0) { userRepository.update(any()) }
    }

    // ---- exportPersonalData() tests ----

    @Test
    fun `exportPersonalData returns structured export for known user`() {
        val now = Instant.parse("2026-03-18T10:00:00Z")
        val user = activeUser()
        val company = company(id = user.companyId)

        every { clock.nowInstant() } returns now
        every { userRepository.findById(user.id) } returns user
        every { companyRepository.findById(user.companyId) } returns company
        every { auditLogRepository.listByCompany(user.companyId) } returns emptyList()

        val result = service.exportPersonalData(user.id)

        assertTrue(result is AppResult.Success)
        val export = (result as AppResult.Success).value
        assertTrue(export.containsKey("profile"))
        assertTrue(export.containsKey("company"))
        assertTrue(export.containsKey("auditLog"))
        val profile = export["profile"] as Map<*, *>
        assertEquals(user.id.toString(), profile["id"])
        assertEquals(user.email, profile["email"])
        assertEquals(user.name, profile["name"])
        val companyData = export["company"] as Map<*, *>
        assertEquals(company.name, companyData["name"])
    }

    @Test
    fun `exportPersonalData returns NotFound for unknown user`() {
        val userId = UUID.randomUUID()
        every { userRepository.findById(userId) } returns null

        val result = service.exportPersonalData(userId)

        assertTrue(result is AppResult.Failure)
        assertTrue((result as AppResult.Failure).error is AppError.NotFound)
    }

    // ---- deleteAccount() tests ----

    @Test
    fun `deleteAccount as last company member cascades full company deletion`() {
        val user = activeUser()
        every { userRepository.findById(user.id) } returns user
        every { userRepository.listByCompany(user.companyId) } returns listOf(user)

        val result = service.deleteAccount(user.id)

        assertTrue(result is AppResult.Success)
        verify(exactly = 1) { refreshTokenRepository.revokeAllForUser(user.id) }
        verify(exactly = 1) { companyRepository.delete(user.companyId) }
        verify(exactly = 0) { userRepository.update(any()) }
    }

    @Test
    fun `deleteAccount with remaining members anonymises user record`() {
        val companyId = UUID.randomUUID()
        val user = activeUser().copy(companyId = companyId)
        val colleague = activeUser().copy(companyId = companyId)
        every { userRepository.findById(user.id) } returns user
        every { userRepository.listByCompany(companyId) } returns listOf(user, colleague)

        val result = service.deleteAccount(user.id)

        assertTrue(result is AppResult.Success)
        verify(exactly = 0) { companyRepository.delete(any()) }
        verify(exactly = 1) {
            userRepository.update(
                match {
                    it.id == user.id &&
                        it.email.startsWith("deleted-") &&
                        it.name == "Deleted User" &&
                        it.passwordHash == "" &&
                        !it.isActive
                }
            )
        }
    }

    // ---- inviteMember() rate-limit test ----

    @Test
    fun `inviteMember returns Forbidden when daily invitation limit is reached`() {
        val now = Instant.parse("2026-03-18T10:00:00Z")
        val currentUser = adminUser()

        every { clock.nowInstant() } returns now
        every { userRepository.findByEmail(any()) } returns null
        every { invitationRepository.countCreatedSince(currentUser.companyId, any()) } returns 20L

        val result = service.inviteMember(currentUser, InviteMemberRequest("new@company.com", UserRole.VIEWER))

        assertTrue(result is AppResult.Failure)
        val error = (result as AppResult.Failure).error
        assertTrue(error is AppError.Forbidden)
        assertTrue(error.message.contains("20"))
        verify(exactly = 0) { invitationRepository.create(any()) }
    }

    // ---- Invitation tests ----

    @Test
    fun `should reuse active invitation and return reusedExisting true`() {
        val now = Instant.parse("2026-02-14T09:00:00Z")
        val currentUser = adminUser()
        val existing = invitation(
            companyId = currentUser.companyId,
            invitedByUserId = UUID.randomUUID(),
            email = "user@company.com",
            role = UserRole.VIEWER,
            token = "existing-token-1",
            expiresAt = now.plusSeconds(86_400),
            acceptedAt = null
        )

        every { clock.nowInstant() } returns now
        every { userRepository.findByEmail("user@company.com") } returns null
        every { invitationRepository.findByCompanyAndEmail(currentUser.companyId, "user@company.com") } returns existing
        every { invitationRepository.update(any()) } answers { firstArg() }
        every { idProvider.newId() } returnsMany listOf(UUID.randomUUID(), UUID.randomUUID())
        every { emailService.sendTeamInviteEmail(any(), any()) } returns EmailDeliveryResult(
            status = EmailDeliveryStatus.SENT,
            message = "Delivered by Resend"
        )
        every { emailDeliveryRepository.create(any()) } answers { firstArg() }
        every { auditLogRepository.append(any()) } answers { firstArg() }

        val result = service.inviteMember(currentUser, InviteMemberRequest("user@company.com", UserRole.EDITOR))

        assertTrue(result is AppResult.Success)
        val payload = (result as AppResult.Success).value
        assertTrue(payload.reusedExisting)
        assertEquals(existing.id, payload.invitation.id)
        assertEquals(UserRole.EDITOR, payload.invitation.role)
        assertEquals(currentUser.id, payload.invitation.invitedByUserId)
        assertTrue(payload.invitation.expiresAt.isAfter(now))
        assertEquals(EmailDeliveryStatus.SENT, payload.emailDelivery.status)
        verify(exactly = 0) { invitationRepository.create(any()) }
        verify(exactly = 1) { invitationRepository.update(any()) }
        verify(exactly = 1) { emailService.sendTeamInviteEmail("user@company.com", "existing-token-1") }
        verify(exactly = 1) { emailDeliveryRepository.create(any()) }
        verify(exactly = 1) {
            invitationRepository.update(
                match {
                    it.id == existing.id &&
                        it.role == UserRole.EDITOR &&
                        it.invitedByUserId == currentUser.id &&
                        it.expiresAt.isAfter(now)
                }
            )
        }
    }

    @Test
    fun `should create invitation when none exists`() {
        val now = Instant.parse("2026-02-14T09:00:00Z")
        val currentUser = adminUser()
        val inviteId = UUID.randomUUID()
        val auditId = UUID.randomUUID()

        every { clock.nowInstant() } returns now
        every { userRepository.findByEmail("new.user@company.com") } returns null
        every { invitationRepository.findByCompanyAndEmail(currentUser.companyId, "new.user@company.com") } returns null
        every { idProvider.newId() } returnsMany listOf(inviteId, UUID.randomUUID(), auditId)
        every { invitationRepository.create(any()) } answers { firstArg() }
        every { emailService.sendTeamInviteEmail(any(), any()) } returns EmailDeliveryResult(
            status = EmailDeliveryStatus.SKIPPED_NOT_CONFIGURED,
            message = "RESEND_API_KEY is empty"
        )
        every { emailDeliveryRepository.create(any()) } answers { firstArg() }
        every { auditLogRepository.append(any()) } answers { firstArg() }

        val result = service.inviteMember(currentUser, InviteMemberRequest("new.user@company.com", UserRole.VIEWER))

        assertTrue(result is AppResult.Success)
        val payload = (result as AppResult.Success).value
        assertEquals(false, payload.reusedExisting)
        assertEquals(inviteId, payload.invitation.id)
        assertEquals("new.user@company.com", payload.invitation.email)
        assertEquals(UserRole.VIEWER, payload.invitation.role)
        assertTrue(payload.invitation.token.length >= 32)
        assertEquals(EmailDeliveryStatus.SKIPPED_NOT_CONFIGURED, payload.emailDelivery.status)
        verify(exactly = 1) { invitationRepository.create(any()) }
        verify(exactly = 1) { emailDeliveryRepository.create(any()) }
        verify(exactly = 1) { emailService.sendTeamInviteEmail("new.user@company.com", payload.invitation.token) }
    }

    @Test
    fun `should refresh expired invitation with new token`() {
        val now = Instant.parse("2026-02-14T09:00:00Z")
        val currentUser = adminUser()
        val expired = invitation(
            companyId = currentUser.companyId,
            invitedByUserId = UUID.randomUUID(),
            email = "expired@company.com",
            role = UserRole.VIEWER,
            token = "expired-token-1",
            expiresAt = now.minusSeconds(60),
            acceptedAt = null
        )

        every { clock.nowInstant() } returns now
        every { userRepository.findByEmail("expired@company.com") } returns null
        every { invitationRepository.findByCompanyAndEmail(currentUser.companyId, "expired@company.com") } returns expired
        every { invitationRepository.update(any()) } answers { firstArg() }
        every { idProvider.newId() } returnsMany listOf(UUID.randomUUID(), UUID.randomUUID())
        every { emailService.sendTeamInviteEmail(any(), any()) } returns EmailDeliveryResult(
            status = EmailDeliveryStatus.SENT,
            message = "Delivered by Resend"
        )
        every { emailDeliveryRepository.create(any()) } answers { firstArg() }
        every { auditLogRepository.append(any()) } answers { firstArg<AuditLogEntry>() }

        val result = service.inviteMember(currentUser, InviteMemberRequest("expired@company.com", UserRole.ADMIN))

        assertTrue(result is AppResult.Success)
        val payload = (result as AppResult.Success).value
        assertEquals(false, payload.reusedExisting)
        assertNotEquals("expired-token-1", payload.invitation.token)
        assertEquals(UserRole.ADMIN, payload.invitation.role)
        assertEquals(currentUser.id, payload.invitation.invitedByUserId)
        assertTrue(payload.invitation.expiresAt.isAfter(now))
        assertEquals(EmailDeliveryStatus.SENT, payload.emailDelivery.status)
        verify(exactly = 1) { invitationRepository.update(any()) }
        verify(exactly = 1) { emailDeliveryRepository.create(any()) }
        verify(exactly = 1) {
            invitationRepository.update(
                match {
                    it.id == expired.id &&
                        it.invitedByUserId == currentUser.id &&
                        it.role == UserRole.ADMIN &&
                        it.acceptedAt == null &&
                        it.createdAt == now &&
                        it.token != "expired-token-1"
                }
            )
        }
    }

    @Test
    fun `should resend invitation by id`() {
        val now = Instant.parse("2026-02-14T09:00:00Z")
        val currentUser = adminUser()
        val invitation = invitation(
            companyId = currentUser.companyId,
            invitedByUserId = UUID.randomUUID(),
            email = "reinvite@company.com",
            role = UserRole.VIEWER,
            token = "token-123",
            expiresAt = now.plus(Duration.ofDays(1)),
            acceptedAt = null
        )
        every { clock.nowInstant() } returns now
        every { invitationRepository.findById(invitation.id) } returns invitation
        every { invitationRepository.update(any()) } answers { firstArg() }
        every { idProvider.newId() } returnsMany listOf(UUID.randomUUID(), UUID.randomUUID())
        every { emailService.sendTeamInviteEmail(any(), any()) } returns EmailDeliveryResult(
            status = EmailDeliveryStatus.SENT,
            message = "Delivered by Resend"
        )
        every { emailDeliveryRepository.create(any()) } answers { firstArg() }
        every { auditLogRepository.append(any()) } answers { firstArg() }

        val result = service.resendInvitation(currentUser, invitation.id)

        assertTrue(result is AppResult.Success)
        val payload = (result as AppResult.Success).value
        assertTrue(payload.reusedExisting)
        assertEquals(invitation.id, payload.invitation.id)
        assertEquals(EmailDeliveryStatus.SENT, payload.emailDelivery.status)
        verify(exactly = 1) { invitationRepository.findById(invitation.id) }
        verify(exactly = 1) { invitationRepository.update(any()) }
        verify(exactly = 1) { emailDeliveryRepository.create(any()) }
    }

    @Test
    fun `should cancel active invitation`() {
        val now = Instant.parse("2026-02-14T09:00:00Z")
        val currentUser = adminUser()
        val activeInvitation = invitation(
            companyId = currentUser.companyId,
            invitedByUserId = UUID.randomUUID(),
            email = "cancel@company.com",
            role = UserRole.VIEWER,
            token = "active-token-1",
            expiresAt = now.plus(Duration.ofDays(2)),
            acceptedAt = null
        )
        every { clock.nowInstant() } returns now
        every { invitationRepository.findById(activeInvitation.id) } returns activeInvitation
        every { invitationRepository.update(any()) } answers { firstArg() }
        every { idProvider.newId() } returns UUID.randomUUID()
        every { auditLogRepository.append(any()) } answers { firstArg() }

        val result = service.cancelInvitation(currentUser, activeInvitation.id)

        assertTrue(result is AppResult.Success)
        verify(exactly = 1) { invitationRepository.findById(activeInvitation.id) }
        verify(exactly = 1) {
            invitationRepository.update(
                match {
                    it.id == activeInvitation.id &&
                        it.invitedByUserId == currentUser.id &&
                        it.token != activeInvitation.token &&
                        it.expiresAt.isBefore(now)
                }
            )
        }
        verify(exactly = 1) { auditLogRepository.append(any()) }
    }

    // ---- Helpers ----

    private fun sha256Hex(input: String): String =
        MessageDigest.getInstance("SHA-256")
            .digest(input.toByteArray(Charsets.UTF_8))
            .joinToString("") { "%02x".format(it) }

    private fun adminUser(id: UUID = UUID.randomUUID()): User = User(
        id = id,
        companyId = UUID.randomUUID(),
        email = "admin@company.com",
        name = "Admin",
        passwordHash = "hash",
        role = UserRole.ADMIN,
        isActive = true,
        lastLoginAt = Instant.parse("2026-02-14T09:00:00Z"),
        createdAt = Instant.parse("2026-02-14T09:00:00Z")
    )

    private fun activeUser(id: UUID = UUID.randomUUID()): User = User(
        id = id,
        companyId = UUID.randomUUID(),
        email = "user@company.com",
        name = "User",
        passwordHash = "hash",
        role = UserRole.VIEWER,
        isActive = true,
        lastLoginAt = Instant.parse("2026-03-10T09:00:00Z"),
        createdAt = Instant.parse("2026-01-01T00:00:00Z")
    )

    private fun company(id: UUID = UUID.randomUUID()): Company = Company(
        id = id,
        name = "Test Co",
        domain = "test.co",
        stripeCustomerId = null,
        subscriptionStatus = CompanySubscriptionStatus.TRIAL,
        trialEndsAt = Instant.parse("2026-06-01T00:00:00Z"),
        monthlyBudget = BigDecimal("1000.00"),
        employeeCount = null,
        settings = "{}",
        createdAt = Instant.parse("2026-01-01T00:00:00Z"),
        updatedAt = Instant.parse("2026-01-01T00:00:00Z")
    )

    private fun invitation(
        companyId: UUID,
        invitedByUserId: UUID,
        email: String,
        role: UserRole,
        token: String,
        expiresAt: Instant,
        acceptedAt: Instant?
    ): TeamInvitation = TeamInvitation(
        id = UUID.randomUUID(),
        companyId = companyId,
        invitedByUserId = invitedByUserId,
        email = email,
        role = role,
        token = token,
        expiresAt = expiresAt,
        acceptedAt = acceptedAt,
        createdAt = Instant.parse("2026-02-14T08:00:00Z")
    )
}
