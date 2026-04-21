package domain.service

import bose.ankush.data.model.PlaceSearchResult
import domain.model.Result
import io.ktor.client.*
import io.ktor.client.call.*
import io.ktor.client.request.*
import io.ktor.http.*
import kotlinx.serialization.Serializable
import kotlinx.serialization.json.JsonElement
import kotlinx.serialization.json.jsonObject
import kotlinx.serialization.json.jsonPrimitive

/**
 * Raw response from Nominatim API.
 */
@Serializable
data class NominatimResponse(
    val name: String? = null,
    val display_name: String? = null,
    val lat: String,
    val lon: String,
    val address: Map<String, JsonElement>? = null
)

/**
 * Service for searching places using OpenStreetMap Nominatim API.
 */
class NominatimService(private val httpClient: HttpClient) {

    companion object {
        private const val NOMINATIM_BASE_URL = "https://nominatim.openstreetmap.org/search"
        private const val USER_AGENT = "AndroidPlayAPI/1.0 (contact@androidplay.app)"
    }

    suspend fun searchPlace(query: String, limit: Int = 10): Result<List<PlaceSearchResult>> {
        return try {
            val response = httpClient.get(NOMINATIM_BASE_URL) {
                parameter("q", query)
                parameter("format", "json")
                parameter("addressdetails", "1")
                parameter("limit", limit.coerceIn(1, 50))
                header(HttpHeaders.UserAgent, USER_AGENT)
            }

            val nominatimResults: List<NominatimResponse> = response.body()

            val places = nominatimResults.map { result ->
                val address = result.address
                PlaceSearchResult(
                    name = result.name ?: result.display_name ?: "",
                    city = address?.extractString("city")
                        ?: address?.extractString("town")
                        ?: address?.extractString("village")
                        ?: "",
                    state = address?.extractString("state") ?: "",
                    country = address?.extractString("country") ?: "",
                    lat = result.lat,
                    lon = result.lon
                )
            }

            Result.success(places)
        } catch (e: Exception) {
            Result.error("Failed to search place: ${e.message}", e)
        }
    }

    private fun Map<String, JsonElement>.extractString(key: String): String? {
        return this[key]?.jsonPrimitive?.content?.takeIf { it.isNotBlank() }
    }
}
