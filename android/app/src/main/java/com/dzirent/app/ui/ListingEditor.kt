package com.dzirent.app.ui

import android.Manifest
import android.content.ActivityNotFoundException
import android.content.pm.PackageManager
import android.net.Uri
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.PickVisualMediaRequest
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.automirrored.filled.KeyboardArrowLeft
import androidx.compose.material.icons.automirrored.filled.KeyboardArrowRight
import androidx.compose.material.icons.filled.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.unit.dp
import androidx.core.content.ContextCompat
import androidx.core.content.FileProvider
import coil.compose.AsyncImage
import com.dzirent.app.data.ListingDraft
import com.dzirent.app.data.ListingPublisher
import com.dzirent.app.data.PhotoItem
import com.dzirent.app.data.PublishStepException
import com.dzirent.app.data.model.*
import com.dzirent.app.data.repository.DzirentRepository
import com.dzirent.app.util.DistanceUnit
import com.dzirent.app.util.FashionStyles
import com.dzirent.app.util.parseDateRange
import com.dzirent.app.util.parsePrice
import com.dzirent.app.util.priceInputText
import com.dzirent.app.util.validateMeasurements
import kotlinx.coroutines.launch
import java.io.File
import java.util.UUID

const val MAX_PHOTOS = 6
private const val DEPOSIT_NOTE_MAX = 120
private const val PRICE_NOTE_MAX = 60
private fun noLocationToPublish(unit: DistanceUnit) =
    "Set your location before publishing. Listings show your approximate area (${unit.locationPrecision}), never your exact address."

/**
 * Create (listingId == null) or edit a listing. Editing loads the listing first and is
 * limited to its owner; RLS enforces the same rule on the server.
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun ListingEditorScreen(
    repository: DzirentRepository,
    profile: Profile,
    listingId: String?,
    onSetLocation: () -> Unit,
    onBack: (() -> Unit)?,
    onSaved: () -> Unit
) {
    var loadState by remember(listingId) {
        mutableStateOf<UiState<Listing?>>(if (listingId == null) UiState.Success(null) else UiState.Loading)
    }
    val scope = rememberCoroutineScope()

    fun load() {
        if (listingId == null) return
        scope.launch {
            loadState = UiState.Loading
            loadState = try {
                UiState.Success(repository.getListingDetail(listingId))
            } catch (e: Exception) {
                UiState.Error(e.userMessage("Couldn't load this listing. Please try again."))
            }
        }
    }

    LaunchedEffect(listingId) { load() }

    Column(modifier = Modifier.fillMaxSize()) {
        if (onBack != null) {
            TopAppBar(
                title = { Text(if (listingId == null) "New Listing" else "Edit Listing") },
                navigationIcon = {
                    IconButton(onClick = onBack) {
                        Icon(Icons.AutoMirrored.Filled.ArrowBack, contentDescription = "Back")
                    }
                }
            )
        }
        when (val state = loadState) {
            is UiState.Loading -> LoadingView(message = "Loading listing...")
            is UiState.Error -> ErrorView(message = state.message, onRetry = { load() })
            is UiState.Success -> {
                val original = state.data
                if (original != null && original.lenderId != profile.id) {
                    EmptyView(
                        title = "Not your listing",
                        description = "You can only edit your own listings.",
                        icon = Icons.Default.Lock
                    )
                } else {
                    ListingEditorForm(
                        repository = repository,
                        profile = profile,
                        original = original,
                        showTitle = onBack == null,
                        onSetLocation = onSetLocation,
                        onSaved = onSaved
                    )
                }
            }
        }
    }
}

private sealed class PublishState {
    object Idle : PublishState()
    data class Running(val progress: String) : PublishState()
    data class Failed(val step: String, val message: String) : PublishState()
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun ListingEditorForm(
    repository: DzirentRepository,
    profile: Profile,
    original: Listing?,
    showTitle: Boolean,
    onSetLocation: () -> Unit,
    onSaved: () -> Unit
) {
    val context = LocalContext.current
    val scope = rememberCoroutineScope()
    val isNew = original == null
    val listingId = remember { original?.id ?: UUID.randomUUID().toString() }
    val publisher = remember { ListingPublisher(repository, context, original) }

    var title by rememberSaveable { mutableStateOf(original?.title.orEmpty()) }
    var category by rememberSaveable { mutableStateOf(original?.category ?: Categories.ALL.first()) }
    var itemType by rememberSaveable { mutableStateOf(original?.itemType) }
    var styles by rememberSaveable { mutableStateOf(original?.listingStyles.orEmpty().filter { it in FashionStyles.ALL }) }
    var showItemTypeError by remember { mutableStateOf(false) }
    val isAccessory = category == Categories.ACCESSORIES
    var sizeLabel by rememberSaveable { mutableStateOf(original?.sizeLabel.orEmpty()) }
    var description by rememberSaveable { mutableStateOf(original?.description.orEmpty()) }
    var priceText by rememberSaveable { mutableStateOf(priceInputText(original?.priceUsd)) }
    var showPriceError by remember { mutableStateOf(false) }
    var priceNote by rememberSaveable { mutableStateOf(original?.priceNote.orEmpty()) }
    var retailValueText by rememberSaveable { mutableStateOf(original?.retailValue?.let { formatNumber(it) }.orEmpty()) }
    var depositNote by rememberSaveable { mutableStateOf(original?.depositNote.orEmpty()) }
    var photos by remember { mutableStateOf<List<PhotoItem>>(original?.photos.orEmpty().map { PhotoItem.Existing(it) }) }
    // One unit per listing: an existing listing keeps its own, a new one starts in the unit
    // from Settings > Preferences.
    val settings = LocalAppSettings.current
    var unit by rememberSaveable { mutableStateOf(original?.measurements?.firstOrNull()?.unit ?: settings.measureUnit.key) }
    var measurementRows by remember {
        mutableStateOf(original?.measurements.orEmpty().map { MeasurementRow(it.id, it.label, formatNumber(it.value)) })
    }
    var ranges by remember {
        mutableStateOf(original?.availability.orEmpty().mapNotNull { parseDateRange(it.startDate, it.endDate) })
    }
    var showAllMeasurementErrors by remember { mutableStateOf(false) }

    var errorMessage by remember { mutableStateOf<String?>(null) }
    var publishState by remember { mutableStateOf<PublishState>(PublishState.Idle) }

    fun validate(): String? {
        if (isNew && !profile.hasLocation) return noLocationToPublish(settings.distanceUnit)
        if (photos.isEmpty()) return "Add at least one photo."
        if (photos.size > MAX_PHOTOS) return "You can add up to $MAX_PHOTOS photos."
        if (title.trim().length !in 3..80) return "Title must be 3 to 80 characters."
        if (isAccessory && itemType == null) {
            showItemTypeError = true
            return "Choose an item type for Accessories."
        }
        if (sizeLabel.isBlank()) return "Please enter a size."
        if (parsePrice(priceText) == null) {
            showPriceError = true
            return "Enter a price above \$0, like 25 or 25.50."
        }
        if (priceNote.trim().length > PRICE_NOTE_MAX) return "Price details can be up to $PRICE_NOTE_MAX characters."
        if (description.trim().length > 1000) return "Description can be up to 1000 characters."
        if (retailValueText.isNotBlank() && (retailValueText.toDoubleOrNull() ?: 0.0) <= 0.0) {
            return "Retail value must be a number above 0, or leave it empty."
        }
        if (validateMeasurements(measurementRows.map { it.toInput() }).any { it != null }) {
            showAllMeasurementErrors = true
            return "Fix the highlighted measurements, or remove those rows."
        }
        if (ranges.isEmpty()) return "Add the dates this piece is available."
        return null
    }

    fun publish() {
        validate()?.let {
            errorMessage = it
            return
        }
        errorMessage = null
        val base = original ?: Listing(
            id = listingId,
            lenderId = profile.id,
            title = "",
            category = category,
            // Listings take the lender's approximate (already rounded) location.
            city = profile.city,
            approxLat = profile.approxLat ?: return,
            approxLng = profile.approxLng ?: return,
            status = "paused"
        )
        val draft = ListingDraft(
            listing = base.copy(
                title = title.trim(),
                category = category,
                itemType = if (isAccessory) itemType else null,
                listingStyles = styles,
                sizeLabel = sizeLabel.trim(),
                description = description.trim(),
                priceUsd = parsePrice(priceText),
                priceNote = priceNote.trim(),
                retailValue = retailValueText.toDoubleOrNull(),
                depositNote = depositNote.trim().ifBlank { null },
                photos = emptyList(),
                measurements = emptyList(),
                availability = emptyList(),
                lender = null
            ),
            photos = photos,
            measurements = measurementRows.filterNot { it.toInput().isBlank }.map { row ->
                ListingMeasurement(
                    id = row.key,
                    listingId = listingId,
                    label = row.label.trim(),
                    value = row.valueText.trim().toDouble(),
                    unit = unit
                )
            },
            availability = ranges.sortedBy { it.start }.map { range ->
                Availability(
                    id = UUID.randomUUID().toString(),
                    listingId = listingId,
                    startDate = range.start.toString(),
                    endDate = range.end.toString()
                )
            }
        )
        scope.launch {
            publishState = PublishState.Running("Starting...")
            try {
                publisher.publish(draft) { progress -> publishState = PublishState.Running(progress) }
                publishState = PublishState.Idle
                onSaved()
            } catch (e: PublishStepException) {
                publishState = PublishState.Failed(e.step, e.message ?: "Please try again.")
            }
        }
    }

    when (val state = publishState) {
        is PublishState.Running -> {
            LoadingView(message = state.progress)
            return
        }
        is PublishState.Failed -> {
            PublishFailedView(
                step = state.step,
                message = state.message,
                isNew = isNew,
                onRetry = { publish() },
                onBackToEdit = { publishState = PublishState.Idle }
            )
            return
        }
        PublishState.Idle -> Unit
    }

    Column(
        modifier = Modifier
            .fillMaxSize()
            .verticalScroll(rememberScrollState())
            .padding(24.dp)
    ) {
        if (showTitle) {
            Text(
                text = if (isNew) "Create New Listing" else "Edit Listing",
                style = MaterialTheme.typography.headlineSmall,
                fontWeight = FontWeight.Bold,
                modifier = Modifier.align(Alignment.CenterHorizontally)
            )
            Spacer(modifier = Modifier.height(16.dp))
        }

        if (isNew && !profile.hasLocation) {
            Card(
                colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.errorContainer),
                modifier = Modifier.fillMaxWidth().padding(bottom = 16.dp)
            ) {
                Column(modifier = Modifier.padding(16.dp)) {
                    Text(text = noLocationToPublish(LocalAppSettings.current.distanceUnit), color = MaterialTheme.colorScheme.onErrorContainer)
                    Spacer(modifier = Modifier.height(8.dp))
                    TextButton(onClick = onSetLocation) { Text("Set my location") }
                }
            }
        } else {
            val city = if (isNew) profile.city else original?.city.orEmpty()
            Text(
                text = "Shown near ${city.ifBlank { "your area" }} (approximate, ${LocalAppSettings.current.distanceUnit.locationPrecision})",
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                modifier = Modifier.padding(bottom = 12.dp)
            )
        }

        errorMessage?.let {
            Card(
                colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.errorContainer),
                modifier = Modifier.fillMaxWidth().padding(bottom = 16.dp)
            ) {
                Text(
                    text = it,
                    color = MaterialTheme.colorScheme.onErrorContainer,
                    modifier = Modifier.padding(16.dp)
                )
            }
        }

        PhotosSection(
            photos = photos,
            onPhotosChange = { photos = it; errorMessage = null },
            onMessage = { errorMessage = it }
        )
        Spacer(modifier = Modifier.height(16.dp))

        OutlinedTextField(
            value = title,
            onValueChange = { if (it.length <= 80) title = it },
            label = { Text("Title (e.g. Designer Evening Gown)") },
            singleLine = true,
            modifier = Modifier.fillMaxWidth()
        )
        Spacer(modifier = Modifier.height(12.dp))

        CategoryPicker(
            selected = category,
            onSelected = {
                category = it
                if (it != Categories.ACCESSORIES) itemType = null
            }
        )
        Spacer(modifier = Modifier.height(12.dp))

        if (isAccessory) {
            ItemTypePicker(
                selected = itemType,
                onSelected = { itemType = it; showItemTypeError = false },
                isError = showItemTypeError
            )
            Text(
                text = DzirentPolicy.ACCESSORIES_NOT_ALLOWED,
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                modifier = Modifier.padding(start = 4.dp, top = 4.dp)
            )
            Spacer(modifier = Modifier.height(12.dp))
        }

        StylePicker(selected = styles, onChange = { styles = it })
        Spacer(modifier = Modifier.height(12.dp))

        OutlinedTextField(
            value = sizeLabel,
            onValueChange = { sizeLabel = it },
            label = { Text("Size Label (e.g. M / US 8)") },
            singleLine = true,
            modifier = Modifier.fillMaxWidth()
        )
        Spacer(modifier = Modifier.height(12.dp))

        OutlinedTextField(
            value = priceText,
            onValueChange = { input ->
                priceText = input.filter { it.isDigit() || it == '.' }
                showPriceError = false
            },
            label = { Text("Price (\$)") },
            prefix = { Text("\$") },
            supportingText = { Text(if (showPriceError) "Required: a price above \$0." else "Per rental") },
            isError = showPriceError,
            keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Decimal),
            singleLine = true,
            modifier = Modifier.fillMaxWidth()
        )
        Spacer(modifier = Modifier.height(4.dp))

        OutlinedTextField(
            value = priceNote,
            onValueChange = { if (it.length <= PRICE_NOTE_MAX) priceNote = it },
            label = { Text("Price details - optional") },
            placeholder = { Text("e.g. for 3 days, cleaning included") },
            singleLine = true,
            modifier = Modifier.fillMaxWidth()
        )
        Spacer(modifier = Modifier.height(12.dp))

        OutlinedTextField(
            value = description,
            onValueChange = { if (it.length <= 1000) description = it },
            label = { Text("Description") },
            modifier = Modifier.fillMaxWidth().height(120.dp)
        )
        Spacer(modifier = Modifier.height(12.dp))

        DepositFields(
            retailValueText = retailValueText,
            onRetailValueChange = { retailValueText = it },
            depositNote = depositNote,
            onDepositNoteChange = { depositNote = it }
        )
        Spacer(modifier = Modifier.height(16.dp))

        MeasurementsSection(
            suggestions = Categories.measurementSuggestions(category, itemType),
            unit = unit,
            onUnitChange = { unit = it },
            rows = measurementRows,
            onRowsChange = { measurementRows = it },
            showAllErrors = showAllMeasurementErrors
        )
        Spacer(modifier = Modifier.height(16.dp))

        AvailabilitySection(
            ranges = ranges,
            onRangesChange = { ranges = it }
        )
        Spacer(modifier = Modifier.height(24.dp))

        Button(
            onClick = { publish() },
            modifier = Modifier.fillMaxWidth().height(50.dp)
        ) {
            Text(if (isNew) "Publish Listing" else "Save Changes")
        }
    }
}

@Composable
private fun PublishFailedView(
    step: String,
    message: String,
    isNew: Boolean,
    onRetry: () -> Unit,
    onBackToEdit: () -> Unit
) {
    Column(
        modifier = Modifier
            .fillMaxSize()
            .padding(24.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.Center
    ) {
        Icon(
            imageVector = Icons.Default.Warning,
            contentDescription = null,
            tint = MaterialTheme.colorScheme.error,
            modifier = Modifier.size(48.dp)
        )
        Spacer(modifier = Modifier.height(16.dp))
        Text(
            text = "Stopped at: $step",
            style = MaterialTheme.typography.titleMedium,
            fontWeight = FontWeight.Bold
        )
        Spacer(modifier = Modifier.height(8.dp))
        Text(text = message, style = MaterialTheme.typography.bodyMedium)
        Spacer(modifier = Modifier.height(8.dp))
        Text(
            text = if (isNew) "Your listing stays hidden until this finishes." else "Your listing is paused until your changes are saved.",
            style = MaterialTheme.typography.bodySmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant
        )
        Spacer(modifier = Modifier.height(24.dp))
        Button(onClick = onRetry, modifier = Modifier.fillMaxWidth()) {
            Icon(Icons.Default.Refresh, contentDescription = null, modifier = Modifier.size(18.dp))
            Spacer(modifier = Modifier.width(8.dp))
            Text("Retry")
        }
        TextButton(onClick = onBackToEdit) { Text("Back to edit") }
    }
}

/** "Style (optional)": up to [FashionStyles.LISTING_MAX], only used to suggest the piece. */
@OptIn(ExperimentalLayoutApi::class)
@Composable
private fun StylePicker(selected: List<String>, onChange: (List<String>) -> Unit) {
    Text("Style (optional)", style = MaterialTheme.typography.titleSmall, fontWeight = FontWeight.Bold)
    Text(
        text = "Up to ${FashionStyles.LISTING_MAX}. Helps show your piece to people who like this style.",
        style = MaterialTheme.typography.bodySmall,
        color = MaterialTheme.colorScheme.onSurfaceVariant
    )
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

@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun CategoryPicker(selected: String, onSelected: (String) -> Unit) {
    var expanded by remember { mutableStateOf(false) }
    ExposedDropdownMenuBox(expanded = expanded, onExpandedChange = { expanded = it }) {
        OutlinedTextField(
            value = selected,
            onValueChange = {},
            readOnly = true,
            label = { Text("Category") },
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
}

/** Required "Item type" dropdown for Accessories (exactly [AccessoryTypes.ALL]). */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun ItemTypePicker(selected: String?, onSelected: (String) -> Unit, isError: Boolean) {
    var expanded by remember { mutableStateOf(false) }
    ExposedDropdownMenuBox(expanded = expanded, onExpandedChange = { expanded = it }) {
        OutlinedTextField(
            value = selected.orEmpty(),
            onValueChange = {},
            readOnly = true,
            label = { Text("Item type") },
            placeholder = { Text("Choose one") },
            isError = isError,
            supportingText = if (isError) {
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

/**
 * 1 to 6 photos from the system Photo Picker or the camera app via a FileProvider URI.
 * Thumbnails can be reordered and removed.
 *
 * The first time each source is used, a short explanation asks for consent (remembered in
 * [PhotoConsentStore]). The camera then needs the CAMERA runtime permission, since the app
 * declares it. The Photo Picker needs no storage permission, so the explanation is the only
 * consent step for the gallery (READ_MEDIA_IMAGES is deliberately not requested).
 */
@Composable
private fun PhotosSection(
    photos: List<PhotoItem>,
    onPhotosChange: (List<PhotoItem>) -> Unit,
    onMessage: (String) -> Unit
) {
    val context = LocalContext.current
    val remaining = MAX_PHOTOS - photos.size
    var pendingCameraUri by rememberSaveable { mutableStateOf<String?>(null) }
    val consent = remember { PhotoConsentStore(context) }
    var askingConsent by remember { mutableStateOf<PhotoSource?>(null) }
    var showCameraDenied by remember { mutableStateOf(false) }

    fun addUris(uris: List<Uri>) {
        val existingKeys = photos.map { it.key }.toSet()
        val fresh = uris.map { PhotoItem.New(it) }.filter { it.key !in existingKeys }
        val added = fresh.take(MAX_PHOTOS - photos.size)
        if (added.size < fresh.size) onMessage("You can add up to $MAX_PHOTOS photos. Added the first ${added.size}.")
        if (added.isNotEmpty()) onPhotosChange(photos + added)
    }

    val galleryLauncher = rememberLauncherForActivityResult(
        ActivityResultContracts.PickMultipleVisualMedia(MAX_PHOTOS)
    ) { uris -> addUris(uris) }

    val cameraLauncher = rememberLauncherForActivityResult(ActivityResultContracts.TakePicture()) { saved ->
        val uri = pendingCameraUri
        pendingCameraUri = null
        if (saved && uri != null) addUris(listOf(Uri.parse(uri)))
    }

    fun openGallery() {
        galleryLauncher.launch(PickVisualMediaRequest(ActivityResultContracts.PickVisualMedia.ImageOnly))
    }

    fun openCamera() {
        val file = File(ListingPublisher.cameraDir(context), "${UUID.randomUUID()}.jpg")
        val uri = FileProvider.getUriForFile(context, "${context.packageName}.fileprovider", file)
        pendingCameraUri = uri.toString()
        try {
            cameraLauncher.launch(uri)
        } catch (e: ActivityNotFoundException) {
            pendingCameraUri = null
            onMessage("No camera app found. Use Gallery instead.")
        }
    }

    val cameraPermissionLauncher = rememberLauncherForActivityResult(
        ActivityResultContracts.RequestPermission()
    ) { granted ->
        if (granted) openCamera() else showCameraDenied = true
    }

    fun startCamera() {
        val granted = ContextCompat.checkSelfPermission(context, Manifest.permission.CAMERA) ==
            PackageManager.PERMISSION_GRANTED
        if (granted) openCamera() else cameraPermissionLauncher.launch(Manifest.permission.CAMERA)
    }

    fun start(source: PhotoSource) {
        if (!consent.hasConsent(source)) {
            askingConsent = source
            return
        }
        when (source) {
            PhotoSource.Camera -> startCamera()
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

    Text(text = "Photos", style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.Bold)
    Text(
        text = "${photos.size}/$MAX_PHOTOS · At least 1 required. The first photo is the cover.",
        style = MaterialTheme.typography.bodySmall,
        color = MaterialTheme.colorScheme.onSurfaceVariant
    )
    Spacer(modifier = Modifier.height(8.dp))

    if (photos.isNotEmpty()) {
        LazyRow(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            itemsIndexed(photos, key = { _, item -> item.key }) { index, item ->
                PhotoThumb(
                    item = item,
                    isCover = index == 0,
                    canMoveLeft = index > 0,
                    canMoveRight = index < photos.lastIndex,
                    onMoveLeft = { onPhotosChange(photos.swap(index, index - 1)) },
                    onMoveRight = { onPhotosChange(photos.swap(index, index + 1)) },
                    onRemove = { onPhotosChange(photos.filterIndexed { i, _ -> i != index }) }
                )
            }
        }
        Spacer(modifier = Modifier.height(8.dp))
    }

    Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
        OutlinedButton(
            onClick = { start(PhotoSource.Gallery) },
            enabled = remaining > 0
        ) {
            Icon(Icons.Default.PhotoLibrary, contentDescription = null, modifier = Modifier.size(18.dp))
            Spacer(modifier = Modifier.width(8.dp))
            Text("Gallery")
        }
        OutlinedButton(
            onClick = { start(PhotoSource.Camera) },
            enabled = remaining > 0
        ) {
            Icon(Icons.Default.PhotoCamera, contentDescription = null, modifier = Modifier.size(18.dp))
            Spacer(modifier = Modifier.width(8.dp))
            Text("Camera")
        }
    }
}

@Composable
private fun PhotoThumb(
    item: PhotoItem,
    isCover: Boolean,
    canMoveLeft: Boolean,
    canMoveRight: Boolean,
    onMoveLeft: () -> Unit,
    onMoveRight: () -> Unit,
    onRemove: () -> Unit
) {
    Column(horizontalAlignment = Alignment.CenterHorizontally) {
        Box(
            modifier = Modifier
                .size(104.dp)
                .clip(RoundedCornerShape(8.dp))
        ) {
            AsyncImage(
                model = when (item) {
                    is PhotoItem.Existing -> item.photo.storagePath
                    is PhotoItem.New -> item.uri
                },
                contentDescription = if (isCover) "Cover photo" else "Photo",
                contentScale = ContentScale.Crop,
                modifier = Modifier.fillMaxSize()
            )
            if (isCover) {
                Surface(
                    color = MaterialTheme.colorScheme.primary,
                    shape = RoundedCornerShape(bottomEnd = 8.dp),
                    modifier = Modifier.align(Alignment.TopStart)
                ) {
                    Text(
                        text = "Cover",
                        style = MaterialTheme.typography.labelSmall,
                        color = MaterialTheme.colorScheme.onPrimary,
                        modifier = Modifier.padding(horizontal = 6.dp, vertical = 2.dp)
                    )
                }
            }
        }
        Row {
            IconButton(onClick = onMoveLeft, enabled = canMoveLeft, modifier = Modifier.size(36.dp)) {
                Icon(Icons.AutoMirrored.Filled.KeyboardArrowLeft, contentDescription = "Move left")
            }
            IconButton(onClick = onRemove, modifier = Modifier.size(36.dp)) {
                Icon(Icons.Default.Delete, contentDescription = "Remove photo", tint = MaterialTheme.colorScheme.error)
            }
            IconButton(onClick = onMoveRight, enabled = canMoveRight, modifier = Modifier.size(36.dp)) {
                Icon(Icons.AutoMirrored.Filled.KeyboardArrowRight, contentDescription = "Move right")
            }
        }
    }
}

private fun <T> List<T>.swap(i: Int, j: Int): List<T> =
    toMutableList().also { val tmp = it[i]; it[i] = it[j]; it[j] = tmp }

/** 40.0 -> "40", 32.5 -> "32.5" for editable text fields. */
private fun formatNumber(value: Double): String =
    if (value % 1.0 == 0.0) value.toLong().toString() else value.toString()

/** Optional retail value and deposit note, with a deposit tip for valuable pieces. */
@Composable
fun DepositFields(
    retailValueText: String,
    onRetailValueChange: (String) -> Unit,
    depositNote: String,
    onDepositNoteChange: (String) -> Unit
) {
    OutlinedTextField(
        value = retailValueText,
        onValueChange = { input -> onRetailValueChange(input.filter { it.isDigit() || it == '.' }) },
        label = { Text("Retail value (\$) - optional") },
        keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Decimal),
        singleLine = true,
        modifier = Modifier.fillMaxWidth()
    )
    val retailValue = retailValueText.toDoubleOrNull()
    if (retailValue != null && retailValue >= DzirentPolicy.DEPOSIT_SUGGESTION_USD) {
        Spacer(modifier = Modifier.height(8.dp))
        PolicyNotice(text = DzirentPolicy.DEPOSIT_TIP)
    }
    Spacer(modifier = Modifier.height(12.dp))
    OutlinedTextField(
        value = depositNote,
        onValueChange = { if (it.length <= DEPOSIT_NOTE_MAX) onDepositNoteChange(it) },
        label = { Text("Deposit note - optional") },
        placeholder = { Text("e.g. \$40 refundable deposit, cash at pickup") },
        supportingText = { Text("${depositNote.length}/$DEPOSIT_NOTE_MAX") },
        singleLine = true,
        modifier = Modifier.fillMaxWidth()
    )
}
