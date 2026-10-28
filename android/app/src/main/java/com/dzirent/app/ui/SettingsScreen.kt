package com.dzirent.app.ui

import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.automirrored.filled.ExitToApp
import androidx.compose.material.icons.filled.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.unit.dp
import com.dzirent.app.data.model.Profile
import com.dzirent.app.data.repository.DzirentRepository
import kotlinx.coroutines.launch

/** Sidebar > Settings: location, blocked users, policies and sign out. */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun SettingsScreen(
    repository: DzirentRepository,
    profile: Profile,
    onBack: () -> Unit,
    onSetLocation: () -> Unit,
    onOpenBlockedUsers: () -> Unit,
    onOpenCommunityPolicy: () -> Unit,
    onOpenHowItWorks: () -> Unit,
    onAppTour: () -> Unit,
    onSignOut: () -> Unit
) {
    val scope = rememberCoroutineScope()
    var isSigningOut by remember { mutableStateOf(false) }
    var error by remember { mutableStateOf<String?>(null) }

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
            SettingsRow(
                title = "Update location",
                subtitle = if (profile.hasLocation) "Near ${profile.city.ifBlank { "your area" }} (about 1 km)" else "Not set yet",
                icon = Icons.Default.LocationOn,
                onClick = onSetLocation
            )
            SettingsRow("Blocked users", null, Icons.Default.Block, onOpenBlockedUsers)
            SettingsRow(DzirentPolicy.COMMUNITY_TITLE, null, Icons.Default.Shield, onOpenCommunityPolicy)
            SettingsRow(DzirentPolicy.TITLE, null, Icons.Default.Info, onOpenHowItWorks)
            SettingsRow("App tour", "A quick look at where things are", Icons.Default.Explore, onAppTour)

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
        }
    }
}

@Composable
private fun SettingsRow(title: String, subtitle: String?, icon: ImageVector, onClick: () -> Unit) {
    ListItem(
        headlineContent = { Text(title) },
        supportingContent = subtitle?.let { { Text(it) } },
        leadingContent = { Icon(icon, contentDescription = null) },
        trailingContent = { Icon(Icons.Default.ChevronRight, contentDescription = null) },
        modifier = Modifier.clickable(onClick = onClick)
    )
    HorizontalDivider(thickness = 0.5.dp)
}
