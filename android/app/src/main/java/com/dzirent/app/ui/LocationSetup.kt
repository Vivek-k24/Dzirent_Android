package com.dzirent.app.ui

import android.Manifest
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.layout.*
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.LocationOn
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.window.DialogProperties
import com.dzirent.app.data.model.Profile
import com.dzirent.app.data.model.UsCity
import com.dzirent.app.data.repository.DzirentRepository
import com.dzirent.app.util.ApproxPlace
import com.dzirent.app.util.DistanceUnit
import com.dzirent.app.util.LocationService
import kotlinx.coroutines.launch

private enum class LocationStep { Reason, Working, PickCity, Confirm }

/**
 * Sets the user's approximate location. Shows the reason first and asks for the coarse
 * location permission only when "Use my location" is tapped. The manual option (and the
 * fallback) is picking a "City, ST" from the city list, which saves that city's location.
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
    var pickedCity by remember { mutableStateOf<UsCity?>(null) }
    var isSaving by remember { mutableStateOf(false) }

    fun locate() {
        scope.launch {
            message = null
            workingText = "Finding your approximate location..."
            step = LocationStep.Working
            val place = locationService.currentPlace()
            when {
                place == null -> {
                    message = "Couldn't get your location. Choose your city instead."
                    step = LocationStep.PickCity
                }
                place.city == null -> {
                    message = "Couldn't tell which city you're in. Choose your city instead."
                    step = LocationStep.PickCity
                }
                else -> {
                    found = place
                    step = LocationStep.Confirm
                }
            }
        }
    }

    /** [city] is "City, ST"; [lat]/[lng] are rounded again by the repository. */
    fun save(lat: Double, lng: Double, city: String) {
        scope.launch {
            isSaving = true
            message = null
            try {
                onSaved(repository.updateLocation(profile.id, lat, lng, city))
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
            message = "Location permission is off. Choose your city instead."
            step = LocationStep.PickCity
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
                        Text(locationReason(LocalAppSettings.current.distanceUnit))
                    }
                    LocationStep.Working -> {
                        Row(verticalAlignment = Alignment.CenterVertically) {
                            CircularProgressIndicator(modifier = Modifier.size(24.dp))
                            Spacer(modifier = Modifier.width(16.dp))
                            Text(workingText)
                        }
                    }
                    LocationStep.PickCity -> {
                        Text("We'll save your city's approximate area. Never your exact address.")
                        Spacer(modifier = Modifier.height(12.dp))
                        CityField(
                            repository = repository,
                            selected = pickedCity,
                            onSelected = { pickedCity = it; message = null },
                            label = "Your city",
                            enabled = !isSaving,
                            modifier = Modifier.fillMaxWidth()
                        )
                    }
                    LocationStep.Confirm -> {
                        Text(
                            text = "Near ${found?.city.orEmpty()}",
                            style = MaterialTheme.typography.titleMedium,
                            fontWeight = FontWeight.Bold
                        )
                        Spacer(modifier = Modifier.height(8.dp))
                        Text("We'll save your area, rounded to ${LocalAppSettings.current.distanceUnit.locationPrecision}. Never your exact address.")
                    }
                }
                if (isSaving) {
                    Spacer(modifier = Modifier.height(8.dp))
                    LinearProgressIndicator(modifier = Modifier.fillMaxWidth())
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
                LocationStep.PickCity -> TextButton(
                    onClick = { pickedCity?.let { save(it.lat, it.lng, it.label) } },
                    enabled = pickedCity != null && !isSaving
                ) { Text("Save") }
                LocationStep.Confirm -> TextButton(
                    onClick = { found?.let { place -> place.city?.let { save(place.lat, place.lng, it) } } },
                    enabled = !isSaving
                ) { Text("Save") }
                LocationStep.Working -> {}
            }
        },
        dismissButton = {
            Row {
                when (step) {
                    LocationStep.Reason -> TextButton(onClick = {
                        message = null
                        step = LocationStep.PickCity
                    }) { Text("Choose a city") }
                    LocationStep.Confirm -> TextButton(onClick = {
                        message = null
                        step = LocationStep.PickCity
                    }, enabled = !isSaving) { Text("Change") }
                    else -> {}
                }
                TextButton(onClick = onDismiss, enabled = !isSaving) { Text("Not now") }
            }
        }
    )
}

private fun locationReason(unit: DistanceUnit) =
    "Dzirent uses your approximate location (${unit.locationPrecision}) to show clothes near you."
