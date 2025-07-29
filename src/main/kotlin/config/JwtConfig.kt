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

/**
 * JWT authentication configuration
 * Handles token generation, validation, and extraction of claims
 */
object JwtConfig {
    private val logger = LoggerFactory.getLogger(JwtConfig::class.java)
    
    // Get JWT secret from Secret Manager
    private val jwtSecret = getSecretValue(Constants.Auth.JWT_SECRET_NAME)
    private val algorithm = Algorithm.HMAC256(jwtSecret)

    // JWT verifier for token validation
    val verifier: JWTVerifier = JWT
        .require(algorithm)
        .withAudience(Environment.getJwtAudience())
        .withIssuer(Environment.getJwtIssuer())
        .build()

    // JWT verifier that ignores expiration for token refresh
    private val verifierIgnoringExpiration: JWTVerifier = JWT
        .require(algorithm)
        .withAudience(Environment.getJwtAudience())
        .withIssuer(Environment.getJwtIssuer())
        .acceptExpiresAt(Long.MAX_VALUE) // Accept any expiration time
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
     * Validate an expired token and extract the email if valid
     * @param token The expired JWT token
     * @return The email from the token if it's valid (except for expiration), null otherwise
     */
    fun validateExpiredTokenAndExtractEmail(token: String): String? {
        return try {
            // First check if the token is actually expired
            try {
                verifier.verify(token)
                // If we get here, the token is still valid, so we shouldn't refresh it
                logger.debug("Token is still valid, no need to refresh")
                return null
            } catch (_: TokenExpiredException) {
                // Token is expired, which is what we want for refresh
                // Now verify everything else about the token
                logger.debug("Token is expired, verifying other claims")
                val jwt = verifierIgnoringExpiration.verify(token)
                jwt.getClaim(Constants.Auth.JWT_CLAIM_EMAIL).asString()
            } catch (e: JWTVerificationException) {
                // Token is invalid for some other reason
                logger.warn("Token verification failed: ${e.message}")
                null
            }
        } catch (e: Exception) {
            // Any other exception means the token is invalid
            logger.error("Token validation error: ${e.message}")
            null
        }
    }
}