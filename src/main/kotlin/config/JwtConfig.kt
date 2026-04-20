package config

import bose.ankush.data.model.UserRole
import bose.ankush.util.getSecretValue
import com.auth0.jwt.JWT
import com.auth0.jwt.JWTVerifier
import com.auth0.jwt.algorithms.Algorithm
import com.auth0.jwt.exceptions.JWTVerificationException
import com.auth0.jwt.exceptions.TokenExpiredException
import com.auth0.jwt.interfaces.Payload
import org.slf4j.LoggerFactory
import util.Constants
import java.util.Date

/** Result of a token-refresh eligibility check. */
sealed class TokenRefreshResult {
    /** Token was expired but otherwise valid; contains the email claim. */
    data class Expired(val email: String) : TokenRefreshResult()
    /** Token is still valid and does not need refreshing; contains the email claim. */
    data class StillValid(val email: String) : TokenRefreshResult()
    /** Token is malformed, has a bad signature, wrong issuer/audience, or unparseable. */
    object Invalid : TokenRefreshResult()
}

/**
 * JWT authentication configuration
 * Handles token generation, validation, and extraction of claims
 */
object JwtConfig {
    private val logger = LoggerFactory.getLogger(JwtConfig::class.java)

    // Get JWT secret from Secret Manager
    private val jwtSecret = getSecretValue(Constants.Auth.JWT_SECRET_NAME)
    private val algorithm = Algorithm.HMAC256(jwtSecret)

    // JWT verifier for token validation — internal so only the auth plugin uses it directly
    internal val verifier: JWTVerifier = JWT
        .require(algorithm)
        .withAudience(Environment.getJwtAudience())
        .withIssuer(Environment.getJwtIssuer())
        .build()

    /**
     * Generate a JWT token for a user with a specific role
     * @param email The user's email
     * @param role The user's role
     * @return A signed JWT token
     */
    fun generateToken(email: String, role: UserRole?): String {
        return JWT.create()
            .withAudience(Environment.getJwtAudience())
            .withIssuer(Environment.getJwtIssuer())
            .withClaim(Constants.Auth.JWT_CLAIM_EMAIL, email)
            .withClaim(Constants.Auth.JWT_CLAIM_ROLE, role?.name)
            .withExpiresAt(Date(System.currentTimeMillis() + Environment.getJwtExpiration()))
            .sign(algorithm)
    }

    /**
     * Determines if the user has the admin role based on their JWT token.
     * Uses a more robust comparison to handle edge cases like whitespace and case sensitivity.
     * @param payload The decoded JWT token.
     * @return `true` if the user has the admin role, `false` otherwise.
     */
    fun isAdmin(payload: Payload): Boolean {
        val roleClaim = payload.getClaim(Constants.Auth.JWT_CLAIM_ROLE)
        if (roleClaim.isNull) {
            logger.warn("Role claim is null in JWT payload")
            return false
        }

        val roleName = roleClaim.asString()
        if (roleName.isNullOrBlank()) {
            logger.warn("Role name is null or blank in JWT payload")
            return false
        }

        // Trim and use case-insensitive comparison for more robust validation
        val isAdmin = roleName.trim().equals(UserRole.ADMIN.name, ignoreCase = true)

        if (!isAdmin) {
            logger.debug("User role '$roleName' is not ADMIN")
        }

        return isAdmin
    }

    /**
     * Checks whether a token is eligible for refresh.
     *
     * - [TokenRefreshResult.StillValid] — token is not yet expired; no refresh needed.
     * - [TokenRefreshResult.Expired]    — token is expired but otherwise valid; issue a new one.
     * - [TokenRefreshResult.Invalid]    — token cannot be trusted (bad signature, issuer, etc.).
     */
    fun checkTokenForRefresh(token: String): TokenRefreshResult {
        return try {
            val decoded = verifier.verify(token)
            val email = decoded.getClaim(Constants.Auth.JWT_CLAIM_EMAIL).asString()
            if (email.isNullOrBlank()) {
                logger.warn("Token missing email claim")
                TokenRefreshResult.Invalid
            } else {
                logger.debug("Token is still valid for user: $email")
                TokenRefreshResult.StillValid(email)
            }
        } catch (_: TokenExpiredException) {
            // Signature, issuer and audience were valid — only expiry failed.
            // Decode without verification to read the email claim.
            try {
                val decoded = JWT.decode(token)
                val email = decoded.getClaim(Constants.Auth.JWT_CLAIM_EMAIL).asString()
                if (email.isNullOrBlank()) {
                    logger.warn("Expired token missing email claim")
                    TokenRefreshResult.Invalid
                } else {
                    logger.debug("Token is expired, eligible for refresh: $email")
                    TokenRefreshResult.Expired(email)
                }
            } catch (e: Exception) {
                logger.warn("Failed to decode expired token: ${e.message}")
                TokenRefreshResult.Invalid
            }
        } catch (e: JWTVerificationException) {
            logger.warn("Token verification failed: ${e.message}")
            TokenRefreshResult.Invalid
        } catch (e: Exception) {
            logger.error("Unexpected token validation error: ${e.message}", e)
            TokenRefreshResult.Invalid
        }
    }
}
