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
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.automirrored.filled.ExitToApp
import androidx.compose.material.icons.automirrored.filled.Send
import androidx.compose.material.icons.filled.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import coil.compose.AsyncImage
import com.dzirent.app.data.model.*
import com.dzirent.app.data.repository.DzirentRepository
import kotlinx.coroutines.launch
import java.util.UUID

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
    onListingClick: (String) -> Unit
) {
    var searchQuery by remember { mutableStateOf("") }
    var selectedCategory by remember { mutableStateOf<String?>(null) }
    var uiState by remember { mutableStateOf<UiState<List<Listing>>>(UiState.Loading) }
    val scope = rememberCoroutineScope()

    fun loadListings() {
        scope.launch {
            uiState = UiState.Loading
            try {
                val listings = repository.getNearbyListings(
                    lat = 0.0,
                    lng = 0.0,
                    radiusKm = 25.0,
                    category = selectedCategory
                )
                uiState = UiState.Success(listings)
            } catch (e: Exception) {
                uiState = UiState.Error(e.localizedMessage ?: "Failed to load listings.")
            }
        }
    }

    LaunchedEffect(selectedCategory) {
        loadListings()
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

        LazyRow(
            contentPadding = PaddingValues(horizontal = 16.dp, vertical = 4.dp),
            horizontalArrangement = Arrangement.spacedBy(8.dp)
        ) {
            item {
                FilterChip(
                    selected = selectedCategory == null,
                    onClick = { selectedCategory = null },
                    label = { Text("All") }
                )
            }
            items(Categories.ALL) { category ->
                FilterChip(
                    selected = selectedCategory == category,
                    onClick = {
                        selectedCategory = if (selectedCategory == category) null else category
                    },
                    label = { Text(category) }
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
                    EmptyView(
                        title = "No Listings Found",
                        description = if (searchQuery.isNotBlank() || selectedCategory != null)
                            "No clothes match your filter criteria. Try clearing search or changing category."
                        else "No nearby clothes have been posted yet. Check back soon or list your own!",
                        icon = Icons.Default.SearchOff,
                        onAction = {
                            searchQuery = ""
                            selectedCategory = null
                            loadListings()
                        },
                        actionLabel = "Reset Filters"
                    )
                } else {
                    LazyVerticalGrid(
                        columns = GridCells.Fixed(2),
                        contentPadding = PaddingValues(12.dp),
                        horizontalArrangement = Arrangement.spacedBy(12.dp),
                        verticalArrangement = Arrangement.spacedBy(12.dp),
                        modifier = Modifier.fillMaxSize()
                    ) {
                        items(filtered) { listing ->
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
                Box(
                    modifier = Modifier
                        .fillMaxWidth()
                        .height(140.dp)
                        .background(MaterialTheme.colorScheme.surfaceVariant),
                    contentAlignment = Alignment.Center
                ) {
                    Icon(
                        imageVector = Icons.Default.Checkroom,
                        contentDescription = null,
                        tint = MaterialTheme.colorScheme.onSurfaceVariant,
                        modifier = Modifier.size(48.dp)
                    )
                }
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
                    text = "${listing.category} • ${listing.sizeLabel}",
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
                    text = listing.city,
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
    onMessageLender: (listingId: String, lenderId: String) -> Unit
) {
    var uiState by remember { mutableStateOf<UiState<Listing>>(UiState.Loading) }
    val scope = rememberCoroutineScope()

    fun loadDetail() {
        scope.launch {
            uiState = UiState.Loading
            try {
                val detail = repository.getListingDetail(listingId)
                uiState = UiState.Success(detail)
            } catch (e: Exception) {
                uiState = UiState.Error(e.localizedMessage ?: "Failed to load listing details.")
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
            }
        )

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
                    val photoUrl = listing.photos.firstOrNull()?.storagePath
                    if (!photoUrl.isNullOrBlank()) {
                        AsyncImage(
                            model = photoUrl,
                            contentDescription = listing.title,
                            contentScale = ContentScale.Crop,
                            modifier = Modifier
                                .fillMaxWidth()
                                .height(260.dp)
                                .clip(RoundedCornerShape(12.dp))
                        )
                    } else {
                        Box(
                            modifier = Modifier
                                .fillMaxWidth()
                                .height(200.dp)
                                .clip(RoundedCornerShape(12.dp))
                                .background(MaterialTheme.colorScheme.surfaceVariant),
                            contentAlignment = Alignment.Center
                        ) {
                            Icon(
                                imageVector = Icons.Default.Checkroom,
                                contentDescription = null,
                                modifier = Modifier.size(64.dp)
                            )
                        }
                    }

                    Spacer(modifier = Modifier.height(16.dp))
                    Text(
                        text = listing.title,
                        style = MaterialTheme.typography.headlineSmall,
                        fontWeight = FontWeight.Bold
                    )
                    Spacer(modifier = Modifier.height(4.dp))
                    Text(
                        text = "${listing.category} | Size ${listing.sizeLabel}",
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
                    Text(
                        text = "Location: ${listing.city}",
                        style = MaterialTheme.typography.bodyMedium,
                        color = MaterialTheme.colorScheme.outline
                    )

                    HorizontalDivider(modifier = Modifier.padding(vertical = 16.dp))

                    Text(
                        text = "Description",
                        style = MaterialTheme.typography.titleMedium,
                        fontWeight = FontWeight.Bold
                    )
                    Spacer(modifier = Modifier.height(4.dp))
                    Text(
                        text = listing.description.ifBlank { "No description provided." },
                        style = MaterialTheme.typography.bodyMedium
                    )

                    if (listing.lenderId != currentUserId) {
                        Spacer(modifier = Modifier.height(32.dp))
                        Button(
                            onClick = { onMessageLender(listing.id, listing.lenderId) },
                            modifier = Modifier.fillMaxWidth().height(50.dp)
                        ) {
                            Icon(Icons.AutoMirrored.Filled.Send, contentDescription = null)
                            Spacer(modifier = Modifier.width(8.dp))
                            Text("Message Lender")
                        }
                    }
                }
            }
        }
    }
}

// --- INBOX SCREEN ---
@Composable
fun InboxScreen(
    repository: DzirentRepository,
    currentUserId: String,
    onConversationClick: (Conversation) -> Unit
) {
    var uiState by remember { mutableStateOf<UiState<List<Conversation>>>(UiState.Loading) }
    val scope = rememberCoroutineScope()

    fun loadConversations() {
        scope.launch {
            uiState = UiState.Loading
            try {
                val list = repository.getConversations(currentUserId)
                uiState = UiState.Success(list)
            } catch (e: Exception) {
                uiState = UiState.Error(e.localizedMessage ?: "Failed to load conversations.")
            }
        }
    }

    LaunchedEffect(currentUserId) {
        loadConversations()
    }

    when (val state = uiState) {
        is UiState.Loading -> LoadingView(message = "Loading messages...")
        is UiState.Error -> ErrorView(message = state.message, onRetry = { loadConversations() })
        is UiState.Success -> {
            if (state.data.isEmpty()) {
                EmptyView(
                    title = "No Conversations Yet",
                    description = "When you message lenders or renters, your chat threads will appear here.",
                    icon = Icons.Default.ChatBubbleOutline
                )
            } else {
                LazyColumn(
                    contentPadding = PaddingValues(16.dp),
                    verticalArrangement = Arrangement.spacedBy(8.dp),
                    modifier = Modifier.fillMaxSize()
                ) {
                    items(state.data) { conv ->
                        Card(
                            modifier = Modifier
                                .fillMaxWidth()
                                .clickable { onConversationClick(conv) },
                            elevation = CardDefaults.cardElevation(defaultElevation = 1.dp)
                        ) {
                            Row(
                                modifier = Modifier.padding(16.dp),
                                verticalAlignment = Alignment.CenterVertically
                            ) {
                                Icon(
                                    imageVector = Icons.Default.Person,
                                    contentDescription = null,
                                    modifier = Modifier.size(40.dp)
                                )
                                Spacer(modifier = Modifier.width(16.dp))
                                Column {
                                    Text(
                                        text = conv.otherUser?.displayName ?: "Chat Thread",
                                        style = MaterialTheme.typography.titleMedium,
                                        fontWeight = FontWeight.Bold
                                    )
                                    Text(
                                        text = conv.lastMessage?.body ?: "Tap to open conversation",
                                        style = MaterialTheme.typography.bodySmall,
                                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                                        maxLines = 1,
                                        overflow = TextOverflow.Ellipsis
                                    )
                                }
                            }
                        }
                    }
                }
            }
        }
    }
}

// --- CHAT SCREEN ---
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun ChatScreen(
    repository: DzirentRepository,
    conversationId: String,
    currentUserId: String,
    onBack: () -> Unit
) {
    var messagesState by remember { mutableStateOf<UiState<List<Message>>>(UiState.Loading) }
    var textInput by remember { mutableStateOf("") }
    val scope = rememberCoroutineScope()

    fun loadMessages() {
        scope.launch {
            messagesState = UiState.Loading
            try {
                val msgs = repository.getMessages(conversationId)
                messagesState = UiState.Success(msgs)
            } catch (e: Exception) {
                messagesState = UiState.Error(e.localizedMessage ?: "Failed to load messages.")
            }
        }
    }

    LaunchedEffect(conversationId) {
        loadMessages()
    }

    Column(modifier = Modifier.fillMaxSize()) {
        TopAppBar(
            title = { Text("Chat") },
            navigationIcon = {
                IconButton(onClick = onBack) {
                    Icon(Icons.AutoMirrored.Filled.ArrowBack, contentDescription = "Back")
                }
            }
        )

        Box(modifier = Modifier.weight(1f)) {
            when (val state = messagesState) {
                is UiState.Loading -> LoadingView(message = "Loading messages...")
                is UiState.Error -> ErrorView(message = state.message, onRetry = { loadMessages() })
                is UiState.Success -> {
                    if (state.data.isEmpty()) {
                        EmptyView(
                            title = "No Messages Yet",
                            description = "Send a message below to start chatting!",
                            icon = Icons.AutoMirrored.Filled.Send
                        )
                    } else {
                        LazyColumn(
                            contentPadding = PaddingValues(16.dp),
                            verticalArrangement = Arrangement.spacedBy(8.dp),
                            modifier = Modifier.fillMaxSize()
                        ) {
                            items(state.data) { msg ->
                                val isMe = msg.senderId == currentUserId
                                Box(
                                    modifier = Modifier.fillMaxWidth(),
                                    contentAlignment = if (isMe) Alignment.CenterEnd else Alignment.CenterStart
                                ) {
                                    Surface(
                                        color = if (isMe) MaterialTheme.colorScheme.primaryContainer else MaterialTheme.colorScheme.secondaryContainer,
                                        shape = RoundedCornerShape(12.dp),
                                        modifier = Modifier.widthIn(max = 280.dp)
                                    ) {
                                        Text(
                                            text = msg.body,
                                            modifier = Modifier.padding(12.dp),
                                            style = MaterialTheme.typography.bodyMedium
                                        )
                                    }
                                }
                            }
                        }
                    }
                }
            }
        }

        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(8.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            OutlinedTextField(
                value = textInput,
                onValueChange = { textInput = it },
                placeholder = { Text("Type a message...") },
                modifier = Modifier.weight(1f),
                singleLine = true
            )
            Spacer(modifier = Modifier.width(8.dp))
            IconButton(
                onClick = {
                    if (textInput.isNotBlank()) {
                        val msgText = textInput.trim()
                        textInput = ""
                        scope.launch {
                            try {
                                repository.sendMessage(conversationId, currentUserId, msgText)
                                loadMessages()
                            } catch (_: Exception) {
                                // Handled via Log.e in repository
                            }
                        }
                    }
                }
            ) {
                Icon(Icons.AutoMirrored.Filled.Send, contentDescription = "Send")
            }
        }
    }
}

// --- CREATE LISTING SCREEN ---
@Composable
fun CreateListingScreen(
    repository: DzirentRepository,
    currentUserId: String,
    onListingCreated: () -> Unit
) {
    var title by remember { mutableStateOf("") }
    var selectedCategory by remember { mutableStateOf(Categories.ALL.first()) }
    var sizeLabel by remember { mutableStateOf("") }
    var description by remember { mutableStateOf("") }
    var priceNote by remember { mutableStateOf("") }
    var city by remember { mutableStateOf("") }

    var isSubmitting by remember { mutableStateOf(false) }
    var errorMessage by remember { mutableStateOf<String?>(null) }
    val scope = rememberCoroutineScope()

    if (isSubmitting) {
        LoadingView(message = "Publishing listing...")
        return
    }

    Column(
        modifier = Modifier
            .fillMaxSize()
            .verticalScroll(rememberScrollState())
            .padding(24.dp),
        horizontalAlignment = Alignment.CenterHorizontally
    ) {
        Text(
            text = "Create New Listing",
            style = MaterialTheme.typography.headlineSmall,
            fontWeight = FontWeight.Bold
        )
        Spacer(modifier = Modifier.height(16.dp))

        if (errorMessage != null) {
            Card(
                colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.errorContainer),
                modifier = Modifier.fillMaxWidth().padding(bottom = 16.dp)
            ) {
                Column(modifier = Modifier.padding(16.dp)) {
                    Text(
                        text = errorMessage ?: "",
                        color = MaterialTheme.colorScheme.onErrorContainer
                    )
                    Spacer(modifier = Modifier.height(8.dp))
                    TextButton(onClick = { errorMessage = null; isSubmitting = false }) {
                        Text("Retry")
                    }
                }
            }
        }

        OutlinedTextField(
            value = title,
            onValueChange = { title = it },
            label = { Text("Title (e.g. Designer Evening Gown)") },
            singleLine = true,
            modifier = Modifier.fillMaxWidth()
        )
        Spacer(modifier = Modifier.height(12.dp))

        OutlinedTextField(
            value = sizeLabel,
            onValueChange = { sizeLabel = it },
            label = { Text("Size Label (e.g. M / US 8)") },
            singleLine = true,
            modifier = Modifier.fillMaxWidth()
        )
        Spacer(modifier = Modifier.height(12.dp))

        OutlinedTextField(
            value = priceNote,
            onValueChange = { priceNote = it },
            label = { Text("Price Note (e.g. $25/day or Trade)") },
            singleLine = true,
            modifier = Modifier.fillMaxWidth()
        )
        Spacer(modifier = Modifier.height(12.dp))

        OutlinedTextField(
            value = city,
            onValueChange = { city = it },
            label = { Text("City / Area") },
            singleLine = true,
            modifier = Modifier.fillMaxWidth()
        )
        Spacer(modifier = Modifier.height(12.dp))

        OutlinedTextField(
            value = description,
            onValueChange = { description = it },
            label = { Text("Description") },
            modifier = Modifier.fillMaxWidth().height(120.dp)
        )
        Spacer(modifier = Modifier.height(24.dp))

        Button(
            onClick = {
                if (title.isBlank() || sizeLabel.isBlank() || priceNote.isBlank()) {
                    errorMessage = "Please enter Title, Size, and Price Note."
                    return@Button
                }
                scope.launch {
                    isSubmitting = true
                    errorMessage = null
                    try {
                        val newListing = Listing(
                            id = UUID.randomUUID().toString(),
                            lenderId = currentUserId,
                            title = title.trim(),
                            category = selectedCategory,
                            sizeLabel = sizeLabel.trim(),
                            description = description.trim(),
                            priceNote = priceNote.trim(),
                            city = if (city.isBlank()) "Nearby" else city.trim(),
                            approxLat = 0.0,
                            approxLng = 0.0,
                            status = "active"
                        )
                        repository.createListing(newListing)
                        isSubmitting = false
                        onListingCreated()
                    } catch (e: Exception) {
                        isSubmitting = false
                        errorMessage = e.localizedMessage ?: "Failed to publish listing. Please try again."
                    }
                }
            },
            modifier = Modifier.fillMaxWidth().height(50.dp)
        ) {
            Text("Publish Listing")
        }
    }
}

// --- MY CLOSET SCREEN ---
@Composable
fun MyClosetScreen(
    repository: DzirentRepository,
    currentUserId: String
) {
    var uiState by remember { mutableStateOf<UiState<List<Listing>>>(UiState.Loading) }
    val scope = rememberCoroutineScope()

    fun loadMyListings() {
        scope.launch {
            uiState = UiState.Loading
            try {
                val list = repository.getMyListings(currentUserId)
                uiState = UiState.Success(list)
            } catch (e: Exception) {
                uiState = UiState.Error(e.localizedMessage ?: "Failed to load your closet.")
            }
        }
    }

    LaunchedEffect(currentUserId) {
        loadMyListings()
    }

    when (val state = uiState) {
        is UiState.Loading -> LoadingView(message = "Loading your closet...")
        is UiState.Error -> ErrorView(message = state.message, onRetry = { loadMyListings() })
        is UiState.Success -> {
            if (state.data.isEmpty()) {
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
                    items(state.data) { listing ->
                        Card(
                            modifier = Modifier.fillMaxWidth(),
                            elevation = CardDefaults.cardElevation(defaultElevation = 2.dp)
                        ) {
                            Row(
                                modifier = Modifier.padding(16.dp),
                                verticalAlignment = Alignment.CenterVertically
                            ) {
                                Column(modifier = Modifier.weight(1f)) {
                                    Text(
                                        text = listing.title,
                                        style = MaterialTheme.typography.titleMedium,
                                        fontWeight = FontWeight.Bold
                                    )
                                    Text(
                                        text = "${listing.category} | ${listing.sizeLabel} | Status: ${listing.status}",
                                        style = MaterialTheme.typography.bodySmall,
                                        color = MaterialTheme.colorScheme.onSurfaceVariant
                                    )
                                }
                                IconButton(
                                    onClick = {
                                        scope.launch {
                                            try {
                                                repository.deleteListing(listing.id)
                                                loadMyListings()
                                            } catch (_: Exception) {
                                                // Logged via Dzirent tag
                                            }
                                        }
                                    }
                                ) {
                                    Icon(Icons.Default.Delete, contentDescription = "Delete", tint = MaterialTheme.colorScheme.error)
                                }
                            }
                        }
                    }
                }
            }
        }
    }
}

// --- PROFILE SCREEN ---
@Composable
fun ProfileScreen(
    repository: DzirentRepository,
    onSignOut: () -> Unit
) {
    var uiState by remember { mutableStateOf<UiState<Profile?>>(UiState.Loading) }
    val scope = rememberCoroutineScope()

    fun loadProfile() {
        scope.launch {
            uiState = UiState.Loading
            try {
                val profile = repository.getCurrentProfile()
                uiState = UiState.Success(profile)
            } catch (e: Exception) {
                uiState = UiState.Error(e.localizedMessage ?: "Failed to load profile.")
            }
        }
    }

    LaunchedEffect(Unit) {
        loadProfile()
    }

    when (val state = uiState) {
        is UiState.Loading -> LoadingView(message = "Loading profile...")
        is UiState.Error -> ErrorView(message = state.message, onRetry = { loadProfile() })
        is UiState.Success -> {
            val profile = state.data
            if (profile == null) {
                EmptyView(
                    title = "No Profile Found",
                    description = "Could not locate account profile details.",
                    icon = Icons.Default.Person
                )
            } else {
                Column(
                    modifier = Modifier
                        .fillMaxSize()
                        .padding(24.dp),
                    horizontalAlignment = Alignment.CenterHorizontally,
                    verticalArrangement = Arrangement.Center
                ) {
                    Icon(
                        imageVector = Icons.Default.AccountCircle,
                        contentDescription = null,
                        modifier = Modifier.size(80.dp),
                        tint = MaterialTheme.colorScheme.primary
                    )
                    Spacer(modifier = Modifier.height(16.dp))
                    Text(
                        text = profile.displayName,
                        style = MaterialTheme.typography.headlineSmall,
                        fontWeight = FontWeight.Bold
                    )
                    Text(
                        text = "Role: ${profile.role.replaceFirstChar { it.uppercase() }}",
                        style = MaterialTheme.typography.bodyLarge,
                        color = MaterialTheme.colorScheme.secondary
                    )
                    Text(
                        text = "City: ${profile.city}",
                        style = MaterialTheme.typography.bodyMedium,
                        color = MaterialTheme.colorScheme.outline
                    )

                    Spacer(modifier = Modifier.height(48.dp))

                    Button(
                        onClick = {
                            scope.launch {
                                repository.signOut()
                                onSignOut()
                            }
                        },
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
        }
    }
}
