package com.dzirent.app.util

/** Settings > Appearance > Theme. */
enum class ThemeMode(val key: String, val label: String) {
    SYSTEM("system", "System"),
    LIGHT("light", "Light"),
    DARK("dark", "Dark");

    fun isDark(systemDark: Boolean): Boolean = when (this) {
        SYSTEM -> systemDark
        LIGHT -> false
        DARK -> true
    }

    companion object {
        fun fromKey(key: String?): ThemeMode = entries.firstOrNull { it.key == key } ?: SYSTEM
    }
}

/** Everything in Settings that is saved on the device rather than in the account. */
data class AppSettings(
    val theme: ThemeMode = ThemeMode.SYSTEM,
    val distanceUnit: DistanceUnit = DistanceUnit.MILES,
    val measureUnit: MeasureUnit = MeasureUnit.INCHES,
    /** One of [RENTAL_LENGTH_OPTIONS]: the length of Home's starting date range. */
    val rentalDays: Int = DEFAULT_RENTAL_DAYS,
    /** The distance filter's starting step (see [DistanceUnit]). */
    val defaultDistanceStep: Int = DEFAULT_DISTANCE_STEP
)
