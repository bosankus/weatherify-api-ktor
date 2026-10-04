package domain.service

import com.androidplay.core.common.Result
import com.androidplay.weatherify.domain.PlaceEvent
import com.androidplay.weatherify.repository.PlaceEventRepository
import java.time.Instant

/**
 * Service for signed-in users' place calendar events (Wander home).
 */
class PlaceEventService(private val repository: PlaceEventRepository) {

    data class CreateInput(
        val placeName: String,
        val lat: Double?,
        val lon: Double?,
        val title: String,
        val startsAt: String,
        val note: String? = null
    )

    fun validateCreate(input: CreateInput): String? {
        if (input.lat == null || input.lon == null) {
            return "Missing place coordinates: lat and lon are required"
        }
        if (input.lat !in -90.0..90.0) {
            return "lat must be between -90 and 90"
        }
        if (input.lon !in -180.0..180.0) {
            return "lon must be between -180 and 180"
        }
        if (input.title.isBlank()) {
            return "title is required"
        }
        if (input.startsAt.isBlank()) {
            return "startsAt is required"
        }
        try {
            Instant.parse(input.startsAt)
        } catch (_: Exception) {
            return "startsAt must be a valid ISO-8601 instant"
        }
        return null
    }

    fun validateListCoords(lat: Double?, lon: Double?): String? {
        if (lat == null || lon == null) {
            return "Missing place coordinates: lat and lon are required"
        }
        if (lat !in -90.0..90.0) {
            return "lat must be between -90 and 90"
        }
        if (lon !in -180.0..180.0) {
            return "lon must be between -180 and 180"
        }
        return null
    }

    suspend fun create(email: String, input: CreateInput): Result<PlaceEvent> {
        validateCreate(input)?.let { return Result.error(it) }
        val event = PlaceEvent(
            userEmail = email,
            placeName = input.placeName.trim(),
            lat = input.lat!!,
            lon = input.lon!!,
            title = input.title.trim(),
            startsAt = Instant.parse(input.startsAt).toString(),
            note = input.note?.trim()?.takeIf { it.isNotEmpty() },
            createdAt = Instant.now().toString()
        )
        return repository.create(event)
    }

    suspend fun list(email: String, lat: Double, lon: Double): Result<List<PlaceEvent>> =
        repository.listByUserAndPlace(email, lat, lon)
}
