package com.dzirent.app.ui

import androidx.compose.runtime.Stable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import com.dzirent.app.data.model.Listing
import com.dzirent.app.data.model.PrivateProfile
import com.dzirent.app.data.repository.DzirentRepository
import com.dzirent.app.util.BrowseFilters
import com.dzirent.app.util.DEFAULT_DISTANCE_STEP
import com.dzirent.app.util.DEFAULT_RENTAL_DAYS
import com.dzirent.app.util.HomeSections
import com.dzirent.app.util.buildHomeSections
import com.dzirent.app.util.defaultBrowseRange
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import java.time.LocalDate

/** Nearby listings plus what Home needs to sort them into rows. */
data class BrowseData(
    val listings: List<Listing>,
    val savedStudioIds: Set<String>,
    val body: PrivateProfile
)

/**
 * Shared by Home, See all and Search: the sidebar filters, the date range and one load of
 * nearby listings (reloaded when the location or distance changes, or on refresh).
 * The distance and the date range start from Settings > Preferences.
 */
@Stable
class BrowseSession(
    private val repository: DzirentRepository,
    private val userId: String,
    defaultDistanceStep: Int = DEFAULT_DISTANCE_STEP,
    rentalDays: Int = DEFAULT_RENTAL_DAYS
) {
    var filters by mutableStateOf(BrowseFilters(distanceStep = defaultDistanceStep))
    var dates by mutableStateOf(defaultBrowseRange(LocalDate.now(), rentalDays))
    var state by mutableStateOf<UiState<BrowseData>>(UiState.Loading)
        private set

    private var loadedFor: Triple<Double, Double, Double>? = null
    private val mutex = Mutex()

    /**
     * Loads listings within [radiusKm] unless this location and radius are already loaded;
     * [force] always reloads.
     */
    suspend fun load(lat: Double, lng: Double, radiusKm: Double, force: Boolean = false) = mutex.withLock {
        val key = Triple(lat, lng, radiusKm)
        if (!force && key == loadedFor && state is UiState.Success) return@withLock
        if (state !is UiState.Success) state = UiState.Loading
        state = try {
            val listings = repository.getBrowseListings(lat, lng, radiusKm)
            val saved = repository.getSavedStudioIds(userId)
            val body = repository.getPrivateProfile(userId)
            loadedFor = key
            UiState.Success(BrowseData(listings, saved, body))
        } catch (e: Exception) {
            UiState.Error(e.userMessage("Couldn't load listings. Please try again."))
        }
    }

    /** The next [load] fetches again, e.g. after saving measurements or a studio. */
    fun invalidate() {
        loadedFor = null
    }

    /**
     * Home's rows and tabs for the current filters and dates, once loaded. [anyDates]
     * (Search only) ignores the date range.
     */
    fun sections(anyDates: Boolean = false): HomeSections? = (state as? UiState.Success)?.data?.let {
        buildHomeSections(it.listings, userId, filters, if (anyDates) null else dates, it.body, it.savedStudioIds)
    }
}
