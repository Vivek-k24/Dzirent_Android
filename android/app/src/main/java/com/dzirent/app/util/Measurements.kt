package com.dzirent.app.util

import kotlin.math.roundToLong

const val MEASUREMENT_LABEL_MAX = 40

/**
 * Settings > Preferences > Measurements: how garment and body measurements are shown and
 * entered. What is stored doesn't change (listings keep their own unit, bodies are in cm).
 */
enum class MeasureUnit(val key: String, val label: String) {
    INCHES("in", "Inches"),
    CENTIMETERS("cm", "Centimeters");

    companion object {
        fun fromKey(key: String?): MeasureUnit = entries.firstOrNull { it.key == key } ?: INCHES
    }
}

/** "38", "38.5": one decimal, no trailing ".0". */
fun formatMeasurementNumber(value: Double): String {
    val rounded = (value * 10).roundToLong() / 10.0
    return if (rounded % 1.0 == 0.0) rounded.toLong().toString() else rounded.toString()
}

/**
 * A listing measurement stored as [value] [storedUnit] ("in" or "cm"), shown in [unit]:
 * 96.5 cm shows as "38 in" for Inches.
 */
fun formatMeasurement(value: Double, storedUnit: String, unit: MeasureUnit): String {
    val shown = when {
        storedUnit == unit.key -> value
        unit == MeasureUnit.INCHES -> value / CM_PER_INCH
        else -> value * CM_PER_INCH
    }
    return "${formatMeasurementNumber(shown)} ${unit.key}"
}

/** One editor row as typed: a label and the number as text. */
data class MeasurementInput(val label: String, val valueText: String) {
    val isBlank: Boolean get() = label.isBlank() && valueText.isBlank()
}

/**
 * Error for each row (null = fine), matching the DB checks: label 1-40 chars, value > 0
 * and < 1000, and no duplicate labels (case-insensitive). Fully blank rows are skipped.
 */
fun validateMeasurements(rows: List<MeasurementInput>): List<String?> {
    val seen = mutableSetOf<String>()
    return rows.map { row ->
        if (row.isBlank) return@map null
        val label = row.label.trim()
        val value = row.valueText.trim().toDoubleOrNull()
        val key = label.lowercase()
        when {
            label.isEmpty() -> "Add a label."
            label.length > MEASUREMENT_LABEL_MAX -> "Label can be up to $MEASUREMENT_LABEL_MAX characters."
            !seen.add(key) -> "\"$label\" is already listed."
            row.valueText.isBlank() -> "Enter a number."
            value == null -> "Enter a number, like 38 or 38.5."
            value <= 0.0 || value >= 1000.0 -> "Must be more than 0 and less than 1000."
            else -> null
        }
    }
}
