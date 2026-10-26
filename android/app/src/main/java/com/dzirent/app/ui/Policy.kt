package com.dzirent.app.ui

import android.content.Context
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.Info
import androidx.compose.material.icons.filled.Shield
import androidx.compose.material3.*
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp

/** Single source of truth for Dzirent's "we only connect people" policy wording. */
object DzirentPolicy {
    const val TITLE = "How Dzirent works"

    const val FULL_TEXT =
        "Dzirent connects people who want to rent clothes. We don't handle payments, hold deposits, " +
            "verify items or guarantee rentals. Agree on price, dates, condition and any deposit in chat " +
            "before you meet. Meet in a public place and never send money before you see the item. " +
            "For valuable pieces, we recommend lenders ask for a refundable deposit."

    const val PROHIBITED_TITLE = "Not allowed on Dzirent"

    const val PROHIBITED_ITEMS =
        "Smartwatches, caps, hats (including cowboy hats), boots, shoes, socks, and pantyhose or other hosiery. " +
            "Use \"Report listing\" with the reason \"Prohibited item\" if you see one."

    /** One line under the Accessories item type on the listing form. */
    const val ACCESSORIES_NOT_ALLOWED = "Not allowed: smartwatches, hats, caps, footwear, socks, hosiery."

    /** Shown above "Message Lender". */
    const val LISTING_NOTICE =
        "Dzirent only connects people. Agree on price, dates, condition and any deposit in chat before you meet."

    /** Shown once per conversation in chat. */
    const val CHAT_SAFETY_TIP =
        "Safety tip: meet in a public place and never send money before you see the item."

    const val SIGN_UP_CONSENT = "I understand Dzirent only connects people"

    // Retail value at or above which Create/Edit Listing suggests a refundable deposit.
    // Placeholder: the owner will confirm the final number.
    const val DEPOSIT_SUGGESTION_USD = 150.0

    const val DEPOSIT_TIP = "This is a valuable piece. Consider asking renters for a refundable deposit."
}

@Composable
private fun PolicyBody() {
    Column {
        Text(
            text = DzirentPolicy.FULL_TEXT,
            style = MaterialTheme.typography.bodyLarge
        )
        Spacer(modifier = Modifier.height(16.dp))
        Text(
            text = DzirentPolicy.PROHIBITED_TITLE,
            style = MaterialTheme.typography.titleMedium,
            fontWeight = FontWeight.Bold
        )
        Spacer(modifier = Modifier.height(4.dp))
        Text(
            text = DzirentPolicy.PROHIBITED_ITEMS,
            style = MaterialTheme.typography.bodyLarge
        )
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun HowDzirentWorksScreen(onBack: () -> Unit) {
    Column(modifier = Modifier.fillMaxSize()) {
        TopAppBar(
            title = { Text(DzirentPolicy.TITLE) },
            navigationIcon = {
                IconButton(onClick = onBack) {
                    Icon(Icons.AutoMirrored.Filled.ArrowBack, contentDescription = "Back")
                }
            }
        )
        Column(
            modifier = Modifier
                .fillMaxSize()
                .verticalScroll(rememberScrollState())
                .padding(24.dp)
        ) {
            Icon(
                imageVector = Icons.Default.Shield,
                contentDescription = null,
                tint = MaterialTheme.colorScheme.primary,
                modifier = Modifier.size(48.dp)
            )
            Spacer(modifier = Modifier.height(16.dp))
            PolicyBody()
        }
    }
}

@Composable
fun HowDzirentWorksDialog(onDismiss: () -> Unit) {
    AlertDialog(
        onDismissRequest = onDismiss,
        icon = { Icon(Icons.Default.Shield, contentDescription = null) },
        title = { Text(DzirentPolicy.TITLE) },
        text = { PolicyBody() },
        confirmButton = {
            TextButton(onClick = onDismiss) { Text("Got it") }
        }
    )
}

/** Small tinted card for inline policy notices and tips. */
@Composable
fun PolicyNotice(
    text: String,
    modifier: Modifier = Modifier,
    onDismiss: (() -> Unit)? = null
) {
    Card(
        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceVariant),
        modifier = modifier.fillMaxWidth()
    ) {
        Row(
            modifier = Modifier.padding(start = 12.dp, top = 12.dp, bottom = 12.dp, end = if (onDismiss == null) 12.dp else 0.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            Icon(
                imageVector = Icons.Default.Info,
                contentDescription = null,
                tint = MaterialTheme.colorScheme.primary,
                modifier = Modifier.size(20.dp)
            )
            Spacer(modifier = Modifier.width(12.dp))
            Text(
                text = text,
                style = MaterialTheme.typography.bodySmall,
                fontWeight = FontWeight.Medium,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                modifier = Modifier.weight(1f)
            )
            if (onDismiss != null) {
                IconButton(onClick = onDismiss) {
                    Icon(Icons.Default.Close, contentDescription = "Dismiss")
                }
            }
        }
    }
}

/** Remembers, per conversation, that the chat safety tip was dismissed. */
class SafetyTipStore(context: Context) {
    private val prefs = context.applicationContext.getSharedPreferences("dzirent_prefs", Context.MODE_PRIVATE)

    fun isDismissed(conversationId: String): Boolean =
        prefs.getBoolean("safety_tip_dismissed_$conversationId", false)

    fun dismiss(conversationId: String) {
        prefs.edit().putBoolean("safety_tip_dismissed_$conversationId", true).apply()
    }
}
