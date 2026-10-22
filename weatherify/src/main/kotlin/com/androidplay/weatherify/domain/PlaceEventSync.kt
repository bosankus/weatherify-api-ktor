package com.androidplay.weatherify.domain

import org.bson.types.ObjectId
import java.time.Instant
import java.time.ZoneOffset
import java.time.format.DateTimeFormatter
import java.time.temporal.ChronoUnit

/**
 * Time, id and cursor helpers shared by the place-event sync service and repositories.
 *
 * Sync order is (effective updatedAt, _id) ascending. The cursor is either a bare ISO-8601
 * instant ("everything strictly after this time") or a compound cursor
 * `<instant>~<24-hex ObjectId>` ("everything after this exact row in sync order"), which is what
 * the server hands back so paging never skips rows that share a millisecond.
 */
object PlaceEventSync {
    const val CURSOR_SEPARATOR = '~'

    private val FORMATTER: DateTimeFormatter =
        DateTimeFormatter.ofPattern("yyyy-MM-dd'T'HH:mm:ss.SSS'Z'").withZone(ZoneOffset.UTC)

    private val UUID_REGEX =
        Regex("^[0-9a-fA-F]{8}-[0-9a-fA-F]{4}-[0-9a-fA-F]{4}-[0-9a-fA-F]{4}-[0-9a-fA-F]{12}$")

    /** Fixed-width UTC millisecond ISO-8601 string; lexicographic order equals time order. */
    fun format(instant: Instant): String = FORMATTER.format(instant.truncatedTo(ChronoUnit.MILLIS))

    fun now(): Instant = Instant.now().truncatedTo(ChronoUnit.MILLIS)

    /** Canonical lowercase UUID, or null when [raw] is not a UUID string. */
    fun normalizeClientId(raw: String): String? =
        raw.trim().takeIf { UUID_REGEX.matches(it) }?.lowercase()

    /** updatedAt, or createdAt for rows written before sync existed; millisecond precision. */
    fun effectiveUpdatedAt(event: PlaceEvent): Instant {
        val raw = event.updatedAt ?: event.createdAt
        return runCatching { Instant.parse(raw) }.getOrDefault(Instant.EPOCH).truncatedTo(ChronoUnit.MILLIS)
    }

    data class Cursor(val after: Instant, val afterId: ObjectId? = null) {
        fun encode(): String =
            if (afterId == null) after.toString() else "${format(after)}$CURSOR_SEPARATOR${afterId.toHexString()}"
    }

    /** Parses a `since` value; null when malformed. */
    fun parseCursor(raw: String): Cursor? {
        val value = raw.trim()
        if (value.isEmpty()) return null
        val sep = value.indexOf(CURSOR_SEPARATOR)
        return if (sep < 0) {
            runCatching { Cursor(Instant.parse(value)) }.getOrNull()
        } else {
            val instant = runCatching { Instant.parse(value.substring(0, sep)) }.getOrNull() ?: return null
            val idPart = value.substring(sep + 1)
            if (!ObjectId.isValid(idPart) || idPart.length != 24) return null
            Cursor(instant.truncatedTo(ChronoUnit.MILLIS), ObjectId(idPart))
        }
    }

    fun cursorAfter(event: PlaceEvent): Cursor = Cursor(effectiveUpdatedAt(event), event.id)

    /** Sync order: effective updatedAt, then _id. */
    val ORDER: Comparator<PlaceEvent> =
        compareBy<PlaceEvent> { effectiveUpdatedAt(it) }.thenBy { it.id }

    /** True when [event] sorts strictly after [cursor] (always true for a null cursor). */
    fun isAfter(event: PlaceEvent, cursor: Cursor?): Boolean {
        if (cursor == null) return true
        val t = effectiveUpdatedAt(event)
        if (t > cursor.after) return true
        return cursor.afterId != null && t == cursor.after && event.id > cursor.afterId
    }
}
