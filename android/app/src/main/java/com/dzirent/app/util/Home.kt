package com.dzirent.app.util

import com.dzirent.app.data.model.Listing
import com.dzirent.app.data.model.PrivateProfile
import com.dzirent.app.data.model.Profile
import java.time.OffsetDateTime

/** A Home row is only shown with at least this many listings. */
const val HOME_ROW_MIN = 3

/** "New near you" covers listings created in the last 7 days. */
const val NEW_NEAR_YOU_DAYS = 7L

/** One lender on Home's Studios tab, built from their listings that match the filters. */
data class StudioSummary(
    val lender: Profile,
    val pieceCount: Int,
    val coverPhotoUrl: String?,
    val distanceKm: Double?
)

/** Everything Home shows, from the listings loaded for the current distance. */
data class HomeSections(
    /** Products tab: matching listings, newest first. */
    val visible: List<Listing>,
    /** Null when the renter has no measurements (Home shows the "Add your measurements" card). */
    val likelyFit: List<Listing>?,
    val newNearYou: List<Listing>,
    val fromSavedStudios: List<Listing>,
    val studios: List<StudioSummary>
)

enum class HomeRow(val title: String, val subtitle: String?) {
    FIT("Likely to fit you", "Matched to your measurements"),
    NEW("New near you", null),
    SAVED_STUDIOS("From studios you saved", null);

    fun listings(sections: HomeSections): List<Listing> = when (this) {
        FIT -> sections.likelyFit.orEmpty()
        NEW -> sections.newNearYou
        SAVED_STUDIOS -> sections.fromSavedStudios
    }
}

/** The listing's availability as date ranges (empty = available any time). */
fun Listing.availabilityWindows(): List<DateRange> = availability.mapNotNull { parseDateRange(it.startDate, it.endDate) }

private fun Listing.distanceOrMax(): Double = distanceKm ?: Double.MAX_VALUE

/**
 * Home's rows and tabs. Leaves out my own listings, and anything that doesn't match
 * [filters] or isn't available for every day of [dates].
 */
fun buildHomeSections(
    listings: List<Listing>,
    me: String,
    filters: BrowseFilters,
    dates: DateRange,
    body: PrivateProfile,
    savedStudioIds: Set<String>,
    now: OffsetDateTime = OffsetDateTime.now()
): HomeSections {
    val visible = listings
        .filter { it.lenderId != me && it.matchesFilters(filters) && isAvailableFor(it.availabilityWindows(), dates) }
        .sortedByDescending { timestampMillis(it.createdAt) }
    val newSince = now.minusDays(NEW_NEAR_YOU_DAYS).toInstant().toEpochMilli()
    return HomeSections(
        visible = visible,
        likelyFit = if (hasFitMeasurements(body)) visible.filter { isLikelyFit(it, body) }.sortedBy { it.distanceOrMax() } else null,
        newNearYou = visible.filter { timestampMillis(it.createdAt) >= newSince }.sortedBy { it.distanceOrMax() },
        fromSavedStudios = visible.filter { it.lenderId in savedStudioIds },
        studios = visible.groupBy { it.lenderId }.map { (lenderId, pieces) ->
            StudioSummary(
                lender = pieces.first().lender ?: Profile(id = lenderId),
                pieceCount = pieces.size,
                coverPhotoUrl = pieces.firstNotNullOfOrNull { piece -> piece.photos.minByOrNull { it.position }?.storagePath },
                distanceKm = pieces.mapNotNull { it.distanceKm }.minOrNull()
            )
        }.sortedBy { it.distanceKm ?: Double.MAX_VALUE }
    )
}
