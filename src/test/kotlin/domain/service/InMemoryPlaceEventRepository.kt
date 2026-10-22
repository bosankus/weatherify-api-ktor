package domain.service

import com.androidplay.core.common.Result
import com.androidplay.weatherify.domain.PlaceEvent
import com.androidplay.weatherify.domain.PlaceEventSync
import com.androidplay.weatherify.domain.PlaceProximity
import com.androidplay.weatherify.repository.PlaceEventRepository
import org.bson.types.ObjectId

/** In-memory [PlaceEventRepository] mirroring the Mongo implementation's filters and order. */
class InMemoryPlaceEventRepository : PlaceEventRepository {
    val created = mutableListOf<PlaceEvent>()

    override suspend fun create(event: PlaceEvent): Result<PlaceEvent> {
        if (event.clientId != null && created.any { it.clientId == event.clientId }) {
            return Result.error("E11000 duplicate key error: clientId_1")
        }
        created += event
        return Result.success(event)
    }

    override suspend fun listByUserAndPlace(
        email: String,
        lat: Double,
        lon: Double
    ): Result<List<PlaceEvent>> =
        Result.success(
            created.filter {
                it.userEmail == email &&
                    it.deletedAt == null &&
                    PlaceProximity.withinMatchRadius(lat, lon, it.lat, it.lon)
            }.sortedBy { it.startsAt }
        )

    override suspend fun findByClientId(clientId: String): Result<PlaceEvent?> =
        Result.success(created.firstOrNull { it.clientId == clientId })

    override suspend fun findById(id: ObjectId): Result<PlaceEvent?> =
        Result.success(created.firstOrNull { it.id == id })

    override suspend fun replaceIfUnchanged(current: PlaceEvent, replacement: PlaceEvent): Result<Boolean> {
        val index = created.indexOfFirst {
            it.id == current.id &&
                it.userEmail == current.userEmail &&
                it.updatedAt == current.updatedAt &&
                it.deletedAt == current.deletedAt
        }
        if (index < 0) return Result.success(false)
        created[index] = replacement
        return Result.success(true)
    }

    override suspend fun listChangedSince(
        email: String,
        cursor: PlaceEventSync.Cursor?,
        limit: Int
    ): Result<List<PlaceEvent>> =
        Result.success(
            created.filter { it.userEmail == email && PlaceEventSync.isAfter(it, cursor) }
                .sortedWith(PlaceEventSync.ORDER)
                .take(limit)
        )
}
