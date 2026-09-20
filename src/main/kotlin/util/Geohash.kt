package util

/**
 * Minimal geohash encoder, used to quantise client coordinates into bounded topic keys.
 *
 * Why this exists: raw lat/lon has effectively infinite cardinality, so keying topics on it
 * would spawn one upstream poller per *device* instead of one per *area*, defeating the whole
 * point of server-side fan-out. Precision 5 gives ~4.9km cells, which is finer than weather
 * meaningfully varies, and bounds the global key space to ~1M cells.
 *
 * Deliberately not a dependency: the algorithm is 30 lines and pulling in a geo library for it
 * would drag in JTS/spatial4j.
 */
object Geohash {

    private const val BASE32 = "0123456789bcdefghjkmnpqrstuvwxyz"

    const val DEFAULT_PRECISION = 5

    fun encode(lat: Double, lon: Double, precision: Int = DEFAULT_PRECISION): String {
        require(precision in 1..12) { "precision must be 1..12" }

        var latMin = -90.0
        var latMax = 90.0
        var lonMin = -180.0
        var lonMax = 180.0

        val hash = StringBuilder(precision)
        var bit = 0
        var chIndex = 0
        var isLonTurn = true

        while (hash.length < precision) {
            if (isLonTurn) {
                val mid = (lonMin + lonMax) / 2
                if (lon >= mid) {
                    chIndex = (chIndex shl 1) or 1
                    lonMin = mid
                } else {
                    chIndex = chIndex shl 1
                    lonMax = mid
                }
            } else {
                val mid = (latMin + latMax) / 2
                if (lat >= mid) {
                    chIndex = (chIndex shl 1) or 1
                    latMin = mid
                } else {
                    chIndex = chIndex shl 1
                    latMax = mid
                }
            }
            isLonTurn = !isLonTurn

            if (bit < 4) {
                bit++
            } else {
                hash.append(BASE32[chIndex])
                bit = 0
                chIndex = 0
            }
        }
        return hash.toString()
    }

    /** Centre of the cell, used as the single canonical coordinate the poller queries upstream. */
    fun decodeCenter(geohash: String): Pair<Double, Double> {
        var latMin = -90.0
        var latMax = 90.0
        var lonMin = -180.0
        var lonMax = 180.0
        var isLonTurn = true

        for (c in geohash) {
            val cd = BASE32.indexOf(c)
            require(cd >= 0) { "invalid geohash character: $c" }
            for (mask in intArrayOf(16, 8, 4, 2, 1)) {
                if (isLonTurn) {
                    val mid = (lonMin + lonMax) / 2
                    if (cd and mask != 0) lonMin = mid else lonMax = mid
                } else {
                    val mid = (latMin + latMax) / 2
                    if (cd and mask != 0) latMin = mid else latMax = mid
                }
                isLonTurn = !isLonTurn
            }
        }
        return ((latMin + latMax) / 2) to ((lonMin + lonMax) / 2)
    }
}

/** Topic key helpers. A topic is the unit of fan-out: one poller, many subscribers. */
object LiveTopic {

    private const val PREFIX = "loc:"

    fun forCoordinates(lat: Double, lon: Double): String =
        PREFIX + Geohash.encode(lat, lon)

    fun geohashOf(topic: String): String = topic.removePrefix(PREFIX)

    fun isValid(topic: String): Boolean =
        topic.startsWith(PREFIX) && topic.length == PREFIX.length + Geohash.DEFAULT_PRECISION

    /** Coordinates a poller should query for this topic — the cell centre, not any user's GPS. */
    fun centerOf(topic: String): Pair<Double, Double> =
        Geohash.decodeCenter(geohashOf(topic))

    fun validCoordinates(lat: Double, lon: Double): Boolean =
        lat in -90.0..90.0 && lon in -180.0..180.0 && !lat.isNaN() && !lon.isNaN()
}
