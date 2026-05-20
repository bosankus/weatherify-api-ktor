package bose.ankush.base

import bose.ankush.route.common.RouteRegistrar
import bose.ankush.route.handleNotFound
import bose.ankush.route.notFoundRoute
import io.ktor.http.*
import io.ktor.server.application.*
import io.ktor.server.request.*
import io.ktor.server.response.*
import io.ktor.server.routing.*
import org.koin.ktor.ext.inject
import org.slf4j.LoggerFactory
import java.awt.BasicStroke
import java.awt.GradientPaint
import java.awt.RenderingHints
import java.awt.image.BufferedImage
import java.io.ByteArrayOutputStream
import javax.imageio.ImageIO

// 180×180 PNG matching the transloom favicon (green gradient + T lettermark).
// Generated once and reused for all apple-touch-icon requests.
private val appleTouchIconPng: ByteArray by lazy {
    val size = 180
    val img = BufferedImage(size, size, BufferedImage.TYPE_INT_ARGB)
    val g = img.createGraphics()
    g.setRenderingHint(RenderingHints.KEY_ANTIALIASING, RenderingHints.VALUE_ANTIALIAS_ON)

    val s = size / 32f  // scale factor from the SVG's 32×32 viewBox

    // Rounded-rect background: gradient from #00F5B0 → #00A87A
    g.paint = GradientPaint(0f, 0f, java.awt.Color(0x00, 0xF5, 0xB0),
                            size.toFloat(), size.toFloat(), java.awt.Color(0x00, 0xA8, 0x7A))
    val arc = (7 * s).toInt()
    g.fillRoundRect(0, 0, size, size, arc, arc)

    // T lettermark: horizontal bar + vertical stem
    g.color = java.awt.Color(0x0A, 0x0A, 0x0A)
    g.stroke = BasicStroke(3.5f * s, BasicStroke.CAP_ROUND, BasicStroke.JOIN_ROUND)
    g.drawLine((8 * s).toInt(), (11 * s).toInt(), (24 * s).toInt(), (11 * s).toInt())
    g.drawLine((16 * s).toInt(), (11 * s).toInt(), (16 * s).toInt(), (24 * s).toInt())

    g.dispose()
    val out = ByteArrayOutputStream()
    ImageIO.write(img, "PNG", out)
    out.toByteArray()
}

fun Application.configureRouting() {
    val logger = LoggerFactory.getLogger("Routing")
    val registrars by inject<List<RouteRegistrar>>()

    val excluded404Paths = setOf(
        "/not-found", "/favicon.ico", "/apple-touch-icon.png", "/apple-touch-icon-precomposed.png", "/"
    )

    routing {
        get("/favicon.ico") {
            call.respondText("Greetings! You have reached a dead end, I won't say where you must go. Decide yourself")
        }
        get("/admin/dashboard/favicon.ico") {
            call.respondText("Greetings! You have reached a dead end, I won't say where you must go. Decide yourself")
        }
        get("/apple-touch-icon.png") {
            call.response.header(HttpHeaders.CacheControl, "public, max-age=86400")
            call.respondBytes(appleTouchIconPng, ContentType.Image.PNG)
        }
        get("/apple-touch-icon-precomposed.png") {
            call.response.header(HttpHeaders.CacheControl, "public, max-age=86400")
            call.respondBytes(appleTouchIconPng, ContentType.Image.PNG)
        }

        registrars.forEach { it.register(this) }
        notFoundRoute()

        get("{...}") {
            val path = call.request.path()
            if (path !in excluded404Paths) {
                logger.info("404 Not Found: GET request to non-existent endpoint: $path")
                call.handleNotFound()
            }
        }
        post("{...}") {
            val path = call.request.path()
            if (path !in excluded404Paths) {
                logger.info("404 Not Found: POST request to non-existent endpoint: $path")
                call.handleNotFound()
            }
        }
        put("{...}") {
            val path = call.request.path()
            if (path !in excluded404Paths) {
                logger.info("404 Not Found: PUT request to non-existent endpoint: $path")
                call.handleNotFound()
            }
        }
        delete("{...}") {
            val path = call.request.path()
            if (path !in excluded404Paths) {
                logger.info("404 Not Found: DELETE request to non-existent endpoint: $path")
                call.handleNotFound()
            }
        }
    }
}
