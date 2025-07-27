package config

import bose.ankush.util.getSecretValue
import com.auth0.jwt.JWT
import com.auth0.jwt.JWTVerifier
import com.auth0.jwt.algorithms.Algorithm
import com.auth0.jwt.exceptions.JWTVerificationException
import com.auth0.jwt.exceptions.TokenExpiredException
import util.Constants
import java.util.Date

/** JWT authentication configuration */
object JwtConfig {
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

    /** Generate a JWT token for a user */
    fun generateToken(email: String): String {
        return JWT.create()
            .withAudience(Environment.getJwtAudience())
            .withIssuer(Environment.getJwtIssuer())
            .withClaim(Constants.Auth.JWT_CLAIM_EMAIL, email)
            .withExpiresAt(Date(System.currentTimeMillis() + Environment.getJwtExpiration()))
            .sign(algorithm)
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
                return null
            } catch (_: TokenExpiredException) {
                // Token is expired, which is what we want for refresh
                // Now verify everything else about the token
                val jwt = verifierIgnoringExpiration.verify(token)
                jwt.getClaim(Constants.Auth.JWT_CLAIM_EMAIL).asString()
            } catch (_: JWTVerificationException) {
                // Token is invalid for some other reason
                null
            }
        } catch (_: Exception) {
            // Any other exception means the token is invalid
            null
        }
    }
}