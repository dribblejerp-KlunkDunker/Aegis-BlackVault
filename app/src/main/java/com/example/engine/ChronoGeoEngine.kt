package com.example.engine

import com.example.model.*
import java.util.Locale
import kotlin.math.*

data class ChronoGeoEvaluation(
    val hourlyBins: List<Int>,
    val troughStartUtc: Int,
    val troughEndUtc: Int,
    val inferredUtcOffset: Int,
    val windowLabel: String,
    val lat: Double,
    val lon: Double,
    val locationLabel: String
)

/**
 * Diurnal chronolocation: activity windows from real timestamps (never fetch time) and geocoding of a
 * location the subject declared. Below [MIN_TIMESTAMPS_FOR_CHRONO] nothing is inferred.
 */
object ChronoGeoEngine {
    val GLOBAL_GAZETTEER: Map<String, Triple<Double, Double, String>> = mapOf(
        "zurich" to Triple(47.3769, 8.5414, "Zürich, CH"),
        "zürich" to Triple(47.3769, 8.5414, "Zürich, CH"),
        "switzerland" to Triple(47.3769, 8.5414, "Zürich, CH"),
        "cambridge" to Triple(42.3629, -71.0839, "Cambridge, US-MA"),
        "boston" to Triple(42.3601, -71.0589, "Boston, US-MA"),
        "san francisco" to Triple(37.7749, -122.4194, "San Francisco, US-CA"),
        "bay area" to Triple(37.7749, -122.4194, "San Francisco Bay Area, US-CA"),
        "california" to Triple(37.4419, -122.1430, "Palo Alto, US-CA"),
        "seattle" to Triple(47.6062, -122.3321, "Seattle, US-WA"),
        "new york" to Triple(40.7128, -74.0060, "New York, US-NY"),
        "nyc" to Triple(40.7128, -74.0060, "New York, US-NY"),
        "austin" to Triple(30.2672, -97.7431, "Austin, US-TX"),
        "chicago" to Triple(41.8781, -87.6298, "Chicago, US-IL"),
        "toronto" to Triple(43.6532, -79.3832, "Toronto, CA"),
        "montreal" to Triple(45.5017, -73.5673, "Montréal, CA"),
        "vancouver" to Triple(49.2827, -123.1207, "Vancouver, CA"),
        "london" to Triple(51.5074, -0.1278, "London, GB"),
        "uk" to Triple(51.5074, -0.1278, "London, GB"),
        "oxford" to Triple(51.7520, -1.2577, "Oxford, GB"),
        "berlin" to Triple(52.5200, 13.4050, "Berlin, DE"),
        "munich" to Triple(48.1351, 11.5820, "Munich, DE"),
        "germany" to Triple(52.5200, 13.4050, "Berlin, DE"),
        "paris" to Triple(48.8566, 2.3522, "Paris, FR"),
        "france" to Triple(48.8566, 2.3522, "Paris, FR"),
        "amsterdam" to Triple(52.3676, 4.9041, "Amsterdam, NL"),
        "stockholm" to Triple(59.3293, 18.0686, "Stockholm, SE"),
        "warsaw" to Triple(52.2297, 21.0122, "Warsaw, PL"),
        "tallinn" to Triple(59.4370, 24.7536, "Tallinn, EE"),
        "tel aviv" to Triple(32.0853, 34.7818, "Tel Aviv, IL"),
        "dubai" to Triple(25.2048, 55.2708, "Dubai, AE"),
        "bengaluru" to Triple(12.9716, 77.5946, "Bengaluru, IN"),
        "bangalore" to Triple(12.9716, 77.5946, "Bengaluru, IN"),
        "singapore" to Triple(1.3521, 103.8198, "Singapore, SG"),
        "tokyo" to Triple(35.6762, 139.6503, "Tokyo, JP"),
        "japan" to Triple(35.6762, 139.6503, "Tokyo, JP"),
        "seoul" to Triple(37.5665, 126.9780, "Seoul, KR"),
        "taipei" to Triple(25.0330, 121.5654, "Taipei, TW"),
        "sydney" to Triple(-33.8688, 151.2093, "Sydney, AU"),
        "melbourne" to Triple(-37.8136, 144.9631, "Melbourne, AU"),
        "sao paulo" to Triple(-23.5505, -46.6333, "São Paulo, BR")
    )

    /**
     * Diurnal Chronolocation Engine (Sliding 6-Hour Minimum Activity Rest Window) + Gazetteer Geocoder
     */
    fun evaluateChronoAndGeo(utcHours: List<Int>, declaredLocation: String?): ChronoGeoEvaluation {
        val bins = IntArray(24)
        utcHours.forEach { bins[(it % 24 + 24) % 24]++ }

        // Too few real timestamps: report that honestly instead of inventing an activity pattern.
        // A location the person publicly declared is not an inference, so it is still geocoded.
        if (utcHours.size < MIN_TIMESTAMPS_FOR_CHRONO) {
            val declared = declaredLocation?.trim()?.takeIf { it.isNotEmpty() }
            val match = declared?.lowercase(Locale.US)?.let { clean ->
                GLOBAL_GAZETTEER.entries.firstOrNull { (cityKey, _) -> cityKey in clean }?.value
            }
            return ChronoGeoEvaluation(
                hourlyBins = bins.toList(),
                troughStartUtc = 0,
                troughEndUtc = 0,
                inferredUtcOffset = 0,
                windowLabel = "INSUFFICIENT TIMESTAMPS (${utcHours.size}/$MIN_TIMESTAMPS_FOR_CHRONO)",
                lat = match?.first ?: 0.0,
                lon = match?.second ?: 0.0,
                locationLabel = when {
                    match != null -> "DECLARED: ${match.third.uppercase(Locale.US)}"
                    declared != null -> "DECLARED: ${declared.uppercase(Locale.US)}"
                    else -> "UNKNOWN"
                }
            )
        }

        var minSum = Int.MAX_VALUE
        var troughStart = 0
        for (startHr in 0 until 24) {
            var windowSum = 0
            for (k in 0 until 6) {
                windowSum += bins[(startHr + k) % 24]
            }
            if (windowSum < minSum) {
                minSum = windowSum
                troughStart = startHr
            }
        }
        val troughEnd = (troughStart + 6) % 24
        var inferredOffset = 1 - troughStart
        if (inferredOffset < -12) inferredOffset += 24
        if (inferredOffset > 14) inferredOffset -= 24

        val windowLabel = String.format(Locale.US, "%02d:00 - %02d:00 UTC (SLEEP TROUGH)", troughStart, troughEnd)
        val offsetSign = if (inferredOffset >= 0) "+$inferredOffset" else "$inferredOffset"

        if (!declaredLocation.isNullOrBlank()) {
            val clean = declaredLocation.trim().lowercase(Locale.US)
            for ((cityKey, coords) in GLOBAL_GAZETTEER) {
                if (cityKey in clean) {
                    return ChronoGeoEvaluation(
                        hourlyBins = bins.toList(),
                        troughStartUtc = troughStart,
                        troughEndUtc = troughEnd,
                        inferredUtcOffset = inferredOffset,
                        windowLabel = windowLabel,
                        lat = coords.first,
                        lon = coords.second,
                        locationLabel = "${coords.third.uppercase(Locale.US)} (CHRONO UTC$offsetSign)"
                    )
                }
            }
        }

        // A UTC offset only constrains longitude; latitude is unknown, so no coordinates are invented.
        val meridianLon = (inferredOffset * 15.0).coerceIn(-180.0, 180.0)
        return ChronoGeoEvaluation(
            hourlyBins = bins.toList(),
            troughStartUtc = troughStart,
            troughEndUtc = troughEnd,
            inferredUtcOffset = inferredOffset,
            windowLabel = windowLabel,
            lat = 0.0,
            lon = 0.0,
            locationLabel = if (!declaredLocation.isNullOrBlank()) {
                "${declaredLocation.uppercase(Locale.US)} // MERIDIAN UTC$offsetSign"
            } else {
                "MERIDIAN PROJECTION UTC$offsetSign ($meridianLon°E/W)"
            }
        )
    }

    /** Minimum number of real, timestamped events before an activity-window estimate is attempted. */
    const val MIN_TIMESTAMPS_FOR_CHRONO = 12
}
