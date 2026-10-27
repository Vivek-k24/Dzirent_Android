package com.dzirent.app.util

import java.math.BigDecimal
import java.math.RoundingMode

const val CM_PER_INCH = 2.54

/**
 * "I'm a" on My measurements, stored as private_profiles.fit_profile. Optional and used only
 * for fit (the Chest / Bust wording and the ease allowances), never to hide listings.
 */
enum class FitProfile(val dbValue: String, val label: String) {
    MAN("man", "Man"),
    WOMAN("woman", "Woman");

    companion object {
        fun fromDb(value: String?): FitProfile? = entries.firstOrNull { it.dbValue == value }
    }
}

/** A renter's body measurement, stored in centimeters in private_profiles.<column>. */
enum class BodyMeasurement(val label: String, val column: String, val howTo: String) {
    CHEST("Chest", "chest_cm", "Around the fullest part of your chest, under your arms."),
    WAIST("Waist", "waist_cm", "Around your natural waist, the narrowest part, above the belly button."),
    HIPS("Hips", "hips_cm", "Around the fullest part of your hips, feet together."),
    SHOULDER("Shoulder", "shoulder_cm", "Across your back, from one shoulder tip to the other."),
    HEIGHT("Height", "height_cm", "Standing straight against a wall, without shoes."),
    INSEAM("Inseam", "inseam_cm", "From the top of your inner thigh down to your ankle.");

    /** "Bust" instead of "Chest" for Woman; everything else (and no choice) keeps [label]. */
    fun labelFor(profile: FitProfile?): String =
        if (this == CHEST && profile == FitProfile.WOMAN) "Bust" else label

    /** The how-to line, saying "bust" for Woman. */
    fun howToFor(profile: FitProfile?): String =
        if (this == CHEST && profile == FitProfile.WOMAN) "Around the fullest part of your bust, under your arms." else howTo
}

/** Matches the profiles_*_cm checks: above 0 and below 300 cm. */
fun isValidBodyCm(cm: Double): Boolean = cm > 0.0 && cm < 300.0

/** [text] in [unit] ("cm" or "in") converted to cm and rounded to 1 decimal, or null if not a number. */
fun bodyInputToCm(text: String, unit: String): Double? {
    val value = text.trim().toDoubleOrNull() ?: return null
    val cm = if (unit == "in") value * CM_PER_INCH else value
    return BigDecimal.valueOf(cm).setScale(1, RoundingMode.HALF_UP).toDouble()
}

/** A stored cm value as text in [unit], rounded to 1 decimal: "91.5", "36". */
fun cmToBodyInput(cm: Double?, unit: String): String {
    if (cm == null) return ""
    val value = if (unit == "in") cm / CM_PER_INCH else cm
    return BigDecimal.valueOf(value).setScale(1, RoundingMode.HALF_UP).stripTrailingZeros().toPlainString()
}
