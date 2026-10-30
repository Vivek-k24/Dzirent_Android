package com.dzirent.app.ui

import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.unit.dp
import com.dzirent.app.BuildConfig
import com.dzirent.app.data.DeclarationCache
import com.dzirent.app.data.DraftStore
import com.dzirent.app.data.ListingDraft
import com.dzirent.app.data.ListingPublisher
import com.dzirent.app.data.PhotoAnalyzer
import com.dzirent.app.data.PhotoItem
import com.dzirent.app.data.PhotoStore
import com.dzirent.app.data.PublishStepException
import com.dzirent.app.data.model.*
import com.dzirent.app.data.repository.DzirentRepository
import com.dzirent.app.util.AccessoryDeclaration
import com.dzirent.app.util.DistanceUnit
import com.dzirent.app.util.ItemGuess
import com.dzirent.app.util.PhotoSlot
import com.dzirent.app.util.SavedDraft
import com.dzirent.app.util.TierVerdict
import com.dzirent.app.util.declarationNeeded
import com.dzirent.app.util.guessItem
import com.dzirent.app.util.keepLabel
import com.dzirent.app.util.nameColors
import com.dzirent.app.util.parsePrice
import com.dzirent.app.util.photosToRemove
import com.dzirent.app.util.step1Missing
import com.dzirent.app.util.step2Missing
import com.dzirent.app.util.tierVerdict
import kotlinx.coroutines.FlowPreview
import kotlinx.coroutines.flow.debounce
import kotlinx.coroutines.flow.drop
import kotlinx.coroutines.launch
import java.time.LocalDate
import java.util.UUID

private const val DEPOSIT_NOTE_MAX = 120
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

/**
 * New listing in two steps. Step 1: photos in slots (Front, Back, Close-up required), checked
 * and recognized on the phone, with title, category, description and style filled from them.
 * Step 2: size, measurements, price and dates, then Publish. Editing uses the same steps,
 * prefilled. New listings are saved as a draft on the phone as they change.
 */
@OptIn(ExperimentalMaterial3Api::class, FlowPreview::class)
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
    val settings = LocalAppSettings.current
    val today = remember { LocalDate.now() }
    val state = remember { ListingEditorState(original, settings.measureUnit.key, today) }
    val isNew = state.isNew
    val listingId = remember { original?.id ?: UUID.randomUUID().toString() }
    val publisher = remember { ListingPublisher(repository, context, original) }
    val analyzer = remember { PhotoAnalyzer(context) }
    val photoStore = remember { PhotoStore(context, profile.id) }
    val drafts = remember { DraftStore(context, profile.id, original?.id) }
    val declarationCache = remember { DeclarationCache(context) }
    val snackbar = remember { SnackbarHostState() }

    var errorMessage by remember { mutableStateOf<String?>(null) }
    var publishState by remember { mutableStateOf<PublishState>(PublishState.Idle) }
    var showWhatCanIList by remember { mutableStateOf(false) }

    // ---- Accessory declaration (once per user) ----
    var declarationSigned by remember { mutableStateOf(declarationCache.isSigned(profile.id, AccessoryDeclaration.VERSION)) }
    var declarationChecked by remember { mutableStateOf(declarationSigned) }
    var showDeclaration by remember { mutableStateOf(false) }
    var declarationOffered by remember { mutableStateOf(false) }
    LaunchedEffect(Unit) {
        if (!declarationSigned) {
            // Offline, this says "not signed"; signing again only adds another record.
            val signed = runCatching { repository.hasAccessoryDeclaration(profile.id, AccessoryDeclaration.VERSION) }.getOrDefault(false)
            if (signed) {
                declarationCache.markSigned(profile.id, AccessoryDeclaration.VERSION)
                declarationSigned = true
            }
        }
        declarationChecked = true
    }
    val declarationPending = declarationNeeded(state.category, state.jewelry, declarationSigned)
    // Shown by itself the first time it's needed (Accessories picked or suggested, or jewelry
    // found), once the server has said whether it was signed on another phone.
    LaunchedEffect(declarationPending, declarationChecked) {
        if (declarationPending && declarationChecked && !declarationOffered) {
            declarationOffered = true
            showDeclaration = true
        }
    }

    fun logEvents(vararg events: SuggestionEvent) {
        scope.launch { repository.logSuggestionEvents(profile.id, events.toList()) }
    }

    // ---- Removals: not fashion / not allowed photos come out at once ----
    fun announceRemoval(removed: RemovedPhoto) {
        logEvents(SuggestionEvent("tier", removed.verdict.label?.name ?: "not fashion", "removed", original?.id))
        scope.launch {
            snackbar.currentSnackbarData?.dismiss()
            val result = snackbar.showSnackbar(
                message = removed.verdict.message,
                actionLabel = keepLabel(removed.verdict.tier),
                withDismissAction = true,
                duration = SnackbarDuration.Long
            )
            if (result == SnackbarResult.ActionPerformed && state.restore(removed)) {
                logEvents(SuggestionEvent("tier", removed.verdict.label?.name ?: "not fashion", "kept_override", original?.id))
            }
        }
    }

    /** Takes out every photo the tier check removes; true if any was. */
    fun removeFlaggedPhotos(): Boolean {
        val flagged = photosToRemove(state.slotPhotos, state.kept.toSet())
        flagged.forEach { photo ->
            val item = state.slots[photo.slot] ?: return@forEach
            state.removePhoto(photo.slot)
            announceRemoval(RemovedPhoto(photo.slot, item, photo.verdict as TierVerdict.Remove))
        }
        return flagged.isNotEmpty()
    }

    // ---- Each new photo is checked once, on the phone, as it's added ----
    val photoKeys = state.slots.values.map { it.key }.toSet()
    LaunchedEffect(photoKeys) {
        state.slots.values.filterIsInstance<PhotoItem.New>().filter { it.key !in state.reviews }.forEach { item ->
            state.reviews[item.key] = com.dzirent.app.util.PhotoReview.Checking
            // The form's scope, so moving on doesn't cancel a check in progress.
            scope.launch {
                val analysis = analyzer.analyze(item.uri)
                state.analyses[item.key] = analysis
                state.reviews[item.key] = state.reviewFor(analysis)
                removeFlaggedPhotos()
            }
        }
    }

    // ---- What it is: Front, Back and Extra together; again whenever they change ----
    val typedKeys = state.typedAnalyses().map { it.first to state.slots[it.first]?.key }
    LaunchedEffect(typedKeys) {
        val combined = state.combinedEmbedding()
        if (combined == null) {
            // No front photo (yet): nothing to suggest or ask.
            state.guess = ItemGuess.Unsure
            return@LaunchedEffect
        }
        val scores = analyzer.scores(combined) ?: return@LaunchedEffect
        val colors = state.typedAnalyses().fold(com.dzirent.app.util.ColorVotes.EMPTY) { acc, (_, a) -> acc + a.colors }
        state.colorVotes = colors
        val guess = guessItem(scores, nameColors(colors))
        state.guess = guess
        // Fabric from the close-up when there is one: that's where the weave shows.
        val closeUp = state.slots[PhotoSlot.CLOSE_UP]?.let { state.analyses[it.key]?.scores }
        state.fabricHintScores = (closeUp ?: scores).fabrics
        if (guess is ItemGuess.Suggest) state.applySuggestion(guess.suggestions)
    }

    // ---- Drafts: restore, and save as things change ----
    var draftOffer by remember { mutableStateOf<SavedDraft?>(null) }
    var draftChecked by remember { mutableStateOf(false) }
    var finished by remember { mutableStateOf(false) }
    // Survives rotation and the app being stopped while the camera is open: then the form comes
    // back without asking. Opening Create listing afresh asks; opening Edit afresh starts clean.
    var sessionStarted by rememberSaveable { mutableStateOf(false) }
    LaunchedEffect(Unit) {
        val saved = drafts.load()
        when {
            sessionStarted && saved != null -> state.restore(saved)
            !isNew -> drafts.clear()
            saved == null || !saved.hasContent -> photoStore.prune(state.slots.values.map { it.key }.toSet())
            else -> draftOffer = saved
        }
        draftChecked = true
        sessionStarted = true
    }
    if (draftChecked) {
        LaunchedEffect(Unit) {
            snapshotFlow { state.toDraft() }
                .drop(1)
                .debounce(600)
                .collect { draft -> if (draftOffer == null && !finished) drafts.save(draft) }
        }
    }
    draftOffer?.let { saved ->
        AlertDialog(
            onDismissRequest = {},
            title = { Text("Continue your draft?") },
            text = { Text(saved.title.ifBlank { "You have a listing you didn't finish." }) },
            confirmButton = {
                TextButton(onClick = {
                    state.restore(saved)
                    draftOffer = null
                }) { Text("Continue") }
            },
            dismissButton = {
                TextButton(onClick = {
                    draftOffer = null
                    scope.launch {
                        drafts.clear()
                        photoStore.clear()
                    }
                }) { Text("Start over") }
            }
        )
    }

    // ---- Publish ----
    fun publish() {
        // Backstop: the tier check again on every photo, right before publishing.
        if (removeFlaggedPhotos()) {
            errorMessage = "Some photos were removed. Check your photos and try again."
            state.step = 1
            return
        }
        val step1 = step1Missing(state.photosGate(), state.title.text, state.category, state.itemType, declarationPending)
        if (step1 != null) {
            errorMessage = step1.message
            state.step = 1
            if (declarationPending && state.photosGate() == null) showDeclaration = true
            return
        }
        if (isNew && !profile.hasLocation) {
            errorMessage = noLocationToPublish(settings.distanceUnit)
            return
        }
        step2Missing(state.step2Input(profile.hasLocation || !isNew))?.let {
            errorMessage = it.message
            return
        }
        errorMessage = null
        val category = state.category!!
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
        val ordered = PhotoSlot.entries.mapNotNull { slot -> state.slots[slot]?.let { slot to it } }
        val draft = ListingDraft(
            listing = base.copy(
                title = state.title.text.trim(),
                category = category,
                itemType = if (state.isAccessory) state.itemType else null,
                listingStyles = state.styles,
                garmentType = state.kind?.key,
                aiReviewNeeded = state.aiReviewNeeded,
                sizeLabel = state.sizeLabel.trim(),
                description = state.description.text.trim(),
                priceUsd = parsePrice(state.priceText),
                priceNote = state.priceNote.trim(),
                retailValue = state.retailValueText.toDoubleOrNull(),
                depositNote = state.depositNote.trim().ifBlank { null },
                photos = emptyList(),
                measurements = emptyList(),
                availability = emptyList(),
                lender = null
            ),
            photos = ordered.map { it.second },
            slots = ordered.map { it.first.key },
            measurements = state.usedMeasurementRows.map { row ->
                ListingMeasurement(
                    id = row.key,
                    listingId = listingId,
                    label = row.label.trim(),
                    value = row.valueText.trim().toDouble(),
                    unit = state.unit
                )
            },
            availability = state.ranges.sortedBy { it.start }.map { range ->
                Availability(
                    id = UUID.randomUUID().toString(),
                    listingId = listingId,
                    startDate = range.start.toString(),
                    endDate = range.end.toString()
                )
            },
            listingDeclarationVersion = AccessoryDeclaration.VERSION.takeIf { state.isAccessory }
        )
        scope.launch {
            publishState = PublishState.Running("Starting...")
            try {
                publisher.publish(draft) { progress -> publishState = PublishState.Running(progress) }
                finished = true
                repository.logSuggestionEvents(profile.id, state.outcomeEvents(listingId))
                drafts.clear()
                // An edit only removes its own copies: a new-listing draft may use the folder too.
                if (isNew) photoStore.clear() else photoStore.delete(draft.photos.filterIsInstance<PhotoItem.New>().map { it.key })
                publishState = PublishState.Idle
                onSaved()
            } catch (e: PublishStepException) {
                publishState = PublishState.Failed(e.step, e.message ?: "Please try again.")
            }
        }
    }

    when (val ps = publishState) {
        is PublishState.Running -> {
            LoadingView(message = ps.progress)
            return
        }
        is PublishState.Failed -> {
            PublishFailedView(
                step = ps.step,
                message = ps.message,
                isNew = isNew,
                onRetry = { publish() },
                onBackToEdit = { publishState = PublishState.Idle }
            )
            return
        }
        PublishState.Idle -> Unit
    }

    if (showDeclaration) {
        AccessoryDeclarationScreen(
            onSign = { name ->
                repository.recordAccessoryDeclaration(name, AccessoryDeclaration.VERSION, BuildConfig.VERSION_NAME)
                declarationCache.markSigned(profile.id, AccessoryDeclaration.VERSION)
                declarationSigned = true
                showDeclaration = false
            },
            onDismiss = { showDeclaration = false }
        )
    }
    if (showWhatCanIList) WhatCanIListSheet(onDismiss = { showWhatCanIList = false })

    Box(modifier = Modifier.fillMaxSize()) {
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
            Text(
                text = "Step ${state.step} of 2 · " + if (state.step == 1) "Photos and details" else "Size, price and dates",
                style = MaterialTheme.typography.labelLarge,
                color = MaterialTheme.colorScheme.primary
            )
            Spacer(modifier = Modifier.height(8.dp))

            if (isNew && !profile.hasLocation) {
                Card(
                    colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.errorContainer),
                    modifier = Modifier.fillMaxWidth().padding(bottom = 16.dp)
                ) {
                    Column(modifier = Modifier.padding(16.dp)) {
                        Text(text = noLocationToPublish(settings.distanceUnit), color = MaterialTheme.colorScheme.onErrorContainer)
                        Spacer(modifier = Modifier.height(8.dp))
                        TextButton(onClick = onSetLocation) { Text("Set my location") }
                    }
                }
            }

            errorMessage?.let {
                Card(
                    colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.errorContainer),
                    modifier = Modifier.fillMaxWidth().padding(bottom = 16.dp)
                ) {
                    Text(text = it, color = MaterialTheme.colorScheme.onErrorContainer, modifier = Modifier.padding(16.dp))
                }
            }

            if (state.step == 1) {
                val missing = step1Missing(state.photosGate(), state.title.text, state.category, state.itemType, declarationPending)
                PhotosAndDetailsStep(
                    state = state,
                    photoStore = photoStore,
                    onMessage = { errorMessage = it },
                    onChipPicked = { label -> logEvents(SuggestionEvent("type_chip", label.name, "picked", original?.id)) },
                    onPolicyWrong = { photo ->
                        state.overruled += photo.key
                        state.aiReviewNeeded = true
                        val label = (photo.verdict as? TierVerdict.NotOffered)?.label?.name.orEmpty()
                        logEvents(SuggestionEvent("tier", label, "kept_override", original?.id))
                        errorMessage = null
                    },
                    onOpenDeclaration = { showDeclaration = true }
                )
                Spacer(modifier = Modifier.height(24.dp))
                Button(
                    onClick = {
                        if (missing == null) {
                            errorMessage = null
                            state.step = 2
                        } else {
                            errorMessage = missing.message
                            if (missing.button == "Sign the declaration") showDeclaration = true
                        }
                    },
                    modifier = Modifier.fillMaxWidth().height(50.dp)
                ) { Text(missing?.button ?: "Next") }
            } else {
                SizePriceDatesStep(
                    state = state,
                    repository = repository,
                    profile = profile,
                    today = today,
                    onWhatCanIList = { showWhatCanIList = true }
                )
                Spacer(modifier = Modifier.height(16.dp))
                val step1 = step1Missing(state.photosGate(), state.title.text, state.category, state.itemType, declarationPending)
                val missing = step1 ?: step2Missing(state.step2Input(profile.hasLocation || !isNew))
                ReadyList(step1Done = step1 == null, input = state.step2Input(profile.hasLocation || !isNew))
                Spacer(modifier = Modifier.height(12.dp))
                Button(
                    onClick = { publish() },
                    modifier = Modifier.fillMaxWidth().height(50.dp)
                ) { Text(missing?.button ?: if (isNew) "Publish listing" else "Save changes") }
                TextButton(onClick = { errorMessage = null; state.step = 1 }, modifier = Modifier.align(Alignment.CenterHorizontally)) {
                    Text("Back to photos and details")
                }
            }
        }
        SnackbarHost(hostState = snackbar, modifier = Modifier.align(Alignment.BottomCenter).padding(16.dp))
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

/** Optional retail value and deposit note, with a deposit tip for valuable pieces. */
@Composable
fun DepositFields(
    retailValueText: String,
    onRetailValueChange: (String) -> Unit,
    depositNote: String,
    onDepositNoteChange: (String) -> Unit,
    retailError: String? = null
) {
    OutlinedTextField(
        value = retailValueText,
        onValueChange = { input -> onRetailValueChange(input.filter { it.isDigit() || it == '.' }) },
        label = { Text("Retail value (\$) - optional") },
        isError = retailError != null,
        supportingText = retailError?.let { { Text(it) } },
        keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Decimal),
        singleLine = true,
        modifier = Modifier.fillMaxWidth()
    )
    val retailValue = retailValueText.toDoubleOrNull()
    if (retailError == null && retailValue != null && retailValue >= DzirentPolicy.DEPOSIT_SUGGESTION_USD) {
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
