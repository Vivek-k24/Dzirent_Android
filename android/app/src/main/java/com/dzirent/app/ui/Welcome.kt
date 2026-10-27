package com.dzirent.app.ui

import android.content.Context
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.CheckCircle
import androidx.compose.material.icons.filled.ChevronRight
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.RadioButtonUnchecked
import androidx.compose.material3.*
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.semantics.stateDescription
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.window.Dialog
import androidx.compose.ui.window.DialogProperties
import com.dzirent.app.ui.theme.DzirentAccent
import com.dzirent.app.ui.theme.OnDzirentAccent
import com.dzirent.app.util.SetupItem
import com.dzirent.app.util.SetupStatus

/** First-run flags on this device, per user id (no database change). */
class OnboardingStore(context: Context, private val userId: String) {
    private val prefs = context.applicationContext.getSharedPreferences("dzirent_prefs", Context.MODE_PRIVATE)

    val welcomeSeen: Boolean get() = prefs.getBoolean("welcome_seen_$userId", false)

    fun markWelcomeSeen() {
        prefs.edit().putBoolean("welcome_seen_$userId", true).apply()
    }

    val setupCardDismissed: Boolean get() = prefs.getBoolean("setup_card_dismissed_$userId", false)

    fun dismissSetupCard() {
        prefs.edit().putBoolean("setup_card_dismissed_$userId", true).apply()
    }
}

/**
 * "Welcome to Dzirent": a checklist whose rows open the right screen in one tap, then
 * "Show me around" (the guide) or "I'll do it later" (same as the X).
 * [status] is null while the ticks are still loading.
 */
@Composable
fun WelcomeDialog(
    items: List<SetupItem>,
    status: SetupStatus?,
    onItem: (SetupItem) -> Unit,
    onShowMeAround: () -> Unit,
    onLater: () -> Unit
) {
    Dialog(onDismissRequest = onLater, properties = DialogProperties(usePlatformDefaultWidth = false)) {
        // surfaceBright: white in light mode, a lighter grey than the app in dark mode.
        Surface(
            shape = RoundedCornerShape(24.dp),
            color = MaterialTheme.colorScheme.surfaceBright,
            modifier = Modifier
                .systemBarsPadding()
                .padding(horizontal = 16.dp, vertical = 24.dp)
                .widthIn(max = 420.dp)
                .fillMaxWidth()
        ) {
            // Scrolls so nothing is cut off on small phones with the largest font.
            Column(modifier = Modifier.verticalScroll(rememberScrollState())) {
                Box(modifier = Modifier.fillMaxWidth()) {
                    IconButton(onClick = onLater, modifier = Modifier.align(Alignment.TopEnd).padding(4.dp)) {
                        Icon(Icons.Default.Close, contentDescription = "Close")
                    }
                }
                Column(modifier = Modifier.padding(start = 24.dp, end = 24.dp, bottom = 24.dp)) {
                    Text(
                        text = "Welcome to Dzirent",
                        style = MaterialTheme.typography.headlineSmall,
                        fontWeight = FontWeight.Bold
                    )
                    Spacer(modifier = Modifier.height(4.dp))
                    Text(
                        text = "Finish setting up your account",
                        style = MaterialTheme.typography.bodyLarge,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                    Spacer(modifier = Modifier.height(16.dp))
                    items.forEach { item ->
                        ChecklistRow(item = item, done = status?.isDone(item) == true, onClick = { onItem(item) })
                    }
                    Spacer(modifier = Modifier.height(20.dp))
                    Button(
                        onClick = onShowMeAround,
                        colors = ButtonDefaults.buttonColors(containerColor = DzirentAccent, contentColor = OnDzirentAccent),
                        modifier = Modifier
                            .fillMaxWidth()
                            .heightIn(min = 50.dp)
                    ) {
                        Text("Show me around", textAlign = TextAlign.Center)
                    }
                    TextButton(
                        onClick = onLater,
                        modifier = Modifier
                            .fillMaxWidth()
                            .heightIn(min = 48.dp)
                    ) {
                        Text("I'll do it later", textAlign = TextAlign.Center)
                    }
                }
            }
        }
    }
}

@Composable
private fun ChecklistRow(item: SetupItem, done: Boolean, onClick: () -> Unit) {
    Row(
        verticalAlignment = Alignment.CenterVertically,
        modifier = Modifier
            .fillMaxWidth()
            .clickable(role = Role.Button, onClick = onClick)
            .semantics { stateDescription = if (done) "Done" else "Not done" }
            .padding(vertical = 10.dp)
    ) {
        Icon(
            imageVector = if (done) Icons.Default.CheckCircle else Icons.Default.RadioButtonUnchecked,
            contentDescription = null,
            tint = if (done) MaterialTheme.colorScheme.onSurface else MaterialTheme.colorScheme.outline
        )
        Spacer(modifier = Modifier.width(12.dp))
        Text(
            text = item.title,
            style = MaterialTheme.typography.bodyLarge,
            color = if (done) MaterialTheme.colorScheme.onSurfaceVariant else MaterialTheme.colorScheme.onSurface,
            modifier = Modifier.weight(1f)
        )
        Icon(Icons.Default.ChevronRight, contentDescription = null, tint = MaterialTheme.colorScheme.outline)
    }
}

/** Small card at the top of Profile until location and measurements are done. */
@Composable
fun FinishSetupCard(onOpen: () -> Unit, onDismiss: () -> Unit) {
    Surface(
        onClick = onOpen,
        shape = RoundedCornerShape(12.dp),
        color = MaterialTheme.colorScheme.surfaceVariant,
        modifier = Modifier.fillMaxWidth()
    ) {
        Row(
            verticalAlignment = Alignment.CenterVertically,
            modifier = Modifier.padding(start = 16.dp, top = 4.dp, bottom = 4.dp)
        ) {
            Column(modifier = Modifier.weight(1f).padding(vertical = 8.dp)) {
                Text("Finish setting up", style = MaterialTheme.typography.titleSmall, fontWeight = FontWeight.Bold)
                Text(
                    text = "Add your location and measurements.",
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
            }
            IconButton(onClick = onDismiss) {
                Icon(Icons.Default.Close, contentDescription = "Dismiss")
            }
        }
    }
}
