package data.atlassian

/**
 * Redacts Atlassian OAuth tokens, Authorization headers, and refresh bodies from
 * log/metric/error strings so secrets never leak into observability.
 *
 * APE-10
 */
object AtlassianLogRedactor {
    private val bearer = Regex("""(?i)Bearer\s+[A-Za-z0-9._\-+=/]+""")
    private val formSecrets = Regex(
        """(?i)(access_token|refresh_token|client_secret|client_id)=([^&\s"']+)"""
    )
    private val jsonSecrets = Regex(
        """(?i)"(access_token|refresh_token|client_secret|authorization)"\s*:\s*"[^"]*""""
    )
    private val authHeader = Regex("""(?i)(Authorization\s*[:=]\s*)([^\s,;]+)""")

    fun redact(message: String?): String {
        if (message.isNullOrEmpty()) return message.orEmpty()
        var out = message
        out = bearer.replace(out, "Bearer [REDACTED]")
        out = formSecrets.replace(out) { mr -> "${mr.groupValues[1]}=[REDACTED]" }
        out = jsonSecrets.replace(out) { mr -> "\"${mr.groupValues[1]}\":\"[REDACTED]\"" }
        out = authHeader.replace(out) { mr -> "${mr.groupValues[1]}[REDACTED]" }
        return out
    }

    /** Safe error message for API responses / exceptions — never includes raw bodies. */
    fun safeError(prefix: String, status: Int? = null, detail: String? = null): String {
        val parts = buildList {
            add(prefix)
            if (status != null) add("status=$status")
            if (!detail.isNullOrBlank()) add(redact(detail).take(200))
        }
        return parts.joinToString(": ")
    }
}
