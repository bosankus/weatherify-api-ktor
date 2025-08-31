package util

import config.Environment
import io.ktor.client.HttpClient
import io.ktor.client.engine.cio.CIO
import io.ktor.client.plugins.contentnegotiation.ContentNegotiation
import io.ktor.client.request.parameter
import io.ktor.client.request.post
import io.ktor.client.request.setBody
import io.ktor.http.ContentType
import io.ktor.http.contentType
import io.ktor.serialization.kotlinx.json.json
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.launch
import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable
import kotlinx.serialization.json.Json
import org.slf4j.LoggerFactory
import java.util.UUID

/** Simple Analytics abstraction */
interface Analytics {
    fun event(
        name: String,
        params: Map<String, Any?> = emptyMap(),
        userId: String? = null,
        clientId: String? = null,
        userAgent: String? = null,
        ipOverride: String? = null
    )
}

/** No-op analytics implementation */
class NoOpAnalytics : Analytics {
    override fun event(
        name: String,
        params: Map<String, Any?>,
        userId: String?,
        clientId: String?,
        userAgent: String?,
        ipOverride: String?
    ) {
        // Do nothing
    }
}

/** Google Analytics (GA4) Measurement Protocol client */
class GoogleAnalyticsClient(
    private val measurementId: String,
    private val apiSecret: String,
    private val http: HttpClient = defaultClient
) : Analytics {
    private val logger = LoggerFactory.getLogger("Analytics")
    private val scope = CoroutineScope(Dispatchers.IO + Job())

    override fun event(
        name: String,
        params: Map<String, Any?>,
        userId: String?,
        clientId: String?,
        userAgent: String?,
        ipOverride: String?
    ) {
        // Fire and forget, never throw
        scope.launch {
            runCatching {
                val body = Ga4Payload(
                    clientId = (clientId ?: UUID.randomUUID().toString()).take(36),
                    userId = userId,
                    events = listOf(
                        Ga4Event(
                            name = name,
                            params = buildMap<String, kotlinx.serialization.json.JsonElement> {
                                val p = params.filterValues { it != null }
                                p.forEach { (k, v) ->
                                    val je = when (v) {
                                        is String -> kotlinx.serialization.json.JsonPrimitive(v)
                                        is Number -> kotlinx.serialization.json.JsonPrimitive(v)
                                        is Boolean -> kotlinx.serialization.json.JsonPrimitive(v)
                                        is Enum<*> -> kotlinx.serialization.json.JsonPrimitive(v.name)
                                        null -> null
                                        else -> kotlinx.serialization.json.JsonPrimitive(v.toString())
                                    }
                                    if (je != null) put(k, je)
                                }
                                // required engagement time helps GA accept event reliably
                                put(
                                    "engagement_time_msec",
                                    kotlinx.serialization.json.JsonPrimitive(1)
                                )
                            }
                        )
                    )
                )

                val response = http.post("https://www.google-analytics.com/mp/collect") {
                    parameter("measurement_id", measurementId)
                    parameter("api_secret", apiSecret)
                    contentType(ContentType.Application.Json)
                    // forward debug context where useful
                    if (!userAgent.isNullOrBlank()) headers.append("User-Agent", userAgent)
                    if (!ipOverride.isNullOrBlank()) headers.append("X-Forwarded-For", ipOverride)
                    setBody(body)
                }
                if (response.status.value !in 200..299) {
                    logger.debug("GA4 event failed status=${'$'}{response.status.value}")
                }
            }.onFailure { e ->
                logger.debug("GA4 event error: ${'$'}{e.message}")
            }
        }
    }

    companion object {
        private val defaultClient: HttpClient by lazy {
            HttpClient(CIO) {
                install(ContentNegotiation) { json(Json { ignoreUnknownKeys = true }) }
            }
        }

        fun fromEnv(): Analytics {
            val enabled = Environment.isAnalyticsEnabled()
            val mid = Environment.getGaMeasurementId()
            val secret = Environment.getGaApiSecret()
            return if (enabled && !mid.isNullOrBlank() && !secret.isNullOrBlank()) {
                GoogleAnalyticsClient(mid, secret)
            } else {
                NoOpAnalytics()
            }
        }
    }
}

@Serializable
private data class Ga4Payload(
    @SerialName("client_id") val clientId: String,
    @SerialName("user_id") val userId: String? = null,
    val events: List<Ga4Event>
)

@Serializable
private data class Ga4Event(
    val name: String,
    val params: Map<String, kotlinx.serialization.json.JsonElement>
)
