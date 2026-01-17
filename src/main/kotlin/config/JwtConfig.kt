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
                // The fact that we got TokenExpiredException (not another JWTVerificationException)
                // means the token's signature, algorithm, audience, and issuer were all valid
                // We just need to decode it to extract the email
                logger.debug("Token is expired, extracting email from decoded token")
                try {
                    val decodedJWT = JWT.decode(token)

                    // Double-check audience and issuer for extra security
                    val audience = decodedJWT.audience
                    val issuer = decodedJWT.issuer
                    val expectedAudience = Environment.getJwtAudience()
                    val expectedIssuer = Environment.getJwtIssuer()

                    if (audience == null || !audience.contains(expectedAudience)) {
                        logger.warn("Token audience mismatch: expected $expectedAudience, got $audience")
                        return null
                    }

                    if (issuer != expectedIssuer) {
                        logger.warn("Token issuer mismatch: expected $expectedIssuer, got $issuer")
                        return null
                    }

                    // Extract email from the decoded token
                    val email = decodedJWT.getClaim(Constants.Auth.JWT_CLAIM_EMAIL).asString()
                    if (email.isNullOrBlank()) {
                        logger.warn("Token does not contain email claim")
                        return null
                    }

                    return email
                } catch (e: Exception) {
                    // Token decoding failed
                    logger.warn("Token decoding failed: ${e.message}")
                    null
                }
            } catch (e: JWTVerificationException) {
                // Token is invalid for some other reason (wrong signature, audience, issuer, etc.)
                logger.warn("Token verification failed: ${e.message}")
                null
            }
        } catch (e: Exception) {
            // Any other exception means the token is invalid
            logger.error("Token validation error: ${e.message}", e)
            null
        }
    }
}
