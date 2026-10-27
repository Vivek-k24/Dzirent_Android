package com.dzirent.app.ui

import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.grid.GridCells
import androidx.compose.foundation.lazy.grid.GridItemSpan
import androidx.compose.foundation.lazy.grid.LazyGridScope
import androidx.compose.foundation.lazy.grid.LazyVerticalGrid
import androidx.compose.foundation.lazy.grid.items
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.*
import androidx.compose.material3.*
import androidx.compose.material3.pulltorefresh.PullToRefreshBox
import androidx.compose.runtime.*
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.dzirent.app.data.model.Listing
import com.dzirent.app.data.model.Profile
import com.dzirent.app.ui.theme.DzirentAccent
import com.dzirent.app.util.BrowseFilters
import com.dzirent.app.util.DISTANCE_OPTIONS_KM
import com.dzirent.app.util.GuideTarget
import com.dzirent.app.util.HOME_ROW_MIN
import com.dzirent.app.util.HomeRow
import com.dzirent.app.util.HomeSections
import com.dzirent.app.util.StudioSummary
import com.dzirent.app.util.formatDateChip
import com.dzirent.app.util.formatDistance
import kotlinx.coroutines.launch

/**
 * Home: top bar (avatar, wordmark, location), one sticky date chip, then up to three rows
 * and the Products / Studios grid. Filters come from the sidebar.
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun HomeScreen(
    session: BrowseSession,
    profile: Profile,
    onOpenSidebar: () -> Unit,
    onSetLocation: () -> Unit,
    onOpenListing: (String) -> Unit,
    onOpenStudio: ((String) -> Unit)?,
    onSeeAll: (HomeRow) -> Unit,
    onOpenMeasurements: () -> Unit
) {
    val lat = profile.approxLat
    val lng = profile.approxLng
    val scope = rememberCoroutineScope()
    var showDatePicker by remember { mutableStateOf(false) }
    var refreshing by remember { mutableStateOf(false) }
    var studiosTab by rememberSaveable { mutableStateOf(false) }

    LaunchedEffect(lat, lng, session.filters.radiusKm) {
        if (lat != null && lng != null) session.load(lat, lng)
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
                showDatePicker = false
            }
        )
    }

    Column(modifier = Modifier.fillMaxSize()) {
        HomeTopBar(profile = profile, onAvatar = onOpenSidebar, onLocation = onSetLocation)
        DateBar(text = formatDateChip(session.dates), onClick = { showDatePicker = true })

        if (lat == null || lng == null) {
            EmptyView(
                title = "Set your location",
                description = "Dzirent shows clothes from closets near you. Set your approximate location to start browsing.",
                icon = Icons.Default.LocationOff,
                onAction = onSetLocation,
                actionLabel = "Set my location"
            )
            return@Column
        }

        when (val state = session.state) {
            is UiState.Loading -> LoadingView(message = "Finding pieces near you...")
            is UiState.Error -> ErrorView(message = state.message, onRetry = { scope.launch { session.load(lat, lng, force = true) } })
            is UiState.Success -> {
                val sections = remember(state, session.filters, session.dates) { session.sections() } ?: return@Column
                PullToRefreshBox(
                    isRefreshing = refreshing,
                    onRefresh = {
                        scope.launch {
                            refreshing = true
                            session.load(lat, lng, force = true)
                            refreshing = false
                        }
                    },
                    modifier = Modifier.fillMaxSize()
                ) {
                    HomeContent(
                        sections = sections,
                        hasMeasurements = sections.likelyFit != null,
                        studiosTab = studiosTab,
                        onTabChange = { studiosTab = it },
                        filters = session.filters,
                        dateText = formatDateChip(session.dates),
                        onOpenListing = onOpenListing,
                        onOpenStudio = onOpenStudio,
                        onSeeAll = onSeeAll,
                        onOpenMeasurements = onOpenMeasurements,
                        onClearFilters = { session.filters = BrowseFilters(radiusKm = session.filters.radiusKm) },
                        onWiden = { session.filters = session.filters.copy(radiusKm = DISTANCE_OPTIONS_KM.last()) },
                        onChangeDates = { showDatePicker = true }
                    )
                }
            }
        }
    }
}

@Composable
private fun HomeTopBar(profile: Profile, onAvatar: () -> Unit, onLocation: () -> Unit) {
    Box(
        modifier = Modifier
            .fillMaxWidth()
            .height(56.dp)
            .padding(horizontal = 8.dp)
    ) {
        IconButton(
            onClick = onAvatar,
            modifier = Modifier
                .align(Alignment.CenterStart)
                .guideTarget(GuideTarget.AVATAR)
        ) {
            Avatar(name = profile.displayName, size = 32.dp)
        }
        // Placeholder logo until the brand rework.
        Row(
            verticalAlignment = Alignment.CenterVertically,
            modifier = Modifier.align(Alignment.Center)
        ) {
            Icon(
                imageVector = Icons.Default.LocalFlorist,
                contentDescription = null,
                modifier = Modifier.size(16.dp)
            )
            Spacer(modifier = Modifier.width(4.dp))
            Text(
                text = "DZIRENT",
                style = MaterialTheme.typography.titleMedium,
                fontWeight = FontWeight.Bold,
                letterSpacing = 3.sp
            )
        }
        Row(
            verticalAlignment = Alignment.CenterVertically,
            modifier = Modifier
                .align(Alignment.CenterEnd)
                .clip(RoundedCornerShape(8.dp))
                .clickable(onClick = onLocation)
                .padding(horizontal = 4.dp, vertical = 8.dp)
                .widthIn(max = 120.dp)
        ) {
            Icon(Icons.Default.LocationOn, contentDescription = "Location")
            Text(
                text = profile.city.ifBlank { "Set location" },
                style = MaterialTheme.typography.labelSmall,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis
            )
        }
    }
}

/** The one slim bar under the top bar: only the date chip. */
@Composable
private fun DateBar(text: String, onClick: () -> Unit) {
    Surface(color = MaterialTheme.colorScheme.background) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 16.dp, vertical = 4.dp)
        ) {
            Surface(
                onClick = onClick,
                shape = RoundedCornerShape(50),
                color = MaterialTheme.colorScheme.surfaceVariant,
                modifier = Modifier.guideTarget(GuideTarget.DATE_CHIP)
            ) {
                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    modifier = Modifier.padding(horizontal = 12.dp, vertical = 6.dp)
                ) {
                    Icon(Icons.Default.CalendarToday, contentDescription = null, modifier = Modifier.size(16.dp))
                    Spacer(modifier = Modifier.width(6.dp))
                    Text(text = text, style = MaterialTheme.typography.labelLarge)
                }
            }
        }
    }
    HorizontalDivider(thickness = 0.5.dp)
}

@Composable
private fun HomeContent(
    sections: HomeSections,
    hasMeasurements: Boolean,
    studiosTab: Boolean,
    onTabChange: (Boolean) -> Unit,
    filters: BrowseFilters,
    dateText: String,
    onOpenListing: (String) -> Unit,
    onOpenStudio: ((String) -> Unit)?,
    onSeeAll: (HomeRow) -> Unit,
    onOpenMeasurements: () -> Unit,
    onClearFilters: () -> Unit,
    onWiden: () -> Unit,
    onChangeDates: () -> Unit
) {
    LazyVerticalGrid(
        columns = GridCells.Fixed(2),
        contentPadding = PaddingValues(16.dp),
        horizontalArrangement = Arrangement.spacedBy(12.dp),
        verticalArrangement = Arrangement.spacedBy(16.dp),
        modifier = Modifier.fillMaxSize()
    ) {
        if (!hasMeasurements) {
            fullWidth("measure-card") { MeasurementsPromptCard(onClick = onOpenMeasurements) }
        }
        HomeRow.entries.forEach { row ->
            val listings = row.listings(sections)
            if (listings.size >= HOME_ROW_MIN) {
                fullWidth("row-${row.name}") {
                    HomeRowSection(
                        row = row,
                        listings = listings,
                        onSeeAll = { onSeeAll(row) },
                        onOpenListing = onOpenListing
                    )
                }
            }
        }
        fullWidth("tabs") {
            DzirentTabRow(selectedTabIndex = if (studiosTab) 1 else 0) {
                Tab(selected = !studiosTab, onClick = { onTabChange(false) }, text = { Text("Products") })
                Tab(selected = studiosTab, onClick = { onTabChange(true) }, text = { Text("Studios") })
            }
        }
        if (sections.visible.isEmpty()) {
            fullWidth("empty") {
                val filtered = filters != BrowseFilters(radiusKm = filters.radiusKm)
                val widest = DISTANCE_OPTIONS_KM.last()
                NothingHere(
                    text = "Nothing near you is available $dateText within ${filters.radiusKm} km.",
                    actionLabel = when {
                        filtered -> "Clear filters"
                        filters.radiusKm < widest -> "Show $widest km"
                        else -> "Change dates"
                    },
                    onAction = when {
                        filtered -> onClearFilters
                        filters.radiusKm < widest -> onWiden
                        else -> onChangeDates
                    }
                )
            }
        } else if (studiosTab) {
            items(sections.studios, key = { "studio-${it.lender.id}" }) { studio ->
                StudioCard(studio = studio, onClick = onOpenStudio?.let { open -> { open(studio.lender.id) } })
            }
        } else {
            items(sections.visible, key = { "piece-${it.id}" }) { listing ->
                ListingCard(listing = listing, onClick = { onOpenListing(listing.id) })
            }
        }
    }
}

private fun LazyGridScope.fullWidth(key: String, content: @Composable () -> Unit) {
    item(key = key, span = { GridItemSpan(maxLineSpan) }) { content() }
}

@Composable
private fun MeasurementsPromptCard(onClick: () -> Unit) {
    Surface(
        onClick = onClick,
        shape = RoundedCornerShape(12.dp),
        color = MaterialTheme.colorScheme.surfaceVariant,
        modifier = Modifier.fillMaxWidth()
    ) {
        Row(
            verticalAlignment = Alignment.CenterVertically,
            modifier = Modifier.padding(horizontal = 16.dp, vertical = 14.dp)
        ) {
            Text(
                text = "Add your measurements to see what fits you",
                style = MaterialTheme.typography.bodyMedium,
                modifier = Modifier.weight(1f)
            )
            Icon(Icons.Default.ChevronRight, contentDescription = null)
        }
    }
}

@Composable
private fun HomeRowSection(
    row: HomeRow,
    listings: List<Listing>,
    onSeeAll: () -> Unit,
    onOpenListing: (String) -> Unit
) {
    Column {
        Row(verticalAlignment = Alignment.CenterVertically) {
            Column(modifier = Modifier.weight(1f)) {
                Text(
                    text = row.title,
                    style = MaterialTheme.typography.titleMedium,
                    fontWeight = FontWeight.Bold,
                    // The fit row is the one row highlighted in the accent color.
                    color = if (row == HomeRow.FIT) DzirentAccent else MaterialTheme.colorScheme.onSurface
                )
                row.subtitle?.let {
                    Text(it, style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                }
            }
            TextButton(onClick = onSeeAll) { Text("See all") }
        }
        Spacer(modifier = Modifier.height(8.dp))
        // Swipe only: no auto-scroll.
        LazyRow(horizontalArrangement = Arrangement.spacedBy(12.dp)) {
            items(listings, key = { it.id }) { listing ->
                ListingCard(listing = listing, onClick = { onOpenListing(listing.id) }, modifier = Modifier.width(140.dp))
            }
        }
    }
}

@Composable
fun StudioCard(studio: StudioSummary, onClick: (() -> Unit)?) {
    Column(
        horizontalAlignment = Alignment.CenterHorizontally,
        modifier = Modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(12.dp))
            .then(if (onClick != null) Modifier.clickable(onClick = onClick) else Modifier)
            .padding(vertical = 12.dp)
    ) {
        Avatar(name = studio.lender.displayName, size = 64.dp)
        Spacer(modifier = Modifier.height(8.dp))
        Text(
            text = studio.lender.displayName.ifBlank { "Studio" },
            style = MaterialTheme.typography.titleSmall,
            maxLines = 1,
            overflow = TextOverflow.Ellipsis
        )
        Text(
            text = listOfNotNull(
                "${studio.pieceCount} ${if (studio.pieceCount == 1) "piece" else "pieces"}",
                studio.distanceKm?.let { formatDistance(it) }
            ).joinToString(" · "),
            style = MaterialTheme.typography.bodySmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant
        )
    }
}

/** Friendly empty state with one action, sized for inside a list. */
@Composable
fun NothingHere(text: String, actionLabel: String, onAction: () -> Unit) {
    Column(
        horizontalAlignment = Alignment.CenterHorizontally,
        modifier = Modifier
            .fillMaxWidth()
            .padding(vertical = 32.dp, horizontal = 8.dp)
    ) {
        Text(text = text, style = MaterialTheme.typography.bodyMedium, color = MaterialTheme.colorScheme.onSurfaceVariant)
        Spacer(modifier = Modifier.height(12.dp))
        OutlinedButton(onClick = onAction) { Text(actionLabel) }
    }
}

/** "See all" for one Home row: the same listings as a grid. */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun SeeAllScreen(
    session: BrowseSession,
    row: HomeRow,
    onBack: () -> Unit,
    onOpenListing: (String) -> Unit
) {
    val listings = session.sections()?.let { row.listings(it) }.orEmpty()
    Column(modifier = Modifier.fillMaxSize()) {
        TopAppBar(
            title = { Text(row.title) },
            navigationIcon = {
                IconButton(onClick = onBack) {
                    Icon(Icons.AutoMirrored.Filled.ArrowBack, contentDescription = "Back")
                }
            }
        )
        if (listings.isEmpty()) {
            EmptyView(
                title = "Nothing here right now",
                description = "Try other dates or a wider distance on Home.",
                icon = Icons.Default.SearchOff,
                onAction = onBack,
                actionLabel = "Back to Home"
            )
        } else {
            LazyVerticalGrid(
                columns = GridCells.Fixed(2),
                contentPadding = PaddingValues(16.dp),
                horizontalArrangement = Arrangement.spacedBy(12.dp),
                verticalArrangement = Arrangement.spacedBy(16.dp),
                modifier = Modifier.fillMaxSize()
            ) {
                items(listings, key = { it.id }) { listing ->
                    ListingCard(listing = listing, onClick = { onOpenListing(listing.id) })
                }
            }
        }
    }
}
