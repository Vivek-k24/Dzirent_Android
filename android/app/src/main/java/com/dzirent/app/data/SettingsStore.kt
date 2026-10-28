package com.dzirent.app.data

import android.content.Context
import androidx.datastore.core.DataStore
import androidx.datastore.preferences.core.Preferences
import androidx.datastore.preferences.core.edit
import androidx.datastore.preferences.core.emptyPreferences
import androidx.datastore.preferences.core.intPreferencesKey
import androidx.datastore.preferences.core.stringPreferencesKey
import androidx.datastore.preferences.preferencesDataStore
import com.dzirent.app.util.AppSettings
import com.dzirent.app.util.DEFAULT_DISTANCE_STEP
import com.dzirent.app.util.DEFAULT_RENTAL_DAYS
import com.dzirent.app.util.DISTANCE_STEPS
import com.dzirent.app.util.DistanceUnit
import com.dzirent.app.util.MeasureUnit
import com.dzirent.app.util.RENTAL_LENGTH_OPTIONS
import com.dzirent.app.util.ThemeMode
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.catch
import kotlinx.coroutines.flow.map
import java.io.IOException

// One DataStore per file per process, so it lives at the top level.
private val Context.settingsDataStore: DataStore<Preferences> by preferencesDataStore(name = "settings")

/** Settings saved on this device (DataStore), shared by every account that signs in on it. */
class SettingsStore(context: Context) {
    private val store = context.applicationContext.settingsDataStore

    val settings: Flow<AppSettings> = store.data
        .catch { e -> if (e is IOException) emit(emptyPreferences()) else throw e }
        .map { prefs ->
            AppSettings(
                theme = ThemeMode.fromKey(prefs[THEME]),
                distanceUnit = DistanceUnit.fromKey(prefs[DISTANCE_UNIT]),
                measureUnit = MeasureUnit.fromKey(prefs[MEASURE_UNIT]),
                rentalDays = prefs[RENTAL_DAYS]?.takeIf { it in RENTAL_LENGTH_OPTIONS } ?: DEFAULT_RENTAL_DAYS,
                defaultDistanceStep = prefs[DEFAULT_DISTANCE]?.takeIf { it in DISTANCE_STEPS } ?: DEFAULT_DISTANCE_STEP
            )
        }

    suspend fun setTheme(mode: ThemeMode) = store.edit { it[THEME] = mode.key }
    suspend fun setDistanceUnit(unit: DistanceUnit) = store.edit { it[DISTANCE_UNIT] = unit.key }
    suspend fun setMeasureUnit(unit: MeasureUnit) = store.edit { it[MEASURE_UNIT] = unit.key }
    suspend fun setRentalDays(days: Int) = store.edit { it[RENTAL_DAYS] = days }
    suspend fun setDefaultDistanceStep(step: Int) = store.edit { it[DEFAULT_DISTANCE] = step }

    private companion object {
        val THEME = stringPreferencesKey("theme")
        val DISTANCE_UNIT = stringPreferencesKey("distance_unit")
        val MEASURE_UNIT = stringPreferencesKey("measure_unit")
        val RENTAL_DAYS = intPreferencesKey("rental_days")
        val DEFAULT_DISTANCE = intPreferencesKey("default_distance_step")
    }
}
