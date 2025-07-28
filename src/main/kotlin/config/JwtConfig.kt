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
     * Generate a JWT token for a user
     * @param email The user's email
     * @return A signed JWT token with default USER role
     */
    fun generateToken(email: String): String {
        return generateToken(email, UserRole.USER)
    }

    /**
     * Generate a JWT token for a user with a specific role
     * @param email The user's email
     * @param role The user's role
     * @return A signed JWT token
     */
    fun generateToken(email: String, role: UserRole): String {
        return JWT.create()
            .withAudience(Environment.getJwtAudience())
            .withIssuer(Environment.getJwtIssuer())
            .withClaim(Constants.Auth.JWT_CLAIM_EMAIL, email)
            .withClaim(Constants.Auth.JWT_CLAIM_ROLE, role.name)
            .withExpiresAt(Date(System.currentTimeMillis() + Environment.getJwtExpiration()))
            .sign(algorithm)
    }

    /**
     * Extract the user role from a JWT token
     * @param payload The JWT payload
     * @return The user role, or USER if the role claim is missing or invalid
     */
    fun extractUserRole(payload: Payload): UserRole {
        return try {
            val roleName = payload.getClaim(Constants.Auth.JWT_CLAIM_ROLE).asString()
            if (roleName.isNullOrEmpty()) {
                logger.warn("Role claim is missing or empty in JWT token")
                UserRole.USER
            } else {
                try {
                    UserRole.valueOf(roleName)
                } catch (_: IllegalArgumentException) {
                    logger.warn("Invalid role in JWT token: $roleName")
                    UserRole.USER
                }
            }
        } catch (e: Exception) {
            logger.warn("Error extracting role from JWT token: ${e.message}")
            UserRole.USER
        }
    }

    /**
     * Check if a user has admin role based on their JWT token
     * @param payload The JWT payload
     * @return True if the user has admin role, false otherwise
     */
    fun isAdmin(payload: Payload): Boolean {
        return extractUserRole(payload) == UserRole.ADMIN
    }

    /**
     * Check if a user has moderator role or higher based on their JWT token
     * @param payload The JWT payload
     * @return True if the user has moderator or admin role, false otherwise
     */
    fun isModeratorOrAdmin(payload: Payload): Boolean {
        val role = extractUserRole(payload)
        return role == UserRole.MODERATOR || role == UserRole.ADMIN
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