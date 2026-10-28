package com.dzirent.app.util

import com.dzirent.app.data.model.Listing
import java.time.LocalDate
import kotlin.math.ceil

/** At most this many pieces under "Suggested for you". */
const val SUGGESTIONS_MAX = 12

/** How far ahead "other dates" looks for a window with matches. */
const val OTHER_DATES_LOOKAHEAD_DAYS = 60

/** The steps, in the order they are tried. */
enum class SuggestionStep {
    /** a. Typo fix: trigram matches from search_listings_similar. */
    SIMILAR,
    /** b. Words related to the query (search_related.json). */
    RELATED,
    /** c. The query's category hint (search_related.json). */
    CATEGORY,
    /** d. One filter loosened at a time; the query and the other filters stay. */
    OTHER_DATES,
    WIDER_DISTANCE,
    HIGHER_PRICE,
    ALL_CATEGORIES,
    ANY_SIZE,
    /** Last resort, so the screen is never empty: the nearest pieces, ignoring the search. */
    NEAR_YOU
}

/** One group under "Suggested for you", with the short reason shown above it. */
data class SuggestionGroup(val step: SuggestionStep, val reason: String, val listings: List<Listing>)

/** Everything the suggestions are built from. */
data class SuggestionInput(
    val query: String,
    val filters: BrowseFilters,
    /** Null = "Any dates" (Search only). */
    val dates: DateRange?,
    val me: String,
    /** Listings loaded for the current distance. */
    val nearby: List<Listing>,
    /** Listings loaded for the widest distance (empty if not loaded or already the widest). */
    val wider: List<Listing>,
    /** search_listings_similar results, best match first, with distanceKm filled in. */
    val similar: List<Listing>,
    val related: RelatedMatch?,
    val distanceUnit: DistanceUnit,
    val today: LocalDate,
    /** My "Styles you're interested in": pieces in these styles go first within a group. */
    val styleInterests: Set<String> = emptySet()
)

/**
 * The suggestions shown when a search, filter or row has no results, in this order, stopping
 * once there are [SUGGESTIONS_MAX] pieces: a typo fix, related words, the category hint, then
 * each filter loosened on its own (other dates, wider distance, higher price, all categories,
 * any size), and finally the nearest pieces if nothing else matched. A piece appears once.
 * Within a group, pieces in the styles I'm interested in come first.
 */
fun buildSuggestions(input: SuggestionInput): List<SuggestionGroup> {
    val groups = mutableListOf<SuggestionGroup>()
    val taken = mutableSetOf<String>()
    var count = 0

    fun add(step: SuggestionStep, reason: String, candidates: List<Listing>) {
        if (count >= SUGGESTIONS_MAX) return
        // Styles only reorder (stable, so nearest or best match stays next); nothing is dropped.
        val fresh = candidates.filter { it.lenderId != input.me && it.id !in taken }
            .sortedByDescending { styleOverlap(it.listingStyles, input.styleInterests) > 0 }
            .take(SUGGESTIONS_MAX - count)
        if (fresh.isEmpty()) return
        groups += SuggestionGroup(step, reason, fresh)
        taken += fresh.map { it.id }
        count += fresh.size
    }

    fun fits(l: Listing, query: String = input.query, filters: BrowseFilters = input.filters, dates: DateRange? = input.dates) =
        l.matchesQuery(query) && l.matchesFilters(filters) && (dates == null || isAvailableFor(l.availabilityWindows(), dates))

    val nearest = input.nearby.sortedBy { it.distanceKm ?: Double.MAX_VALUE }
    val query = input.query.trim()
    val radiusKm = input.distanceUnit.radiusKm(input.filters.distanceStep)

    // a. Typo fix: the same filters and dates, the similar text instead of the exact one.
    if (query.isNotEmpty()) {
        add(SuggestionStep.SIMILAR, "Similar to ‘$query’",
            input.similar.filter { (it.distanceKm ?: Double.MAX_VALUE) <= radiusKm && fits(it, query = "") })
    }
    // b. Related words.
    input.related?.takeIf { it.words.isNotEmpty() }?.let { match ->
        add(SuggestionStep.RELATED, "Related to ${match.keyword}",
            nearest.filter { l -> match.words.any { word -> fits(l, query = word) } })
    }
    // c. The category hint, in place of any category filter.
    input.related?.category?.let { category ->
        add(SuggestionStep.CATEGORY, "More in $category",
            nearest.filter { fits(it, query = "", filters = input.filters.withCategory(category)) })
    }
    // d. Loosen one filter at a time.
    input.dates?.let { dates ->
        val days = dates.end.toEpochDay() - dates.start.toEpochDay()
        for (shift in 1..OTHER_DATES_LOOKAHEAD_DAYS) {
            val start = maxOf(dates.start, input.today).plusDays(shift.toLong())
            val window = DateRange(start, start.plusDays(days))
            val found = nearest.filter { fits(it, dates = window) }
            if (found.isNotEmpty()) {
                add(SuggestionStep.OTHER_DATES, "Available ${formatDateChip(window)}", found)
                break
            }
        }
    }
    if (input.filters.distanceStep < DISTANCE_STEPS.last) {
        val widerNearest = input.wider.sortedBy { it.distanceKm ?: Double.MAX_VALUE }
        for (step in input.filters.distanceStep + 1..DISTANCE_STEPS.last) {
            val limit = input.distanceUnit.radiusKm(step)
            val found = widerNearest.filter { (it.distanceKm ?: Double.MAX_VALUE) <= limit && fits(it) }
            if (found.any { it.id !in taken }) {
                add(SuggestionStep.WIDER_DISTANCE, "Within ${input.distanceUnit.withinLabel(step)}", found)
                break
            }
        }
    }
    input.filters.maxPrice?.let { max ->
        val pricier = nearest.filter { (it.priceUsd ?: 0.0) > max && fits(it, filters = input.filters.copy(maxPrice = null)) }
            .sortedBy { it.priceUsd }
            .filter { it.id !in taken }
            .take(SUGGESTIONS_MAX - count)
        if (pricier.isNotEmpty()) {
            add(SuggestionStep.HIGHER_PRICE, "Up to ${formatPrice(roundUpPrice(pricier.last().priceUsd!!))}",
                pricier.sortedBy { it.distanceKm ?: Double.MAX_VALUE })
        }
    }
    if (input.filters.category != null) {
        add(SuggestionStep.ALL_CATEGORIES, "All categories", nearest.filter { fits(it, filters = input.filters.withCategory(null)) })
    }
    if (input.filters.size != null) {
        add(SuggestionStep.ANY_SIZE, "Any size", nearest.filter { fits(it, filters = input.filters.copy(size = null)) })
    }
    // Never an empty screen: the nearest pieces for these dates, or for any dates.
    if (count == 0) {
        val forDates = nearest.filter { fits(it, query = "", filters = BrowseFilters(distanceStep = input.filters.distanceStep)) }
        add(SuggestionStep.NEAR_YOU, "Near you", forDates.ifEmpty { nearest })
    }
    return groups
}

/** The next whole $5 at or above [usd]: 37.5 -> 40. */
fun roundUpPrice(usd: Double): Double = ceil(usd / 5.0) * 5.0
