package com.dzirent.app.util

import kotlin.math.asin
import kotlin.math.cos
import kotlin.math.pow
import kotlin.math.roundToLong
import kotlin.math.sin
import kotlin.math.sqrt

private const val EARTH_RADIUS_KM = 6371.0

/** Distance chips on Browse, in km. */
val DISTANCE_OPTIONS_KM = listOf(2, 5, 10, 25)
const val DEFAULT_DISTANCE_KM = 10

/** Rounds a coordinate to 2 decimals (about 1 km) before it is saved, for privacy. */
fun roundCoord(value: Double): Double = (value * 100.0).roundToLong() / 100.0

/** Great-circle distance in km. Same formula as the listings_nearby RPC. */
fun haversineKm(lat1: Double, lng1: Double, lat2: Double, lng2: Double): Double {
    val dLat = Math.toRadians(lat2 - lat1)
    val dLng = Math.toRadians(lng2 - lng1)
    val a = sin(dLat / 2).pow(2) +
        cos(Math.toRadians(lat1)) * cos(Math.toRadians(lat2)) * sin(dLng / 2).pow(2)
    return EARTH_RADIUS_KM * 2 * asin(sqrt(a.coerceIn(0.0, 1.0)))
}

/** "Under 1 km", "2.4 km", "13 km". Locations are rounded to ~1 km, so this stays approximate. */
fun formatDistance(km: Double): String = when {
    km < 1.0 -> "Under 1 km"
    km < 10.0 -> "${(km * 10).roundToLong() / 10.0} km"
    else -> "${km.roundToLong()} km"
}
