package com.dzirent.app.ui

import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.automirrored.filled.AssignmentReturn
import androidx.compose.material.icons.automirrored.filled.Send
import androidx.compose.material.icons.filled.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import com.dzirent.app.data.model.*
import com.dzirent.app.data.repository.ChatSubscription
import com.dzirent.app.data.repository.DzirentRepository
import com.dzirent.app.util.DateRange
import com.dzirent.app.util.daysSince
import com.dzirent.app.util.formatInboxTime
import com.dzirent.app.util.formatMessageTime
import com.dzirent.app.util.parseDateRange
import com.dzirent.app.util.timestampMillis
import kotlinx.coroutines.NonCancellable
import kotlinx.coroutines.awaitCancellation
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

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
                uiState = UiState.Success(repository.getConversations(currentUserId))
            } catch (e: Exception) {
                uiState = UiState.Error(e.userMessage("Failed to load conversations."))
            }
        }
    }

    // Runs each time the Inbox tab is opened, since the screen re-enters composition.
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
                    items(state.data, key = { it.id }) { conv ->
                        InboxRow(conv = conv, currentUserId = currentUserId, onClick = { onConversationClick(conv) })
                    }
                }
            }
        }
    }
}

@Composable
private fun InboxRow(conv: Conversation, currentUserId: String, onClick: () -> Unit) {
    val last = conv.lastMessage
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
            ListingThumbnail(listing = conv.listing, size = 56.dp)
            Spacer(modifier = Modifier.width(12.dp))
            Column(modifier = Modifier.weight(1f)) {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Text(
                        text = conv.otherUser(currentUserId)?.displayName?.ifBlank { null } ?: "Dzirent member",
                        style = MaterialTheme.typography.titleMedium,
                        fontWeight = FontWeight.Bold,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis,
                        modifier = Modifier.weight(1f)
                    )
                    Text(
                        text = formatInboxTime(last?.createdAt ?: conv.createdAt),
                        style = MaterialTheme.typography.labelSmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                }
                Text(
                    text = conv.listing?.title ?: "Listing no longer available",
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.primary,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis
                )
                Text(
                    text = when {
                        last == null -> "No messages yet"
                        last.senderId == currentUserId -> "You: ${last.body}"
                        else -> last.body
                    },
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis
                )
            }
        }
    }
}

// --- CHAT SCREEN ---

private val QUICK_REPLIES = listOf("Is this available on these dates?", "Can I see more photos?")
private const val MESSAGE_MAX = 2000
private const val FOLLOW_UP_AFTER_DAYS = 3L

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun ChatScreen(
    repository: DzirentRepository,
    conversationId: String,
    currentUserId: String,
    onBack: () -> Unit,
    onOpenListing: (listingId: String) -> Unit
) {
    val scope = rememberCoroutineScope()
    val context = LocalContext.current
    val safetyTips = remember { SafetyTipStore(context) }
    var showSafetyTip by remember(conversationId) { mutableStateOf(!safetyTips.isDismissed(conversationId)) }

    var uiState by remember { mutableStateOf<UiState<Conversation>>(UiState.Loading) }
    var messages by remember { mutableStateOf<List<Message>>(emptyList()) }
    var rental by remember { mutableStateOf<Rental?>(null) }
    var myOutcome by remember { mutableStateOf<RentalOutcome?>(null) }
    var liveUnavailable by remember { mutableStateOf(false) }

    var input by remember { mutableStateOf("") }
    var isSending by remember { mutableStateOf(false) }
    var sendError by remember { mutableStateOf<String?>(null) }
    var actionError by remember { mutableStateOf<String?>(null) }
    var isSavingRental by remember { mutableStateOf(false) }
    var menuOpen by remember { mutableStateOf(false) }
    var showRentedPicker by remember { mutableStateOf(false) }

    val listState = rememberLazyListState()

    /** Adds messages, skipping ids we already have (my own send arrives live too). */
    fun merge(incoming: List<Message>) {
        val known = messages.map { it.id }.toSet()
        val fresh = incoming.filter { it.id !in known }
        if (fresh.isNotEmpty()) messages = (messages + fresh).sortedBy { timestampMillis(it.createdAt) }
    }

    suspend fun loadAll() {
        uiState = UiState.Loading
        try {
            val conversation = repository.getConversation(conversationId)
            val history = repository.getMessages(conversationId)
            val loadedRental = repository.getRental(conversationId)
            val outcome = repository.getMyRentalOutcome(conversationId, currentUserId)
            merge(history)
            rental = loadedRental
            myOutcome = outcome
            uiState = UiState.Success(conversation)
        } catch (e: Exception) {
            uiState = UiState.Error(e.userMessage("Couldn't open this chat. Please try again."))
        }
    }

    // Subscribe when the chat opens, then load history (so nothing sent in between is
    // missed), and leave the realtime channel when the chat closes.
    LaunchedEffect(conversationId) {
        var subscription: ChatSubscription? = null
        try {
            subscription = try {
                repository.subscribeToChat(conversationId)
            } catch (e: Exception) {
                liveUnavailable = true
                null
            }
            subscription?.let { sub ->
                launch { sub.messages.collect { merge(listOf(it)) } }
                launch { sub.rentals.collect { rental = it } }
            }
            loadAll()
            awaitCancellation()
        } finally {
            subscription?.let { sub -> withContext(NonCancellable) { sub.close() } }
        }
    }

    LaunchedEffect(messages.size) {
        val count = listState.layoutInfo.totalItemsCount
        if (messages.isNotEmpty() && count > 0) listState.animateScrollToItem(count - 1)
    }

    val conversation = (uiState as? UiState.Success)?.data
    val otherName = conversation?.otherUser(currentUserId)?.displayName?.ifBlank { null } ?: "Dzirent member"

    fun send(text: String) {
        val body = text.trim()
        if (body.isEmpty() || isSending) return
        scope.launch {
            isSending = true
            sendError = null
            try {
                merge(listOf(repository.sendMessage(conversationId, currentUserId, body)))
                // Keep anything typed while sending; clear only the text we sent.
                if (input.trim() == body) input = ""
            } catch (e: Exception) {
                sendError = e.userMessage("Couldn't send your message. Please try again.")
            }
            isSending = false
        }
    }

    fun updateRental(status: String, range: DateRange? = null, outcome: Boolean? = null) {
        val conv = conversation ?: return
        scope.launch {
            isSavingRental = true
            actionError = null
            try {
                rental = repository.saveRental(
                    conversation = conv,
                    status = status,
                    startDate = range?.start?.toString(),
                    endDate = range?.end?.toString(),
                    userId = currentUserId
                )
                if (outcome != null) {
                    repository.submitRentalOutcome(RentalOutcome(conv.id, currentUserId, happened = outcome))
                    myOutcome = RentalOutcome(conv.id, currentUserId, happened = outcome)
                }
            } catch (e: Exception) {
                actionError = e.userMessage("Couldn't update the rental. Please try again.")
            }
            isSavingRental = false
        }
    }

    fun answerFollowUpNo() {
        val conv = conversation ?: return
        scope.launch {
            actionError = null
            try {
                val answer = RentalOutcome(conv.id, currentUserId, happened = false)
                repository.submitRentalOutcome(answer)
                myOutcome = answer
            } catch (e: Exception) {
                actionError = e.userMessage("Couldn't save your answer. Please try again.")
            }
        }
    }

    if (showRentedPicker) {
        DateRangePickerDialog(
            title = "Rental dates",
            allowPast = true,
            initial = rental?.let { parseDateRange(it.startDate, it.endDate) },
            confirmLabel = "Mark as rented",
            onDismiss = { showRentedPicker = false },
            onConfirm = { range ->
                showRentedPicker = false
                updateRental("active", range, outcome = true)
            }
        )
    }

    Column(modifier = Modifier.fillMaxSize()) {
        TopAppBar(
            title = {
                Column {
                    Text(otherName, maxLines = 1, overflow = TextOverflow.Ellipsis)
                    conversation?.listing?.title?.let {
                        Text(
                            text = it,
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                            maxLines = 1,
                            overflow = TextOverflow.Ellipsis
                        )
                    }
                }
            },
            navigationIcon = {
                IconButton(onClick = onBack) {
                    Icon(Icons.AutoMirrored.Filled.ArrowBack, contentDescription = "Back")
                }
            },
            actions = {
                if (conversation != null) {
                    Box {
                        IconButton(onClick = { menuOpen = true }) {
                            Icon(Icons.Default.MoreVert, contentDescription = "More options")
                        }
                        DropdownMenu(expanded = menuOpen, onDismissRequest = { menuOpen = false }) {
                            val status = rental?.status
                            DropdownMenuItem(
                                text = { Text(if (status == "active") "Change rental dates" else "Mark as rented") },
                                leadingIcon = { Icon(Icons.Default.EventAvailable, contentDescription = null) },
                                enabled = !isSavingRental,
                                onClick = { menuOpen = false; showRentedPicker = true }
                            )
                            if (status == "active") {
                                DropdownMenuItem(
                                    text = { Text("Mark as returned") },
                                    leadingIcon = { Icon(Icons.AutoMirrored.Filled.AssignmentReturn, contentDescription = null) },
                                    enabled = !isSavingRental,
                                    onClick = { menuOpen = false; updateRental("returned") }
                                )
                            }
                            if (status == null || status == "active") {
                                DropdownMenuItem(
                                    text = { Text("Deal didn't happen") },
                                    leadingIcon = { Icon(Icons.Default.EventBusy, contentDescription = null) },
                                    enabled = !isSavingRental,
                                    onClick = { menuOpen = false; updateRental("cancelled", outcome = false) }
                                )
                            }
                        }
                    }
                }
            }
        )

        when (val state = uiState) {
            is UiState.Loading -> LoadingView(message = "Loading messages...")
            is UiState.Error -> ErrorView(message = state.message, onRetry = { scope.launch { loadAll() } })
            is UiState.Success -> {
                val conv = state.data
                PinnedListing(listing = conv.listing, onClick = { onOpenListing(conv.listingId) })
                rental?.let { RentalBanner(it, currentUserId, otherName) }
                if (isSavingRental) LinearProgressIndicator(modifier = Modifier.fillMaxWidth())
                if (liveUnavailable) {
                    Text(
                        text = "Live updates are off. Leave and reopen the chat to see new messages.",
                        style = MaterialTheme.typography.labelSmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                        modifier = Modifier.padding(horizontal = 16.dp, vertical = 4.dp)
                    )
                }

                val showFollowUp = rental == null && myOutcome == null &&
                    (daysSince(conv.createdAt) ?: 0L) >= FOLLOW_UP_AFTER_DAYS

                LazyColumn(
                    state = listState,
                    contentPadding = PaddingValues(16.dp),
                    verticalArrangement = Arrangement.spacedBy(8.dp),
                    modifier = Modifier.weight(1f)
                ) {
                    if (showSafetyTip) {
                        item(key = "safety") {
                            PolicyNotice(
                                text = DzirentPolicy.CHAT_SAFETY_TIP,
                                onDismiss = {
                                    safetyTips.dismiss(conversationId)
                                    showSafetyTip = false
                                }
                            )
                        }
                    }
                    if (showFollowUp) {
                        item(key = "follow-up") {
                            FollowUpCard(
                                onYes = { showRentedPicker = true },
                                onNo = { answerFollowUpNo() }
                            )
                        }
                    }
                    if (messages.isEmpty()) {
                        item(key = "empty") {
                            Column(
                                horizontalAlignment = Alignment.CenterHorizontally,
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .padding(vertical = 24.dp)
                            ) {
                                Text(
                                    text = "Say hi to $otherName",
                                    style = MaterialTheme.typography.titleSmall
                                )
                                Spacer(modifier = Modifier.height(12.dp))
                                QUICK_REPLIES.forEach { reply ->
                                    SuggestionChip(onClick = { input = reply }, label = { Text(reply) })
                                }
                            }
                        }
                    }
                    items(messages, key = { it.id }) { msg ->
                        MessageBubble(message = msg, isMe = msg.senderId == currentUserId)
                    }
                }

                (sendError ?: actionError)?.let { message ->
                    Row(
                        verticalAlignment = Alignment.CenterVertically,
                        modifier = Modifier.padding(horizontal = 16.dp)
                    ) {
                        Text(
                            text = message,
                            color = MaterialTheme.colorScheme.error,
                            style = MaterialTheme.typography.bodySmall,
                            modifier = Modifier.weight(1f)
                        )
                        if (sendError != null) {
                            TextButton(onClick = { send(input) }, enabled = !isSending) { Text("Retry") }
                        } else {
                            TextButton(onClick = { actionError = null }) { Text("OK") }
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
                        value = input,
                        onValueChange = { if (it.length <= MESSAGE_MAX) input = it; sendError = null },
                        placeholder = { Text("Type a message...") },
                        modifier = Modifier.weight(1f),
                        maxLines = 4
                    )
                    Spacer(modifier = Modifier.width(8.dp))
                    if (isSending) {
                        CircularProgressIndicator(modifier = Modifier.size(24.dp).padding(2.dp))
                    } else {
                        IconButton(onClick = { send(input) }, enabled = input.isNotBlank()) {
                            Icon(Icons.AutoMirrored.Filled.Send, contentDescription = "Send")
                        }
                    }
                }
            }
        }
    }
}

@Composable
private fun PinnedListing(listing: Listing?, onClick: () -> Unit) {
    Surface(
        color = MaterialTheme.colorScheme.surfaceVariant,
        modifier = Modifier
            .fillMaxWidth()
            .clickable(enabled = listing != null) { onClick() }
    ) {
        Row(
            modifier = Modifier.padding(horizontal = 16.dp, vertical = 8.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            ListingThumbnail(listing = listing, size = 44.dp)
            Spacer(modifier = Modifier.width(12.dp))
            Column(modifier = Modifier.weight(1f)) {
                Text(
                    text = listing?.title ?: "Listing no longer available",
                    style = MaterialTheme.typography.titleSmall,
                    fontWeight = FontWeight.Bold,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis
                )
                listing?.priceNote?.ifBlank { null }?.let {
                    Text(
                        text = it,
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.primary
                    )
                }
            }
            if (listing != null) Icon(Icons.Default.ChevronRight, contentDescription = "Open listing")
        }
    }
}

@Composable
private fun RentalBanner(rental: Rental, currentUserId: String, otherName: String) {
    val dates = parseDateRange(rental.startDate, rental.endDate)?.format()
    val (text, emphasized) = when (rental.status) {
        "active" -> listOfNotNull("Rented", dates).joinToString(" · ") to true
        "returned" -> listOfNotNull("Returned", dates).joinToString(" · ") to false
        else -> "Deal didn't happen" to false
    }
    val by = when (rental.updatedBy) {
        null -> null
        currentUserId -> "Marked by you"
        else -> "Marked by $otherName"
    }
    Surface(
        color = if (emphasized) MaterialTheme.colorScheme.primaryContainer else MaterialTheme.colorScheme.secondaryContainer,
        modifier = Modifier.fillMaxWidth()
    ) {
        Row(
            modifier = Modifier.padding(horizontal = 16.dp, vertical = 6.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            Icon(
                imageVector = if (rental.status == "active") Icons.Default.EventAvailable else Icons.Default.Info,
                contentDescription = null,
                modifier = Modifier.size(16.dp)
            )
            Spacer(modifier = Modifier.width(8.dp))
            Text(text = text, style = MaterialTheme.typography.labelLarge, modifier = Modifier.weight(1f))
            by?.let { Text(text = it, style = MaterialTheme.typography.labelSmall) }
        }
    }
}

@Composable
private fun FollowUpCard(onYes: () -> Unit, onNo: () -> Unit) {
    Card(
        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.primaryContainer),
        modifier = Modifier.fillMaxWidth()
    ) {
        Column(modifier = Modifier.padding(16.dp)) {
            Text(
                text = "Did this rental happen?",
                style = MaterialTheme.typography.titleSmall,
                fontWeight = FontWeight.Bold
            )
            Text(
                text = "Your answer helps us improve Dzirent. Only you see this question.",
                style = MaterialTheme.typography.bodySmall
            )
            Spacer(modifier = Modifier.height(8.dp))
            Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                Button(onClick = onYes) { Text("Yes") }
                OutlinedButton(onClick = onNo) { Text("No") }
            }
        }
    }
}

@Composable
private fun MessageBubble(message: Message, isMe: Boolean) {
    Box(
        modifier = Modifier.fillMaxWidth(),
        contentAlignment = if (isMe) Alignment.CenterEnd else Alignment.CenterStart
    ) {
        Surface(
            color = if (isMe) MaterialTheme.colorScheme.primaryContainer else MaterialTheme.colorScheme.secondaryContainer,
            shape = RoundedCornerShape(12.dp),
            modifier = Modifier.widthIn(max = 280.dp)
        ) {
            Column(modifier = Modifier.padding(horizontal = 12.dp, vertical = 8.dp)) {
                Text(text = message.body, style = MaterialTheme.typography.bodyMedium)
                Text(
                    text = formatMessageTime(message.createdAt),
                    style = MaterialTheme.typography.labelSmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    modifier = Modifier.align(Alignment.End)
                )
            }
        }
    }
}
