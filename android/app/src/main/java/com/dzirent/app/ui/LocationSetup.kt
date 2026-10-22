package com.dzirent.app.ui

import android.Manifest
import android.content.Context
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.text.KeyboardActions
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.LocationOn
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.unit.dp
import androidx.compose.ui.window.DialogProperties
import com.dzirent.app.data.model.Profile
import com.dzirent.app.data.repository.DzirentRepository
import com.dzirent.app.util.ApproxPlace
import com.dzirent.app.util.LocationService
import kotlinx.coroutines.launch

private enum class LocationStep { Reason, Working, TypeCity, Confirm }

/**
 * Sets the user's approximate location. Shows the reason first and asks for the coarse
 * location permission only when "Use my location" is tapped. Falls back to a typed city.
 */
@Composable
fun LocationSetupDialog(
    repository: DzirentRepository,
    profile: Profile,
    onDismiss: () -> Unit,
    onSaved: (Profile) -> Unit
) {
    val context = LocalContext.current
    val scope = rememberCoroutineScope()
    val locationService = remember { LocationService(context) }

    var step by remember { mutableStateOf(LocationStep.Reason) }
    var workingText by remember { mutableStateOf("") }
    var message by remember { mutableStateOf<String?>(null) }
    var found by remember { mutableStateOf<ApproxPlace?>(null) }
    var cityText by remember { mutableStateOf(profile.city) }
    var isSaving by remember { mutableStateOf(false) }

    fun locate() {
        scope.launch {
            message = null
            workingText = "Finding your approximate location..."
            step = LocationStep.Working
            val place = locationService.currentPlace()
            if (place == null) {
                message = "Couldn't get your location. Type your city instead."
                step = LocationStep.TypeCity
            } else {
                found = place
                cityText = place.city.orEmpty()
                step = LocationStep.Confirm
            }
        }
    }

    fun findCity() {
        val name = cityText.trim()
        if (name.isEmpty()) {
            message = "Enter your city name."
            return
        }
        scope.launch {
            message = null
            workingText = "Looking up $name..."
            step = LocationStep.Working
            val place = locationService.placeForCity(name)
            if (place == null) {
                message = "Couldn't find that city. Check the spelling or try a nearby larger city."
                step = LocationStep.TypeCity
            } else {
                found = place
                cityText = place.city ?: name
                step = LocationStep.Confirm
            }
        }
    }

    fun save() {
        val place = found ?: return
        val city = cityText.trim()
        if (city.isEmpty()) {
            message = "Enter your city name."
            return
        }
        scope.launch {
            isSaving = true
            message = null
            try {
                onSaved(repository.updateLocation(profile.id, place.lat, place.lng, city))
            } catch (e: Exception) {
                message = e.userMessage("Couldn't save your location. Please try again.")
            }
            isSaving = false
        }
    }

    val permissionLauncher = rememberLauncherForActivityResult(
        ActivityResultContracts.RequestPermission()
    ) { granted ->
        if (granted) {
            locate()
        } else {
            message = "Location permission is off. Type your city instead."
            step = LocationStep.TypeCity
        }
    }

    AlertDialog(
        onDismissRequest = { if (!isSaving) onDismiss() },
        properties = DialogProperties(dismissOnClickOutside = !isSaving),
        icon = { Icon(Icons.Default.LocationOn, contentDescription = null) },
        title = { Text("Set your location") },
        text = {
            Column {
                when (step) {
                    LocationStep.Reason -> {
                        Text(LOCATION_REASON)
                    }
                    LocationStep.Working -> {
                        Row(verticalAlignment = Alignment.CenterVertically) {
                            CircularProgressIndicator(modifier = Modifier.size(24.dp))
                            Spacer(modifier = Modifier.width(16.dp))
                            Text(workingText)
                        }
                    }
                    LocationStep.TypeCity -> {
                        OutlinedTextField(
                            value = cityText,
                            onValueChange = { cityText = it; message = null },
                            label = { Text("City") },
                            placeholder = { Text("e.g. Austin, TX") },
                            singleLine = true,
                            keyboardOptions = KeyboardOptions(imeAction = ImeAction.Search),
                            keyboardActions = KeyboardActions(onSearch = { findCity() }),
                            modifier = Modifier.fillMaxWidth()
                        )
                    }
                    LocationStep.Confirm -> {
                        Text("We'll save your area, rounded to about 1 km. Never your exact address.")
                        Spacer(modifier = Modifier.height(12.dp))
                        OutlinedTextField(
                            value = cityText,
                            onValueChange = { cityText = it; message = null },
                            label = { Text("City shown on your profile") },
                            singleLine = true,
                            enabled = !isSaving,
                            modifier = Modifier.fillMaxWidth()
                        )
                        if (isSaving) {
                            Spacer(modifier = Modifier.height(8.dp))
                            LinearProgressIndicator(modifier = Modifier.fillMaxWidth())
                        }
                    }
                }
                message?.let {
                    Spacer(modifier = Modifier.height(8.dp))
                    Text(
                        text = it,
                        color = MaterialTheme.colorScheme.error,
                        style = MaterialTheme.typography.bodySmall
                    )
                }
            }
        },
        confirmButton = {
            when (step) {
                LocationStep.Reason -> TextButton(onClick = {
                    if (locationService.hasPermission()) locate()
                    else permissionLauncher.launch(Manifest.permission.ACCESS_COARSE_LOCATION)
                }) { Text("Use my location") }
                LocationStep.TypeCity -> TextButton(onClick = { findCity() }) { Text("Find") }
                LocationStep.Confirm -> TextButton(onClick = { save() }, enabled = !isSaving) { Text("Save") }
                LocationStep.Working -> {}
            }
        },
        dismissButton = {
            Row {
                when (step) {
                    LocationStep.Reason -> TextButton(onClick = {
                        message = null
                        step = LocationStep.TypeCity
                    }) { Text("Type a city") }
                    LocationStep.Confirm -> TextButton(onClick = {
                        message = null
                        step = LocationStep.TypeCity
                    }, enabled = !isSaving) { Text("Change") }
                    else -> {}
                }
                TextButton(onClick = onDismiss, enabled = !isSaving) { Text("Not now") }
            }
        }
    )
}

private const val LOCATION_REASON =
    "Dzirent uses your approximate location (about 1 km) to show clothes near you."

/** Remembers that we already asked a user to set their location after login. */
class LocationPromptStore(context: Context) {
    private val prefs = context.applicationContext.getSharedPreferences("dzirent_prefs", Context.MODE_PRIVATE)

    fun wasAsked(userId: String): Boolean = prefs.getBoolean("location_prompt_shown_$userId", false)

    fun markAsked(userId: String) {
        prefs.edit().putBoolean("location_prompt_shown_$userId", true).apply()
    }
}
