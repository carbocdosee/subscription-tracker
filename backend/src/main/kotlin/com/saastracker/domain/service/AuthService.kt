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
import com.saastracker.persistence.repository.NotificationReadRepository
import com.saastracker.persistence.repository.PasswordResetRepository
import com.saastracker.persistence.repository.RefreshTokenRepository
import com.saastracker.persistence.repository.SubscriptionRepository
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
import java.security.MessageDigest
import java.time.Duration
import java.util.UUID

private val INVITATION_TTL = Duration.ofDays(2)
private const val MAX_INVITATIONS_PER_DAY = 20
private val PASSWORD_RESET_TTL = Duration.ofHours(1)

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
    private val subscriptionRepository: SubscriptionRepository,
    private val refreshTokenRepository: RefreshTokenRepository,
    private val notificationReadRepository: NotificationReadRepository,
    private val passwordResetRepository: PasswordResetRepository,
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
            subscriptionStatus = CompanySubscriptionStatus.TRIAL, // kept for Stripe compat, unused
            trialEndsAt = now.plus(Duration.ofDays(14)),         // kept in schema, unused
            planTier = com.saastracker.domain.model.PlanTier.FREE,
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

        val sentToday = invitationRepository.countCreatedSince(currentUser.companyId, now.minus(Duration.ofDays(1)))
        if (sentToday >= MAX_INVITATIONS_PER_DAY) {
            return AppResult.Failure(AppError.Forbidden("Daily invitation limit of $MAX_INVITATIONS_PER_DAY reached. Try again tomorrow."))
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
                    expiresAt = now.plus(INVITATION_TTL),
                    acceptedAt = null,
                    createdAt = now
                )
            )
            previousInvite.acceptedAt == null && previousInvite.expiresAt.isAfter(now) -> invitationRepository.update(
                previousInvite.copy(
                    invitedByUserId = currentUser.id,
                    role = request.role,
                    expiresAt = now.plus(INVITATION_TTL)
                )
            )
            else -> invitationRepository.update(
                previousInvite.copy(
                    invitedByUserId = currentUser.id,
                    role = request.role,
                    token = generateInvitationToken(),
                    expiresAt = now.plus(INVITATION_TTL),
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
            expiresAt = now.plus(INVITATION_TTL),
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

    /**
     * GDPR Art. 20 — Right to data portability.
     *
     * Returns a structured JSON-compatible map of all personal data held for the user.
     */
    fun exportPersonalData(userId: UUID): AppResult<Map<String, Any?>> {
        val user = userRepository.findById(userId)
            ?: return AppResult.Failure(AppError.NotFound("User not found"))
        val company = companyRepository.findById(user.companyId)

        // GDPR Art. 15 — all subscriptions (active + archived) created by this user
        val allSubs = subscriptionRepository.listByCompany(user.companyId) +
            subscriptionRepository.listArchivedByCompany(user.companyId)
        val subscriptions = allSubs
            .filter { it.createdById == userId }
            .map { sub ->
                mapOf(
                    "id" to sub.id.toString(),
                    "vendorName" to sub.vendorName,
                    "vendorUrl" to sub.vendorUrl,
                    "category" to sub.category,
                    "amount" to sub.amount.toPlainString(),
                    "currency" to sub.currency,
                    "billingCycle" to sub.billingCycle.name,
                    "renewalDate" to sub.renewalDate.toString(),
                    "status" to sub.status.name,
                    "archivedAt" to sub.archivedAt?.toString(),
                    "createdAt" to sub.createdAt.toString()
                )
            }

        // GDPR Art. 15 — all email delivery logs addressed to this user (no limit)
        val emailLogs = emailDeliveryRepository.listByRecipientEmail(user.email)
            .map { log ->
                mapOf(
                    "id" to log.id.toString(),
                    "templateType" to log.templateType.name,
                    "status" to log.status.name,
                    "providerMessageId" to log.providerMessageId,
                    "errorMessage" to log.errorMessage,
                    "createdAt" to log.createdAt.toString()
                )
            }

        val export = mapOf(
            "exportedAt" to clock.nowInstant().toString(),
            "profile" to mapOf(
                "id" to user.id.toString(),
                "email" to user.email,
                "name" to user.name,
                "role" to user.role.name,
                "isActive" to user.isActive,
                "createdAt" to user.createdAt.toString(),
                "lastLoginAt" to user.lastLoginAt?.toString()
            ),
            "company" to company?.let {
                mapOf(
                    "id" to it.id.toString(),
                    "name" to it.name,
                    "domain" to it.domain,
                    "subscriptionStatus" to it.subscriptionStatus.name,
                    "createdAt" to it.createdAt.toString()
                )
            },
            "subscriptions" to subscriptions,
            "emailDeliveryLogs" to emailLogs,
            "auditLog" to auditLogRepository.listByCompany(user.companyId)
                .filter { it.userId == userId }
                .map { entry ->
                    mapOf(
                        "action" to entry.action.name,
                        "entityType" to entry.entityType.name,
                        "createdAt" to entry.createdAt.toString()
                    )
                }
        )
        return AppResult.Success(export)
    }

    /**
     * Initiates a password reset flow.
     *
     * Always returns Success to prevent email-enumeration attacks — the caller
     * cannot distinguish between "user exists" and "user not found".
     */
    fun requestPasswordReset(email: String): AppResult<Unit> {
        val user = userRepository.findByEmail(email.trim().lowercase())
        if (user == null || !user.isActive) return AppResult.Success(Unit)

        val now = clock.nowInstant()
        val rawToken = generateSecureToken()
        val tokenHash = hashToken(rawToken)
        passwordResetRepository.deleteExpiredForUser(user.id)
        passwordResetRepository.create(user.id, tokenHash, now.plus(PASSWORD_RESET_TTL))

        runCatching {
            emailService.sendPasswordResetEmail(user.email, rawToken)
        }.onFailure { ex ->
            // Log but don't surface — the user always sees 200
            org.slf4j.LoggerFactory.getLogger(AuthService::class.java)
                .warn("Failed to send password reset email userId={}", user.id, ex)
        }
        return AppResult.Success(Unit)
    }

    /** Validates the reset token and replaces the user's password. */
    fun resetPassword(token: String, newPassword: String): AppResult<Unit> {
        val tokenHash = hashToken(token)
        val record = passwordResetRepository.findByHash(tokenHash)
            ?: return AppResult.Failure(AppError.Validation("Invalid or expired reset token"))
        if (record.usedAt != null)
            return AppResult.Failure(AppError.Validation("Reset token already used"))
        if (record.expiresAt.isBefore(clock.nowInstant()))
            return AppResult.Failure(AppError.Validation("Reset token expired"))

        val user = userRepository.findById(record.userId)
            ?: return AppResult.Failure(AppError.NotFound("User not found"))
        if (!user.isActive)
            return AppResult.Failure(AppError.Forbidden("User is inactive"))

        userRepository.update(user.copy(passwordHash = passwordService.hash(newPassword)))
        passwordResetRepository.markUsed(record.id)
        refreshTokenRepository.revokeAllForUser(user.id) // invalidate all active sessions
        return AppResult.Success(Unit)
    }

    /**
     * GDPR Art. 17 — Right to erasure.
     *
     * If the user is the last member of their company, the whole company record is
     * deleted (all related data cascades via DB foreign keys).  Otherwise, the user's
     * personal data is anonymised in-place so that audit and billing records remain
     * internally consistent while all PII is removed.
     */
    fun deleteAccount(userId: UUID): AppResult<Unit> {
        val user = userRepository.findById(userId)
            ?: return AppResult.Failure(AppError.NotFound("User not found"))

        refreshTokenRepository.revokeAllForUser(userId)
        notificationReadRepository.clearForUser(userId)

        val remainingMembers = userRepository.listByCompany(user.companyId)
            .filter { it.id != userId }

        if (remainingMembers.isEmpty()) {
            companyRepository.delete(user.companyId) // cascades all company data
        } else {
            val anonymised = user.copy(
                email = "deleted-$userId@gdpr.invalid",
                name = "Deleted User",
                passwordHash = "",
                isActive = false
            )
            userRepository.update(anonymised)
        }

        return AppResult.Success(Unit)
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
                providerResponse = null, // GDPR: full provider body may contain PII; only status/id are retained
                errorMessage = if (result.status == EmailDeliveryStatus.FAILED) result.message else null,
                createdAt = now
            )
        )
        return result
    }
}

private fun generateInvitationToken(): String =
    UUID.randomUUID().toString().replace("-", "") + UUID.randomUUID().toString().replace("-", "")

private fun generateSecureToken(): String =
    UUID.randomUUID().toString().replace("-", "") + UUID.randomUUID().toString().replace("-", "")

private fun hashToken(token: String): String =
    MessageDigest.getInstance("SHA-256")
        .digest(token.toByteArray(Charsets.UTF_8))
        .joinToString("") { "%02x".format(it) }

private fun User.toPrincipal(): PrincipalUser = PrincipalUser(
    userId = id,
    companyId = companyId,
    email = email,
    role = role
)
