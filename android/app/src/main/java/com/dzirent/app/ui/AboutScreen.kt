package com.dzirent.app.ui

import android.net.Uri
import android.widget.Toast
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.automirrored.filled.OpenInNew
import androidx.compose.material.icons.filled.*
import androidx.compose.material3.*
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalUriHandler
import com.dzirent.app.AppLinks
import com.dzirent.app.BuildConfig
import com.mikepenz.aboutlibraries.ui.compose.m3.LibrariesContainer

/**
 * Settings > About Dzirent: the policies, terms and privacy links, contact, the app's
 * version and build number, and open-source licenses.
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun AboutScreen(
    onBack: () -> Unit,
    onOpenHowItWorks: () -> Unit,
    onOpenCommunityPolicy: () -> Unit,
    onOpenLicenses: () -> Unit
) {
    val context = LocalContext.current
    val uriHandler = LocalUriHandler.current

    /** Opens a web or mailto link; says so if nothing on the phone can open it. */
    fun open(uri: String, failure: String) {
        try {
            uriHandler.openUri(uri)
        } catch (e: Exception) {
            Toast.makeText(context, failure, Toast.LENGTH_LONG).show()
        }
    }

    Column(modifier = Modifier.fillMaxSize()) {
        TopAppBar(
            title = { Text("About Dzirent") },
            navigationIcon = {
                IconButton(onClick = onBack) {
                    Icon(Icons.AutoMirrored.Filled.ArrowBack, contentDescription = "Back")
                }
            }
        )
        Column(modifier = Modifier.verticalScroll(rememberScrollState())) {
            SettingsRow(DzirentPolicy.TITLE, null, Icons.Default.Info, onClick = onOpenHowItWorks)
            SettingsRow(DzirentPolicy.COMMUNITY_TITLE, null, Icons.Default.Shield, onClick = onOpenCommunityPolicy)
            LinkRow("Terms of Service", Icons.Default.Description) {
                open(AppLinks.TERMS_URL, "Couldn't open the link. Visit ${AppLinks.TERMS_URL}")
            }
            LinkRow("Privacy Policy", Icons.Default.PrivacyTip) {
                open(AppLinks.PRIVACY_URL, "Couldn't open the link. Visit ${AppLinks.PRIVACY_URL}")
            }
            SettingsRow("Contact us", AppLinks.SUPPORT_EMAIL, Icons.Default.Email) {
                open(
                    "mailto:${AppLinks.SUPPORT_EMAIL}?subject=${Uri.encode("Dzirent")}",
                    "No email app found. Write to ${AppLinks.SUPPORT_EMAIL}"
                )
            }
            SettingsRow(
                title = "App version",
                subtitle = "${BuildConfig.VERSION_NAME} (build ${BuildConfig.VERSION_CODE})",
                icon = Icons.Default.Smartphone,
                onClick = null
            )
            SettingsRow("Open-source licenses", null, Icons.Default.Code, onClick = onOpenLicenses)
        }
    }
}

/** A row that leaves the app (opens the browser), so it shows that instead of a chevron. */
@Composable
private fun LinkRow(title: String, icon: ImageVector, onClick: () -> Unit) {
    ListItem(
        headlineContent = { Text(title) },
        leadingContent = { Icon(icon, contentDescription = null) },
        trailingContent = { Icon(Icons.AutoMirrored.Filled.OpenInNew, contentDescription = "Opens in your browser") },
        modifier = Modifier.clickable(onClick = onClick)
    )
}

/** The libraries in the app and their licenses, collected at build time. */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun LicensesScreen(onBack: () -> Unit) {
    Column(modifier = Modifier.fillMaxSize()) {
        TopAppBar(
            title = { Text("Open-source licenses") },
            navigationIcon = {
                IconButton(onClick = onBack) {
                    Icon(Icons.AutoMirrored.Filled.ArrowBack, contentDescription = "Back")
                }
            }
        )
        LibrariesContainer(modifier = Modifier.fillMaxSize())
    }
}
