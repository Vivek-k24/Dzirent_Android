package com.dzirent.app.util

import kotlin.math.asin
import kotlin.math.cos
import kotlin.math.pow
import kotlin.math.roundToLong
import kotlin.math.sin
import kotlin.math.sqrt

private const val EARTH_RADIUS_KM = 6371.0

const val KM_PER_MILE = 1.609344

/**
 * Settings > Preferences > Distance. The filter options line up by position (step), so
 * switching units keeps the same step: 1 mi / 2 km, 3 / 5, 5 / 10, 15 / 25.
 * Distances are stored and queried in km; only what is shown changes.
 */
enum class DistanceUnit(val key: String, val label: String, val short: String, private val kmPerUnit: Double, val options: List<Int>) {
    MILES("mi", "Miles", "mi", KM_PER_MILE, listOf(1, 3, 5, 15)),
    KILOMETERS("km", "Kilometers", "km", 1.0, listOf(2, 5, 10, 25));

    /** Search radius in km for the option at [step]. */
    fun radiusKm(step: Int): Double = options[clampStep(step)] * kmPerUnit

    /** "5 mi" for the option at [step]. */
    fun optionLabel(step: Int): String = "${options[clampStep(step)]} $short"

    /** "15 miles", "1 mile" or "25 km" for the option at [step], after "Within". */
    fun withinLabel(step: Int): String {
        val n = options[clampStep(step)]
        return if (this == MILES) "$n ${if (n == 1) "mile" else "miles"}" else "$n km"
    }

    fun fromKm(km: Double): Double = km / kmPerUnit

    /** How precisely locations are saved (2 decimals of a degree), in words. */
    val locationPrecision: String get() = if (this == MILES) "about half a mile" else "about 1 km"

    companion object {
        fun fromKey(key: String?): DistanceUnit = entries.firstOrNull { it.key == key } ?: MILES
    }
}

/** Distance filter steps (positions in [DistanceUnit.options]). */
val DISTANCE_STEPS = 0..3
/** 5 mi / 10 km. */
const val DEFAULT_DISTANCE_STEP = 2

fun clampStep(step: Int): Int = step.coerceIn(DISTANCE_STEPS)

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

/**
 * "Under 1 mi", "2.4 mi", "13 mi" (or km). Locations are rounded to ~1 km, so this stays
 * approximate.
 */
fun formatDistance(km: Double, unit: DistanceUnit): String {
    val value = unit.fromKm(km)
    return when {
        value < 1.0 -> "Under 1 ${unit.short}"
        value < 10.0 -> "${(value * 10).roundToLong() / 10.0} ${unit.short}"
        else -> "${value.roundToLong()} ${unit.short}"
    }
}
