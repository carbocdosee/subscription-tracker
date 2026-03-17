package com.saastracker.transport.security

import com.nimbusds.jose.JOSEObjectType
import com.nimbusds.jose.JWSAlgorithm
import com.nimbusds.jose.JWSHeader
import com.nimbusds.jose.crypto.MACSigner
import com.nimbusds.jose.crypto.MACVerifier
import com.nimbusds.jwt.JWTClaimsSet
import com.nimbusds.jwt.SignedJWT
import com.saastracker.config.JwtConfig
import com.saastracker.domain.model.UserRole
import java.security.MessageDigest
import java.time.Instant
import java.util.Date
import java.util.UUID
import org.slf4j.LoggerFactory

data class PrincipalUser(
    val userId: UUID,
    val companyId: UUID,
    val email: String,
    val role: UserRole
)

class JwtService(private val config: JwtConfig) {
    private val logger = LoggerFactory.getLogger(JwtService::class.java)
    private val signer = MACSigner(config.secret.toByteArray(Charsets.UTF_8))
    private val verifier = MACVerifier(config.secret.toByteArray(Charsets.UTF_8))

    val refreshTokenTtlDays: Long get() = config.refreshTokenTtlDays

    fun generateRefreshToken(): String =
        UUID.randomUUID().toString() + UUID.randomUUID().toString()

    fun hashRefreshToken(token: String): String =
        MessageDigest.getInstance("SHA-256")
            .digest(token.toByteArray())
            .joinToString("") { "%02x".format(it) }

    fun createToken(user: PrincipalUser, now: Instant = Instant.now()): String {
        val expiresAt = now.plusSeconds(config.accessTokenTtlMinutes * 60)
        val claims = JWTClaimsSet.Builder()
            .issuer(config.issuer)
            .audience(config.audience)
            .subject(user.userId.toString())
            .issueTime(Date.from(now))
            .expirationTime(Date.from(expiresAt))
            .claim("company_id", user.companyId.toString())
            .claim("email", user.email)
            .claim("role", user.role.name)
            .jwtID(UUID.randomUUID().toString())
            .build()

        val header = JWSHeader.Builder(JWSAlgorithm.HS256)
            .type(JOSEObjectType.JWT)
            .build()
        val signedJwt = SignedJWT(header, claims)
        signedJwt.sign(signer)
        return signedJwt.serialize()
    }

    fun parseToken(rawToken: String): PrincipalUser? {
        val jwt = runCatching { SignedJWT.parse(rawToken) }.getOrElse {
            logger.warn("JWT parsing failed: {}", it.message)
            return null
        }
        val validSignature = runCatching { jwt.verify(verifier) }.getOrElse {
            logger.warn("JWT signature verification raised error: {}", it.message)
            false
        }
        if (!validSignature) {
            logger.warn("JWT signature verification failed")
            return null
        }

        val claims = jwt.jwtClaimsSet ?: return null
        if (claims.issuer != config.issuer) {
            logger.warn("JWT issuer mismatch. expected='{}' actual='{}'", config.issuer, claims.issuer)
            return null
        }
        if (claims.audience.none { it == config.audience }) {
            logger.warn("JWT audience mismatch. expected='{}' actual='{}'", config.audience, claims.audience)
            return null
        }
        if (claims.expirationTime?.before(Date()) == true) {
            logger.warn("JWT expired at {}", claims.expirationTime)
            return null
        }

        val userId = runCatching { UUID.fromString(claims.subject) }.getOrElse {
            logger.warn("JWT subject is invalid UUID: {}", claims.subject)
            return null
        }
        val companyId = runCatching { UUID.fromString(claims.getStringClaim("company_id")) }.getOrElse {
            logger.warn("JWT company_id is invalid UUID: {}", claims.getStringClaim("company_id"))
            return null
        }
        val email = claims.getStringClaim("email") ?: run {
            logger.warn("JWT email claim missing")
            return null
        }
        val role = runCatching { UserRole.valueOf(claims.getStringClaim("role")) }.getOrElse {
            logger.warn("JWT role claim is invalid: {}", claims.getStringClaim("role"))
            return null
        }
        return PrincipalUser(userId = userId, companyId = companyId, email = email, role = role)
    }
}
