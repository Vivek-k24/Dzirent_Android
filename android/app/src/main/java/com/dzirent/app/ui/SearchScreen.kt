package com.dzirent.app.ui

import android.content.Context
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.grid.GridCells
import androidx.compose.foundation.lazy.grid.GridItemSpan
import androidx.compose.foundation.lazy.grid.LazyVerticalGrid
import androidx.compose.foundation.lazy.grid.items
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.text.KeyboardActions
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.Sort
import androidx.compose.material.icons.filled.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.focus.focusRequester
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalFocusManager
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.unit.dp
import com.dzirent.app.data.model.Profile
import com.dzirent.app.util.BrowseFilters
import com.dzirent.app.util.SearchSort
import com.dzirent.app.util.addRecentSearch
import com.dzirent.app.util.filterChips
import com.dzirent.app.util.formatDateChip
import com.dzirent.app.util.matchesQuery
import com.dzirent.app.util.sortSearchResults
import kotlinx.coroutines.launch

/** Recent searches on this device, per user, newest first. */
class RecentSearchStore(context: Context, private val userId: String) {
    private val prefs = context.applicationContext.getSharedPreferences("dzirent_prefs", Context.MODE_PRIVATE)
    private val key get() = "recent_searches_$userId"

    fun load(): List<String> = prefs.getString(key, null)?.split('\n')?.filter { it.isNotBlank() }.orEmpty()

    fun add(query: String): List<String> = addRecentSearch(load(), query).also { save(it) }

    fun clear() = save(emptyList())

    private fun save(list: List<String>) {
        prefs.edit().putString(key, list.joinToString("\n")).apply()
    }
}

/**
 * Text search over titles and descriptions. The field is focused right away; recent
 * searches show while it's empty. Results respect the date range and the sidebar filters,
 * shown as removable chips, and can be sorted with the icon in the results header.
 */
@Composable
fun SearchScreen(
    session: BrowseSession,
    profile: Profile,
    onSetLocation: () -> Unit,
    onOpenListing: (String) -> Unit
) {
    val context = LocalContext.current
    val store = remember(profile.id) { RecentSearchStore(context, profile.id) }
    var query by rememberSaveable { mutableStateOf("") }
    var recent by remember { mutableStateOf(store.load()) }
    var sort by rememberSaveable { mutableStateOf(SearchSort.NEAREST) }
    var sortMenuOpen by remember { mutableStateOf(false) }
    var showDatePicker by remember { mutableStateOf(false) }
    // Search can drop the date range ("Any dates"); Home always keeps one.
    var anyDates by rememberSaveable { mutableStateOf(false) }
    val focusRequester = remember { FocusRequester() }
    val focusManager = LocalFocusManager.current
    val scope = rememberCoroutineScope()
    val lat = profile.approxLat
    val lng = profile.approxLng

    LaunchedEffect(Unit) { focusRequester.requestFocus() }
    val settings = LocalAppSettings.current
    val radiusKm = settings.distanceUnit.radiusKm(session.filters.distanceStep)
    LaunchedEffect(lat, lng, radiusKm) {
        if (lat != null && lng != null) session.load(lat, lng, radiusKm)
    }

    fun saveRecent(q: String) {
        recent = store.add(q)
    }

    if (showDatePicker) {
        DateRangePickerDialog(
            title = "Your dates",
            allowPast = false,
            initial = session.dates,
            confirmLabel = "Done",
            onDismiss = { showDatePicker = false },
            onConfirm = { range ->
                session.dates = range
                anyDates = false
                showDatePicker = false
            }
        )
    }

    Column(modifier = Modifier.fillMaxSize()) {
        OutlinedTextField(
            value = query,
            onValueChange = { if (it.length <= 80) query = it },
            placeholder = { Text("Search pieces") },
            leadingIcon = { Icon(Icons.Default.Search, contentDescription = null) },
            trailingIcon = {
                if (query.isNotEmpty()) {
                    IconButton(onClick = { query = "" }) { Icon(Icons.Default.Clear, contentDescription = "Clear search") }
                }
            },
            singleLine = true,
            keyboardOptions = KeyboardOptions(imeAction = ImeAction.Search),
            keyboardActions = KeyboardActions(onSearch = {
                saveRecent(query)
                focusManager.clearFocus()
            }),
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 16.dp, vertical = 8.dp)
                .focusRequester(focusRequester)
        )

        if (query.isBlank()) {
            RecentSearches(
                recent = recent,
                onPick = { picked ->
                    query = picked
                    saveRecent(picked)
                    focusManager.clearFocus()
                },
                onClear = {
                    store.clear()
                    recent = emptyList()
                }
            )
            return@Column
        }

        if (lat == null || lng == null) {
            EmptyView(
                title = "Set your location",
                description = "Search looks at pieces near you. Set your approximate location first.",
                icon = Icons.Default.LocationOff,
                onAction = onSetLocation,
                actionLabel = "Set my location"
            )
            return@Column
        }

        val chips = filterChips(session.filters, settings.distanceUnit, settings.defaultDistanceStep)
        LazyRow(
            contentPadding = PaddingValues(horizontal = 16.dp),
            horizontalArrangement = Arrangement.spacedBy(8.dp)
        ) {
            // The dates are a removable chip like the other filters; without them, availability
            // is ignored and the chip offers to pick dates again.
            item(key = "dates") {
                if (anyDates) {
                    AssistChip(
                        onClick = { showDatePicker = true },
                        label = { Text("Any dates") },
                        leadingIcon = { Icon(Icons.Default.CalendarToday, contentDescription = null, modifier = Modifier.size(16.dp)) }
                    )
                } else {
                    InputChip(
                        selected = false,
                        onClick = { anyDates = true },
                        label = { Text(formatDateChip(session.dates)) },
                        leadingIcon = { Icon(Icons.Default.CalendarToday, contentDescription = null, modifier = Modifier.size(16.dp)) },
                        trailingIcon = {
                            Icon(Icons.Default.Close, contentDescription = "Remove dates", modifier = Modifier.size(16.dp))
                        }
                    )
                }
            }
            items(chips, key = { it.label }) { chip ->
                InputChip(
                    selected = false,
                    onClick = { session.filters = chip.remove(session.filters) },
                    label = { Text(chip.label) },
                    trailingIcon = { Icon(Icons.Default.Close, contentDescription = "Remove ${chip.label}", modifier = Modifier.size(16.dp)) }
                )
            }
        }

        when (val state = session.state) {
            is UiState.Loading -> LoadingView(message = "Searching...")
            is UiState.Error -> ErrorView(message = state.message, onRetry = { scope.launch { session.load(lat, lng, radiusKm, force = true) } })
            is UiState.Success -> {
                val results = remember(state, session.filters, session.dates, anyDates, query, sort) {
                    sortSearchResults(session.sections(anyDates)?.visible.orEmpty().filter { it.matchesQuery(query) }, sort)
                }
                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    modifier = Modifier.padding(start = 16.dp, end = 4.dp)
                ) {
                    Text(
                        text = "${results.size} ${if (results.size == 1) "result" else "results"}",
                        style = MaterialTheme.typography.labelLarge,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                        modifier = Modifier.weight(1f)
                    )
                    Box {
                        IconButton(onClick = { sortMenuOpen = true }) {
                            Icon(Icons.AutoMirrored.Filled.Sort, contentDescription = "Sort: ${sort.label}")
                        }
                        DropdownMenu(expanded = sortMenuOpen, onDismissRequest = { sortMenuOpen = false }) {
                            SearchSort.entries.forEach { option ->
                                DropdownMenuItem(
                                    text = {
                                        Text(option.label, fontWeight = if (option == sort) FontWeight.Bold else FontWeight.Normal)
                                    },
                                    onClick = {
                                        sort = option
                                        sortMenuOpen = false
                                    }
                                )
                            }
                        }
                    }
                }
                if (results.isEmpty()) {
                    NothingHere(
                        text = "No pieces match “${query.trim()}” for these dates and filters.",
                        actionLabel = if (chips.isNotEmpty()) "Clear filters" else "Change dates",
                        onAction = {
                            if (chips.isNotEmpty()) session.filters = BrowseFilters(distanceStep = settings.defaultDistanceStep)
                            else showDatePicker = true
                        }
                    )
                } else {
                    LazyVerticalGrid(
                        columns = GridCells.Fixed(2),
                        contentPadding = PaddingValues(start = 16.dp, end = 16.dp, bottom = 16.dp),
                        horizontalArrangement = Arrangement.spacedBy(12.dp),
                        verticalArrangement = Arrangement.spacedBy(16.dp),
                        modifier = Modifier.fillMaxSize()
                    ) {
                        items(results, key = { it.id }) { listing ->
                            ListingCard(
                                listing = listing,
                                onClick = {
                                    saveRecent(query)
                                    onOpenListing(listing.id)
                                }
                            )
                        }
                    }
                }
            }
        }
    }
}

@Composable
private fun RecentSearches(recent: List<String>, onPick: (String) -> Unit, onClear: () -> Unit) {
    if (recent.isEmpty()) {
        Text(
            text = "Search titles and descriptions of pieces near you.",
            style = MaterialTheme.typography.bodyMedium,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
            modifier = Modifier.padding(horizontal = 16.dp, vertical = 8.dp)
        )
        return
    }
    Row(
        verticalAlignment = Alignment.CenterVertically,
        modifier = Modifier.padding(start = 16.dp, end = 8.dp)
    ) {
        Text(
            text = "Recent searches",
            style = MaterialTheme.typography.titleSmall,
            fontWeight = FontWeight.Bold,
            modifier = Modifier.weight(1f)
        )
        TextButton(onClick = onClear) { Text("Clear") }
    }
    recent.forEach { item ->
        Row(
            verticalAlignment = Alignment.CenterVertically,
            modifier = Modifier
                .fillMaxWidth()
                .clickable { onPick(item) }
                .padding(horizontal = 16.dp, vertical = 12.dp)
        ) {
            Icon(
                Icons.Default.History,
                contentDescription = null,
                tint = MaterialTheme.colorScheme.onSurfaceVariant,
                modifier = Modifier.size(20.dp)
            )
            Spacer(modifier = Modifier.width(12.dp))
            Text(item, style = MaterialTheme.typography.bodyLarge)
        }
    }
}
