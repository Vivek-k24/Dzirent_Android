package com.dzirent.app.ui

import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.grid.GridCells
import androidx.compose.foundation.lazy.grid.GridItemSpan
import androidx.compose.foundation.lazy.grid.LazyGridScope
import androidx.compose.foundation.lazy.grid.LazyVerticalGrid
import androidx.compose.foundation.lazy.grid.items
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import com.dzirent.app.data.SearchRelatedStore
import com.dzirent.app.data.model.Listing
import com.dzirent.app.util.BrowseFilters
import com.dzirent.app.util.DISTANCE_STEPS
import com.dzirent.app.util.DateRange
import com.dzirent.app.util.SuggestionGroup
import com.dzirent.app.util.SuggestionInput
import com.dzirent.app.util.buildSuggestions
import kotlinx.coroutines.delay
import java.time.LocalDate

/** Wait this long after the last keystroke before asking the server for typo matches. */
private const val SIMILAR_DEBOUNCE_MS = 350L

/**
 * "Suggested for you" for a search, filter or row with no results, while [active]. Shows
 * suggestions from what's already loaded at once, then adds typo matches and pieces further
 * away when those arrive. Null while inactive.
 */
@Composable
fun rememberSuggestions(
    session: BrowseSession,
    me: String,
    lat: Double?,
    lng: Double?,
    query: String,
    filters: BrowseFilters,
    dates: DateRange?,
    active: Boolean
): List<SuggestionGroup>? {
    val context = LocalContext.current
    val unit = LocalAppSettings.current.distanceUnit
    var groups by remember { mutableStateOf<List<SuggestionGroup>?>(null) }
    val loaded = session.loadedListings()

    LaunchedEffect(active, query, filters, dates, lat, lng, unit, loaded) {
        if (!active || lat == null || lng == null) {
            groups = null
            return@LaunchedEffect
        }
        val related = query.takeIf { it.isNotBlank() }?.let { SearchRelatedStore.get(context).lookup(it) }
        fun build(similar: List<Listing>, wider: List<Listing>) =
            buildSuggestions(SuggestionInput(query, filters, dates, me, loaded, wider, similar, related, unit, LocalDate.now()))
        groups = build(emptyList(), emptyList())
        delay(SIMILAR_DEBOUNCE_MS)
        val similar = if (query.trim().length >= 3) runCatching { session.similarListings(query.trim(), lat, lng) }.getOrDefault(emptyList()) else emptyList()
        val wider = if (filters.distanceStep < DISTANCE_STEPS.last) {
            runCatching { session.widerListings(lat, lng, unit.radiusKm(DISTANCE_STEPS.last)) }.getOrDefault(emptyList())
        } else {
            emptyList()
        }
        groups = build(similar, wider)
    }
    return if (active) groups else null
}

/**
 * The no-results block inside a 2-column grid: "0 matching products" (with the query in
 * quotes), "Suggested for you", then each group under its reason.
 */
fun LazyGridScope.suggestionItems(
    groups: List<SuggestionGroup>?,
    query: String,
    onOpenListing: (String) -> Unit
) {
    item(key = "no-results", span = { GridItemSpan(maxLineSpan) }) {
        Text(
            text = if (query.isBlank()) "0 matching products" else "0 matching products for “${query.trim()}”",
            style = MaterialTheme.typography.bodyMedium,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
            modifier = Modifier.padding(top = 8.dp)
        )
    }
    when {
        groups == null -> item(key = "suggestions-loading", span = { GridItemSpan(maxLineSpan) }) {
            LinearProgressIndicator(modifier = Modifier.fillMaxWidth().padding(vertical = 16.dp))
        }
        groups.isEmpty() -> item(key = "suggestions-none", span = { GridItemSpan(maxLineSpan) }) {
            Text(
                text = "Nothing near you yet. Try a wider distance in the filters.",
                style = MaterialTheme.typography.bodyMedium,
                modifier = Modifier.padding(vertical = 16.dp)
            )
        }
        else -> {
            item(key = "suggested", span = { GridItemSpan(maxLineSpan) }) {
                Text(
                    text = "Suggested for you",
                    style = MaterialTheme.typography.titleMedium,
                    fontWeight = FontWeight.Bold,
                    modifier = Modifier.padding(top = 16.dp)
                )
            }
            groups.forEach { group ->
                item(key = "reason-${group.step}", span = { GridItemSpan(maxLineSpan) }) {
                    Text(
                        text = group.reason,
                        style = MaterialTheme.typography.labelLarge,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                }
                items(group.listings, key = { "suggested-${it.id}" }) { listing ->
                    ListingCard(listing = listing, onClick = { onOpenListing(listing.id) })
                }
            }
        }
    }
}

/** A full screen of [suggestionItems], for places that otherwise show an empty list. */
@Composable
fun SuggestionsGrid(
    groups: List<SuggestionGroup>?,
    query: String,
    onOpenListing: (String) -> Unit,
    modifier: Modifier = Modifier
) {
    LazyVerticalGrid(
        columns = GridCells.Fixed(2),
        contentPadding = PaddingValues(start = 16.dp, end = 16.dp, bottom = 16.dp),
        horizontalArrangement = Arrangement.spacedBy(12.dp),
        verticalArrangement = Arrangement.spacedBy(12.dp),
        modifier = modifier.fillMaxSize()
    ) {
        suggestionItems(groups, query, onOpenListing)
    }
}
