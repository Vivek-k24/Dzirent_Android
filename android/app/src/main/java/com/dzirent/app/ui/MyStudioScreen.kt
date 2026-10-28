package com.dzirent.app.ui

import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import com.dzirent.app.data.model.Profile
import com.dzirent.app.data.repository.DzirentRepository
import com.dzirent.app.util.HANDLE_MIN
import com.dzirent.app.util.PICKUP_AREA_MAX
import com.dzirent.app.util.STUDIO_BIO_MAX
import com.dzirent.app.util.STUDIO_NAME_MAX
import com.dzirent.app.util.STYLE_TAGS_MAX
import com.dzirent.app.util.StyleTags
import com.dzirent.app.util.cleanHandleInput
import com.dzirent.app.util.isValidHandle
import com.dzirent.app.util.suggestHandle
import kotlinx.coroutines.launch

/** Profile > My studio (lenders): what the studio page shows. Everything is optional. */
@OptIn(ExperimentalMaterial3Api::class, ExperimentalLayoutApi::class)
@Composable
fun MyStudioScreen(
    repository: DzirentRepository,
    profile: Profile,
    onBack: () -> Unit,
    onSaved: (Profile) -> Unit
) {
    val scope = rememberCoroutineScope()
    var name by rememberSaveable { mutableStateOf(profile.studioName.orEmpty()) }
    var handle by rememberSaveable {
        mutableStateOf(profile.studioHandle ?: suggestHandle(profile.studioName ?: profile.displayName))
    }
    var bio by rememberSaveable { mutableStateOf(profile.studioBio.orEmpty()) }
    var tags by rememberSaveable { mutableStateOf(profile.styleTags) }
    var pickup by rememberSaveable { mutableStateOf(profile.pickupArea.orEmpty()) }
    var isSaving by remember { mutableStateOf(false) }
    var error by remember { mutableStateOf<String?>(null) }
    val handleError = handle.takeIf { it.isNotEmpty() && !isValidHandle(it) }?.let {
        "Use at least $HANDLE_MIN letters, numbers, dots or underscores."
    }

    fun save() {
        if (handleError != null) return
        scope.launch {
            isSaving = true
            error = null
            try {
                onSaved(repository.updateStudio(profile.id, name.trim(), handle, bio.trim(), tags, pickup.trim()))
            } catch (e: Exception) {
                error = e.userMessage("Couldn't save your studio. Please try again.")
            }
            isSaving = false
        }
    }

    Column(modifier = Modifier.fillMaxSize()) {
        TopAppBar(
            title = { Text("My studio") },
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
                .padding(horizontal = 24.dp, vertical = 8.dp)
        ) {
            OutlinedTextField(
                value = name,
                onValueChange = { if (it.length <= STUDIO_NAME_MAX) name = it },
                label = { Text("Studio name") },
                placeholder = { Text(profile.displayName) },
                singleLine = true,
                modifier = Modifier.fillMaxWidth()
            )
            Spacer(modifier = Modifier.height(12.dp))
            OutlinedTextField(
                value = handle,
                onValueChange = { handle = cleanHandleInput(it) },
                label = { Text("Handle") },
                prefix = { Text("@") },
                supportingText = { Text(handleError ?: "Shown at the top of your studio page.") },
                isError = handleError != null,
                singleLine = true,
                modifier = Modifier.fillMaxWidth()
            )
            Spacer(modifier = Modifier.height(4.dp))
            OutlinedTextField(
                value = bio,
                onValueChange = { if (it.length <= STUDIO_BIO_MAX) bio = it },
                label = { Text("Bio") },
                supportingText = { Text("${bio.length}/$STUDIO_BIO_MAX") },
                minLines = 3,
                modifier = Modifier.fillMaxWidth()
            )
            Spacer(modifier = Modifier.height(4.dp))
            OutlinedTextField(
                value = pickup,
                onValueChange = { if (it.length <= PICKUP_AREA_MAX) pickup = it },
                label = { Text("Pickup area") },
                placeholder = { Text("e.g. East Austin") },
                supportingText = { Text("A neighborhood, never your address.") },
                singleLine = true,
                modifier = Modifier.fillMaxWidth()
            )

            Spacer(modifier = Modifier.height(12.dp))
            Text("Style tags", style = MaterialTheme.typography.titleSmall, fontWeight = FontWeight.Bold)
            Text(
                text = "Pick up to $STYLE_TAGS_MAX.",
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )
            Spacer(modifier = Modifier.height(8.dp))
            FlowRow(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                StyleTags.ALL.forEach { tag ->
                    val selected = tag in tags
                    FilterChip(
                        selected = selected,
                        onClick = { tags = if (selected) tags - tag else tags + tag },
                        enabled = selected || tags.size < STYLE_TAGS_MAX,
                        label = { Text(tag) }
                    )
                }
            }

            error?.let {
                Spacer(modifier = Modifier.height(8.dp))
                Text(it, color = MaterialTheme.colorScheme.error, style = MaterialTheme.typography.bodySmall)
            }
            Spacer(modifier = Modifier.height(24.dp))
            Button(
                onClick = { save() },
                enabled = !isSaving && handleError == null,
                modifier = Modifier.fillMaxWidth().height(50.dp)
            ) {
                Text(if (isSaving) "Saving..." else "Save")
            }
            Spacer(modifier = Modifier.height(16.dp))
        }
    }
}
