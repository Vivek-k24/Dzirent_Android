package com.dzirent.app.ui

import android.widget.Toast
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.automirrored.filled.ExitToApp
import androidx.compose.material.icons.filled.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.input.PasswordVisualTransformation
import androidx.compose.ui.unit.dp
import androidx.compose.ui.window.DialogProperties
import com.dzirent.app.data.SettingsStore
import com.dzirent.app.data.model.Profile
import com.dzirent.app.data.repository.DzirentRepository
import com.dzirent.app.util.DISTANCE_STEPS
import com.dzirent.app.util.DistanceUnit
import com.dzirent.app.util.MeasureUnit
import com.dzirent.app.util.RENTAL_LENGTH_OPTIONS
import com.dzirent.app.util.ThemeMode
import kotlinx.coroutines.launch

/** The shortest password Supabase accepts by default; sign-up asks for the same. */
private const val PASSWORD_MIN = 6

/**
 * Sidebar > Settings, as a grouped list: Appearance, Preferences (saved on the device and
 * applied at once), Location, Privacy & safety, Account, About, then Sign out.
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun SettingsScreen(
    repository: DzirentRepository,
    settingsStore: SettingsStore,
    profile: Profile,
    onBack: () -> Unit,
    onSetLocation: () -> Unit,
    onOpenBlockedUsers: () -> Unit,
    onClearSearchHistory: () -> Unit,
    onAppTour: () -> Unit,
    onOpenAbout: () -> Unit,
    onSignOut: () -> Unit,
    /** After the account is gone and the session is cleared. */
    onAccountDeleted: () -> Unit
) {
    val scope = rememberCoroutineScope()
    val context = LocalContext.current
    val settings = LocalAppSettings.current
    val email = remember { repository.currentEmail() }
    var isSigningOut by remember { mutableStateOf(false) }
    var error by remember { mutableStateOf<String?>(null) }
    var showChangePassword by remember { mutableStateOf(false) }
    var showDeleteAccount by remember { mutableStateOf(false) }

    if (showChangePassword) {
        ChangePasswordDialog(
            repository = repository,
            onDismiss = { showChangePassword = false },
            onChanged = {
                showChangePassword = false
                Toast.makeText(context, "Password changed", Toast.LENGTH_SHORT).show()
            }
        )
    }

    if (showDeleteAccount) {
        DeleteAccountDialog(
            repository = repository,
            userId = profile.id,
            onDismiss = { showDeleteAccount = false },
            onDeleted = {
                showDeleteAccount = false
                Toast.makeText(context, "Your account was deleted", Toast.LENGTH_LONG).show()
                onAccountDeleted()
            }
        )
    }

    Column(modifier = Modifier.fillMaxSize()) {
        TopAppBar(
            title = { Text("Settings") },
            navigationIcon = {
                IconButton(onClick = onBack) {
                    Icon(Icons.AutoMirrored.Filled.ArrowBack, contentDescription = "Back")
                }
            }
        )
        Column(modifier = Modifier.verticalScroll(rememberScrollState())) {
            SettingsGroup("Appearance")
            ChoiceSetting(
                title = "Theme",
                options = ThemeMode.entries,
                selected = settings.theme,
                label = { it.label },
                onSelect = { scope.launch { settingsStore.setTheme(it) } }
            )

            SettingsGroup("Preferences")
            ChoiceSetting(
                title = "Distance",
                options = DistanceUnit.entries,
                selected = settings.distanceUnit,
                label = { it.label },
                onSelect = { scope.launch { settingsStore.setDistanceUnit(it) } }
            )
            ChoiceSetting(
                title = "Measurements",
                options = MeasureUnit.entries,
                selected = settings.measureUnit,
                label = { it.label },
                onSelect = { scope.launch { settingsStore.setMeasureUnit(it) } }
            )
            ChoiceSetting(
                title = "Default rental length",
                subtitle = "Days, for the date chip on Home",
                options = RENTAL_LENGTH_OPTIONS,
                selected = settings.rentalDays,
                label = { it.toString() },
                onSelect = { scope.launch { settingsStore.setRentalDays(it) } }
            )
            ChoiceSetting(
                title = "Default distance",
                options = DISTANCE_STEPS.toList(),
                selected = settings.defaultDistanceStep,
                label = { settings.distanceUnit.optionLabel(it) },
                onSelect = { scope.launch { settingsStore.setDefaultDistanceStep(it) } }
            )

            SettingsGroup("Location")
            SettingsRow(
                title = "Update location",
                subtitle = if (profile.hasLocation) {
                    "Near ${profile.city.ifBlank { "your area" }} (${settings.distanceUnit.locationPrecision})"
                } else {
                    "Not set yet"
                },
                icon = Icons.Default.LocationOn,
                onClick = onSetLocation
            )

            SettingsGroup("Privacy & safety")
            SettingsRow("Blocked users", null, Icons.Default.Block, onClick = onOpenBlockedUsers)
            SettingsRow("Clear search history", null, Icons.Default.History, showChevron = false) {
                onClearSearchHistory()
                Toast.makeText(context, "Search history cleared", Toast.LENGTH_SHORT).show()
            }

            SettingsGroup("Account")
            SettingsRow("Email", email ?: "Not available", Icons.Default.Email, onClick = null)
            SettingsRow("Change password", null, Icons.Default.Lock, onClick = { showChangePassword = true })
            SettingsRow("Delete account", null, Icons.Default.DeleteForever, onClick = { showDeleteAccount = true })

            SettingsGroup("About")
            SettingsRow("App tour", "A quick look at where things are", Icons.Default.Explore, onClick = onAppTour)
            SettingsRow("About Dzirent", "Policies, contact, version and licenses", Icons.Default.Info, onClick = onOpenAbout)

            Spacer(modifier = Modifier.height(32.dp))
            error?.let {
                Text(
                    text = it,
                    color = MaterialTheme.colorScheme.error,
                    style = MaterialTheme.typography.bodySmall,
                    modifier = Modifier.padding(horizontal = 24.dp, vertical = 8.dp)
                )
            }
            OutlinedButton(
                onClick = {
                    scope.launch {
                        isSigningOut = true
                        error = null
                        try {
                            repository.signOut()
                            onSignOut()
                        } catch (e: Exception) {
                            isSigningOut = false
                            error = e.userMessage("Couldn't sign out. Please try again.")
                        }
                    }
                },
                enabled = !isSigningOut,
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(horizontal = 24.dp)
                    .height(50.dp)
            ) {
                Icon(Icons.AutoMirrored.Filled.ExitToApp, contentDescription = null)
                Spacer(modifier = Modifier.width(8.dp))
                Text("Sign out")
            }
            Spacer(modifier = Modifier.height(24.dp))
        }
    }
}

/** A group heading in Settings and About. */
@Composable
fun SettingsGroup(title: String) {
    Text(
        text = title,
        style = MaterialTheme.typography.titleSmall,
        fontWeight = FontWeight.Bold,
        color = MaterialTheme.colorScheme.onSurfaceVariant,
        modifier = Modifier.padding(start = 16.dp, end = 16.dp, top = 24.dp, bottom = 4.dp)
    )
}

/** A tappable row. [onClick] null makes it read-only (no chevron). */
@Composable
fun SettingsRow(
    title: String,
    subtitle: String?,
    icon: ImageVector,
    showChevron: Boolean = true,
    onClick: (() -> Unit)?
) {
    ListItem(
        headlineContent = { Text(title) },
        supportingContent = subtitle?.let { { Text(it) } },
        leadingContent = { Icon(icon, contentDescription = null) },
        trailingContent = if (onClick != null && showChevron) {
            { Icon(Icons.Default.ChevronRight, contentDescription = null) }
        } else {
            null
        },
        modifier = if (onClick != null) Modifier.clickable(onClick = onClick) else Modifier
    )
}

/** A setting picked from a few options with a segmented control; applies on tap. */
@Composable
private fun <T> ChoiceSetting(
    title: String,
    options: List<T>,
    selected: T,
    label: (T) -> String,
    onSelect: (T) -> Unit,
    subtitle: String? = null
) {
    Column(modifier = Modifier.padding(horizontal = 16.dp, vertical = 8.dp)) {
        Text(title, style = MaterialTheme.typography.bodyLarge)
        subtitle?.let {
            Text(it, style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
        }
        Spacer(modifier = Modifier.height(8.dp))
        SingleChoiceSegmentedButtonRow(modifier = Modifier.fillMaxWidth()) {
            options.forEachIndexed { index, option ->
                SegmentedButton(
                    selected = option == selected,
                    onClick = { if (option != selected) onSelect(option) },
                    shape = SegmentedButtonDefaults.itemShape(index = index, count = options.size),
                    // No check mark: with four options it would crowd out the label.
                    icon = {}
                ) {
                    Text(label(option), maxLines = 1)
                }
            }
        }
    }
}

/** Current password plus a new one. Checking the current one proves it's the owner. */
@Composable
private fun ChangePasswordDialog(
    repository: DzirentRepository,
    onDismiss: () -> Unit,
    onChanged: () -> Unit
) {
    val scope = rememberCoroutineScope()
    var current by remember { mutableStateOf("") }
    var new by remember { mutableStateOf("") }
    var isSaving by remember { mutableStateOf(false) }
    var error by remember { mutableStateOf<String?>(null) }

    fun save() {
        error = when {
            current.isBlank() -> "Enter your current password."
            new.trim().length < PASSWORD_MIN -> "Use at least $PASSWORD_MIN characters."
            new.trim() == current.trim() -> "Choose a password that's different from your current one."
            else -> null
        }
        if (error != null) return
        scope.launch {
            isSaving = true
            try {
                repository.changePassword(current.trim(), new.trim())
                onChanged()
            } catch (e: Exception) {
                error = e.userMessage("Couldn't change your password. Please try again.")
            }
            isSaving = false
        }
    }

    AlertDialog(
        onDismissRequest = { if (!isSaving) onDismiss() },
        properties = DialogProperties(dismissOnClickOutside = !isSaving),
        title = { Text("Change password") },
        text = {
            Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                PasswordField("Current password", current) { current = it; error = null }
                PasswordField("New password", new) { new = it; error = null }
                Text(
                    text = error ?: "At least $PASSWORD_MIN characters.",
                    style = MaterialTheme.typography.bodySmall,
                    color = if (error != null) MaterialTheme.colorScheme.error else MaterialTheme.colorScheme.onSurfaceVariant
                )
            }
        },
        confirmButton = {
            TextButton(onClick = { save() }, enabled = !isSaving) { Text(if (isSaving) "Saving..." else "Change") }
        },
        dismissButton = {
            TextButton(onClick = onDismiss, enabled = !isSaving) { Text("Cancel") }
        }
    )
}

/** What a deleted account takes with it, shown before the person confirms. */
private const val DELETE_ACCOUNT_EXPLANATION =
    "This permanently deletes your account and everything in it: your profile and studio, " +
        "your listings and their photos, your chats and messages (for both people), your saved " +
        "pieces and studios, your measurements and your rental history. It can't be undone. " +
        // Placeholder wording for lawyer review (docs/PRIVACY_POLICY_NOTES.md).
        "Accessory declarations you signed are kept as a record, without your account."

private const val DELETE_CONFIRM_WORD = "DELETE"

/**
 * Settings > Account > Delete account: explains what goes, blocks it during an active
 * rental, and asks for DELETE typed out before calling the delete-account function.
 */
@Composable
private fun DeleteAccountDialog(
    repository: DzirentRepository,
    userId: String,
    onDismiss: () -> Unit,
    onDeleted: () -> Unit
) {
    val scope = rememberCoroutineScope()
    var typed by remember { mutableStateOf("") }
    var isDeleting by remember { mutableStateOf(false) }
    var error by remember { mutableStateOf<String?>(null) }
    // Null while checking. The function checks again; this only saves typing DELETE for nothing.
    var activeRental by remember { mutableStateOf<Boolean?>(null) }

    LaunchedEffect(userId) {
        activeRental = runCatching { repository.hasActiveRental(userId) }.getOrDefault(false)
    }

    fun delete() {
        scope.launch {
            isDeleting = true
            error = null
            try {
                repository.deleteAccount()
                // The server session is gone with the user; this clears the one on the device.
                runCatching { repository.signOut() }
                onDeleted()
            } catch (e: Exception) {
                error = e.userMessage("Couldn't delete your account. Please try again.")
                isDeleting = false
            }
        }
    }

    AlertDialog(
        onDismissRequest = { if (!isDeleting) onDismiss() },
        properties = DialogProperties(dismissOnClickOutside = !isDeleting),
        icon = { Icon(Icons.Default.DeleteForever, contentDescription = null) },
        title = { Text("Delete account?") },
        text = {
            Column(
                verticalArrangement = Arrangement.spacedBy(12.dp),
                modifier = Modifier.verticalScroll(rememberScrollState())
            ) {
                when (activeRental) {
                    null -> LinearProgressIndicator(modifier = Modifier.fillMaxWidth())
                    true -> Text(
                        "You have an active rental. In its chat, mark it as returned (or that the deal " +
                            "didn't happen), then you can delete your account."
                    )
                    false -> {
                        Text(DELETE_ACCOUNT_EXPLANATION)
                        OutlinedTextField(
                            value = typed,
                            onValueChange = { typed = it; error = null },
                            label = { Text("Type $DELETE_CONFIRM_WORD to confirm") },
                            singleLine = true,
                            enabled = !isDeleting,
                            modifier = Modifier.fillMaxWidth()
                        )
                    }
                }
                error?.let { Text(it, color = MaterialTheme.colorScheme.error, style = MaterialTheme.typography.bodySmall) }
            }
        },
        confirmButton = {
            if (activeRental == false) {
                TextButton(
                    onClick = { delete() },
                    enabled = typed.trim() == DELETE_CONFIRM_WORD && !isDeleting,
                    colors = ButtonDefaults.textButtonColors(contentColor = MaterialTheme.colorScheme.error)
                ) { Text(if (isDeleting) "Deleting..." else "Delete account") }
            }
        },
        dismissButton = {
            TextButton(onClick = onDismiss, enabled = !isDeleting) {
                Text(if (activeRental == true) "OK" else "Cancel")
            }
        }
    )
}

@Composable
private fun PasswordField(label: String, value: String, onValueChange: (String) -> Unit) {
    OutlinedTextField(
        value = value,
        onValueChange = onValueChange,
        label = { Text(label) },
        singleLine = true,
        visualTransformation = PasswordVisualTransformation(),
        keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Password),
        modifier = Modifier.fillMaxWidth()
    )
}
