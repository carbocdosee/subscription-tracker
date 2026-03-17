package com.saastracker.domain.service

import com.saastracker.domain.error.AppError
import com.saastracker.domain.error.AppResult
import com.saastracker.domain.model.AuditLogEntry
import com.saastracker.domain.model.TeamInvitation
import com.saastracker.domain.model.User
import com.saastracker.domain.model.UserRole
import com.saastracker.persistence.repository.AuditLogRepository
import com.saastracker.persistence.repository.ClockProvider
import com.saastracker.persistence.repository.CompanyRepository
import com.saastracker.persistence.repository.EmailDeliveryRepository
import com.saastracker.persistence.repository.IdentityProvider
import com.saastracker.persistence.repository.RefreshTokenRecord
import com.saastracker.persistence.repository.RefreshTokenRepository
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
        refreshTokenRepository = refreshTokenRepository,
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

    // ---- Invitation tests (unchanged) ----

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
