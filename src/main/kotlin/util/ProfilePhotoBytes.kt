package util

import org.slf4j.LoggerFactory
import java.io.File
import java.nio.charset.StandardCharsets
import java.nio.file.Files
import java.util.concurrent.TimeUnit

/**
 * Profile-photo bytes prepared before they are written to GCS.
 * JPEG is recompressed with MozJPEG. PNG is optimized with OxiPNG.
 * HEIC/HEIF is never stored. WebP and GIF are unchanged. SVG is rejected.
 * Neither path crops or resizes.
 */
object ProfilePhotoBytes {
    const val HEIC_REJECTED =
        "HEIC/HEIF is not accepted. Decode the photo on the device and upload a JPEG."

    const val JPEG_QUALITY = "92"

    const val ENCODER_UNAVAILABLE = "Profile photo encoder is unavailable"

    private val ALLOWED = setOf(
        "image/jpeg",
        "image/jpg",
        "image/png",
        "image/webp",
        "image/gif"
    )

    private val HEIC_TYPES = setOf(
        "image/heic",
        "image/heif",
        "image/heic-sequence",
        "image/heif-sequence"
    )

    private val HEIC_BRANDS = setOf(
        "heic", "heix", "hevc", "hevx", "heim", "heis", "heif", "hevm", "hevs"
    )

    private val logger = LoggerFactory.getLogger(ProfilePhotoBytes::class.java)

    data class PreparedPhoto(
        val bytes: ByteArray,
        val contentType: String
    )

    fun isAllowed(contentType: String): Boolean = normalizeType(contentType) in ALLOWED

    fun isHeic(bytes: ByteArray, contentType: String, filename: String? = null): Boolean {
        val type = normalizeType(contentType)
        if (type in HEIC_TYPES) return true
        val name = baseName(filename)
        if (name.endsWith(".heic") || name.endsWith(".heif")) return true
        return heifMagic(bytes)
    }

    fun isSvg(bytes: ByteArray, contentType: String, filename: String? = null): Boolean {
        val type = normalizeType(contentType)
        if (type == "image/svg+xml" || type == "image/svg") return true
        if (baseName(filename).endsWith(".svg")) return true
        val head = bytes.copyOfRange(0, minOf(bytes.size, 256))
            .toString(StandardCharsets.UTF_8)
            .trimStart()
            .lowercase()
        return head.startsWith("<svg") || (head.startsWith("<?xml") && head.contains("<svg"))
    }

    /**
     * Reject HEIC/HEIF, reject SVG, send JPEG through MozJPEG, send PNG through OxiPNG.
     * WebP and GIF are returned unchanged. Dimensions are never changed.
     */
    fun prepare(
        bytes: ByteArray,
        contentType: String,
        filename: String? = null,
        locate: (String) -> File? = { executable(it) }
    ): PreparedPhoto {
        if (isHeic(bytes, contentType, filename)) {
            throw ProfilePhotoRejectedException(HEIC_REJECTED)
        }
        if (isSvg(bytes, contentType, filename)) {
            throw ProfilePhotoRejectedException("Unsupported content type: image/svg+xml")
        }
        val type = normalizeType(contentType)
        if (type !in ALLOWED) {
            throw ProfilePhotoRejectedException("Unsupported content type: $contentType")
        }
        return when (type) {
            "image/jpeg", "image/jpg" -> PreparedPhoto(mozjpeg(bytes, locate), "image/jpeg")
            "image/png" -> PreparedPhoto(oxipng(bytes, locate), "image/png")
            else -> PreparedPhoto(bytes.copyOf(), type)
        }
    }

    private fun mozjpeg(bytes: ByteArray, locate: (String) -> File?): ByteArray {
        val cjpeg = locate("cjpeg") ?: encoderUnavailable("MozJPEG cjpeg is missing")
        if (!isMozjpeg(cjpeg)) encoderUnavailable("cjpeg is not MozJPEG")
        val djpeg = File(cjpeg.parentFile, "djpeg").takeIf { it.isFile && it.canExecute() }
            ?: encoderUnavailable("MozJPEG djpeg is missing")
        return try {
            inTempDir("profile-jpeg") { dir ->
                val input = File(dir, "in.jpg")
                val ppm = File(dir, "in.ppm")
                val output = File(dir, "out.jpg")
                input.writeBytes(bytes)
                run(djpeg.absolutePath, "-outfile", ppm.absolutePath, input.absolutePath)
                run(
                    cjpeg.absolutePath,
                    "-quality",
                    JPEG_QUALITY,
                    "-optimize",
                    "-outfile",
                    output.absolutePath,
                    ppm.absolutePath
                )
                val encoded = if (output.isFile) output.readBytes() else ByteArray(0)
                if (encoded.isNotEmpty() && encoded.size <= bytes.size) encoded else bytes
            }
        } catch (e: ProfilePhotoEncoderUnavailableException) {
            throw e
        } catch (e: Exception) {
            logger.warn("MozJPEG failed: ${e.message}")
            val reason = if (e.message?.contains("timed out") == true) "MozJPEG timed out" else "MozJPEG failed"
            encoderUnavailable(reason)
        }
    }

    private fun oxipng(bytes: ByteArray, locate: (String) -> File?): ByteArray {
        val oxipng = locate("oxipng") ?: encoderUnavailable("OxiPNG is missing")
        return try {
            inTempDir("profile-png") { dir ->
                val input = File(dir, "in.png")
                val output = File(dir, "out.png")
                input.writeBytes(bytes)
                run(
                    oxipng.absolutePath,
                    "-o",
                    "2",
                    "--strip",
                    "safe",
                    "--out",
                    output.absolutePath,
                    input.absolutePath
                )
                val encoded = if (output.isFile) output.readBytes() else ByteArray(0)
                if (encoded.isNotEmpty() && encoded.size < bytes.size) encoded else bytes
            }
        } catch (e: ProfilePhotoEncoderUnavailableException) {
            throw e
        } catch (e: Exception) {
            logger.warn("OxiPNG failed: ${e.message}")
            val reason = if (e.message?.contains("timed out") == true) "OxiPNG timed out" else "OxiPNG failed"
            encoderUnavailable(reason)
        }
    }

    private fun encoderUnavailable(detail: String): Nothing {
        logger.warn("$ENCODER_UNAVAILABLE: $detail")
        throw ProfilePhotoEncoderUnavailableException("$ENCODER_UNAVAILABLE: $detail")
    }

    private fun isMozjpeg(cjpeg: File): Boolean {
        return try {
            val output = inTempDir("profile-cjpeg-version") { dir ->
                val log = File(dir, "version.txt")
                val proc = ProcessBuilder(cjpeg.absolutePath, "-version")
                    .redirectOutput(log)
                    .redirectError(log)
                    .start()
                if (!proc.waitFor(10, TimeUnit.SECONDS)) {
                    proc.destroyForcibly()
                    return@inTempDir ""
                }
                log.readText()
            }
            output.contains("mozjpeg", ignoreCase = true)
        } catch (e: Exception) {
            logger.warn("Could not confirm MozJPEG: ${e.message}")
            false
        }
    }

    private fun executable(name: String): File? {
        val override = System.getenv("PROFILE_PHOTO_${name.uppercase()}")
        val candidates = listOfNotNull(override, "/usr/local/bin/$name", "/usr/bin/$name")
        candidates.forEach { path ->
            val file = File(path)
            if (file.isFile && file.canExecute()) return file
        }
        val pathEnv = System.getenv("PATH").orEmpty()
        return pathEnv.split(File.pathSeparator).firstNotNullOfOrNull { dir ->
            val file = File(dir, name)
            if (file.isFile && file.canExecute()) file else null
        }
    }

    private fun run(vararg command: String) {
        val log = File.createTempFile("profile-photo-tool", ".log")
        try {
            val proc = ProcessBuilder(*command)
                .redirectOutput(log)
                .redirectError(log)
                .start()
            if (!proc.waitFor(30, TimeUnit.SECONDS)) {
                proc.destroyForcibly()
                error("${command.first()} timed out")
            }
            if (proc.exitValue() != 0) {
                val detail = log.readText().lineSequence().firstOrNull { it.isNotBlank() }.orEmpty()
                error("${File(command.first()).name} failed: $detail")
            }
        } finally {
            log.delete()
        }
    }

    private fun <T> inTempDir(prefix: String, block: (File) -> T): T {
        val dir = Files.createTempDirectory(prefix).toFile()
        try {
            return block(dir)
        } finally {
            dir.listFiles()?.forEach { it.delete() }
            dir.delete()
        }
    }

    private fun normalizeType(contentType: String): String =
        contentType.substringBefore(";").trim().lowercase()

    private fun baseName(filename: String?): String =
        filename
            ?.substringAfterLast('/')
            ?.substringAfterLast('\\')
            ?.trim()
            ?.lowercase()
            .orEmpty()

    private fun heifMagic(bytes: ByteArray): Boolean {
        if (bytes.size < 12) return false
        if (String(bytes, 4, 4, StandardCharsets.US_ASCII) != "ftyp") return false
        val brands = ArrayList<String>(4)
        brands.add(String(bytes, 8, 4, StandardCharsets.US_ASCII))
        val limit = boxLimit(bytes)
        var offset = 16
        while (offset + 4 <= limit) {
            brands.add(String(bytes, offset, 4, StandardCharsets.US_ASCII))
            offset += 4
        }
        if (brands.any { it in HEIC_BRANDS }) return true
        val avif = brands.any { it == "avif" || it == "avis" }
        return !avif && brands.any { it == "mif1" || it == "msf1" }
    }

    private fun boxLimit(bytes: ByteArray): Int {
        val size = ((bytes[0].toInt() and 0xFF) shl 24) or
            ((bytes[1].toInt() and 0xFF) shl 16) or
            ((bytes[2].toInt() and 0xFF) shl 8) or
            (bytes[3].toInt() and 0xFF)
        val bounded = if (size < 16) minOf(bytes.size, 64) else minOf(size, bytes.size)
        return bounded
    }
}

class ProfilePhotoRejectedException(message: String) : IllegalArgumentException(message)

class ProfilePhotoEncoderUnavailableException(message: String) : IllegalStateException(message)
