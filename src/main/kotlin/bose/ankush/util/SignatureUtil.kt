package bose.ankush.util

import javax.crypto.Mac
import javax.crypto.spec.SecretKeySpec

/**
 * Utility for cryptographic signatures
 */
object SignatureUtil {
    /** Generate HMAC-SHA256 and return as lowercase hex string */
    fun secure(data: String, secret: String): String {
        val mac = Mac.getInstance("HmacSHA256")
        val secretKeySpec = SecretKeySpec(secret.toByteArray(Charsets.UTF_8), "HmacSHA256")
        mac.init(secretKeySpec)
        val raw = mac.doFinal(data.toByteArray(Charsets.UTF_8))
        return raw.toHexLower()
    }

    /** Constant-time string comparison to prevent timing attacks */
    fun compare(a: String, b: String): Boolean {
        if (a.length != b.length) return false
        var result = 0
        for (i in a.indices) {
            result = result or (a[i].code xor b[i].code)
        }
        return result == 0
    }

    private fun ByteArray.toHexLower(): String {
        val hexChars = CharArray(this.size * 2)
        val hexArray = "0123456789abcdef".toCharArray()
        var j = 0
        for (b in this) {
            val v = b.toInt() and 0xFF
            hexChars[j++] = hexArray[v ushr 4]
            hexChars[j++] = hexArray[v and 0x0F]
        }
        return String(hexChars)
    }
}
