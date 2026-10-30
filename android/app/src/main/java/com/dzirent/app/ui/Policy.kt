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
import com.dzirent.app.data.model.AccessoryTypes
import com.dzirent.app.data.model.Categories
import com.dzirent.app.util.ItemLabels
import com.dzirent.app.util.joinWithAnd

/** Single source of truth for Dzirent's "we only connect people" policy wording. */
object DzirentPolicy {
    const val TITLE = "How Dzirent works"

    const val FULL_TEXT =
        "Dzirent connects people who want to rent clothes. We don't handle payments, hold deposits, " +
            "verify items or guarantee rentals. Agree on price, dates, condition and any deposit in chat " +
            "before you meet. Meet in a public place and never send money before you see the item. " +
            "For valuable pieces, we recommend lenders ask for a refundable deposit."

    const val COMMUNITY_TITLE = "Community policy"

    // Placeholder wording: the owner will confirm the final rules.
    const val COMMUNITY_RULES =
        "Be honest and respectful. List only pieces you own, show them as they are, and keep " +
            "chats friendly. Harassment, scams and fake listings aren't allowed. Use Report on a " +
            "listing, a person or a message, and Block to stop someone contacting you."

    const val PROHIBITED_TITLE = "What can't be listed"

    /**
     * What Dzirent is for, then the "Not allowed" and "Not offered" lists, built from the same
     * labels the photo checks use (ItemLabels), so the policy, the "What can I list?" sheet and
     * the automatic removals never drift apart.
     */
    val ITEM_RULES: String = ItemLabels.SCOPE + "\n" + ItemLabels.notAllowedText + "\n" + ItemLabels.notOfferedText

    const val WHAT_CAN_I_LIST_TITLE = "What can I list?"

    /** "You can list clothing in …, and these accessories: …" for the "What can I list?" sheet. */
    val CAN_LIST: String = "You can list clothing in " +
        joinWithAnd(Categories.ALL.filter { it != Categories.ACCESSORIES }) +
        ", and these accessories: " + AccessoryTypes.ALL.joinToString(", ") { it.lowercase() } + "."

    /** How jewelry works, from the same constants as the declaration. */
    val JEWELRY_NOTE: String =
        "Costume jewelry is welcome. The first time you list jewelry or an accessory, you sign a short declaration " +
            "that it isn't ${ItemLabels.JEWELRY_GROUP.text} and is worth less than \$${ItemLabels.ACCESSORY_MAX_VALUE_USD}."

    /** Why a photo can disappear from the listing form. */
    const val REMOVAL_NOTE =
        "Photos that look like something Dzirent doesn't list are removed from the form right away. If we got it " +
            "wrong, tap \"It's fashion, keep it\" and someone from Dzirent will check the listing later."

    val PROHIBITED_ITEMS: String =
        ITEM_RULES + "\nUse \"Report listing\" with the reason \"Prohibited item\" if you see one."

    /** Shown above "Message Lender". */
    const val LISTING_NOTICE =
        "Dzirent only connects people. Agree on price, dates, condition and any deposit in chat before you meet."

    /** Shown once per conversation in chat. */
    const val CHAT_SAFETY_TIP =
        "Safety tip: meet in a public place and never send money before you see the item. " +
            "We recommend washing anything worn against the skin before wearing."

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

@Composable
fun HowDzirentWorksScreen(onBack: () -> Unit) {
    PolicyScreen(title = DzirentPolicy.TITLE, onBack = onBack) {
        Text(text = DzirentPolicy.FULL_TEXT, style = MaterialTheme.typography.bodyLarge)
    }
}

@Composable
fun CommunityPolicyScreen(onBack: () -> Unit) {
    PolicyScreen(title = DzirentPolicy.COMMUNITY_TITLE, onBack = onBack) {
        Text(text = DzirentPolicy.COMMUNITY_RULES, style = MaterialTheme.typography.bodyLarge)
        Spacer(modifier = Modifier.height(16.dp))
        Text(
            text = DzirentPolicy.PROHIBITED_TITLE,
            style = MaterialTheme.typography.titleMedium,
            fontWeight = FontWeight.Bold
        )
        Spacer(modifier = Modifier.height(4.dp))
        Text(text = DzirentPolicy.PROHIBITED_ITEMS, style = MaterialTheme.typography.bodyLarge)
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun PolicyScreen(title: String, onBack: () -> Unit, content: @Composable ColumnScope.() -> Unit) {
    Column(modifier = Modifier.fillMaxSize()) {
        TopAppBar(
            title = { Text(title) },
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
                .padding(24.dp),
            content = content
        )
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

/** "What can I list?": what Dzirent is for, what can and can't be listed, and how jewelry works. */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun WhatCanIListSheet(onDismiss: () -> Unit) {
    ModalBottomSheet(onDismissRequest = onDismiss) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .verticalScroll(rememberScrollState())
                .padding(start = 24.dp, end = 24.dp, bottom = 32.dp)
        ) {
            Text(DzirentPolicy.WHAT_CAN_I_LIST_TITLE, style = MaterialTheme.typography.titleLarge, fontWeight = FontWeight.Bold)
            Spacer(modifier = Modifier.height(12.dp))
            Text(ItemLabels.SCOPE, style = MaterialTheme.typography.bodyLarge, fontWeight = FontWeight.Medium)
            Spacer(modifier = Modifier.height(8.dp))
            Text(DzirentPolicy.CAN_LIST, style = MaterialTheme.typography.bodyMedium)
            Spacer(modifier = Modifier.height(8.dp))
            Text(ItemLabels.notAllowedText, style = MaterialTheme.typography.bodyMedium)
            Spacer(modifier = Modifier.height(8.dp))
            Text(ItemLabels.notOfferedText, style = MaterialTheme.typography.bodyMedium)
            Spacer(modifier = Modifier.height(8.dp))
            Text(DzirentPolicy.JEWELRY_NOTE, style = MaterialTheme.typography.bodyMedium)
            Spacer(modifier = Modifier.height(8.dp))
            Text(
                DzirentPolicy.REMOVAL_NOTE,
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )
        }
    }
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
