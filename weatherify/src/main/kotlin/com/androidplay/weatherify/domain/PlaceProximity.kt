package com.androidplay.weatherify.domain

import kotlin.math.atan2
import kotlin.math.cos
import kotlin.math.sin
import kotlin.math.sqrt

/**
 * Place match for calendar events. Exact lat/lon equality drops events when GPS drifts,
 * so listing uses this radius instead. Creation still stores the coordinates as sent.
 */
object PlaceProximity {
    const val MATCH_RADIUS_METERS = 1_000.0
    private const val EARTH_RADIUS_METERS = 6_371_000.0
    private const val METERS_PER_DEGREE_LAT = 111_320.0

    data class BoundingBox(
        val minLat: Double,
        val maxLat: Double,
        val minLon: Double,
        val maxLon: Double,
        val crossesDateline: Boolean
    )

    fun withinMatchRadius(
        lat1: Double,
        lon1: Double,
        lat2: Double,
        lon2: Double,
        radiusMeters: Double = MATCH_RADIUS_METERS
    ): Boolean = distanceMeters(lat1, lon1, lat2, lon2) <= radiusMeters

    fun distanceMeters(lat1: Double, lon1: Double, lat2: Double, lon2: Double): Double {
        val dLat = Math.toRadians(lat2 - lat1)
        val dLon = Math.toRadians(normalizeLongitudeDelta(lon2 - lon1))
        val a = sin(dLat / 2) * sin(dLat / 2) +
            cos(Math.toRadians(lat1)) * cos(Math.toRadians(lat2)) *
            sin(dLon / 2) * sin(dLon / 2)
        val c = 2 * atan2(sqrt(a), sqrt(1 - a))
        return EARTH_RADIUS_METERS * c
    }

    /** Box slightly larger than the circle, so a later haversine check can drop the corners. */
    fun boundingBox(lat: Double, lon: Double, radiusMeters: Double = MATCH_RADIUS_METERS): BoundingBox {
        val dLat = radiusMeters / METERS_PER_DEGREE_LAT
        val cosLat = cos(Math.toRadians(lat)).let { kotlin.math.abs(it) }.coerceAtLeast(0.01)
        val dLon = radiusMeters / (METERS_PER_DEGREE_LAT * cosLat)
        val minLat = (lat - dLat).coerceIn(-90.0, 90.0)
        val maxLat = (lat + dLat).coerceIn(-90.0, 90.0)
        var minLon = lon - dLon
        var maxLon = lon + dLon
        val crosses = minLon < -180.0 || maxLon > 180.0
        if (minLon < -180.0) minLon += 360.0
        if (maxLon > 180.0) maxLon -= 360.0
        return BoundingBox(minLat, maxLat, minLon, maxLon, crosses)
    }

    private fun normalizeLongitudeDelta(delta: Double): Double {
        var value = delta
        while (value > 180.0) value -= 360.0
        while (value < -180.0) value += 360.0
        return value
    }
}
