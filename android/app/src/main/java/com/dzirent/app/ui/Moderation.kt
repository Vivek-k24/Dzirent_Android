package com.dzirent.app.ui

import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.selection.selectable
import androidx.compose.foundation.selection.selectableGroup
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.Block
import androidx.compose.material.icons.filled.CheckCircle
import androidx.compose.material.icons.filled.Flag
import androidx.compose.material.icons.filled.Person
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.unit.dp
import androidx.compose.ui.window.DialogProperties
import com.dzirent.app.data.model.Block
import com.dzirent.app.data.model.Report
import com.dzirent.app.data.model.ReportReasons
import com.dzirent.app.data.repository.DzirentRepository
import kotlinx.coroutines.launch

/** What is being reported; [targetType] is 'listing' | 'user' | 'message'. */
data class ReportTarget(val targetType: String, val targetId: String, val title: String)

/** Reason picker with optional details. Inserts into reports, then thanks the user. */
@Composable
fun ReportDialog(
    repository: DzirentRepository,
    reporterId: String,
    target: ReportTarget,
    onDismiss: () -> Unit
) {
    val scope = rememberCoroutineScope()
    var reason by remember { mutableStateOf<String?>(null) }
    var details by remember { mutableStateOf("") }
    var isSending by remember { mutableStateOf(false) }
    var error by remember { mutableStateOf<String?>(null) }
    var sent by remember { mutableStateOf(false) }

    if (sent) {
        AlertDialog(
            onDismissRequest = onDismiss,
            icon = { Icon(Icons.Default.CheckCircle, contentDescription = null) },
            title = { Text("Report sent") },
            text = { Text("Thanks. We'll review this.") },
            confirmButton = { TextButton(onClick = onDismiss) { Text("OK") } }
        )
        return
    }

    AlertDialog(
        onDismissRequest = { if (!isSending) onDismiss() },
        properties = DialogProperties(dismissOnClickOutside = !isSending),
        icon = { Icon(Icons.Default.Flag, contentDescription = null) },
        title = { Text(target.title) },
        text = {
            Column(modifier = Modifier.verticalScroll(rememberScrollState())) {
                Text("Why are you reporting this?", style = MaterialTheme.typography.bodyMedium)
                Spacer(modifier = Modifier.height(8.dp))
                Column(modifier = Modifier.selectableGroup()) {
                    ReportReasons.ALL.forEach { option ->
                        Row(
                            verticalAlignment = Alignment.CenterVertically,
                            modifier = Modifier
                                .fillMaxWidth()
                                .selectable(
                                    selected = reason == option,
                                    onClick = { reason = option; error = null },
                                    role = Role.RadioButton,
                                    enabled = !isSending
                                )
                                .padding(vertical = 4.dp)
                        ) {
                            RadioButton(selected = reason == option, onClick = null)
                            Spacer(modifier = Modifier.width(8.dp))
                            Text(option)
                        }
                    }
                }
                Spacer(modifier = Modifier.height(8.dp))
                OutlinedTextField(
                    value = details,
                    onValueChange = { if (it.length <= ReportReasons.DETAILS_MAX) details = it },
                    label = { Text("Details (optional)") },
                    supportingText = { Text("${details.length}/${ReportReasons.DETAILS_MAX}") },
                    enabled = !isSending,
                    minLines = 2,
                    maxLines = 5,
                    modifier = Modifier.fillMaxWidth()
                )
                error?.let {
                    Text(text = it, color = MaterialTheme.colorScheme.error, style = MaterialTheme.typography.bodySmall)
                }
            }
        },
        confirmButton = {
            TextButton(
                onClick = {
                    val picked = reason
                    if (picked == null) {
                        error = "Pick a reason."
                        return@TextButton
                    }
                    scope.launch {
                        isSending = true
                        error = null
                        try {
                            repository.report(
                                Report(
                                    reporterId = reporterId,
                                    targetType = target.targetType,
                                    targetId = target.targetId,
                                    reason = picked,
                                    details = details.trim().ifBlank { null }
                                )
                            )
                            sent = true
                        } catch (e: Exception) {
                            error = e.userMessage("Couldn't send your report. Please try again.")
                        }
                        isSending = false
                    }
                },
                enabled = !isSending
            ) { Text(if (isSending) "Sending..." else "Send report") }
        },
        dismissButton = {
            TextButton(onClick = onDismiss, enabled = !isSending) { Text("Cancel") }
        }
    )
}

/** Confirmation before blocking someone from a chat. */
@Composable
fun BlockUserDialog(name: String, onConfirm: () -> Unit, onDismiss: () -> Unit) {
    AlertDialog(
        onDismissRequest = onDismiss,
        icon = { Icon(Icons.Default.Block, contentDescription = null) },
        title = { Text("Block $name?") },
        text = {
            Text(
                "You won't be able to message each other, and you won't see each other's listings. " +
                    "You can unblock them any time from Profile > Blocked users."
            )
        },
        confirmButton = {
            TextButton(onClick = onConfirm) { Text("Block", color = MaterialTheme.colorScheme.error) }
        },
        dismissButton = { TextButton(onClick = onDismiss) { Text("Cancel") } }
    )
}

// --- BLOCKED USERS SCREEN ---
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun BlockedUsersScreen(
    repository: DzirentRepository,
    currentUserId: String,
    onBack: () -> Unit
) {
    var uiState by remember { mutableStateOf<UiState<List<Block>>>(UiState.Loading) }
    var busyIds by remember { mutableStateOf(emptySet<String>()) }
    var actionError by remember { mutableStateOf<String?>(null) }
    val scope = rememberCoroutineScope()

    fun load() {
        scope.launch {
            uiState = UiState.Loading
            uiState = try {
                UiState.Success(repository.getBlockedUsers(currentUserId))
            } catch (e: Exception) {
                UiState.Error(e.userMessage("Couldn't load your blocked users. Please try again."))
            }
        }
    }

    fun unblock(block: Block) {
        scope.launch {
            busyIds = busyIds + block.blockedId
            actionError = null
            try {
                repository.unblockUser(currentUserId, block.blockedId)
                val current = (uiState as? UiState.Success)?.data.orEmpty()
                uiState = UiState.Success(current.filterNot { it.blockedId == block.blockedId })
            } catch (e: Exception) {
                actionError = e.userMessage("Couldn't unblock this user. Please try again.")
            }
            busyIds = busyIds - block.blockedId
        }
    }

    LaunchedEffect(currentUserId) { load() }

    Column(modifier = Modifier.fillMaxSize()) {
        TopAppBar(
            title = { Text("Blocked users") },
            navigationIcon = {
                IconButton(onClick = onBack) {
                    Icon(Icons.AutoMirrored.Filled.ArrowBack, contentDescription = "Back")
                }
            }
        )
        when (val state = uiState) {
            is UiState.Loading -> LoadingView(message = "Loading blocked users...")
            is UiState.Error -> ErrorView(message = state.message, onRetry = { load() })
            is UiState.Success -> {
                if (state.data.isEmpty()) {
                    EmptyView(
                        title = "No blocked users",
                        description = "People you block from a chat show up here, and you can unblock them any time.",
                        icon = Icons.Default.Block
                    )
                } else {
                    LazyColumn(modifier = Modifier.fillMaxSize()) {
                        actionError?.let { message ->
                            item {
                                Text(
                                    text = message,
                                    color = MaterialTheme.colorScheme.error,
                                    style = MaterialTheme.typography.bodySmall,
                                    modifier = Modifier.padding(16.dp)
                                )
                            }
                        }
                        items(state.data, key = { it.blockedId }) { block ->
                            ListItem(
                                headlineContent = { Text(block.blocked?.displayName?.ifBlank { null } ?: "Dzirent member") },
                                supportingContent = block.blocked?.city?.ifBlank { null }?.let { city -> { Text(city) } },
                                leadingContent = { Icon(Icons.Default.Person, contentDescription = null) },
                                trailingContent = {
                                    OutlinedButton(
                                        onClick = { unblock(block) },
                                        enabled = block.blockedId !in busyIds
                                    ) { Text("Unblock") }
                                }
                            )
                            HorizontalDivider()
                        }
                    }
                }
            }
        }
    }
}
