package util

import java.awt.image.BufferedImage
import java.io.ByteArrayInputStream
import java.io.ByteArrayOutputStream
import javax.imageio.ImageIO
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFailsWith
import kotlin.test.assertTrue

class ProfilePhotoBytesTest {

    @Test
    fun `jpeg keeps dimensions and is not larger after mozjpeg`() {
        val image = BufferedImage(80, 60, BufferedImage.TYPE_INT_RGB)
        var seed = 17
        for (y in 0 until image.height) {
            for (x in 0 until image.width) {
                seed = seed * 1103515245 + 12345
                val r = (seed ushr 16) and 0xFF
                val g = (seed ushr 8) and 0xFF
                val b = seed and 0xFF
                image.setRGB(x, y, (r shl 16) or (g shl 8) or b)
            }
        }
        val raw = ByteArrayOutputStream().also { ImageIO.write(image, "jpg", it) }.toByteArray()
        val commented = withJpegComment(raw, ByteArray(32_000) { 'A'.code.toByte() })
        val prepared = ProfilePhotoBytes.prepare(commented, "image/jpeg", "photo.jpg")
        assertEquals("image/jpeg", prepared.contentType)
        assertTrue(prepared.bytes.size < commented.size)
        val decoded = ImageIO.read(ByteArrayInputStream(prepared.bytes))
        assertEquals(80, decoded.width)
        assertEquals(60, decoded.height)
    }

    @Test
    fun `png keeps dimensions and is not larger`() {
        val image = BufferedImage(40, 30, BufferedImage.TYPE_INT_RGB)
        for (y in 0 until image.height) {
            for (x in 0 until image.width) {
                image.setRGB(x, y, 0x336699)
            }
        }
        val raw = ByteArrayOutputStream().also { ImageIO.write(image, "png", it) }.toByteArray()
        val prepared = ProfilePhotoBytes.prepare(raw, "image/png", "card.png")
        assertEquals("image/png", prepared.contentType)
        assertTrue(prepared.bytes.size <= raw.size)
        val decoded = ImageIO.read(ByteArrayInputStream(prepared.bytes))
        assertEquals(40, decoded.width)
        assertEquals(30, decoded.height)
    }

    @Test
    fun `webp and gif are unchanged`() {
        val gif = "GIF89a".encodeToByteArray() + byteArrayOf(1, 2, 3)
        val webp = "RIFF".encodeToByteArray() + byteArrayOf(0, 0, 0, 0) + "WEBP".encodeToByteArray() + byteArrayOf(9)
        val gifOut = ProfilePhotoBytes.prepare(gif, "image/gif", "a.gif")
        val webpOut = ProfilePhotoBytes.prepare(webp, "image/webp", "a.webp")
        assertTrue(gifOut.bytes.contentEquals(gif))
        assertTrue(webpOut.bytes.contentEquals(webp))
    }

    @Test
    fun `heic content type filename and header are rejected`() {
        val jpeg = byteArrayOf(0xFF.toByte(), 0xD8.toByte(), 0xFF.toByte(), 0xD9.toByte())
        val heic = heicStub()
        for ((bytes, type, name) in listOf(
            Triple(heic, "image/heic", "photo.jpg"),
            Triple(heic, "image/heif", null),
            Triple(jpeg, "image/jpeg", "IMG_2045.HEIC"),
            Triple(heic, "application/octet-stream", "shot.heif")
        )) {
            val error = assertFailsWith<ProfilePhotoRejectedException> {
                ProfilePhotoBytes.prepare(bytes, type, name)
            }
            assertTrue(error.message!!.contains("upload a JPEG"))
        }
    }

    @Test
    fun `svg is rejected`() {
        val svg = "<svg xmlns=\"http://www.w3.org/2000/svg\"></svg>".encodeToByteArray()
        assertFailsWith<ProfilePhotoRejectedException> {
            ProfilePhotoBytes.prepare(svg, "image/svg+xml", "icon.svg")
        }
        assertFailsWith<ProfilePhotoRejectedException> {
            ProfilePhotoBytes.prepare(svg, "image/svg+xml", null)
        }
    }


    @Test
    fun `missing or failed encoders do not return the original jpeg or png`() {
        val jpeg = byteArrayOf(0xFF.toByte(), 0xD8.toByte(), 0x00)
        val png = byteArrayOf(1, 2, 3, 4)
        val missingJpeg = assertFailsWith<ProfilePhotoEncoderUnavailableException> {
            ProfilePhotoBytes.prepare(jpeg, "image/jpeg", "a.jpg", locate = { null })
        }
        assertTrue(missingJpeg.message!!.contains("unavailable", ignoreCase = true))
        assertTrue(missingJpeg.message!!.contains("cjpeg is missing"))
        val missingPng = assertFailsWith<ProfilePhotoEncoderUnavailableException> {
            ProfilePhotoBytes.prepare(png, "image/png", "a.png", locate = { null })
        }
        assertTrue(missingPng.message!!.contains("OxiPNG is missing"))
        val notMozjpeg = assertFailsWith<ProfilePhotoEncoderUnavailableException> {
            ProfilePhotoBytes.prepare(jpeg, "image/jpeg", locate = { name ->
                if (name == "cjpeg") java.io.File("/bin/true") else null
            })
        }
        assertTrue(notMozjpeg.message!!.contains("not MozJPEG"))
        val brokenJpeg = assertFailsWith<ProfilePhotoEncoderUnavailableException> {
            ProfilePhotoBytes.prepare(jpeg, "image/jpeg", "broken.jpg")
        }
        assertTrue(brokenJpeg.message!!.contains("unavailable", ignoreCase = true))
        val brokenPng = assertFailsWith<ProfilePhotoEncoderUnavailableException> {
            ProfilePhotoBytes.prepare(png, "image/png", "broken.png")
        }
        assertTrue(brokenPng.message!!.contains("unavailable", ignoreCase = true))
    }

    @Test
    fun `webp and gif ignore a missing encoder`() {
        val gif = "GIF89a".encodeToByteArray() + byteArrayOf(1, 2, 3)
        val prepared = ProfilePhotoBytes.prepare(gif, "image/gif", "a.gif", locate = { null })
        assertTrue(prepared.bytes.contentEquals(gif))
    }
    private fun heicStub(): ByteArray {
        val bytes = ByteArray(24)
        bytes[3] = 24
        "ftyp".encodeToByteArray().copyInto(bytes, 4)
        "heic".encodeToByteArray().copyInto(bytes, 8)
        "heic".encodeToByteArray().copyInto(bytes, 16)
        return bytes
    }

    private fun withJpegComment(jpeg: ByteArray, comment: ByteArray): ByteArray {
        check(jpeg.size > 2 && jpeg[0] == 0xFF.toByte() && jpeg[1] == 0xD8.toByte())
        val len = comment.size + 2
        val segment = ByteArray(4 + comment.size)
        segment[0] = 0xFF.toByte()
        segment[1] = 0xFE.toByte()
        segment[2] = (len shr 8).toByte()
        segment[3] = (len and 0xFF).toByte()
        comment.copyInto(segment, 4)
        return jpeg.copyOfRange(0, 2) + segment + jpeg.copyOfRange(2, jpeg.size)
    }
}
