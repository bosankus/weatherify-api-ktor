package config

import bose.ankush.util.getSecretValue
import com.auth0.jwt.JWT
import com.auth0.jwt.JWTVerifier
import com.auth0.jwt.algorithms.Algorithm
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

    /** Generate a JWT token for a user */
    fun generateToken(email: String): String {
        return JWT.create()
            .withAudience(Environment.getJwtAudience())
            .withIssuer(Environment.getJwtIssuer())
            .withClaim(Constants.Auth.JWT_CLAIM_EMAIL, email)
            .withExpiresAt(Date(System.currentTimeMillis() + Environment.getJwtExpiration()))
            .sign(algorithm)
    }
}