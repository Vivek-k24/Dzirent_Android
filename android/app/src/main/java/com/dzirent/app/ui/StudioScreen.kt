package com.dzirent.app.ui

import android.widget.Toast
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.grid.GridCells
import androidx.compose.foundation.lazy.grid.GridItemSpan
import androidx.compose.foundation.lazy.grid.LazyVerticalGrid
import androidx.compose.foundation.lazy.grid.items
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.Bookmark
import androidx.compose.material.icons.filled.BookmarkBorder
import androidx.compose.material.icons.filled.Storefront
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import com.dzirent.app.data.model.Listing
import com.dzirent.app.data.model.StudioPage
import com.dzirent.app.data.repository.DzirentRepository
import com.dzirent.app.util.replyTimeText
import com.dzirent.app.util.sinceText
import kotlinx.coroutines.launch

/**
 * A lender's studio: handle in the top bar with a bookmark, then avatar, name, place and
 * "since", bio, style tags, pickup area, reply time and counts, a full-width "Message"
 * button and a 3-column grid of active pieces. No follow button or follower counts.
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun StudioScreen(
    repository: DzirentRepository,
    lenderId: String,
    currentUserId: String,
    onBack: () -> Unit,
    onOpenListing: (String) -> Unit,
    onMessageAbout: (Listing) -> Unit,
    onSavedChanged: () -> Unit
) {
    val scope = rememberCoroutineScope()
    val context = LocalContext.current
    var uiState by remember { mutableStateOf<UiState<StudioPage>>(UiState.Loading) }
    var savingBookmark by remember { mutableStateOf(false) }
    var showPiecePicker by remember { mutableStateOf(false) }

    fun load() {
        scope.launch {
            uiState = UiState.Loading
            uiState = try {
                UiState.Success(repository.getStudio(lenderId, currentUserId))
            } catch (e: Exception) {
                UiState.Error(e.userMessage("Couldn't load this studio. Please try again."))
            }
        }
    }

    fun toggleSaved(page: StudioPage) {
        if (savingBookmark) return
        scope.launch {
            savingBookmark = true
            try {
                if (page.isSaved) repository.unsaveStudio(currentUserId, lenderId)
                else repository.saveStudio(currentUserId, lenderId)
                uiState = UiState.Success(page.copy(isSaved = !page.isSaved))
                onSavedChanged()
            } catch (e: Exception) {
                Toast.makeText(context, e.userMessage("Couldn't update your saved studios. Please try again."), Toast.LENGTH_SHORT).show()
            }
            savingBookmark = false
        }
    }

    LaunchedEffect(lenderId) { load() }

    val page = (uiState as? UiState.Success)?.data
    val isMine = lenderId == currentUserId

    if (showPiecePicker && page != null) {
        PiecePickerSheet(
            pieces = page.pieces,
            onPick = { piece ->
                showPiecePicker = false
                onMessageAbout(piece)
            },
            onDismiss = { showPiecePicker = false }
        )
    }

    Column(modifier = Modifier.fillMaxSize()) {
        CenterAlignedTopAppBar(
            title = {
                Text(
                    text = page?.lender?.studioHandle?.let { "@$it" } ?: page?.lender?.studioTitle.orEmpty(),
                    style = MaterialTheme.typography.titleMedium,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis
                )
            },
            navigationIcon = {
                IconButton(onClick = onBack) {
                    Icon(Icons.AutoMirrored.Filled.ArrowBack, contentDescription = "Back")
                }
            },
            actions = {
                if (page != null && !isMine) {
                    IconButton(onClick = { toggleSaved(page) }, enabled = !savingBookmark) {
                        Icon(
                            imageVector = if (page.isSaved) Icons.Default.Bookmark else Icons.Default.BookmarkBorder,
                            contentDescription = if (page.isSaved) "Remove studio from Saved" else "Save studio"
                        )
                    }
                }
            }
        )

        when (val state = uiState) {
            is UiState.Loading -> LoadingView(message = "Loading studio...")
            is UiState.Error -> ErrorView(message = state.message, onRetry = { load() })
            is UiState.Success -> StudioContent(
                page = state.data,
                isMine = isMine,
                onMessage = {
                    val pieces = state.data.pieces
                    // One piece: straight to the chat. More: pick which one it's about.
                    if (pieces.size == 1) onMessageAbout(pieces.first()) else showPiecePicker = true
                },
                onOpenListing = onOpenListing
            )
        }
    }
}

@Composable
private fun StudioContent(
    page: StudioPage,
    isMine: Boolean,
    onMessage: () -> Unit,
    onOpenListing: (String) -> Unit
) {
    val lender = page.lender
    LazyVerticalGrid(
        columns = GridCells.Fixed(3),
        contentPadding = PaddingValues(bottom = 16.dp),
        horizontalArrangement = Arrangement.spacedBy(2.dp),
        verticalArrangement = Arrangement.spacedBy(2.dp),
        modifier = Modifier.fillMaxSize()
    ) {
        item(span = { GridItemSpan(maxLineSpan) }) {
            Column(
                horizontalAlignment = Alignment.CenterHorizontally,
                modifier = Modifier.padding(horizontal = 24.dp, vertical = 8.dp)
            ) {
                Avatar(name = lender.studioTitle, size = 72.dp)
                Spacer(modifier = Modifier.height(12.dp))
                Text(lender.studioTitle, style = MaterialTheme.typography.titleLarge, fontWeight = FontWeight.Bold)
                val place = listOfNotNull(lender.city.ifBlank { null }, sinceText(lender.createdAt)).joinToString(" · ")
                if (place.isNotEmpty()) {
                    Text(place, style = MaterialTheme.typography.bodyMedium, color = MaterialTheme.colorScheme.onSurfaceVariant)
                }
                lender.studioBio?.ifBlank { null }?.let {
                    Spacer(modifier = Modifier.height(8.dp))
                    Text(it, style = MaterialTheme.typography.bodyMedium, textAlign = TextAlign.Center)
                }
                if (lender.styleTags.isNotEmpty()) {
                    Spacer(modifier = Modifier.height(8.dp))
                    StyleTagRow(lender.styleTags)
                }
                Spacer(modifier = Modifier.height(8.dp))
                val facts = listOfNotNull(
                    lender.pickupArea?.ifBlank { null }?.let { "Pickup: $it" },
                    replyTimeText(page.replyMinutes),
                    page.rentedCount.takeIf { it > 0 }?.let { "Rented $it ${if (it == 1) "time" else "times"}" },
                    "${page.pieces.size} ${if (page.pieces.size == 1) "piece" else "pieces"}"
                )
                facts.forEach {
                    Text(it, style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                }
                if (!isMine && page.pieces.isNotEmpty()) {
                    Spacer(modifier = Modifier.height(16.dp))
                    Button(onClick = onMessage, modifier = Modifier.fillMaxWidth().height(50.dp)) {
                        Text("Message ${lender.studioTitle}", maxLines = 1, overflow = TextOverflow.Ellipsis)
                    }
                }
                Spacer(modifier = Modifier.height(16.dp))
            }
        }
        if (page.pieces.isEmpty()) {
            item(span = { GridItemSpan(maxLineSpan) }) {
                EmptyView(
                    title = "No pieces right now",
                    description = "This studio has nothing listed at the moment.",
                    icon = Icons.Default.Storefront,
                    modifier = Modifier.height(240.dp)
                )
            }
        }
        items(page.pieces, key = { it.id }) { piece ->
            ListingPhoto(
                url = piece.photos.minByOrNull { it.position }?.storagePath,
                contentDescription = piece.title,
                modifier = Modifier
                    .aspectRatio(1f)
                    .clickable { onOpenListing(piece.id) }
            )
        }
    }
}

/** Style tags as small plain pills (not buttons). */
@OptIn(ExperimentalLayoutApi::class)
@Composable
private fun StyleTagRow(tags: List<String>) {
    FlowRow(
        horizontalArrangement = Arrangement.spacedBy(6.dp, Alignment.CenterHorizontally),
        verticalArrangement = Arrangement.spacedBy(6.dp)
    ) {
        tags.forEach { tag ->
            Surface(shape = RoundedCornerShape(50), color = MaterialTheme.colorScheme.surfaceVariant) {
                Text(
                    text = tag,
                    style = MaterialTheme.typography.labelMedium,
                    modifier = Modifier.padding(horizontal = 10.dp, vertical = 4.dp)
                )
            }
        }
    }
}

/** "Which piece is this about?" Chats are always about one listing. */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun PiecePickerSheet(pieces: List<Listing>, onPick: (Listing) -> Unit, onDismiss: () -> Unit) {
    ModalBottomSheet(onDismissRequest = onDismiss) {
        Text(
            text = "Which piece is this about?",
            style = MaterialTheme.typography.titleMedium,
            fontWeight = FontWeight.Bold,
            modifier = Modifier.padding(horizontal = 24.dp, vertical = 8.dp)
        )
        LazyColumn(contentPadding = PaddingValues(bottom = 24.dp)) {
            items(pieces, key = { it.id }) { piece ->
                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    modifier = Modifier
                        .fillMaxWidth()
                        .clickable { onPick(piece) }
                        .padding(horizontal = 24.dp, vertical = 8.dp)
                ) {
                    ListingPhoto(
                        url = piece.photos.minByOrNull { it.position }?.storagePath,
                        contentDescription = null,
                        modifier = Modifier
                            .size(56.dp)
                            .clip(RoundedCornerShape(8.dp))
                    )
                    Spacer(modifier = Modifier.width(12.dp))
                    Column(modifier = Modifier.weight(1f)) {
                        Text(piece.title, style = MaterialTheme.typography.titleSmall, maxLines = 1, overflow = TextOverflow.Ellipsis)
                        Text(
                            text = cardSubtitle(piece, LocalAppSettings.current.distanceUnit).ifBlank { piece.typeLabel },
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                    }
                }
            }
        }
    }
}
