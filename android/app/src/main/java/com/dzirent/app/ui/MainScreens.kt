package com.dzirent.app.ui

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.grid.GridCells
import androidx.compose.foundation.lazy.grid.LazyVerticalGrid
import androidx.compose.foundation.lazy.grid.items
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.pager.HorizontalPager
import androidx.compose.foundation.pager.rememberPagerState
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.automirrored.filled.ExitToApp
import androidx.compose.material.icons.automirrored.filled.Send
import androidx.compose.material.icons.filled.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import coil.compose.AsyncImage
import com.dzirent.app.data.model.*
import com.dzirent.app.data.repository.DzirentRepository
import com.dzirent.app.util.DEFAULT_DISTANCE_KM
import com.dzirent.app.util.DISTANCE_OPTIONS_KM
import com.dzirent.app.util.formatDateRange
import com.dzirent.app.util.formatDistance
import com.dzirent.app.util.parseDateRange
import kotlinx.coroutines.launch
import java.time.LocalDate

sealed class UiState<out T> {
    object Loading : UiState<Nothing>()
    data class Error(val message: String) : UiState<Nothing>()
    data class Success<T>(val data: T) : UiState<T>()
}

// --- BROWSE SCREEN ---
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun BrowseScreen(
    repository: DzirentRepository,
    profile: Profile,
    onSetLocation: () -> Unit,
    onListingClick: (String) -> Unit
) {
    var searchQuery by rememberSaveable { mutableStateOf("") }
    var selectedCategory by rememberSaveable { mutableStateOf<String?>(null) }
    var radiusKm by rememberSaveable { mutableIntStateOf(DEFAULT_DISTANCE_KM) }
    var uiState by remember { mutableStateOf<UiState<List<Listing>>>(UiState.Loading) }
    val scope = rememberCoroutineScope()
    val lat = profile.approxLat
    val lng = profile.approxLng

    fun loadListings() {
        if (lat == null || lng == null) return
        scope.launch {
            uiState = UiState.Loading
            try {
                val listings = repository.getNearbyListings(
                    lat = lat,
                    lng = lng,
                    radiusKm = radiusKm.toDouble(),
                    category = selectedCategory
                )
                uiState = UiState.Success(listings)
            } catch (e: Exception) {
                uiState = UiState.Error(e.userMessage("Failed to load listings."))
            }
        }
    }

    LaunchedEffect(selectedCategory, radiusKm, lat, lng) {
        loadListings()
    }

    if (lat == null || lng == null) {
        EmptyView(
            title = "Set your location",
            description = "Dzirent shows clothes from closets near you. Set your approximate location to start browsing.",
            icon = Icons.Default.LocationOff,
            onAction = onSetLocation,
            actionLabel = "Set my location"
        )
        return
    }

    Column(modifier = Modifier.fillMaxSize()) {
        OutlinedTextField(
            value = searchQuery,
            onValueChange = { searchQuery = it },
            label = { Text("Search title or description") },
            leadingIcon = { Icon(Icons.Default.Search, contentDescription = null) },
            trailingIcon = {
                if (searchQuery.isNotEmpty()) {
                    IconButton(onClick = { searchQuery = "" }) {
                        Icon(Icons.Default.Clear, contentDescription = "Clear")
                    }
                }
            },
            singleLine = true,
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 16.dp, vertical = 8.dp)
        )

        // Both chip rows scroll sideways; the end padding lets the last chip scroll fully into view.
        LazyRow(
            contentPadding = PaddingValues(horizontal = 16.dp, vertical = 4.dp),
            horizontalArrangement = Arrangement.spacedBy(8.dp)
        ) {
            item {
                FilterChip(
                    selected = selectedCategory == null,
                    onClick = { selectedCategory = null },
                    label = { Text("All", maxLines = 1) }
                )
            }
            items(Categories.ALL) { category ->
                FilterChip(
                    selected = selectedCategory == category,
                    onClick = {
                        selectedCategory = if (selectedCategory == category) null else category
                    },
                    label = { Text(category, maxLines = 1) }
                )
            }
        }

        // The city gets its own line so a long name can't push the distance chips off-screen.
        Row(
            verticalAlignment = Alignment.CenterVertically,
            modifier = Modifier.padding(start = 16.dp, end = 16.dp, top = 4.dp)
        ) {
            Icon(
                imageVector = Icons.Default.NearMe,
                contentDescription = null,
                tint = MaterialTheme.colorScheme.onSurfaceVariant,
                modifier = Modifier.size(16.dp)
            )
            Spacer(modifier = Modifier.width(6.dp))
            Text(
                text = "Distance from ${profile.city.ifBlank { "you" }}",
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis
            )
        }
        LazyRow(
            contentPadding = PaddingValues(horizontal = 16.dp, vertical = 4.dp),
            horizontalArrangement = Arrangement.spacedBy(8.dp)
        ) {
            items(DISTANCE_OPTIONS_KM) { km ->
                FilterChip(
                    selected = radiusKm == km,
                    onClick = { radiusKm = km },
                    label = { Text("$km km", maxLines = 1) }
                )
            }
        }

        when (val state = uiState) {
            is UiState.Loading -> LoadingView(message = "Searching nearby listings...")
            is UiState.Error -> ErrorView(message = state.message, onRetry = { loadListings() })
            is UiState.Success -> {
                val filtered = state.data.filter {
                    if (searchQuery.isBlank()) true
                    else it.title.contains(searchQuery, ignoreCase = true) ||
                            it.description.contains(searchQuery, ignoreCase = true)
                }

                if (filtered.isEmpty()) {
                    val widest = DISTANCE_OPTIONS_KM.last()
                    val filtersOn = searchQuery.isNotBlank() || selectedCategory != null
                    EmptyView(
                        title = "No Listings Found",
                        description = when {
                            filtersOn -> "No clothes match your filters within $radiusKm km. Try clearing search, changing category or a larger distance."
                            radiusKm < widest -> "Nothing listed within $radiusKm km yet. Try a larger distance."
                            else -> "No nearby clothes have been posted yet. Check back soon or list your own!"
                        },
                        icon = Icons.Default.SearchOff,
                        onAction = if (filtersOn || radiusKm < widest) {
                            {
                                searchQuery = ""
                                selectedCategory = null
                                radiusKm = widest
                            }
                        } else {
                            { loadListings() }
                        },
                        actionLabel = if (filtersOn || radiusKm < widest) "Clear filters, show $widest km" else "Refresh"
                    )
                } else {
                    LazyVerticalGrid(
                        columns = GridCells.Fixed(2),
                        contentPadding = PaddingValues(12.dp),
                        horizontalArrangement = Arrangement.spacedBy(12.dp),
                        verticalArrangement = Arrangement.spacedBy(12.dp),
                        modifier = Modifier.fillMaxSize()
                    ) {
                        items(filtered, key = { it.id }) { listing ->
                            ListingCard(listing = listing, onClick = { onListingClick(listing.id) })
                        }
                    }
                }
            }
        }
    }
}

@Composable
fun ListingCard(listing: Listing, onClick: () -> Unit) {
    Card(
        modifier = Modifier
            .fillMaxWidth()
            .clickable { onClick() },
        elevation = CardDefaults.cardElevation(defaultElevation = 2.dp)
    ) {
        Column {
            val photoUrl = listing.photos.firstOrNull()?.storagePath
            if (!photoUrl.isNullOrBlank()) {
                AsyncImage(
                    model = photoUrl,
                    contentDescription = listing.title,
                    contentScale = ContentScale.Crop,
                    modifier = Modifier
                        .fillMaxWidth()
                        .height(140.dp)
                )
            } else {
                NoPhotoPlaceholder(
                    modifier = Modifier
                        .fillMaxWidth()
                        .height(140.dp)
                )
            }

            Column(modifier = Modifier.padding(12.dp)) {
                Text(
                    text = listing.title,
                    style = MaterialTheme.typography.titleMedium,
                    fontWeight = FontWeight.Bold,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis
                )
                Text(
                    text = "${listing.typeLabel} • ${listing.sizeLabel}",
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
                Spacer(modifier = Modifier.height(4.dp))
                Text(
                    text = listing.priceNote,
                    style = MaterialTheme.typography.labelLarge,
                    color = MaterialTheme.colorScheme.primary,
                    fontWeight = FontWeight.SemiBold
                )
                Text(
                    text = listOfNotNull(
                        listing.distanceKm?.let { formatDistance(it) },
                        listing.city.ifBlank { null }
                    ).joinToString(" · "),
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.outline
                )
            }
        }
    }
}

// --- LISTING DETAIL SCREEN ---
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun ListingDetailScreen(
    repository: DzirentRepository,
    listingId: String,
    currentUserId: String,
    onBack: () -> Unit,
    onEdit: (listingId: String) -> Unit,
    onMessageLender: (listingId: String, lenderId: String) -> Unit
) {
    var uiState by remember { mutableStateOf<UiState<Listing>>(UiState.Loading) }
    var menuOpen by remember { mutableStateOf(false) }
    var reportTarget by remember { mutableStateOf<ReportTarget?>(null) }
    val scope = rememberCoroutineScope()

    fun loadDetail() {
        scope.launch {
            uiState = UiState.Loading
            try {
                val detail = repository.getListingDetail(listingId)
                uiState = UiState.Success(detail)
            } catch (e: Exception) {
                uiState = UiState.Error(e.userMessage("Failed to load listing details."))
            }
        }
    }

    LaunchedEffect(listingId) {
        loadDetail()
    }

    Column(modifier = Modifier.fillMaxSize()) {
        TopAppBar(
            title = { Text("Listing Details") },
            navigationIcon = {
                IconButton(onClick = onBack) {
                    Icon(Icons.AutoMirrored.Filled.ArrowBack, contentDescription = "Back")
                }
            },
            actions = {
                // Editing is for the owner only (RLS enforces this too); others can report.
                val loaded = (uiState as? UiState.Success)?.data
                if (loaded != null && loaded.lenderId == currentUserId) {
                    IconButton(onClick = { onEdit(loaded.id) }) {
                        Icon(Icons.Default.Edit, contentDescription = "Edit listing")
                    }
                } else if (loaded != null) {
                    Box {
                        IconButton(onClick = { menuOpen = true }) {
                            Icon(Icons.Default.MoreVert, contentDescription = "More options")
                        }
                        DropdownMenu(expanded = menuOpen, onDismissRequest = { menuOpen = false }) {
                            DropdownMenuItem(
                                text = { Text("Report listing") },
                                leadingIcon = { Icon(Icons.Default.Flag, contentDescription = null) },
                                onClick = {
                                    menuOpen = false
                                    reportTarget = ReportTarget("listing", loaded.id, "Report listing")
                                }
                            )
                        }
                    }
                }
            }
        )

        reportTarget?.let { target ->
            ReportDialog(
                repository = repository,
                reporterId = currentUserId,
                target = target,
                onDismiss = { reportTarget = null }
            )
        }

        when (val state = uiState) {
            is UiState.Loading -> LoadingView(message = "Loading listing details...")
            is UiState.Error -> ErrorView(message = state.message, onRetry = { loadDetail() })
            is UiState.Success -> {
                val listing = state.data
                Column(
                    modifier = Modifier
                        .fillMaxSize()
                        .verticalScroll(rememberScrollState())
                        .padding(16.dp)
                ) {
                    PhotoCarousel(photos = listing.photos, title = listing.title)

                    Spacer(modifier = Modifier.height(16.dp))
                    Text(
                        text = listing.title,
                        style = MaterialTheme.typography.headlineSmall,
                        fontWeight = FontWeight.Bold
                    )
                    Spacer(modifier = Modifier.height(4.dp))
                    Text(
                        text = listOfNotNull(
                            listOfNotNull(listing.category, listing.itemType).joinToString(" · "),
                            listing.sizeLabel.ifBlank { null }?.let { "Size $it" }
                        ).joinToString(" | "),
                        style = MaterialTheme.typography.titleMedium,
                        color = MaterialTheme.colorScheme.secondary
                    )
                    Spacer(modifier = Modifier.height(8.dp))
                    Text(
                        text = listing.priceNote,
                        style = MaterialTheme.typography.titleLarge,
                        color = MaterialTheme.colorScheme.primary,
                        fontWeight = FontWeight.Bold
                    )
                    Spacer(modifier = Modifier.height(8.dp))
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Icon(
                            imageVector = Icons.Default.Person,
                            contentDescription = null,
                            tint = MaterialTheme.colorScheme.outline,
                            modifier = Modifier.size(18.dp)
                        )
                        Spacer(modifier = Modifier.width(6.dp))
                        Text(
                            text = listOfNotNull(
                                listing.lender?.displayName?.ifBlank { null } ?: "Lender",
                                (listing.lender?.city?.ifBlank { null } ?: listing.city.ifBlank { null })
                            ).joinToString(" · "),
                            style = MaterialTheme.typography.bodyMedium,
                            color = MaterialTheme.colorScheme.outline
                        )
                    }

                    DetailSection("Description") {
                        Text(
                            text = listing.description.ifBlank { "No description provided." },
                            style = MaterialTheme.typography.bodyMedium
                        )
                    }

                    if (listing.measurements.isNotEmpty()) {
                        DetailSection("Measurements") {
                            MeasurementsTable(listing.measurements)
                        }
                    }

                    DetailSection("Available dates") {
                        val today = remember { LocalDate.now() }
                        val upcoming = listing.availability
                            .mapNotNull { parseDateRange(it.startDate, it.endDate) }
                            .filter { !it.end.isBefore(today) }
                        if (upcoming.isEmpty()) {
                            Text(
                                text = "No dates listed. Ask the lender in chat.",
                                style = MaterialTheme.typography.bodyMedium,
                                color = MaterialTheme.colorScheme.onSurfaceVariant
                            )
                        } else {
                            upcoming.forEach { range ->
                                Row(
                                    verticalAlignment = Alignment.CenterVertically,
                                    modifier = Modifier.padding(vertical = 2.dp)
                                ) {
                                    Icon(
                                        imageVector = Icons.Default.DateRange,
                                        contentDescription = null,
                                        tint = MaterialTheme.colorScheme.primary,
                                        modifier = Modifier.size(18.dp)
                                    )
                                    Spacer(modifier = Modifier.width(8.dp))
                                    Text(text = range.format(), style = MaterialTheme.typography.bodyMedium)
                                }
                            }
                        }
                    }

                    val depositNote = listing.depositNote
                    if (!depositNote.isNullOrBlank()) {
                        DetailSection("Deposit") {
                            Text(text = depositNote, style = MaterialTheme.typography.bodyMedium)
                        }
                    }

                    if (listing.lenderId != currentUserId) {
                        Spacer(modifier = Modifier.height(24.dp))
                        PolicyNotice(text = DzirentPolicy.LISTING_NOTICE)
                        Spacer(modifier = Modifier.height(12.dp))
                        Button(
                            onClick = { onMessageLender(listing.id, listing.lenderId) },
                            modifier = Modifier.fillMaxWidth().height(50.dp)
                        ) {
                            Icon(Icons.AutoMirrored.Filled.Send, contentDescription = null)
                            Spacer(modifier = Modifier.width(8.dp))
                            Text("Message Lender")
                        }
                    }
                    Spacer(modifier = Modifier.height(16.dp))
                }
            }
        }
    }
}

@Composable
private fun DetailSection(title: String, content: @Composable ColumnScope.() -> Unit) {
    HorizontalDivider(modifier = Modifier.padding(vertical = 16.dp))
    Text(text = title, style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.Bold)
    Spacer(modifier = Modifier.height(8.dp))
    Column(content = content)
}

/** Swipeable photos with page dots, or the placeholder when there are none. */
@Composable
private fun PhotoCarousel(photos: List<ListingPhoto>, title: String) {
    val shape = RoundedCornerShape(12.dp)
    if (photos.isEmpty()) {
        NoPhotoPlaceholder(
            modifier = Modifier
                .fillMaxWidth()
                .height(260.dp)
                .clip(shape)
        )
        return
    }
    val pagerState = rememberPagerState { photos.size }
    HorizontalPager(
        state = pagerState,
        modifier = Modifier
            .fillMaxWidth()
            .height(320.dp)
            .clip(shape)
    ) { page ->
        AsyncImage(
            model = photos[page].storagePath,
            contentDescription = "$title, photo ${page + 1} of ${photos.size}",
            contentScale = ContentScale.Crop,
            modifier = Modifier.fillMaxSize()
        )
    }
    if (photos.size > 1) {
        Row(
            horizontalArrangement = Arrangement.Center,
            modifier = Modifier
                .fillMaxWidth()
                .padding(top = 8.dp)
        ) {
            repeat(photos.size) { index ->
                val selected = pagerState.currentPage == index
                Box(
                    modifier = Modifier
                        .padding(horizontal = 3.dp)
                        .size(if (selected) 8.dp else 6.dp)
                        .clip(CircleShape)
                        .background(
                            if (selected) MaterialTheme.colorScheme.primary
                            else MaterialTheme.colorScheme.outlineVariant
                        )
                )
            }
        }
    }
}

@Composable
private fun MeasurementsTable(measurements: List<ListingMeasurement>) {
    Column {
        measurements.forEachIndexed { index, m ->
            if (index > 0) HorizontalDivider(color = MaterialTheme.colorScheme.outlineVariant)
            Row(modifier = Modifier.padding(vertical = 8.dp)) {
                Text(
                    text = m.label,
                    style = MaterialTheme.typography.bodyMedium,
                    modifier = Modifier.weight(1f)
                )
                Text(
                    text = "${formatMeasurementValue(m.value)} ${m.unit}",
                    style = MaterialTheme.typography.bodyMedium,
                    fontWeight = FontWeight.SemiBold
                )
            }
        }
    }
}

private fun formatMeasurementValue(value: Double): String =
    if (value % 1.0 == 0.0) value.toLong().toString() else value.toString()

// --- MY CLOSET SCREEN ---

/**
 * Lenders see their listings. "Both" users also get a "My Rentals" tab, since they have
 * no separate My Rentals entry in the bottom bar.
 */
@Composable
fun MyClosetScreen(
    repository: DzirentRepository,
    currentUserId: String,
    showRentalsTab: Boolean,
    onOpenListing: (listingId: String) -> Unit,
    onEditListing: (listingId: String) -> Unit,
    onOpenChat: (conversationId: String) -> Unit
) {
    var selectedTab by rememberSaveable { mutableIntStateOf(0) }
    Column(modifier = Modifier.fillMaxSize()) {
        // The bottom bar says "Closet"; the screen keeps the full name.
        ScreenTitle("My Closet")
        if (!showRentalsTab) {
            MyListingsContent(repository, currentUserId, onOpenListing, onEditListing)
        } else {
            TabRow(selectedTabIndex = selectedTab) {
                Tab(selected = selectedTab == 0, onClick = { selectedTab = 0 }, text = { Text("My Listings") })
                Tab(selected = selectedTab == 1, onClick = { selectedTab = 1 }, text = { Text("My Rentals") })
            }
            when (selectedTab) {
                0 -> MyListingsContent(repository, currentUserId, onOpenListing, onEditListing)
                else -> MyRentalsScreen(
                    repository = repository,
                    currentUserId = currentUserId,
                    showTitle = false,
                    onOpenChat = onOpenChat
                )
            }
        }
    }
}

/** Heading for bottom-bar screens, which have no top app bar. */
@Composable
private fun ScreenTitle(text: String) {
    Text(
        text = text,
        style = MaterialTheme.typography.headlineSmall,
        fontWeight = FontWeight.Bold,
        modifier = Modifier
            .fillMaxWidth()
            .padding(start = 16.dp, end = 16.dp, top = 16.dp, bottom = 8.dp)
    )
}

private data class ClosetData(val listings: List<Listing>, val rentedOut: Set<String>)

@Composable
private fun MyListingsContent(
    repository: DzirentRepository,
    currentUserId: String,
    onOpenListing: (listingId: String) -> Unit,
    onEditListing: (listingId: String) -> Unit
) {
    var uiState by remember { mutableStateOf<UiState<ClosetData>>(UiState.Loading) }
    var busyIds by remember { mutableStateOf(emptySet<String>()) }
    var actionError by remember { mutableStateOf<String?>(null) }
    var pendingDelete by remember { mutableStateOf<Listing?>(null) }
    val scope = rememberCoroutineScope()

    fun loadMyListings() {
        scope.launch {
            uiState = UiState.Loading
            try {
                val listings = repository.getMyListings(currentUserId)
                val rentedOut = repository.getRentedOutListingIds(currentUserId)
                uiState = UiState.Success(ClosetData(listings, rentedOut))
            } catch (e: Exception) {
                uiState = UiState.Error(e.userMessage("Failed to load your closet."))
            }
        }
    }

    fun updateListing(id: String, transform: (Listing) -> Listing?) {
        val data = (uiState as? UiState.Success)?.data ?: return
        uiState = UiState.Success(data.copy(listings = data.listings.mapNotNull { if (it.id == id) transform(it) else it }))
    }

    fun setActive(listing: Listing, active: Boolean) {
        if (active && listing.photos.isEmpty()) {
            actionError = "Add at least one photo (Edit) before turning \"${listing.title}\" on."
            return
        }
        scope.launch {
            busyIds = busyIds + listing.id
            actionError = null
            try {
                val status = if (active) "active" else "paused"
                repository.toggleListingStatus(listing.id, status)
                updateListing(listing.id) { it.copy(status = status) }
            } catch (e: Exception) {
                actionError = e.userMessage("Couldn't update your listing. Please try again.")
            }
            busyIds = busyIds - listing.id
        }
    }

    fun delete(listing: Listing) {
        scope.launch {
            busyIds = busyIds + listing.id
            actionError = null
            try {
                repository.deleteListing(listing.id)
                updateListing(listing.id) { null }
            } catch (e: Exception) {
                actionError = e.userMessage("Couldn't delete your listing. Please try again.")
            }
            busyIds = busyIds - listing.id
        }
    }

    LaunchedEffect(currentUserId) {
        loadMyListings()
    }

    pendingDelete?.let { listing ->
        AlertDialog(
            onDismissRequest = { pendingDelete = null },
            icon = { Icon(Icons.Default.Delete, contentDescription = null) },
            title = { Text("Delete this listing?") },
            text = { Text("\"${listing.title}\" and its photos will be removed for good. Chats about it will be deleted too.") },
            confirmButton = {
                TextButton(onClick = {
                    pendingDelete = null
                    delete(listing)
                }) { Text("Delete", color = MaterialTheme.colorScheme.error) }
            },
            dismissButton = {
                TextButton(onClick = { pendingDelete = null }) { Text("Cancel") }
            }
        )
    }

    when (val state = uiState) {
        is UiState.Loading -> LoadingView(message = "Loading your closet...")
        is UiState.Error -> ErrorView(message = state.message, onRetry = { loadMyListings() })
        is UiState.Success -> {
            if (state.data.listings.isEmpty()) {
                EmptyView(
                    title = "Your Closet is Empty",
                    description = "You haven't listed any clothes yet. Tap 'List' to share items from your wardrobe!",
                    icon = Icons.Default.Checkroom
                )
            } else {
                LazyColumn(
                    contentPadding = PaddingValues(16.dp),
                    verticalArrangement = Arrangement.spacedBy(12.dp),
                    modifier = Modifier.fillMaxSize()
                ) {
                    actionError?.let { message ->
                        item {
                            Text(
                                text = message,
                                color = MaterialTheme.colorScheme.error,
                                style = MaterialTheme.typography.bodySmall
                            )
                        }
                    }
                    items(state.data.listings, key = { it.id }) { listing ->
                        ClosetListingCard(
                            listing = listing,
                            rentedOut = listing.id in state.data.rentedOut,
                            busy = listing.id in busyIds,
                            onOpen = { onOpenListing(listing.id) },
                            onActiveChange = { setActive(listing, it) },
                            onEdit = { onEditListing(listing.id) },
                            onDelete = { pendingDelete = listing }
                        )
                    }
                }
            }
        }
    }
}

@Composable
private fun ClosetListingCard(
    listing: Listing,
    rentedOut: Boolean,
    busy: Boolean,
    onOpen: () -> Unit,
    onActiveChange: (Boolean) -> Unit,
    onEdit: () -> Unit,
    onDelete: () -> Unit
) {
    val isActive = listing.status == "active"
    Card(
        modifier = Modifier
            .fillMaxWidth()
            .clickable { onOpen() },
        elevation = CardDefaults.cardElevation(defaultElevation = 2.dp)
    ) {
        Row(
            modifier = Modifier.padding(12.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            ListingThumbnail(listing = listing, size = 72.dp)
            Spacer(modifier = Modifier.width(12.dp))
            Column(modifier = Modifier.weight(1f)) {
                Text(
                    text = listing.title,
                    style = MaterialTheme.typography.titleMedium,
                    fontWeight = FontWeight.Bold,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis
                )
                Text(
                    text = "${listing.typeLabel} · ${listing.sizeLabel}",
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis
                )
                Spacer(modifier = Modifier.height(4.dp))
                Row(horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                    StatusBadge(
                        text = if (isActive) "Active" else "Paused",
                        highlighted = isActive
                    )
                    if (rentedOut) StatusBadge(text = "Rented out", highlighted = true, emphasis = true)
                }
            }
            Column(horizontalAlignment = Alignment.End) {
                Switch(
                    checked = isActive,
                    onCheckedChange = onActiveChange,
                    enabled = !busy
                )
                Row {
                    IconButton(onClick = onEdit, enabled = !busy) {
                        Icon(Icons.Default.Edit, contentDescription = "Edit")
                    }
                    IconButton(onClick = onDelete, enabled = !busy) {
                        Icon(Icons.Default.Delete, contentDescription = "Delete", tint = MaterialTheme.colorScheme.error)
                    }
                }
            }
        }
    }
}

@Composable
private fun StatusBadge(text: String, highlighted: Boolean, emphasis: Boolean = false) {
    Surface(
        color = when {
            emphasis -> MaterialTheme.colorScheme.primary
            highlighted -> MaterialTheme.colorScheme.primaryContainer
            else -> MaterialTheme.colorScheme.surfaceVariant
        },
        shape = RoundedCornerShape(50)
    ) {
        Text(
            text = text,
            style = MaterialTheme.typography.labelSmall,
            color = when {
                emphasis -> MaterialTheme.colorScheme.onPrimary
                highlighted -> MaterialTheme.colorScheme.onPrimaryContainer
                else -> MaterialTheme.colorScheme.onSurfaceVariant
            },
            modifier = Modifier.padding(horizontal = 8.dp, vertical = 2.dp)
        )
    }
}

// --- MY RENTALS SCREEN ---

/** [showTitle] is false inside My Closet, whose tab row already says "My Rentals". */
@Composable
fun MyRentalsScreen(
    repository: DzirentRepository,
    currentUserId: String,
    showTitle: Boolean = true,
    onOpenChat: (conversationId: String) -> Unit
) {
    var uiState by remember { mutableStateOf<UiState<List<Rental>>>(UiState.Loading) }
    val scope = rememberCoroutineScope()

    fun loadRentals() {
        scope.launch {
            uiState = UiState.Loading
            try {
                uiState = UiState.Success(repository.getMyRentals(currentUserId))
            } catch (e: Exception) {
                uiState = UiState.Error(e.userMessage("Couldn't load your rentals. Please try again."))
            }
        }
    }

    LaunchedEffect(currentUserId) {
        loadRentals()
    }

    Column(modifier = Modifier.fillMaxSize()) {
        if (showTitle) ScreenTitle("My Rentals")
        when (val state = uiState) {
            is UiState.Loading -> LoadingView(message = "Loading your rentals...")
            is UiState.Error -> ErrorView(message = state.message, onRetry = { loadRentals() })
            is UiState.Success -> {
                // Cancelled deals never happened, so they aren't rentals worth listing.
                val active = state.data.filter { it.status == "active" }
                val past = state.data.filter { it.status == "returned" }
                if (active.isEmpty() && past.isEmpty()) {
                    EmptyView(
                        title = "No active rentals yet",
                        description = "No active rentals yet. When you rent something, mark it as rented in the chat and it will show up here.",
                        icon = Icons.Default.ShoppingBag
                    )
                } else {
                    LazyColumn(
                        contentPadding = PaddingValues(16.dp),
                        verticalArrangement = Arrangement.spacedBy(8.dp),
                        modifier = Modifier.fillMaxSize()
                    ) {
                        if (active.isNotEmpty()) {
                            item { SectionHeader("Active") }
                            items(active, key = { it.conversationId }) { rental ->
                                RentalRow(rental = rental, onClick = { onOpenChat(rental.conversationId) })
                            }
                        }
                        if (past.isNotEmpty()) {
                            item { SectionHeader("Past") }
                            items(past, key = { it.conversationId }) { rental ->
                                RentalRow(rental = rental, onClick = { onOpenChat(rental.conversationId) })
                            }
                        }
                    }
                }
            }
        }
    }
}

@Composable
private fun SectionHeader(text: String) {
    Text(
        text = text,
        style = MaterialTheme.typography.titleSmall,
        fontWeight = FontWeight.Bold,
        color = MaterialTheme.colorScheme.primary,
        modifier = Modifier.padding(top = 8.dp, bottom = 4.dp)
    )
}

@Composable
private fun RentalRow(rental: Rental, onClick: () -> Unit) {
    Card(
        modifier = Modifier
            .fillMaxWidth()
            .clickable { onClick() },
        elevation = CardDefaults.cardElevation(defaultElevation = 1.dp)
    ) {
        Row(
            modifier = Modifier.padding(12.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            ListingThumbnail(listing = rental.listing, size = 56.dp)
            Spacer(modifier = Modifier.width(12.dp))
            Column(modifier = Modifier.weight(1f)) {
                Text(
                    text = rental.listing?.title ?: "Listing no longer available",
                    style = MaterialTheme.typography.titleSmall,
                    fontWeight = FontWeight.Bold,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis
                )
                Text(
                    text = "From ${rental.lender?.displayName?.ifBlank { null } ?: "a lender"}",
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
                Text(
                    text = formatDateRange(rental.startDate, rental.endDate) ?: "Dates not set",
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
            }
            AssistChip(
                onClick = onClick,
                label = { Text(if (rental.status == "active") "Active" else "Returned") }
            )
        }
    }
}

/** Square first-photo thumbnail, or the no-photo placeholder. */
@Composable
fun ListingThumbnail(listing: Listing?, size: androidx.compose.ui.unit.Dp) {
    val photoUrl = listing?.photos?.minByOrNull { it.position }?.storagePath
    val modifier = Modifier
        .size(size)
        .clip(RoundedCornerShape(8.dp))
    if (!photoUrl.isNullOrBlank()) {
        AsyncImage(
            model = photoUrl,
            contentDescription = listing.title,
            contentScale = ContentScale.Crop,
            modifier = modifier
        )
    } else {
        NoPhotoPlaceholder(modifier = modifier, compact = true)
    }
}

// --- PROFILE SCREEN ---
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun ProfileScreen(
    repository: DzirentRepository,
    profile: Profile,
    onProfileUpdated: (Profile) -> Unit,
    onSetLocation: () -> Unit,
    onOpenPolicy: () -> Unit,
    onOpenBlockedUsers: () -> Unit,
    onSignOut: () -> Unit
) {
    val scope = rememberCoroutineScope()
    var isRefreshing by remember { mutableStateOf(true) }
    var refreshError by remember { mutableStateOf<String?>(null) }
    var isSaving by remember { mutableStateOf(false) }
    var actionError by remember { mutableStateOf<String?>(null) }
    // Set when switching to Rent while listings are still active.
    var pendingRenterSwitch by remember { mutableStateOf<List<Listing>?>(null) }

    fun refresh() {
        scope.launch {
            isRefreshing = true
            refreshError = null
            try {
                repository.getCurrentProfile()?.let(onProfileUpdated)
            } catch (e: Exception) {
                refreshError = e.userMessage("Couldn't refresh your profile.")
            }
            isRefreshing = false
        }
    }

    fun saveRole(role: String, pauseFirst: List<Listing> = emptyList()) {
        scope.launch {
            isSaving = true
            actionError = null
            try {
                pauseFirst.forEach { repository.toggleListingStatus(it.id, "paused") }
                onProfileUpdated(repository.updateRole(profile.id, role))
            } catch (e: Exception) {
                actionError = e.userMessage("Couldn't save your choice. Please try again.")
            }
            isSaving = false
        }
    }

    fun onRoleSelected(role: String) {
        if (role == profile.role || isSaving) return
        if (role != Roles.RENTER) {
            saveRole(role)
            return
        }
        // Renters have no My Closet tab, so offer to pause active listings first.
        scope.launch {
            isSaving = true
            actionError = null
            try {
                val active = repository.getMyListings(profile.id).filter { it.status == "active" }
                isSaving = false
                if (active.isEmpty()) saveRole(role) else pendingRenterSwitch = active
            } catch (e: Exception) {
                isSaving = false
                actionError = e.userMessage("Couldn't save your choice. Please try again.")
            }
        }
    }

    LaunchedEffect(Unit) {
        refresh()
    }

    pendingRenterSwitch?.let { active ->
        AlertDialog(
            onDismissRequest = { pendingRenterSwitch = null },
            title = { Text("Switch to Rent only?") },
            text = {
                Text(
                    "You have ${active.size} active listing${if (active.size == 1) "" else "s"}. " +
                        "Renters don't have My Closet, so we'll pause them. Switch back to Lend or Both any time to turn them on again."
                )
            },
            confirmButton = {
                TextButton(onClick = {
                    pendingRenterSwitch = null
                    saveRole(Roles.RENTER, pauseFirst = active)
                }) { Text("Pause and switch") }
            },
            dismissButton = {
                TextButton(onClick = { pendingRenterSwitch = null }) { Text("Cancel") }
            }
        )
    }

    Column(
        modifier = Modifier
            .fillMaxSize()
            .verticalScroll(rememberScrollState())
            .padding(24.dp),
        horizontalAlignment = Alignment.CenterHorizontally
    ) {
        if (isRefreshing) {
            LinearProgressIndicator(modifier = Modifier.fillMaxWidth())
        }
        refreshError?.let { message ->
            PolicyNotice(text = message, modifier = Modifier.padding(bottom = 8.dp))
            TextButton(onClick = { refresh() }) { Text("Retry") }
        }
        Spacer(modifier = Modifier.height(8.dp))
        Icon(
            imageVector = Icons.Default.AccountCircle,
            contentDescription = null,
            modifier = Modifier.size(80.dp),
            tint = MaterialTheme.colorScheme.primary
        )
        Spacer(modifier = Modifier.height(16.dp))
        Text(
            text = profile.displayName.ifBlank { "Your profile" },
            style = MaterialTheme.typography.headlineSmall,
            fontWeight = FontWeight.Bold
        )
        Text(
            text = profile.city.ifBlank { "No city set" },
            style = MaterialTheme.typography.bodyMedium,
            color = MaterialTheme.colorScheme.outline
        )
        Text(
            text = "Role: ${roleLabel(profile.role)}",
            style = MaterialTheme.typography.bodyLarge,
            color = MaterialTheme.colorScheme.secondary
        )

        Spacer(modifier = Modifier.height(24.dp))
        Text(
            text = "I want to:",
            style = MaterialTheme.typography.titleSmall,
            modifier = Modifier.fillMaxWidth()
        )
        Spacer(modifier = Modifier.height(8.dp))
        val roleOptions = listOf(Roles.RENTER to "Rent", Roles.LENDER to "Lend", Roles.BOTH to "Both")
        SingleChoiceSegmentedButtonRow(modifier = Modifier.fillMaxWidth()) {
            roleOptions.forEachIndexed { index, (role, label) ->
                SegmentedButton(
                    selected = profile.role == role,
                    onClick = { onRoleSelected(role) },
                    enabled = !isSaving,
                    shape = SegmentedButtonDefaults.itemShape(index = index, count = roleOptions.size)
                ) {
                    Text(label)
                }
            }
        }
        if (isSaving) {
            Spacer(modifier = Modifier.height(8.dp))
            LinearProgressIndicator(modifier = Modifier.fillMaxWidth())
        }
        actionError?.let { message ->
            Spacer(modifier = Modifier.height(8.dp))
            Text(text = message, color = MaterialTheme.colorScheme.error, style = MaterialTheme.typography.bodySmall)
        }

        Spacer(modifier = Modifier.height(24.dp))
        HorizontalDivider()
        ListItem(
            headlineContent = { Text("Update my location") },
            supportingContent = {
                Text(if (profile.hasLocation) "Near ${profile.city.ifBlank { "your area" }} (about 1 km)" else "Not set yet")
            },
            leadingContent = { Icon(Icons.Default.LocationOn, contentDescription = null) },
            trailingContent = { Icon(Icons.Default.ChevronRight, contentDescription = null) },
            modifier = Modifier.clickable { onSetLocation() }
        )
        HorizontalDivider()
        ListItem(
            headlineContent = { Text(DzirentPolicy.TITLE) },
            leadingContent = { Icon(Icons.Default.Info, contentDescription = null) },
            trailingContent = { Icon(Icons.Default.ChevronRight, contentDescription = null) },
            modifier = Modifier.clickable { onOpenPolicy() }
        )
        HorizontalDivider()
        ListItem(
            headlineContent = { Text("Blocked users") },
            leadingContent = { Icon(Icons.Default.Block, contentDescription = null) },
            trailingContent = { Icon(Icons.Default.ChevronRight, contentDescription = null) },
            modifier = Modifier.clickable { onOpenBlockedUsers() }
        )
        HorizontalDivider()

        Spacer(modifier = Modifier.height(32.dp))
        Button(
            onClick = {
                scope.launch {
                    isSaving = true
                    actionError = null
                    try {
                        repository.signOut()
                        onSignOut()
                    } catch (e: Exception) {
                        isSaving = false
                        actionError = e.userMessage("Couldn't sign out. Please try again.")
                    }
                }
            },
            enabled = !isSaving,
            colors = ButtonDefaults.buttonColors(
                containerColor = MaterialTheme.colorScheme.errorContainer,
                contentColor = MaterialTheme.colorScheme.onErrorContainer
            ),
            modifier = Modifier.fillMaxWidth().height(50.dp)
        ) {
            Icon(Icons.AutoMirrored.Filled.ExitToApp, contentDescription = null)
            Spacer(modifier = Modifier.width(8.dp))
            Text("Sign Out")
        }
    }
}

private fun roleLabel(role: String) = when (role) {
    Roles.LENDER -> "Lender"
    Roles.BOTH -> "Renter and lender"
    else -> "Renter"
}
