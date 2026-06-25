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

// 180×180 PNG matching the Syncling favicon (violet gradient + sync arrows mark).
// Generated once and reused for all apple-touch-icon requests.
private val appleTouchIconPng: ByteArray by lazy {
    val size = 180
    val img = BufferedImage(size, size, BufferedImage.TYPE_INT_ARGB)
    val g = img.createGraphics()
    g.setRenderingHint(RenderingHints.KEY_ANTIALIASING, RenderingHints.VALUE_ANTIALIAS_ON)
    g.setRenderingHint(RenderingHints.KEY_RENDERING, RenderingHints.VALUE_RENDER_QUALITY)

    val s = size / 32f  // scale factor from the SVG's 32×32 viewBox

    // Rounded-rect background: violet gradient #A890FF → #5535DD
    g.paint = GradientPaint(0f, 0f, java.awt.Color(0xA8, 0x90, 0xFF),
                            size.toFloat(), size.toFloat(), java.awt.Color(0x55, 0x35, 0xDD))
    val arc = (7 * s).toInt()
    g.fillRoundRect(0, 0, size, size, arc, arc)

    // Sync arrows mark: two curved arcs with arrowheads (white, matching SVG logo)
    g.color = java.awt.Color(0xFF, 0xFF, 0xFF, 235)
    g.stroke = BasicStroke(2.5f * s, BasicStroke.CAP_ROUND, BasicStroke.JOIN_ROUND)
    // Upper arc approximated as a quadratic curve: left(10,11) → peak(16,6) → right(22,11)
    val path1 = java.awt.geom.GeneralPath()
    path1.moveTo(10 * s, 11 * s)
    path1.curveTo(10 * s, 6 * s, 22 * s, 6 * s, 22 * s, 11 * s)
    g.draw(path1)
    // Arrowhead for upper arc at (22,11) pointing downward: V shape
    g.drawLine((19.5f * s).toInt(), (8.5f * s).toInt(), (22 * s).toInt(), (11 * s).toInt())
    g.drawLine((22 * s).toInt(), (11 * s).toInt(), (24.5f * s).toInt(), (8.5f * s).toInt())
    // Lower arc: right(22,21) → peak(16,26) → left(10,21)
    val path2 = java.awt.geom.GeneralPath()
    path2.moveTo(22 * s, 21 * s)
    path2.curveTo(22 * s, 26 * s, 10 * s, 26 * s, 10 * s, 21 * s)
    g.draw(path2)
    // Arrowhead for lower arc at (10,21) pointing upward: V shape
    g.drawLine((12.5f * s).toInt(), (23.5f * s).toInt(), (10 * s).toInt(), (21 * s).toInt())
    g.drawLine((10 * s).toInt(), (21 * s).toInt(), (7.5f * s).toInt(), (23.5f * s).toInt())

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

        // Genuine catch-all: Ktor only reaches this wildcard when no literal route matched,
        // so it never intercepts 404s that a real handler returns intentionally (e.g. "item not found").
        route("{...}") {
            handle {
                val path = call.request.path()
                if (path in excluded404Paths) {
                    call.respond(HttpStatusCode.NotFound)
                } else {
                    logger.info("404 Not Found: ${call.request.httpMethod.value} request to non-existent endpoint: $path")
                    call.handleNotFound()
                }
            }
        }
    }
}
