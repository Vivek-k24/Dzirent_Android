package com.dzirent.app.ui

import android.widget.Toast
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.BookmarkBorder
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.Delete
import androidx.compose.material.icons.filled.Storefront
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import com.dzirent.app.data.model.Listing
import com.dzirent.app.data.model.SavedListing
import com.dzirent.app.data.model.SavedStudio
import com.dzirent.app.data.repository.DzirentRepository
import com.dzirent.app.util.formatPrice
import kotlinx.coroutines.launch

private data class BagData(val pieces: List<SavedListing>, val studios: List<SavedStudio>)

/**
 * Bag: saved Pieces (newest first, each with "Message about dates") and saved Studios.
 * Remove by swiping left or with the X.
 */
@Composable
fun BagScreen(
    repository: DzirentRepository,
    currentUserId: String,
    onOpenListing: (String) -> Unit,
    onOpenStudio: (String) -> Unit,
    onMessageAboutDates: (Listing) -> Unit,
    onBrowse: () -> Unit,
    onStudiosChanged: () -> Unit
) {
    val scope = rememberCoroutineScope()
    val context = LocalContext.current
    var uiState by remember { mutableStateOf<UiState<BagData>>(UiState.Loading) }
    var studiosTab by rememberSaveable { mutableStateOf(false) }

    fun load() {
        scope.launch {
            uiState = UiState.Loading
            uiState = try {
                UiState.Success(BagData(repository.getSavedListings(currentUserId), repository.getSavedStudios(currentUserId)))
            } catch (e: Exception) {
                UiState.Error(e.userMessage("Couldn't load your Bag. Please try again."))
            }
        }
    }

    /** Removes at once and puts the row back if the server says no. */
    fun update(transform: (BagData) -> BagData, undo: (BagData) -> BagData, call: suspend () -> Unit) {
        val data = (uiState as? UiState.Success)?.data ?: return
        uiState = UiState.Success(transform(data))
        scope.launch {
            try {
                call()
            } catch (e: Exception) {
                (uiState as? UiState.Success)?.data?.let { uiState = UiState.Success(undo(it)) }
                Toast.makeText(context, e.userMessage("Couldn't remove it. Please try again."), Toast.LENGTH_SHORT).show()
            }
        }
    }

    fun removePiece(saved: SavedListing) = update(
        transform = { it.copy(pieces = it.pieces - saved) },
        undo = { it.copy(pieces = listOf(saved) + it.pieces) },
        call = { repository.unsaveListing(currentUserId, saved.listingId) }
    )

    fun removeStudio(saved: SavedStudio) = update(
        transform = { it.copy(studios = it.studios - saved) },
        undo = { it.copy(studios = listOf(saved) + it.studios) },
        call = {
            repository.unsaveStudio(currentUserId, saved.lenderId)
            onStudiosChanged()
        }
    )

    LaunchedEffect(currentUserId) { load() }

    Column(modifier = Modifier.fillMaxSize()) {
        ScreenTitle("Bag")
        TabRow(selectedTabIndex = if (studiosTab) 1 else 0) {
            Tab(selected = !studiosTab, onClick = { studiosTab = false }, text = { Text("Pieces") })
            Tab(selected = studiosTab, onClick = { studiosTab = true }, text = { Text("Studios") })
        }
        when (val state = uiState) {
            is UiState.Loading -> LoadingView(message = "Loading your Bag...")
            is UiState.Error -> ErrorView(message = state.message, onRetry = { load() })
            is UiState.Success -> if (!studiosTab) {
                if (state.data.pieces.isEmpty()) {
                    EmptyView(
                        title = "No saved pieces",
                        description = "Pieces you save will show up here.",
                        icon = Icons.Default.BookmarkBorder,
                        onAction = onBrowse,
                        actionLabel = "Browse Home"
                    )
                } else {
                    LazyColumn(modifier = Modifier.fillMaxSize()) {
                        items(state.data.pieces, key = { it.listingId }) { saved ->
                            SwipeToRemove(onRemove = { removePiece(saved) }) {
                                SavedPieceRow(
                                    saved = saved,
                                    onOpen = { saved.listing?.let { onOpenListing(it.id) } },
                                    onMessage = { saved.listing?.let(onMessageAboutDates) },
                                    onRemove = { removePiece(saved) }
                                )
                            }
                        }
                    }
                }
            } else {
                if (state.data.studios.isEmpty()) {
                    EmptyView(
                        title = "No saved studios",
                        description = "Studios you save will show up here.",
                        icon = Icons.Default.Storefront,
                        onAction = onBrowse,
                        actionLabel = "Browse Home"
                    )
                } else {
                    LazyColumn(modifier = Modifier.fillMaxSize()) {
                        items(state.data.studios, key = { it.lenderId }) { saved ->
                            SwipeToRemove(onRemove = { removeStudio(saved) }) {
                                SavedStudioRow(
                                    saved = saved,
                                    onOpen = { onOpenStudio(saved.lenderId) },
                                    onRemove = { removeStudio(saved) }
                                )
                            }
                        }
                    }
                }
            }
        }
    }
}

/** Swipe a row to the left to remove it. */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun SwipeToRemove(onRemove: () -> Unit, content: @Composable RowScope.() -> Unit) {
    val state = rememberSwipeToDismissBoxState(
        confirmValueChange = { value ->
            if (value == SwipeToDismissBoxValue.EndToStart) {
                onRemove()
                true
            } else false
        }
    )
    SwipeToDismissBox(
        state = state,
        enableDismissFromStartToEnd = false,
        backgroundContent = {
            Box(
                contentAlignment = Alignment.CenterEnd,
                modifier = Modifier
                    .fillMaxSize()
                    .background(MaterialTheme.colorScheme.surfaceVariant)
                    .padding(horizontal = 24.dp)
            ) {
                Icon(Icons.Default.Delete, contentDescription = null)
            }
        },
        content = content
    )
}

@Composable
private fun SavedPieceRow(saved: SavedListing, onOpen: () -> Unit, onMessage: () -> Unit, onRemove: () -> Unit) {
    val listing = saved.listing
    Surface(color = MaterialTheme.colorScheme.background) {
        Row(
            verticalAlignment = Alignment.CenterVertically,
            modifier = Modifier
                .fillMaxWidth()
                .clickable(enabled = listing != null, onClick = onOpen)
                .padding(horizontal = 16.dp, vertical = 10.dp)
        ) {
            ListingPhoto(
                url = listing?.photos?.minByOrNull { it.position }?.storagePath,
                contentDescription = listing?.title,
                modifier = Modifier
                    .size(width = 72.dp, height = 96.dp)
                    .clip(RoundedCornerShape(8.dp))
            )
            Spacer(modifier = Modifier.width(12.dp))
            Column(modifier = Modifier.weight(1f)) {
                Text(
                    text = listing?.title ?: "No longer available",
                    style = MaterialTheme.typography.titleSmall,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis
                )
                listing?.priceUsd?.let {
                    Text(formatPrice(it), style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                }
                if (listing != null) {
                    TextButton(onClick = onMessage, contentPadding = PaddingValues(horizontal = 0.dp)) {
                        Text("Message about dates")
                    }
                }
            }
            IconButton(onClick = onRemove) {
                Icon(Icons.Default.Close, contentDescription = "Remove from Bag")
            }
        }
    }
    HorizontalDivider(thickness = 0.5.dp)
}

@Composable
private fun SavedStudioRow(saved: SavedStudio, onOpen: () -> Unit, onRemove: () -> Unit) {
    val lender = saved.lender
    val title = lender?.studioTitle ?: "Studio"
    Surface(color = MaterialTheme.colorScheme.background) {
        Row(
            verticalAlignment = Alignment.CenterVertically,
            modifier = Modifier
                .fillMaxWidth()
                .clickable(onClick = onOpen)
                .padding(horizontal = 16.dp, vertical = 12.dp)
        ) {
            Avatar(name = title, size = 48.dp)
            Spacer(modifier = Modifier.width(12.dp))
            Column(modifier = Modifier.weight(1f)) {
                Text(title, style = MaterialTheme.typography.titleSmall, maxLines = 1, overflow = TextOverflow.Ellipsis)
                val line = listOfNotNull(lender?.studioHandle?.let { "@$it" }, lender?.city?.ifBlank { null }).joinToString(" · ")
                if (line.isNotEmpty()) {
                    Text(line, style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                }
            }
            IconButton(onClick = onRemove) {
                Icon(Icons.Default.Close, contentDescription = "Remove from Bag")
            }
        }
    }
    HorizontalDivider(thickness = 0.5.dp)
}
