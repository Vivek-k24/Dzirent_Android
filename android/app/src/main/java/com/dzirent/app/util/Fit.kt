package com.dzirent.app.util

import com.dzirent.app.data.model.Categories
import com.dzirent.app.data.model.Listing
import com.dzirent.app.data.model.ListingMeasurement
import com.dzirent.app.data.model.PrivateProfile

/** How many mapped measurements must be in range for "Likely to fit you". */
const val FIT_MIN_MATCHES = 2

/** Body measurements the fit rule compares with garment measurements. */
val FIT_PARTS = listOf(BodyMeasurement.CHEST, BodyMeasurement.WAIST, BodyMeasurement.HIPS, BodyMeasurement.SHOULDER)

/** Allowed garment minus body, in cm: the garment must be at least [min] and at most [max] bigger. */
data class Ease(val min: Double, val max: Double)

/**
 * Ease allowance per category and body part, in cm. PLACEHOLDER VALUES: owner to confirm.
 * Fitted occasion wear allows little ease, outerwear a lot. Shoulder is a width, so its
 * range sits around 0. Accessories have none, so they never count as a fit.
 */
val EASE_BY_CATEGORY: Map<String, Map<BodyMeasurement, Ease>> = mapOf(
    "Career & Professional" to mapOf(
        BodyMeasurement.CHEST to Ease(4.0, 14.0),
        BodyMeasurement.WAIST to Ease(2.0, 12.0),
        BodyMeasurement.HIPS to Ease(4.0, 14.0),
        BodyMeasurement.SHOULDER to Ease(-1.0, 3.0)
    ),
    "Occasion & Party" to mapOf(
        BodyMeasurement.CHEST to Ease(0.0, 8.0),
        BodyMeasurement.WAIST to Ease(0.0, 6.0),
        BodyMeasurement.HIPS to Ease(0.0, 10.0),
        BodyMeasurement.SHOULDER to Ease(-2.0, 2.0)
    ),
    "Cultural & Traditional" to mapOf(
        BodyMeasurement.CHEST to Ease(4.0, 16.0),
        BodyMeasurement.WAIST to Ease(4.0, 16.0),
        BodyMeasurement.HIPS to Ease(6.0, 20.0),
        BodyMeasurement.SHOULDER to Ease(-1.0, 3.0)
    ),
    "Everyday" to mapOf(
        BodyMeasurement.CHEST to Ease(4.0, 20.0),
        BodyMeasurement.WAIST to Ease(0.0, 14.0),
        BodyMeasurement.HIPS to Ease(2.0, 18.0),
        BodyMeasurement.SHOULDER to Ease(-2.0, 4.0)
    ),
    "Outerwear" to mapOf(
        BodyMeasurement.CHEST to Ease(10.0, 28.0),
        BodyMeasurement.WAIST to Ease(8.0, 30.0),
        BodyMeasurement.HIPS to Ease(8.0, 30.0),
        BodyMeasurement.SHOULDER to Ease(0.0, 5.0)
    )
)

/**
 * The body measurement a listing measurement label refers to, or null if it isn't one the
 * fit rule uses: "Chest", "Bust (flat)", "Chest/Bust", "Pit to pit" -> chest; "Waist";
 * "Hips"; "Shoulder". Lengths and sleeves don't count.
 */
fun bodyPartForLabel(label: String): BodyMeasurement? {
    val l = label.lowercase()
    return when {
        "shoulder" in l -> BodyMeasurement.SHOULDER
        "chest" in l || "bust" in l || "pit to pit" in l || "pit-to-pit" in l -> BodyMeasurement.CHEST
        "waist" in l -> BodyMeasurement.WAIST
        "hip" in l -> BodyMeasurement.HIPS
        else -> null
    }
}

/**
 * The garment measurement in cm as a circumference. A width measured flat ("Chest (flat)")
 * is doubled; shoulder is always a straight width, so it never is.
 */
fun garmentCm(measurement: ListingMeasurement, part: BodyMeasurement): Double {
    val cm = if (measurement.unit == "in") measurement.value * CM_PER_INCH else measurement.value
    val flat = "flat" in measurement.label.lowercase()
    return if (flat && part != BodyMeasurement.SHOULDER) cm * 2 else cm
}

/** True if the renter has at least one measurement the fit rule can use. */
fun hasFitMeasurements(body: PrivateProfile): Boolean = FIT_PARTS.any { body.bodyCm(it) != null }

/** Distinct body parts where this listing's measurements are within the ease range. */
fun fitMatches(listing: Listing, body: PrivateProfile): Set<BodyMeasurement> {
    if (listing.category == Categories.ACCESSORIES) return emptySet()
    val ease = EASE_BY_CATEGORY[listing.category] ?: EASE_BY_CATEGORY.getValue("Everyday")
    return listing.measurements.mapNotNull { measurement ->
        val part = bodyPartForLabel(measurement.label) ?: return@mapNotNull null
        val bodyCm = body.bodyCm(part) ?: return@mapNotNull null
        val range = ease[part] ?: return@mapNotNull null
        val extra = garmentCm(measurement, part) - bodyCm
        part.takeIf { extra >= range.min && extra <= range.max }
    }.toSet()
}

/** "Likely to fit you": at least [FIT_MIN_MATCHES] mapped measurements within range. */
fun isLikelyFit(listing: Listing, body: PrivateProfile): Boolean = fitMatches(listing, body).size >= FIT_MIN_MATCHES
