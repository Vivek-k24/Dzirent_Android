package com.dzirent.app.ui

import android.content.Context
import android.content.Intent
import android.net.Uri
import android.provider.Settings
import androidx.compose.foundation.layout.Row
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.NoPhotography
import androidx.compose.material.icons.filled.PhotoCamera
import androidx.compose.material.icons.filled.PhotoLibrary
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Icon
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable

/** Which photo source a consent question is about. */
enum class PhotoSource { Camera, Gallery }

/**
 * Remembers on this device that the user tapped Continue on the camera or photos
 * explanation, so it is shown only once. "Not now" is not stored: the next tap asks again.
 */
class PhotoConsentStore(context: Context) {
    private val prefs = context.applicationContext.getSharedPreferences("dzirent_prefs", Context.MODE_PRIVATE)

    fun hasConsent(source: PhotoSource): Boolean = prefs.getBoolean(key(source), false)

    fun giveConsent(source: PhotoSource) {
        prefs.edit().putBoolean(key(source), true).apply()
    }

    private fun key(source: PhotoSource) = when (source) {
        PhotoSource.Camera -> "photo_consent_camera"
        PhotoSource.Gallery -> "photo_consent_gallery"
    }
}

/** Shown before the camera or Photo Picker opens for the first time (same style as location). */
@Composable
fun PhotoConsentDialog(source: PhotoSource, onContinue: () -> Unit, onDismiss: () -> Unit) {
    val camera = source == PhotoSource.Camera
    AlertDialog(
        onDismissRequest = onDismiss,
        icon = { Icon(if (camera) Icons.Default.PhotoCamera else Icons.Default.PhotoLibrary, contentDescription = null) },
        title = { Text(if (camera) "Use your camera" else "Use your photos") },
        text = {
            Text(
                if (camera) "Dzirent uses your camera only to add pictures to your listing."
                else "Dzirent uses your photos only to add pictures to your listing."
            )
        },
        confirmButton = { TextButton(onClick = onContinue) { Text("Continue") } },
        dismissButton = { TextButton(onClick = onDismiss) { Text("Not now") } }
    )
}

/** Camera permission was refused: explain how to turn it on, or use the gallery instead. */
@Composable
fun CameraDeniedDialog(context: Context, onUseGallery: () -> Unit, onDismiss: () -> Unit) {
    AlertDialog(
        onDismissRequest = onDismiss,
        icon = { Icon(Icons.Default.NoPhotography, contentDescription = null) },
        title = { Text("Camera access is off") },
        text = {
            Text(
                "To take photos in Dzirent, open Settings > Apps > Dzirent > Permissions and allow Camera. " +
                    "You can also add photos from your gallery."
            )
        },
        confirmButton = { TextButton(onClick = onUseGallery) { Text("Use gallery") } },
        dismissButton = {
            Row {
                TextButton(onClick = {
                    onDismiss()
                    openAppSettings(context)
                }) { Text("Open Settings") }
                TextButton(onClick = onDismiss) { Text("Not now") }
            }
        }
    )
}

private fun openAppSettings(context: Context) {
    val intent = Intent(Settings.ACTION_APPLICATION_DETAILS_SETTINGS, Uri.fromParts("package", context.packageName, null))
        .addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
    runCatching { context.startActivity(intent) }
}
