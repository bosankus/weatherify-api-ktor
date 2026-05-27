package com.transloom.plugins

import io.ktor.server.application.ApplicationPlugin
import io.ktor.server.application.createApplicationPlugin
import io.ktor.server.application.hooks.ResponseSent
import io.ktor.server.request.path
import org.slf4j.MDC
import java.util.UUID

/**
 * Assigns a unique X-Request-ID to every request and populates SLF4J MDC so that
 * all log lines emitted during request handling carry requestId, method, and path.
 * MDC is cleared after the response is sent to prevent context leaking between coroutines.
 */
val RequestContextPlugin: ApplicationPlugin<Unit> = createApplicationPlugin("RequestContext") {
    onCall { call ->
        val requestId = call.request.headers["X-Request-ID"]
            ?.takeIf { it.isNotBlank() }
            ?: UUID.randomUUID().toString()

        MDC.put("requestId", requestId)
        MDC.put("method", call.request.local.method.value)
        MDC.put("path", call.request.path())

        call.response.headers.append("X-Request-ID", requestId)
    }
    on(ResponseSent) {
        MDC.remove("requestId")
        MDC.remove("method")
        MDC.remove("path")
        MDC.remove("userId")
        MDC.remove("projectId")
        MDC.remove("runId")
    }
}
