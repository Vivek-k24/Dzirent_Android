package com.dzirent.app.ui

import android.Manifest
import android.content.ActivityNotFoundException
import android.content.pm.PackageManager
import android.net.Uri
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.PickVisualMediaRequest
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.core.content.ContextCompat
import androidx.core.content.FileProvider
import coil.compose.AsyncImage
import com.dzirent.app.data.ListingPublisher
import com.dzirent.app.data.PhotoItem
import com.dzirent.app.data.PhotoStore
import com.dzirent.app.data.model.AccessoryTypes
import com.dzirent.app.data.model.Categories
import com.dzirent.app.ui.theme.DzirentPass
import com.dzirent.app.ui.theme.DzirentWarn
import com.dzirent.app.util.CHOOSE_CATEGORY
import com.dzirent.app.util.FashionStyles
import com.dzirent.app.util.FieldOrigin
import com.dzirent.app.util.ItemGuess
import com.dzirent.app.util.ItemLabel
import com.dzirent.app.util.MAX_PHOTOS
import com.dzirent.app.util.PHOTO_TIP
import com.dzirent.app.util.PhotoReview
import com.dzirent.app.util.PhotoSlot
import com.dzirent.app.util.PhotoVerdict
import com.dzirent.app.util.ReviewState
import com.dzirent.app.util.SlotPhoto
import com.dzirent.app.util.SuggestField
import com.dzirent.app.util.TierVerdict
import com.dzirent.app.util.assignSlots
import com.dzirent.app.util.descriptionHint
import com.dzirent.app.util.nameColors
import com.dzirent.app.util.readyLine
import com.dzirent.app.util.suggestionsForChip
import kotlinx.coroutines.launch
import java.io.File
import java.util.UUID

/**
 * Step 1: the photo slots, then title, category, description and style, filled from the photos
 * when the phone is confident (each with ✕ to clear).
 *
 * The first time each source is used, a short explanation asks for consent (remembered in
 * [PhotoConsentStore]). The camera then needs the CAMERA runtime permission, since the app
 * declares it. The Photo Picker needs no storage permission, so the explanation is the only
 * consent step for the gallery (READ_MEDIA_IMAGES is deliberately not requested). Either way
 * the photo is copied into the app ([PhotoStore]) before anything reads it.
 */
@OptIn(ExperimentalLayoutApi::class)
@Composable
fun PhotosAndDetailsStep(
    state: ListingEditorState,
    photoStore: PhotoStore,
    onMessage: (String) -> Unit,
    onChipPicked: (ItemLabel) -> Unit,
    onPolicyWrong: (SlotPhoto) -> Unit,
    onOpenDeclaration: () -> Unit
) {
    val context = LocalContext.current
    val scope = rememberCoroutineScope()
    val consent = remember { PhotoConsentStore(context) }
    var askingConsent by remember { mutableStateOf<PhotoSource?>(null) }
    var showCameraDenied by remember { mutableStateOf(false) }
    var pendingCameraPath by rememberSaveable { mutableStateOf<String?>(null) }
    var targetKey by rememberSaveable { mutableStateOf<String?>(null) }
    var choosingSourceFor by remember { mutableStateOf<PhotoSlot?>(null) }
    val remaining = MAX_PHOTOS - state.slots.size

    fun add(uris: List<Uri>) {
        if (uris.isEmpty()) return
        val (slots, left) = assignSlots(state.slots.toMap(), uris.map { PhotoItem.New(it) }, PhotoSlot.fromKey(targetKey))
        targetKey = null
        state.slots.clear()
        state.slots.putAll(slots)
        state.photosChanged = true
        if (left > 0) onMessage("You can add up to $MAX_PHOTOS photos. Added the first ${uris.size - left}.")
    }

    val galleryLauncher = rememberLauncherForActivityResult(ActivityResultContracts.PickMultipleVisualMedia(MAX_PHOTOS)) { picked ->
        scope.launch {
            val copies = picked.mapNotNull { photoStore.copyIn(it) }
            if (copies.size < picked.size) onMessage("Some photos couldn't be read. Pick different ones.")
            add(copies)
        }
    }

    val cameraLauncher = rememberLauncherForActivityResult(ActivityResultContracts.TakePicture()) { saved ->
        val path = pendingCameraPath
        pendingCameraPath = null
        if (path == null) return@rememberLauncherForActivityResult
        scope.launch {
            val uri = if (saved) photoStore.moveIn(File(path)) else null.also { File(path).delete() }
            if (uri != null) add(listOf(uri)) else if (saved) onMessage("The camera didn't save a photo. Try again.")
        }
    }

    fun openGallery() {
        galleryLauncher.launch(PickVisualMediaRequest(ActivityResultContracts.PickVisualMedia.ImageOnly))
    }

    fun openCamera() {
        val file = File(ListingPublisher.cameraDir(context), "${UUID.randomUUID()}.jpg")
        val uri = FileProvider.getUriForFile(context, "${context.packageName}.fileprovider", file)
        pendingCameraPath = file.absolutePath
        try {
            cameraLauncher.launch(uri)
        } catch (e: ActivityNotFoundException) {
            pendingCameraPath = null
            onMessage("No camera app found. Use Gallery instead.")
        }
    }

    val cameraPermissionLauncher = rememberLauncherForActivityResult(ActivityResultContracts.RequestPermission()) { granted ->
        if (granted) openCamera() else showCameraDenied = true
    }

    fun start(source: PhotoSource) {
        if (!consent.hasConsent(source)) {
            askingConsent = source
            return
        }
        when (source) {
            PhotoSource.Camera -> {
                val granted = ContextCompat.checkSelfPermission(context, Manifest.permission.CAMERA) == PackageManager.PERMISSION_GRANTED
                if (granted) openCamera() else cameraPermissionLauncher.launch(Manifest.permission.CAMERA)
            }
            PhotoSource.Gallery -> openGallery()
        }
    }

    askingConsent?.let { source ->
        PhotoConsentDialog(
            source = source,
            onContinue = {
                askingConsent = null
                consent.giveConsent(source)
                start(source)
            },
            onDismiss = { askingConsent = null }
        )
    }
    if (showCameraDenied) {
        CameraDeniedDialog(
            context = context,
            onUseGallery = {
                showCameraDenied = false
                start(PhotoSource.Gallery)
            },
            onDismiss = { showCameraDenied = false }
        )
    }
    choosingSourceFor?.let { slot ->
        AlertDialog(
            onDismissRequest = { choosingSourceFor = null },
            title = { Text("Add the ${slot.label.lowercase()} photo") },
            confirmButton = {
                TextButton(onClick = { choosingSourceFor = null; targetKey = slot.key; start(PhotoSource.Camera) }) { Text("Camera") }
            },
            dismissButton = {
                TextButton(onClick = { choosingSourceFor = null; targetKey = slot.key; start(PhotoSource.Gallery) }) { Text("Gallery") }
            }
        )
    }

    // ---- Photos ----
    Row(verticalAlignment = Alignment.CenterVertically) {
        Text(text = "Photos", style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.Bold, modifier = Modifier.weight(1f))
        Text(text = readyLine(state.verdicts), style = MaterialTheme.typography.labelLarge, color = MaterialTheme.colorScheme.primary)
    }
    Text(text = PHOTO_TIP, style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
    Spacer(modifier = Modifier.height(8.dp))

    PhotoSlot.entries.chunked(3).forEach { row ->
        Row(horizontalArrangement = Arrangement.spacedBy(8.dp), modifier = Modifier.fillMaxWidth()) {
            row.forEach { slot ->
                SlotTile(
                    slot = slot,
                    item = state.slots[slot],
                    review = state.reviewOf(slot),
                    onAdd = { choosingSourceFor = slot },
                    onRemove = { state.removePhoto(slot) },
                    onUseAnyway = {
                        state.slots[slot]?.let { item ->
                            state.reviews[item.key] = (state.reviews[item.key] ?: PhotoReview.Checking).copy(usedAnyway = true)
                        }
                    },
                    modifier = Modifier.weight(1f)
                )
            }
        }
        Spacer(modifier = Modifier.height(8.dp))
    }

    Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
        OutlinedButton(onClick = { targetKey = null; start(PhotoSource.Gallery) }, enabled = remaining > 0) {
            Icon(Icons.Default.PhotoLibrary, contentDescription = null, modifier = Modifier.size(18.dp))
            Spacer(modifier = Modifier.width(8.dp))
            Text("Gallery")
        }
        OutlinedButton(onClick = { targetKey = null; start(PhotoSource.Camera) }, enabled = remaining > 0) {
            Icon(Icons.Default.PhotoCamera, contentDescription = null, modifier = Modifier.size(18.dp))
            Spacer(modifier = Modifier.width(8.dp))
            Text("Camera")
        }
    }

    // An everyday basic: a friendly warning, the lender decides.
    state.notOffered?.let { photo ->
        Spacer(modifier = Modifier.height(8.dp))
        PolicyWarningCard(
            message = (photo.verdict as TierVerdict.NotOffered).message,
            onChooseAnother = {
                state.removePhoto(photo.slot)
                targetKey = photo.slot.key
                start(PhotoSource.Gallery)
            },
            onWrong = { onPolicyWrong(photo) }
        )
    }
    Spacer(modifier = Modifier.height(16.dp))

    // ---- Details ----
    val anySuggested = SuggestField.entries.any { state.field(it).origin == FieldOrigin.SUGGESTED }
    Text("Details", style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.Bold)
    if (anySuggested) {
        Text(
            text = "Filled from your photos. Tap ✕ if anything's off.",
            style = MaterialTheme.typography.bodySmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant
        )
    }
    Spacer(modifier = Modifier.height(8.dp))

    // Not sure what it is: the three likeliest, one tap fills title, category and style.
    val guess = state.guess
    if (guess is ItemGuess.Choices && state.chipsAnsweredFor != state.front?.key) {
        Text(
            text = "Is it a: " + guess.labels.joinToString(" · ") { it.chip } + "?",
            style = MaterialTheme.typography.bodyMedium,
            fontWeight = FontWeight.Medium
        )
        FlowRow(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            guess.labels.forEach { label ->
                AssistChip(
                    onClick = {
                        state.applyChip(suggestionsForChip(label, nameColors(state.colorVotes)))
                        onChipPicked(label)
                    },
                    label = { Text(label.chip) }
                )
            }
            TextButton(onClick = { state.chipsAnsweredFor = state.front?.key }) { Text("None of these") }
        }
        Spacer(modifier = Modifier.height(8.dp))
    }

    OutlinedTextField(
        value = state.title,
        onValueChange = { state.editTitle(it) },
        label = { Text("Title") },
        placeholder = { Text("e.g. Maroon lehenga") },
        singleLine = true,
        trailingIcon = clearIcon("title", state.title.text.isNotEmpty()) { state.clear(SuggestField.TITLE) },
        modifier = Modifier.fillMaxWidth()
    )
    Spacer(modifier = Modifier.height(12.dp))

    CategoryPicker(
        selected = state.category,
        onSelected = { state.pickCategory(it) },
        onClear = { state.clear(SuggestField.CATEGORY) }
    )
    Spacer(modifier = Modifier.height(12.dp))
    if (state.isAccessory) {
        ItemTypePicker(selected = state.itemType, onSelected = { state.itemType = it })
        Spacer(modifier = Modifier.height(12.dp))
    }
    if (state.isAccessory || state.jewelry) {
        DeclarationStatusNote(onOpenDeclaration)
    }

    OutlinedTextField(
        value = state.description,
        onValueChange = { state.editDescription(it) },
        label = { Text("Description") },
        // The tip and any fabric guess are a hint only; they're never part of the description.
        supportingText = { Text(descriptionHint(state.fabricHintScores), color = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.7f)) },
        minLines = 4,
        trailingIcon = clearIcon("description", state.description.text.isNotEmpty()) { state.clear(SuggestField.DESCRIPTION) },
        modifier = Modifier.fillMaxWidth()
    )
    Spacer(modifier = Modifier.height(12.dp))

    StylePicker(
        selected = state.styles,
        onChange = { state.editStyles(it) },
        onClear = { state.clear(SuggestField.STYLE) }
    )
}

/** The ✕ that clears a filled field. */
private fun clearIcon(what: String, visible: Boolean, onClear: () -> Unit): (@Composable () -> Unit)? =
    if (!visible) null else {
        { IconButton(onClick = onClear) { Icon(Icons.Default.Close, contentDescription = "Clear $what") } }
    }

/** One photo slot: "+ Front" when empty, the photo with ✕ and its check result when filled. */
@Composable
private fun SlotTile(
    slot: PhotoSlot,
    item: PhotoItem?,
    review: PhotoReview?,
    onAdd: () -> Unit,
    onRemove: () -> Unit,
    onUseAnyway: () -> Unit,
    modifier: Modifier = Modifier
) {
    val shape = RoundedCornerShape(8.dp)
    val name = slot.label + if (slot.required) "" else " (optional)"
    Column(horizontalAlignment = Alignment.CenterHorizontally, modifier = modifier) {
        if (item == null) {
            Surface(
                shape = shape,
                color = MaterialTheme.colorScheme.surfaceVariant,
                modifier = Modifier
                    .fillMaxWidth()
                    .aspectRatio(1f)
                    .clip(shape)
                    .clickable(role = Role.Button, onClickLabel = "Add the ${slot.label.lowercase()} photo", onClick = onAdd)
            ) {
                Column(horizontalAlignment = Alignment.CenterHorizontally, verticalArrangement = Arrangement.Center) {
                    Icon(Icons.Default.AddAPhoto, contentDescription = null, tint = MaterialTheme.colorScheme.onSurfaceVariant)
                    Spacer(modifier = Modifier.height(4.dp))
                    Text(slot.label, style = MaterialTheme.typography.labelMedium, color = MaterialTheme.colorScheme.onSurfaceVariant)
                    if (slot.required) {
                        Text("Required", style = MaterialTheme.typography.labelSmall, color = MaterialTheme.colorScheme.primary)
                    }
                }
            }
            if (!slot.required) Text("Optional", style = MaterialTheme.typography.labelSmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
            return@Column
        }
        Box(
            modifier = Modifier
                .fillMaxWidth()
                .aspectRatio(1f)
                .clip(shape)
                .then(if (review?.verdict == PhotoVerdict.BLOCKED) Modifier.border(2.dp, MaterialTheme.colorScheme.error, shape) else Modifier)
        ) {
            AsyncImage(
                model = when (item) {
                    is PhotoItem.Existing -> item.photo.storagePath
                    is PhotoItem.New -> item.uri
                },
                contentDescription = "$name photo",
                contentScale = ContentScale.Crop,
                modifier = Modifier.fillMaxSize()
            )
            Surface(
                color = MaterialTheme.colorScheme.primary,
                shape = RoundedCornerShape(bottomEnd = 8.dp),
                modifier = Modifier.align(Alignment.TopStart)
            ) {
                Text(
                    text = slot.label,
                    style = MaterialTheme.typography.labelSmall,
                    color = MaterialTheme.colorScheme.onPrimary,
                    modifier = Modifier.padding(horizontal = 6.dp, vertical = 2.dp)
                )
            }
            Surface(
                shape = RoundedCornerShape(50),
                color = MaterialTheme.colorScheme.scrim.copy(alpha = 0.55f),
                modifier = Modifier.align(Alignment.TopEnd).padding(2.dp)
            ) {
                IconButton(onClick = onRemove, modifier = Modifier.size(32.dp)) {
                    Icon(Icons.Default.Close, contentDescription = "Remove the ${slot.label.lowercase()} photo", tint = Color.White)
                }
            }
            if (review?.verdict == PhotoVerdict.CHECKING) {
                Box(
                    contentAlignment = Alignment.Center,
                    modifier = Modifier.matchParentSize().background(MaterialTheme.colorScheme.scrim.copy(alpha = 0.35f))
                ) {
                    CircularProgressIndicator(
                        strokeWidth = 2.dp,
                        color = Color.White,
                        modifier = Modifier.size(24.dp).semantics { contentDescription = "Checking photo" }
                    )
                }
            }
        }
        review?.let { PhotoCheckResult(it, onUseAnyway) }
    }
}

/** "This looks like a T-shirt. …" with a way out either way. */
@Composable
private fun PolicyWarningCard(message: String, onChooseAnother: () -> Unit, onWrong: () -> Unit) {
    Card(
        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.errorContainer),
        modifier = Modifier.fillMaxWidth()
    ) {
        Column(modifier = Modifier.padding(start = 16.dp, top = 16.dp, end = 8.dp, bottom = 4.dp)) {
            Text(text = message, color = MaterialTheme.colorScheme.onErrorContainer, modifier = Modifier.padding(end = 8.dp))
            Row(horizontalArrangement = Arrangement.End, modifier = Modifier.fillMaxWidth()) {
                TextButton(onClick = onWrong) { Text("This is wrong") }
                TextButton(onClick = onChooseAnother) { Text("Choose another photo") }
            }
        }
    }
}

/** Under a thumbnail: a green tick, or why the photo can't be used or needs "Use anyway". */
@Composable
private fun PhotoCheckResult(review: PhotoReview, onUseAnyway: () -> Unit) {
    // Saved photos were published before the checks; nothing to show for them.
    if (review.state == ReviewState.SAVED) return
    val color = when (review.verdict) {
        PhotoVerdict.BLOCKED -> MaterialTheme.colorScheme.error
        PhotoVerdict.WARNING -> DzirentWarn
        else -> MaterialTheme.colorScheme.onSurfaceVariant
    }
    when (review.verdict) {
        PhotoVerdict.CHECKING -> Unit
        PhotoVerdict.PASSED -> Icon(
            Icons.Default.CheckCircle,
            contentDescription = "Photo passed the checks",
            tint = DzirentPass,
            modifier = Modifier.size(20.dp)
        )
        else -> {
            review.messages.forEach { message ->
                Text(text = message, style = MaterialTheme.typography.labelSmall, color = color, textAlign = TextAlign.Center)
            }
            when (review.verdict) {
                PhotoVerdict.WARNING -> TextButton(
                    onClick = onUseAnyway,
                    contentPadding = PaddingValues(horizontal = 8.dp, vertical = 0.dp)
                ) { Text("Use anyway", style = MaterialTheme.typography.labelMedium) }
                PhotoVerdict.USED_ANYWAY -> Text(
                    text = "Used anyway",
                    style = MaterialTheme.typography.labelSmall,
                    fontWeight = FontWeight.Bold,
                    color = color
                )
                else -> Unit
            }
        }
    }
}

/** For accessories and jewelry: a reminder that the declaration is part of listing them. */
@Composable
private fun DeclarationStatusNote(onOpenDeclaration: () -> Unit) {
    TextButton(onClick = onOpenDeclaration, contentPadding = PaddingValues(horizontal = 4.dp)) {
        Icon(Icons.Default.Gavel, contentDescription = null, modifier = Modifier.size(18.dp))
        Spacer(modifier = Modifier.width(6.dp))
        Text("Accessory declaration")
    }
    Spacer(modifier = Modifier.height(4.dp))
}

/** "Style": up to [FashionStyles.LISTING_MAX], only used to suggest the piece; ✕ clears it. */
@OptIn(ExperimentalLayoutApi::class)
@Composable
private fun StylePicker(selected: List<String>, onChange: (List<String>) -> Unit, onClear: () -> Unit) {
    Row(verticalAlignment = Alignment.CenterVertically) {
        Column(modifier = Modifier.weight(1f)) {
            Text("Style (optional)", style = MaterialTheme.typography.titleSmall, fontWeight = FontWeight.Bold)
            Text(
                text = "Up to ${FashionStyles.LISTING_MAX}. Helps show your piece to people who like this style.",
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )
        }
        if (selected.isNotEmpty()) {
            IconButton(onClick = onClear) { Icon(Icons.Default.Close, contentDescription = "Clear style") }
        }
    }
    Spacer(modifier = Modifier.height(8.dp))
    FlowRow(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
        FashionStyles.ALL.forEach { style ->
            val isSelected = style in selected
            FilterChip(
                selected = isSelected,
                onClick = { onChange(if (isSelected) selected - style else selected + style) },
                enabled = isSelected || selected.size < FashionStyles.LISTING_MAX,
                label = { Text(style) }
            )
        }
    }
}

/** Category: blank until chosen ("Choose a category"), required; ✕ clears it. */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun CategoryPicker(selected: String?, onSelected: (String) -> Unit, onClear: () -> Unit) {
    var expanded by remember { mutableStateOf(false) }
    Row(verticalAlignment = Alignment.CenterVertically) {
        ExposedDropdownMenuBox(expanded = expanded, onExpandedChange = { expanded = it }, modifier = Modifier.weight(1f)) {
            OutlinedTextField(
                value = selected.orEmpty(),
                onValueChange = {},
                readOnly = true,
                label = { Text("Category") },
                placeholder = { Text(CHOOSE_CATEGORY) },
                supportingText = if (selected == null) {
                    { Text("Required") }
                } else null,
                trailingIcon = { ExposedDropdownMenuDefaults.TrailingIcon(expanded = expanded) },
                modifier = Modifier
                    .menuAnchor(MenuAnchorType.PrimaryNotEditable)
                    .fillMaxWidth()
            )
            ExposedDropdownMenu(expanded = expanded, onDismissRequest = { expanded = false }) {
                Categories.ALL.forEach { option ->
                    DropdownMenuItem(
                        text = { Text(option) },
                        onClick = {
                            onSelected(option)
                            expanded = false
                        }
                    )
                }
            }
        }
        if (selected != null) {
            IconButton(onClick = onClear) { Icon(Icons.Default.Close, contentDescription = "Clear category") }
        }
    }
}

/** Required "Item type" dropdown for Accessories (exactly [AccessoryTypes.ALL]). */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun ItemTypePicker(selected: String?, onSelected: (String) -> Unit) {
    var expanded by remember { mutableStateOf(false) }
    ExposedDropdownMenuBox(expanded = expanded, onExpandedChange = { expanded = it }) {
        OutlinedTextField(
            value = selected.orEmpty(),
            onValueChange = {},
            readOnly = true,
            label = { Text("Item type") },
            placeholder = { Text("Choose one") },
            supportingText = if (selected == null) {
                { Text("Required for Accessories.") }
            } else null,
            trailingIcon = { ExposedDropdownMenuDefaults.TrailingIcon(expanded = expanded) },
            modifier = Modifier
                .menuAnchor(MenuAnchorType.PrimaryNotEditable)
                .fillMaxWidth()
        )
        ExposedDropdownMenu(expanded = expanded, onDismissRequest = { expanded = false }) {
            AccessoryTypes.ALL.forEach { option ->
                DropdownMenuItem(
                    text = { Text(option) },
                    onClick = {
                        onSelected(option)
                        expanded = false
                    }
                )
            }
        }
    }
}
