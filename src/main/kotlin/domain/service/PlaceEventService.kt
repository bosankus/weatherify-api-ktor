package domain.service

import com.androidplay.core.common.Result
import com.androidplay.weatherify.domain.PlaceEvent
import com.androidplay.weatherify.domain.PlaceEventSync
import com.androidplay.weatherify.repository.PlaceEventRepository
import org.bson.types.ObjectId
import java.time.Instant
import java.time.temporal.ChronoUnit

/**
 * Service for signed-in users' place calendar events (Wander home), including the
 * offline-first sync surface (client ids, update, soft delete, change feed).
 *
 * Ownership rule: an id that belongs to another account is reported exactly like an unknown id
 * ([NotFound]) so ids never reveal that another account has them.
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

    /** Conflict base for an update. [secondPrecision] is true for an HTTP-date header value. */
    data class UpdateBase(val instant: Instant, val secondPrecision: Boolean = false)

    sealed interface CreateOutcome {
        data class Created(val event: PlaceEvent) : CreateOutcome
        /** Same client id already stored for this account: idempotent retry. */
        data class Existing(val event: PlaceEvent) : CreateOutcome
        /** Client id belongs to another account. */
        data object NotFound : CreateOutcome
    }

    sealed interface UpdateOutcome {
        data class Updated(val event: PlaceEvent) : UpdateOutcome
        data class Conflict(val current: PlaceEvent) : UpdateOutcome
        data object NotFound : UpdateOutcome
    }

    sealed interface DeleteOutcome {
        data object Deleted : DeleteOutcome
        data object NotFound : DeleteOutcome
    }

    data class SyncPage(
        val items: List<PlaceEvent>,
        /** Cursor for the next page; null when this page reached the end. */
        val nextSince: String?,
        /** Position after the last item (or the request cursor when empty); store for the next sync. */
        val checkpoint: String?
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

    /** Canonical client id, or null when [raw] is not a UUID string. */
    fun normalizeClientId(raw: String): String? = PlaceEventSync.normalizeClientId(raw)

    suspend fun create(email: String, input: CreateInput): Result<PlaceEvent> {
        validateCreate(input)?.let { return Result.error(it) }
        return repository.create(newEvent(email, input, clientId = null))
    }

    /**
     * Create with an optional client id. Retrying with the same id returns the stored event
     * ([CreateOutcome.Existing]) instead of creating a duplicate.
     */
    suspend fun createWithClientId(email: String, clientId: String?, input: CreateInput): Result<CreateOutcome> {
        validateCreate(input)?.let { return Result.error(it) }
        if (clientId == null) {
            return repository.create(newEvent(email, input, null)).map { CreateOutcome.Created(it) }
        }
        when (val found = repository.findByClientId(clientId)) {
            is Result.Error -> return found
            is Result.Success -> found.data?.let { return Result.success(existingOutcome(email, it)) }
        }
        return when (val created = repository.create(newEvent(email, input, clientId))) {
            is Result.Success -> Result.success(CreateOutcome.Created(created.data))
            is Result.Error -> {
                // A concurrent retry may have inserted the same id first (unique clientId index).
                when (val again = repository.findByClientId(clientId)) {
                    is Result.Success -> again.data?.let { Result.success(existingOutcome(email, it)) } ?: created
                    is Result.Error -> created
                }
            }
        }
    }

    suspend fun list(email: String, lat: Double, lon: Double): Result<List<PlaceEvent>> =
        repository.listByUserAndPlace(email, lat, lon)

    /**
     * Full replace of the editable fields. Without [base] the last write wins; with [base] a row
     * changed after it returns [UpdateOutcome.Conflict] carrying the current row.
     */
    suspend fun update(email: String, id: String, input: CreateInput, base: UpdateBase?): Result<UpdateOutcome> {
        validateCreate(input)?.let { return Result.error(it) }
        repeat(MAX_CAS_ATTEMPTS) {
            val current = when (val found = findOwned(email, id)) {
                is Result.Error -> return found
                is Result.Success -> found.data
            }
            if (current == null || current.deletedAt != null) return Result.success(UpdateOutcome.NotFound)
            if (base != null && changedAfter(current, base)) return Result.success(UpdateOutcome.Conflict(current))
            val stamp = PlaceEventSync.format(nextUpdatedAt(current))
            val replacement = current.copy(
                placeName = input.placeName.trim(),
                lat = input.lat!!,
                lon = input.lon!!,
                title = input.title.trim(),
                startsAt = Instant.parse(input.startsAt).toString(),
                note = input.note?.trim()?.takeIf { it.isNotEmpty() },
                updatedAt = stamp
            )
            when (val swapped = repository.replaceIfUnchanged(current, replacement)) {
                is Result.Error -> return swapped
                is Result.Success -> if (swapped.data) return Result.success(UpdateOutcome.Updated(replacement))
            }
        }
        return Result.error("Place event changed concurrently; retry")
    }

    /** Soft delete. Already-deleted rows report [DeleteOutcome.Deleted] without writing. */
    suspend fun delete(email: String, id: String): Result<DeleteOutcome> {
        repeat(MAX_CAS_ATTEMPTS) {
            val current = when (val found = findOwned(email, id)) {
                is Result.Error -> return found
                is Result.Success -> found.data
            } ?: return Result.success(DeleteOutcome.NotFound)
            if (current.deletedAt != null) return Result.success(DeleteOutcome.Deleted)
            val stamp = PlaceEventSync.format(nextUpdatedAt(current))
            val tombstone = current.copy(updatedAt = stamp, deletedAt = stamp)
            when (val swapped = repository.replaceIfUnchanged(current, tombstone)) {
                is Result.Error -> return swapped
                is Result.Success -> if (swapped.data) return Result.success(DeleteOutcome.Deleted)
            }
        }
        return Result.error("Place event changed concurrently; retry")
    }

    /** One page of [email]'s changes after [cursor] (null = from the beginning), tombstones included. */
    suspend fun sync(email: String, cursor: PlaceEventSync.Cursor?, limit: Int = SYNC_PAGE_LIMIT): Result<SyncPage> {
        val pageLimit = limit.coerceIn(1, SYNC_PAGE_LIMIT)
        return repository.listChangedSince(email, cursor, pageLimit + 1).map { rows ->
            val items = rows.take(pageLimit)
            val checkpoint = items.lastOrNull()?.let { PlaceEventSync.cursorAfter(it).encode() } ?: cursor?.encode()
            SyncPage(
                items = items,
                nextSince = if (rows.size > pageLimit) checkpoint else null,
                checkpoint = checkpoint
            )
        }
    }

    private fun newEvent(email: String, input: CreateInput, clientId: String?): PlaceEvent {
        val stamp = PlaceEventSync.format(PlaceEventSync.now())
        return PlaceEvent(
            userEmail = email,
            placeName = input.placeName.trim(),
            lat = input.lat!!,
            lon = input.lon!!,
            title = input.title.trim(),
            startsAt = Instant.parse(input.startsAt).toString(),
            note = input.note?.trim()?.takeIf { it.isNotEmpty() },
            createdAt = stamp,
            clientId = clientId,
            updatedAt = stamp
        )
    }

    private fun existingOutcome(email: String, event: PlaceEvent): CreateOutcome =
        if (event.userEmail == email) CreateOutcome.Existing(event) else CreateOutcome.NotFound

    /**
     * The caller's event for a path id: a UUID matches the client id, a 24-hex id matches a
     * server-id row (rows with a client id are addressed only by it). Anything else, unknown ids
     * and other accounts' ids all resolve to null.
     */
    private suspend fun findOwned(email: String, id: String): Result<PlaceEvent?> {
        val found = PlaceEventSync.normalizeClientId(id)?.let { repository.findByClientId(it) }
            ?: if (ObjectId.isValid(id) && id.length == 24) {
                repository.findById(ObjectId(id)).map { it?.takeIf { row -> row.clientId == null } }
            } else {
                Result.success(null)
            }
        return found.map { it?.takeIf { row -> row.userEmail == email } }
    }

    private fun changedAfter(current: PlaceEvent, base: UpdateBase): Boolean {
        val stored = PlaceEventSync.effectiveUpdatedAt(current)
        return if (base.secondPrecision) {
            stored.truncatedTo(ChronoUnit.SECONDS) > base.instant.truncatedTo(ChronoUnit.SECONDS)
        } else {
            stored > base.instant
        }
    }

    /** Never moves updatedAt backwards, even with clock skew between instances. */
    private fun nextUpdatedAt(current: PlaceEvent): Instant {
        val now = PlaceEventSync.now()
        val floor = PlaceEventSync.effectiveUpdatedAt(current).plusMillis(1)
        return if (now < floor) floor else now
    }

    companion object {
        const val SYNC_PAGE_LIMIT = 500
        private const val MAX_CAS_ATTEMPTS = 3
    }
}
