package com.saastracker.domain.service

import com.saastracker.domain.error.AppError
import com.saastracker.domain.error.AppResult
import com.saastracker.domain.model.AuditAction
import com.saastracker.domain.model.AuditEntityType
import com.saastracker.domain.model.AuditLogEntry
import com.saastracker.domain.model.Company
import com.saastracker.domain.model.CompanySubscriptionStatus
import com.saastracker.domain.model.EmailDeliveryLog
import com.saastracker.domain.model.EmailDeliveryState
import com.saastracker.domain.model.EmailTemplateType
import com.saastracker.domain.model.TeamInvitation
import com.saastracker.domain.model.User
import com.saastracker.domain.model.UserRole
import com.saastracker.persistence.repository.AuditLogRepository
import com.saastracker.persistence.repository.ClockProvider
import com.saastracker.persistence.repository.CompanyRepository
import com.saastracker.persistence.repository.EmailDeliveryRepository
import com.saastracker.persistence.repository.IdentityProvider
import com.saastracker.persistence.repository.RefreshTokenRepository
import com.saastracker.persistence.repository.TeamInvitationRepository
import com.saastracker.persistence.repository.UserRepository
import com.saastracker.transport.email.EmailService
import com.saastracker.transport.email.EmailDeliveryResult
import com.saastracker.transport.email.EmailDeliveryStatus
import com.saastracker.transport.security.JwtService
import com.saastracker.transport.security.PrincipalUser
import com.saastracker.transport.http.request.AcceptInviteRequest
import com.saastracker.transport.http.request.InviteMemberRequest
import com.saastracker.transport.http.request.LoginRequest
import com.saastracker.transport.http.request.RegisterRequest
import java.math.BigDecimal
import java.time.Duration
import java.util.UUID

data class AuthPayload(
    val accessToken: String,
    val refreshToken: String,
    val user: User
)

data class InviteMemberResult(
    val invitation: TeamInvitation,
    val reusedExisting: Boolean,
    val emailDelivery: EmailDeliveryResult
)

class AuthService(
    private val companyRepository: CompanyRepository,
    private val userRepository: UserRepository,
    private val invitationRepository: TeamInvitationRepository,
    private val auditLogRepository: AuditLogRepository,
    private val emailDeliveryRepository: EmailDeliveryRepository,
    private val refreshTokenRepository: RefreshTokenRepository,
    private val passwordService: PasswordService,
    private val jwtService: JwtService,
    private val emailService: EmailService,
    private val idProvider: IdentityProvider,
    private val clock: ClockProvider
) {
    fun register(request: RegisterRequest): AppResult<AuthPayload> {
        val existingDomain = companyRepository.findByDomain(request.companyDomain.lowercase())
        if (existingDomain != null) {
            return AppResult.Failure(AppError.Conflict("Company domain already exists"))
        }
        val existingUser = userRepository.findByEmail(request.email.lowercase())
        if (existingUser != null) {
            return AppResult.Failure(AppError.Conflict("Email already exists"))
        }

        val now = clock.nowInstant()
        val company = Company(
            id = idProvider.newId(),
            name = request.companyName.trim(),
            domain = request.companyDomain.trim().lowercase(),
            stripeCustomerId = null,
            subscriptionStatus = CompanySubscriptionStatus.TRIAL,
            trialEndsAt = now.plus(Duration.ofDays(14)),
            monthlyBudget = request.monthlyBudget?.toBigDecimalOrNull(),
            employeeCount = null,
            settings = "{}",
            createdAt = now,
            updatedAt = now
        )
        val savedCompany = companyRepository.create(company)

        val user = User(
            id = idProvider.newId(),
            companyId = savedCompany.id,
            email = request.email.trim().lowercase(),
            name = request.fullName.trim(),
            passwordHash = passwordService.hash(request.password),
            role = UserRole.ADMIN,
            isActive = true,
            lastLoginAt = now,
            createdAt = now
        )
        val savedUser = userRepository.create(user)
        val accessToken = jwtService.createToken(savedUser.toPrincipal())
        val refreshToken = jwtService.generateRefreshToken()
        refreshTokenRepository.create(
            savedUser.id,
            jwtService.hashRefreshToken(refreshToken),
            clock.nowInstant().plus(Duration.ofDays(jwtService.refreshTokenTtlDays))
        )
        return AppResult.Success(AuthPayload(accessToken, refreshToken, savedUser))
    }

    fun login(request: LoginRequest): AppResult<AuthPayload> {
        val user = userRepository.findByEmail(request.email.trim().lowercase())
            ?: return AppResult.Failure(AppError.Unauthorized("Invalid email or password"))

        if (!user.isActive) {
            return AppResult.Failure(AppError.Forbidden("User is inactive"))
        }
        if (!passwordService.verify(request.password, user.passwordHash)) {
            return AppResult.Failure(AppError.Unauthorized("Invalid email or password"))
        }

        val updated = user.copy(lastLoginAt = clock.nowInstant())
        userRepository.update(updated)
        val accessToken = jwtService.createToken(updated.toPrincipal())
        val refreshToken = jwtService.generateRefreshToken()
        refreshTokenRepository.create(
            updated.id,
            jwtService.hashRefreshToken(refreshToken),
            clock.nowInstant().plus(Duration.ofDays(jwtService.refreshTokenTtlDays))
        )
        return AppResult.Success(AuthPayload(accessToken, refreshToken, updated))
    }

    fun refresh(rawRefreshToken: String): AppResult<AuthPayload> {
        val hash = jwtService.hashRefreshToken(rawRefreshToken)
        val record = refreshTokenRepository.findByHash(hash)
            ?: return AppResult.Failure(AppError.Unauthorized("Invalid refresh token"))
        if (record.revokedAt != null)
            return AppResult.Failure(AppError.Unauthorized("Refresh token revoked"))
        if (record.expiresAt.isBefore(clock.nowInstant()))
            return AppResult.Failure(AppError.Unauthorized("Refresh token expired"))

        val user = userRepository.findById(record.userId)
            ?: return AppResult.Failure(AppError.Unauthorized("User not found"))
        if (!user.isActive)
            return AppResult.Failure(AppError.Unauthorized("User is inactive"))

        refreshTokenRepository.revoke(record.id)
        val newRefreshToken = jwtService.generateRefreshToken()
        refreshTokenRepository.create(
            user.id,
            jwtService.hashRefreshToken(newRefreshToken),
            clock.nowInstant().plus(Duration.ofDays(jwtService.refreshTokenTtlDays))
        )
        val newAccessToken = jwtService.createToken(user.toPrincipal())
        return AppResult.Success(AuthPayload(newAccessToken, newRefreshToken, user))
    }

    fun logout(rawRefreshToken: String): AppResult<Unit> {
        val hash = jwtService.hashRefreshToken(rawRefreshToken)
        val record = refreshTokenRepository.findByHash(hash) ?: return AppResult.Success(Unit)
        refreshTokenRepository.revoke(record.id)
        return AppResult.Success(Unit)
    }

    fun inviteMember(
        currentUser: User,
        request: InviteMemberRequest
    ): AppResult<InviteMemberResult> {
        if (currentUser.role != UserRole.ADMIN) {
            return AppResult.Failure(AppError.Forbidden("Only admins can invite users"))
        }

        val email = request.email.trim().lowercase()
        val now = clock.nowInstant()
        val existingUser = userRepository.findByEmail(email)
        if (existingUser != null && existingUser.companyId == currentUser.companyId) {
            return AppResult.Failure(AppError.Conflict("User already exists in company"))
        }

        val previousInvite = invitationRepository.findByCompanyAndEmail(currentUser.companyId, email)
        val invitation = when {
            previousInvite == null -> invitationRepository.create(
                TeamInvitation(
                    id = idProvider.newId(),
                    companyId = currentUser.companyId,
                    invitedByUserId = currentUser.id,
                    email = email,
                    role = request.role,
                    token = generateInvitationToken(),
                    expiresAt = now.plus(Duration.ofDays(7)),
                    acceptedAt = null,
                    createdAt = now
                )
            )
            previousInvite.acceptedAt == null && previousInvite.expiresAt.isAfter(now) -> invitationRepository.update(
                previousInvite.copy(
                    invitedByUserId = currentUser.id,
                    role = request.role,
                    expiresAt = now.plus(Duration.ofDays(7))
                )
            )
            else -> invitationRepository.update(
                previousInvite.copy(
                    invitedByUserId = currentUser.id,
                    role = request.role,
                    token = generateInvitationToken(),
                    expiresAt = now.plus(Duration.ofDays(7)),
                    acceptedAt = null,
                    createdAt = now
                )
            )
        }

        val emailDelivery = sendInviteEmail(invitation)
        val reusedExisting = previousInvite != null && previousInvite.acceptedAt == null && previousInvite.expiresAt.isAfter(now)

        auditLogRepository.append(
            AuditLogEntry(
                id = idProvider.newId(),
                companyId = currentUser.companyId,
                userId = currentUser.id,
                action = AuditAction.INVITED,
                entityType = AuditEntityType.INVITATION,
                entityId = invitation.id,
                oldValue = null,
                newValue = """{"email":"${invitation.email}","role":"${invitation.role.name}","reusedExisting":$reusedExisting,"emailDelivery":"${emailDelivery.status.name}"}""",
                createdAt = now
            )
        )
        return AppResult.Success(
            InviteMemberResult(
                invitation = invitation,
                reusedExisting = reusedExisting,
                emailDelivery = emailDelivery
            )
        )
    }

    fun resendInvitation(currentUser: User, invitationId: UUID): AppResult<InviteMemberResult> {
        if (currentUser.role != UserRole.ADMIN) {
            return AppResult.Failure(AppError.Forbidden("Only admins can resend invitations"))
        }

        val invitation = invitationRepository.findById(invitationId)
            ?: return AppResult.Failure(AppError.NotFound("Invitation not found"))
        if (invitation.companyId != currentUser.companyId) {
            return AppResult.Failure(AppError.Forbidden("Cross-company access denied"))
        }
        if (invitation.acceptedAt != null) {
            return AppResult.Failure(AppError.Conflict("Invitation already accepted"))
        }

        val now = clock.nowInstant()
        val expired = invitation.expiresAt.isBefore(now)
        val refreshed = invitation.copy(
            invitedByUserId = currentUser.id,
            token = if (expired) generateInvitationToken() else invitation.token,
            expiresAt = now.plus(Duration.ofDays(7)),
            createdAt = if (expired) now else invitation.createdAt
        )
        val updatedInvitation = invitationRepository.update(refreshed)
        val emailDelivery = sendInviteEmail(updatedInvitation)

        auditLogRepository.append(
            AuditLogEntry(
                id = idProvider.newId(),
                companyId = currentUser.companyId,
                userId = currentUser.id,
                action = AuditAction.INVITED,
                entityType = AuditEntityType.INVITATION,
                entityId = updatedInvitation.id,
                oldValue = null,
                newValue = """{"email":"${updatedInvitation.email}","role":"${updatedInvitation.role.name}","reusedExisting":true,"emailDelivery":"${emailDelivery.status.name}","resend":true}""",
                createdAt = now
            )
        )

        return AppResult.Success(
            InviteMemberResult(
                invitation = updatedInvitation,
                reusedExisting = true,
                emailDelivery = emailDelivery
            )
        )
    }

    fun cancelInvitation(currentUser: User, invitationId: UUID): AppResult<Unit> {
        if (currentUser.role != UserRole.ADMIN) {
            return AppResult.Failure(AppError.Forbidden("Only admins can cancel invitations"))
        }

        val invitation = invitationRepository.findById(invitationId)
            ?: return AppResult.Failure(AppError.NotFound("Invitation not found"))
        if (invitation.companyId != currentUser.companyId) {
            return AppResult.Failure(AppError.Forbidden("Cross-company access denied"))
        }
        if (invitation.acceptedAt != null) {
            return AppResult.Failure(AppError.Conflict("Cannot cancel accepted invitation"))
        }

        val now = clock.nowInstant()
        val canceled = invitation.copy(
            invitedByUserId = currentUser.id,
            token = generateInvitationToken(),
            expiresAt = now.minusSeconds(1)
        )
        invitationRepository.update(canceled)

        auditLogRepository.append(
            AuditLogEntry(
                id = idProvider.newId(),
                companyId = currentUser.companyId,
                userId = currentUser.id,
                action = AuditAction.DELETED,
                entityType = AuditEntityType.INVITATION,
                entityId = invitation.id,
                oldValue = """{"email":"${invitation.email}","role":"${invitation.role.name}","expiresAt":"${invitation.expiresAt}"}""",
                newValue = """{"status":"CANCELED","expiresAt":"${canceled.expiresAt}"}""",
                createdAt = now
            )
        )
        return AppResult.Success(Unit)
    }

    fun acceptInvite(request: AcceptInviteRequest): AppResult<AuthPayload> {
        val invitation = invitationRepository.findByToken(request.token)
            ?: return AppResult.Failure(AppError.NotFound("Invitation not found"))

        if (invitation.acceptedAt != null) {
            return AppResult.Failure(AppError.Conflict("Invitation already accepted"))
        }
        if (invitation.expiresAt.isBefore(clock.nowInstant())) {
            return AppResult.Failure(AppError.Validation("Invitation expired"))
        }
        if (userRepository.findByEmail(invitation.email) != null) {
            return AppResult.Failure(AppError.Conflict("Email already registered"))
        }

        val user = User(
            id = idProvider.newId(),
            companyId = invitation.companyId,
            email = invitation.email,
            name = request.fullName.trim(),
            passwordHash = passwordService.hash(request.password),
            role = invitation.role,
            isActive = true,
            lastLoginAt = clock.nowInstant(),
            createdAt = clock.nowInstant()
        )
        val savedUser = userRepository.create(user)
        invitationRepository.update(invitation.copy(acceptedAt = clock.nowInstant()))

        auditLogRepository.append(
            AuditLogEntry(
                id = idProvider.newId(),
                companyId = invitation.companyId,
                userId = savedUser.id,
                action = AuditAction.ACCEPTED_INVITE,
                entityType = AuditEntityType.USER,
                entityId = savedUser.id,
                oldValue = null,
                newValue = """{"email":"${savedUser.email}","role":"${savedUser.role.name}"}""",
                createdAt = clock.nowInstant()
            )
        )

        val accessToken = jwtService.createToken(savedUser.toPrincipal())
        val refreshToken = jwtService.generateRefreshToken()
        refreshTokenRepository.create(
            savedUser.id,
            jwtService.hashRefreshToken(refreshToken),
            clock.nowInstant().plus(Duration.ofDays(jwtService.refreshTokenTtlDays))
        )
        return AppResult.Success(AuthPayload(accessToken, refreshToken, savedUser))
    }

    fun changeUserRole(currentUser: User, targetUserId: UUID, role: UserRole): AppResult<User> {
        if (currentUser.role != UserRole.ADMIN) {
            return AppResult.Failure(AppError.Forbidden("Only admins can change roles"))
        }
        val targetUser = userRepository.findById(targetUserId)
            ?: return AppResult.Failure(AppError.NotFound("User not found"))
        if (targetUser.companyId != currentUser.companyId) {
            return AppResult.Failure(AppError.Forbidden("Cross-company access denied"))
        }
        if (currentUser.id == targetUserId) {
            return AppResult.Failure(AppError.Forbidden("Cannot change your own role"))
        }

        val updated = targetUser.copy(role = role)
        userRepository.update(updated)
        auditLogRepository.append(
            AuditLogEntry(
                id = idProvider.newId(),
                companyId = currentUser.companyId,
                userId = currentUser.id,
                action = AuditAction.ROLE_CHANGED,
                entityType = AuditEntityType.USER,
                entityId = targetUser.id,
                oldValue = """{"role":"${targetUser.role.name}"}""",
                newValue = """{"role":"${role.name}"}""",
                createdAt = clock.nowInstant()
            )
        )
        return AppResult.Success(updated)
    }

    private fun sendInviteEmail(invitation: TeamInvitation): EmailDeliveryResult {
        val now = clock.nowInstant()
        val result = runCatching {
            emailService.sendTeamInviteEmail(invitation.email, invitation.token)
        }.getOrElse { ex ->
            EmailDeliveryResult(
                status = EmailDeliveryStatus.FAILED,
                message = ex.message ?: "Invite email sending failed"
            )
        }
        emailDeliveryRepository.create(
            EmailDeliveryLog(
                id = idProvider.newId(),
                companyId = invitation.companyId,
                invitationId = invitation.id,
                recipientEmail = invitation.email,
                templateType = EmailTemplateType.TEAM_INVITE,
                status = when (result.status) {
                    EmailDeliveryStatus.SENT -> EmailDeliveryState.SENT
                    EmailDeliveryStatus.SKIPPED_NOT_CONFIGURED -> EmailDeliveryState.SKIPPED_NOT_CONFIGURED
                    EmailDeliveryStatus.FAILED -> EmailDeliveryState.FAILED
                },
                providerMessageId = result.providerMessageId,
                providerStatusCode = result.providerStatusCode,
                providerResponse = result.providerResponse,
                errorMessage = if (result.status == EmailDeliveryStatus.FAILED) result.message else null,
                createdAt = now
            )
        )
        return result
    }
}

private fun generateInvitationToken(): String =
    UUID.randomUUID().toString().replace("-", "") + UUID.randomUUID().toString().replace("-", "")

private fun User.toPrincipal(): PrincipalUser = PrincipalUser(
    userId = id,
    companyId = companyId,
    email = email,
    role = role
)
