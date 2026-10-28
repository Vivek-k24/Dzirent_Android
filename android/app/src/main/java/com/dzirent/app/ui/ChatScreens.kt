package com.dzirent.app.ui

import androidx.compose.foundation.ExperimentalFoundationApi
import androidx.compose.foundation.clickable
import androidx.compose.foundation.combinedClickable
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
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import com.dzirent.app.data.model.*
import com.dzirent.app.data.repository.CANT_MESSAGE_USER
import com.dzirent.app.data.repository.ChatSubscription
import com.dzirent.app.data.repository.DzirentRepository
import com.dzirent.app.data.repository.InboxSnapshot
import com.dzirent.app.util.ActivityItem
import com.dzirent.app.util.DateRange
import com.dzirent.app.util.FOLLOW_UP_AFTER_DAYS
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

/**
 * Two tabs: Messages (my chats, unread ones marked) and Activity (rental changes and
 * follow-up reminders). [onNewsChanged] keeps the dot on the Inbox tab in sync.
 */
@Composable
fun InboxScreen(
    repository: DzirentRepository,
    currentUserId: String,
    onConversationClick: (conversationId: String) -> Unit,
    onNewsChanged: (Boolean) -> Unit,
    onBrowse: () -> Unit
) {
    var uiState by remember { mutableStateOf<UiState<InboxSnapshot>>(UiState.Loading) }
    var selectedTab by rememberSaveable { mutableIntStateOf(0) }
    // Items that were new when this visit started keep their mark until the screen closes.
    var activitySeenThisVisit by remember { mutableStateOf(false) }
    val scope = rememberCoroutineScope()

    fun load() {
        scope.launch {
            uiState = UiState.Loading
            try {
                val snapshot = repository.getInboxSnapshot(currentUserId)
                uiState = UiState.Success(snapshot)
                onNewsChanged(snapshot.hasNews)
            } catch (e: Exception) {
                uiState = UiState.Error(e.userMessage("Failed to load your inbox."))
            }
        }
    }

    // Runs each time the Inbox tab is opened, since the screen re-enters composition.
    LaunchedEffect(currentUserId) { load() }

    val snapshot = (uiState as? UiState.Success)?.data
    LaunchedEffect(selectedTab, snapshot) {
        if (selectedTab == 1 && snapshot != null && !activitySeenThisVisit) {
            activitySeenThisVisit = true
            repository.markActivitySeen()
            onNewsChanged(snapshot.unreadIds.isNotEmpty())
        }
    }

    Column(modifier = Modifier.fillMaxSize()) {
        ScreenTitle("Inbox")
        DzirentTabRow(selectedTabIndex = selectedTab) {
            Tab(
                selected = selectedTab == 0,
                onClick = { selectedTab = 0 },
                text = { TabLabel("Messages", dot = snapshot?.unreadIds?.isNotEmpty() == true) }
            )
            Tab(
                selected = selectedTab == 1,
                onClick = { selectedTab = 1 },
                text = {
                    TabLabel("Activity", dot = !activitySeenThisVisit && snapshot?.activity?.any { it.isNew } == true)
                }
            )
        }
        when (val state = uiState) {
            is UiState.Loading -> LoadingView(message = "Loading your inbox...")
            is UiState.Error -> ErrorView(message = state.message, onRetry = { load() })
            is UiState.Success -> if (selectedTab == 0) {
                MessagesList(state.data, currentUserId, onConversationClick, onBrowse)
            } else {
                ActivityList(state.data.activity, onConversationClick, onBrowse)
            }
        }
    }
}

@Composable
private fun TabLabel(text: String, dot: Boolean) {
    Row(verticalAlignment = Alignment.CenterVertically) {
        Text(text, maxLines = 1)
        if (dot) {
            Spacer(modifier = Modifier.width(6.dp))
            AccentDot()
        }
    }
}

@Composable
private fun MessagesList(
    snapshot: InboxSnapshot,
    currentUserId: String,
    onConversationClick: (String) -> Unit,
    onBrowse: () -> Unit
) {
    if (snapshot.conversations.isEmpty()) {
        EmptyView(
            title = "No messages yet",
            description = "When you message a lender or a renter messages you, your chats show up here.",
            icon = Icons.Default.ChatBubbleOutline,
            onAction = onBrowse,
            actionLabel = "Browse Home"
        )
        return
    }
    LazyColumn(
        contentPadding = PaddingValues(16.dp),
        verticalArrangement = Arrangement.spacedBy(8.dp),
        modifier = Modifier.fillMaxSize()
    ) {
        items(snapshot.conversations, key = { it.id }) { conv ->
            InboxRow(
                conv = conv,
                currentUserId = currentUserId,
                unread = conv.id in snapshot.unreadIds,
                onClick = { onConversationClick(conv.id) }
            )
        }
    }
}

@Composable
private fun ActivityList(items: List<ActivityItem>, onConversationClick: (String) -> Unit, onBrowse: () -> Unit) {
    if (items.isEmpty()) {
        EmptyView(
            title = "No activity yet",
            description = "Rental updates and reminders about your chats show up here.",
            icon = Icons.Default.NotificationsNone,
            onAction = onBrowse,
            actionLabel = "Browse Home"
        )
        return
    }
    LazyColumn(modifier = Modifier.fillMaxSize()) {
        items(items, key = { "${it.kind}-${it.conversationId}" }) { item ->
            ListItem(
                headlineContent = {
                    Text(
                        text = item.text,
                        fontWeight = if (item.isNew) FontWeight.Bold else FontWeight.Normal,
                        maxLines = 3,
                        overflow = TextOverflow.Ellipsis
                    )
                },
                supportingContent = { Text(formatInboxTime(item.time.toString())) },
                trailingContent = if (item.isNew) {
                    { AccentDot() }
                } else null,
                modifier = Modifier.clickable { onConversationClick(item.conversationId) }
            )
            HorizontalDivider()
        }
    }
}

@Composable
private fun InboxRow(conv: Conversation, currentUserId: String, unread: Boolean, onClick: () -> Unit) {
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
                    if (unread) {
                        AccentDot()
                        Spacer(modifier = Modifier.width(6.dp))
                    }
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
                    fontWeight = if (unread) FontWeight.Bold else FontWeight.Normal,
                    color = if (unread) MaterialTheme.colorScheme.onSurface else MaterialTheme.colorScheme.onSurfaceVariant,
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

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun ChatScreen(
    repository: DzirentRepository,
    conversationId: String,
    currentUserId: String,
    onBack: () -> Unit,
    onOpenListing: (listingId: String) -> Unit,
    /** Text to start the message box with, e.g. a question about dates. */
    initialDraft: String? = null
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

    var input by remember { mutableStateOf(initialDraft.orEmpty()) }
    var isSending by remember { mutableStateOf(false) }
    var sendError by remember { mutableStateOf<String?>(null) }
    var actionError by remember { mutableStateOf<String?>(null) }
    var isSavingRental by remember { mutableStateOf(false) }
    var menuOpen by remember { mutableStateOf(false) }
    var showRentedPicker by remember { mutableStateOf(false) }
    var iBlocked by remember { mutableStateOf(false) }
    var showBlockConfirm by remember { mutableStateOf(false) }
    var reportTarget by remember { mutableStateOf<ReportTarget?>(null) }

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
            iBlocked = repository.haveIBlocked(currentUserId, conversation.otherUserId(currentUserId))
            merge(history)
            rental = loadedRental
            myOutcome = outcome
            uiState = UiState.Success(conversation)
            repository.markConversationRead(conversationId)
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
                launch {
                    sub.messages.collect { message ->
                        merge(listOf(message))
                        // I'm looking at it, so it's read.
                        if (message.senderId != currentUserId) repository.markConversationRead(conversationId)
                    }
                }
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

    fun setBlocked(block: Boolean) {
        val conv = conversation ?: return
        val otherId = conv.otherUserId(currentUserId)
        scope.launch {
            actionError = null
            try {
                if (block) repository.blockUser(currentUserId, otherId) else repository.unblockUser(currentUserId, otherId)
                iBlocked = block
                sendError = null
            } catch (e: Exception) {
                actionError = e.userMessage(if (block) "Couldn't block this user." else "Couldn't unblock this user.")
            }
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

    if (showBlockConfirm) {
        BlockUserDialog(
            name = otherName,
            onConfirm = { showBlockConfirm = false; setBlocked(true) },
            onDismiss = { showBlockConfirm = false }
        )
    }

    reportTarget?.let { target ->
        ReportDialog(
            repository = repository,
            reporterId = currentUserId,
            target = target,
            onDismiss = { reportTarget = null }
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
                            HorizontalDivider()
                            DropdownMenuItem(
                                text = { Text("Report user") },
                                leadingIcon = { Icon(Icons.Default.Flag, contentDescription = null) },
                                onClick = {
                                    menuOpen = false
                                    reportTarget = ReportTarget("user", conversation.otherUserId(currentUserId), "Report $otherName")
                                }
                            )
                            DropdownMenuItem(
                                text = { Text(if (iBlocked) "Unblock user" else "Block user") },
                                leadingIcon = { Icon(Icons.Default.Block, contentDescription = null) },
                                onClick = {
                                    menuOpen = false
                                    if (iBlocked) setBlocked(false) else showBlockConfirm = true
                                }
                            )
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
                if (iBlocked) {
                    Surface(color = MaterialTheme.colorScheme.errorContainer, modifier = Modifier.fillMaxWidth()) {
                        Row(
                            modifier = Modifier.padding(horizontal = 16.dp),
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            Icon(Icons.Default.Block, contentDescription = null, modifier = Modifier.size(16.dp))
                            Spacer(modifier = Modifier.width(8.dp))
                            Text(
                                text = "You blocked this user",
                                style = MaterialTheme.typography.labelLarge,
                                color = MaterialTheme.colorScheme.onErrorContainer,
                                modifier = Modifier.weight(1f)
                            )
                            TextButton(onClick = { setBlocked(false) }) { Text("Unblock") }
                        }
                    }
                }
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
                    if (messages.isEmpty() && !iBlocked) {
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
                        val isMe = msg.senderId == currentUserId
                        MessageBubble(
                            message = msg,
                            isMe = isMe,
                            onLongPress = if (isMe) null else {
                                { reportTarget = ReportTarget("message", msg.id, "Report message") }
                            }
                        )
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
                        if (sendError != null && sendError != CANT_MESSAGE_USER) {
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
                        placeholder = { Text(if (iBlocked) "You blocked this user" else "Type a message...") },
                        enabled = !iBlocked,
                        modifier = Modifier.weight(1f),
                        maxLines = 4
                    )
                    Spacer(modifier = Modifier.width(8.dp))
                    if (isSending) {
                        CircularProgressIndicator(modifier = Modifier.size(24.dp).padding(2.dp))
                    } else {
                        IconButton(onClick = { send(input) }, enabled = input.isNotBlank() && !iBlocked) {
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
                listing?.priceLabel?.ifBlank { null }?.let {
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

/** A chat bubble. Long-pressing someone else's message reports it. */
@OptIn(ExperimentalFoundationApi::class)
@Composable
private fun MessageBubble(message: Message, isMe: Boolean, onLongPress: (() -> Unit)?) {
    val shape = RoundedCornerShape(12.dp)
    Box(
        modifier = Modifier.fillMaxWidth(),
        contentAlignment = if (isMe) Alignment.CenterEnd else Alignment.CenterStart
    ) {
        Surface(
            color = if (isMe) MaterialTheme.colorScheme.primaryContainer else MaterialTheme.colorScheme.secondaryContainer,
            shape = shape,
            modifier = Modifier
                .widthIn(max = 280.dp)
                .clip(shape)
                .then(
                    if (onLongPress != null) {
                        Modifier.combinedClickable(
                            onClick = {},
                            onLongClick = onLongPress,
                            onLongClickLabel = "Report message"
                        )
                    } else {
                        Modifier
                    }
                )
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
