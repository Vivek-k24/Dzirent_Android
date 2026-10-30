package com.dzirent.app.ui

import androidx.compose.foundation.gestures.snapping.rememberSnapFlingBehavior
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.foundation.selection.toggleable
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.CheckCircle
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.ExpandLess
import androidx.compose.material.icons.filled.ExpandMore
import androidx.compose.material.icons.filled.RadioButtonUnchecked
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.heading
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.semantics.stateDescription
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.unit.dp
import com.dzirent.app.data.model.Listing
import com.dzirent.app.data.model.Profile
import com.dzirent.app.data.repository.DzirentRepository
import com.dzirent.app.ui.theme.DzirentPass
import com.dzirent.app.util.AccessoryDeclaration
import com.dzirent.app.util.GarmentKind
import com.dzirent.app.util.PRICE_NOTE_MAX
import com.dzirent.app.util.Sizes
import com.dzirent.app.util.Step2Input
import com.dzirent.app.util.accessoryValueError
import com.dzirent.app.util.availabilityChipLabel
import com.dzirent.app.util.copiedMeasurements
import com.dzirent.app.util.firstOverlap
import com.dzirent.app.util.measurementLabelsFor
import com.dzirent.app.util.readyList
import com.dzirent.app.util.sameAsCandidate
import com.dzirent.app.util.sameAsQuestion
import java.time.LocalDate

/** Step 2: size, measurements, price (and the accessory tick), dates, more options. */
@OptIn(ExperimentalLayoutApi::class)
@Composable
fun SizePriceDatesStep(
    state: ListingEditorState,
    repository: DzirentRepository,
    profile: Profile,
    today: LocalDate,
    onWhatCanIList: () -> Unit
) {
    val kind = state.kind
    LaunchedEffect(kind, state.itemType) {
        state.refreshFixedSize()
        state.refreshMeasurementTemplate()
    }
    // My other listings, for "Same as your …?". Never my body measurements.
    val mine by produceState<List<Listing>>(emptyList()) {
        value = runCatching { repository.getMyListings(profile.id) }.getOrDefault(emptyList())
    }

    // ---- What it is, and its size ----
    SectionTitle("Size")
    Text(
        text = if (kind == null) "What is it? Pick one to see the sizes." else "What it is",
        style = MaterialTheme.typography.bodySmall,
        color = MaterialTheme.colorScheme.onSurfaceVariant
    )
    FlowRow(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
        GarmentKind.entries.forEach { option ->
            FilterChip(
                selected = kind == option,
                onClick = { state.pickedKind = option },
                label = { Text(option.label) }
            )
        }
    }
    Spacer(modifier = Modifier.height(8.dp))
    if (kind != null) SizePicker(kind = kind, sizeLabel = state.sizeLabel, onSizeLabel = { state.sizeLabel = it })
    Spacer(modifier = Modifier.height(16.dp))

    // ---- Measurements (open by default) ----
    OutlinedCard(modifier = Modifier.fillMaxWidth()) {
        Column(modifier = Modifier.padding(12.dp)) {
            val candidate = sameAsCandidate(mine, kind, state.original?.id)
            if (candidate != null && !state.sameAsAnswered && state.usedMeasurementRows.isEmpty()) {
                Text(sameAsQuestion(candidate), style = MaterialTheme.typography.bodyMedium, fontWeight = FontWeight.Medium)
                Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    Button(onClick = {
                        val (rows, unit) = copiedMeasurements(candidate)
                        state.measurementRows = rows.map { (label, value) -> MeasurementRow(label = label, valueText = formatNumber(value)) }
                        unit?.let { state.unit = it }
                        state.sameAsAnswered = true
                    }) { Text("Yes") }
                    OutlinedButton(onClick = { state.sameAsAnswered = true }) { Text("No") }
                }
                Spacer(modifier = Modifier.height(8.dp))
            }
            MeasurementsSection(
                suggestions = measurementLabelsFor(kind, state.itemType),
                unit = state.unit,
                onUnitChange = { state.unit = it },
                rows = state.measurementRows,
                onRowsChange = { state.measurementRows = it },
                showAllErrors = false
            )
        }
    }
    Spacer(modifier = Modifier.height(16.dp))

    // ---- Price ----
    OutlinedTextField(
        value = state.priceText,
        onValueChange = { input -> state.priceText = input.filter { it.isDigit() || it == '.' } },
        label = { Text("Price (\$)") },
        prefix = { Text("\$") },
        supportingText = { Text("Per rental. Required.") },
        keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Decimal),
        singleLine = true,
        modifier = Modifier.fillMaxWidth()
    )
    if (state.isAccessory) {
        Spacer(modifier = Modifier.height(8.dp))
        Row(
            verticalAlignment = Alignment.CenterVertically,
            modifier = Modifier
                .fillMaxWidth()
                .toggleable(
                    value = state.listingDeclarationChecked,
                    role = Role.Checkbox,
                    onValueChange = { state.listingDeclarationChecked = it }
                )
        ) {
            Checkbox(checked = state.listingDeclarationChecked, onCheckedChange = null)
            Spacer(modifier = Modifier.width(8.dp))
            Text(AccessoryDeclaration.LISTING_CHECKBOX, style = MaterialTheme.typography.bodyMedium)
        }
    }
    Spacer(modifier = Modifier.height(16.dp))

    // ---- Dates ----
    AvailabilityChips(ranges = state.ranges, today = today, onRangesChange = { state.ranges = it })
    Spacer(modifier = Modifier.height(16.dp))

    // ---- More options (collapsed) ----
    val retailError = accessoryValueError(state.isAccessory, state.retailValueText)
    var moreOpen by remember { mutableStateOf(retailError != null) }
    TextButton(onClick = { moreOpen = !moreOpen }, contentPadding = PaddingValues(horizontal = 4.dp)) {
        Text("More options")
        Icon(if (moreOpen) Icons.Default.ExpandLess else Icons.Default.ExpandMore, contentDescription = null)
    }
    if (moreOpen || retailError != null) {
        OutlinedTextField(
            value = state.priceNote,
            onValueChange = { if (it.length <= PRICE_NOTE_MAX) state.priceNote = it },
            label = { Text("Price details - optional") },
            placeholder = { Text("e.g. for 3 days, cleaning included") },
            singleLine = true,
            modifier = Modifier.fillMaxWidth()
        )
        Spacer(modifier = Modifier.height(12.dp))
        DepositFields(
            retailValueText = state.retailValueText,
            onRetailValueChange = { state.retailValueText = it },
            depositNote = state.depositNote,
            onDepositNoteChange = { state.depositNote = it },
            retailError = retailError
        )
    }
    TextButton(onClick = onWhatCanIList, contentPadding = PaddingValues(horizontal = 4.dp)) { Text(DzirentPolicy.WHAT_CAN_I_LIST_TITLE) }
}

@Composable
private fun SectionTitle(text: String) {
    Text(
        text = text,
        style = MaterialTheme.typography.titleMedium,
        fontWeight = FontWeight.Bold,
        modifier = Modifier.semantics { heading() }
    )
}

/**
 * The size picker for [kind]: one horizontal, snapping row per step (waist, then inseam), swipe
 * or tap. Each choice is a chip a screen reader reads as "Waist 32, selected". Dresses and
 * skirts can switch between letter and US sizes.
 */
@Composable
fun SizePicker(kind: GarmentKind, sizeLabel: String, onSizeLabel: (String) -> Unit) {
    val systems = Sizes.systemsFor(kind)
    if (systems.isEmpty()) {
        Text("Size: ${Sizes.ONE_SIZE}", style = MaterialTheme.typography.bodyLarge)
        return
    }
    val parsed = remember(kind) { Sizes.parse(kind, sizeLabel) }
    var systemIndex by remember(kind) { mutableIntStateOf(parsed?.let { systems.indexOf(it.first) }?.coerceAtLeast(0) ?: 0) }
    val system = systems[systemIndex]
    val picks = remember(kind, systemIndex) {
        mutableStateListOf<String?>().apply {
            addAll(if (parsed?.first == system) parsed.second else List(system.steps.size) { null })
        }
    }
    if (systems.size > 1) {
        SingleChoiceSegmentedButtonRow {
            systems.forEachIndexed { index, option ->
                SegmentedButton(
                    selected = index == systemIndex,
                    onClick = {
                        if (index != systemIndex) {
                            systemIndex = index
                            onSizeLabel("")
                        }
                    },
                    shape = SegmentedButtonDefaults.itemShape(index = index, count = systems.size)
                ) { Text(option.label) }
            }
        }
        Spacer(modifier = Modifier.height(8.dp))
    }
    system.steps.forEachIndexed { stepIndex, step ->
        Text(step.title, style = MaterialTheme.typography.labelLarge, modifier = Modifier.semantics { heading() })
        val listState = rememberLazyListState()
        val selected = picks[stepIndex]?.let { step.options.indexOf(it) } ?: -1
        LaunchedEffect(kind, systemIndex) {
            if (selected > 0) listState.scrollToItem((selected - 2).coerceAtLeast(0))
        }
        LazyRow(
            state = listState,
            flingBehavior = rememberSnapFlingBehavior(listState),
            horizontalArrangement = Arrangement.spacedBy(8.dp),
            modifier = Modifier.fillMaxWidth()
        ) {
            itemsIndexed(step.options, key = { _, option -> option }) { _, option ->
                val isSelected = picks[stepIndex] == option
                FilterChip(
                    selected = isSelected,
                    onClick = {
                        picks[stepIndex] = option
                        val chosen = picks.toList()
                        onSizeLabel(if (chosen.all { it != null }) system.format(chosen.map { it!! }) else "")
                    },
                    label = { Text(option) },
                    modifier = Modifier.semantics {
                        contentDescription = "${step.title} $option"
                        stateDescription = if (isSelected) "Selected" else "Not selected"
                    }
                )
            }
        }
        Spacer(modifier = Modifier.height(4.dp))
    }
    Text(
        text = if (sizeLabel.isBlank()) "Pick a size" else "Size: $sizeLabel",
        style = MaterialTheme.typography.bodyMedium,
        fontWeight = FontWeight.Medium
    )
}

/** Available dates as chips: "Next 30 days" to start, each with ✕, and "+ Add". */
@OptIn(ExperimentalLayoutApi::class, ExperimentalMaterial3Api::class)
@Composable
private fun AvailabilityChips(ranges: List<com.dzirent.app.util.DateRange>, today: LocalDate, onRangesChange: (List<com.dzirent.app.util.DateRange>) -> Unit) {
    var showPicker by remember { mutableStateOf(false) }
    var error by remember { mutableStateOf<String?>(null) }
    SectionTitle("Available dates")
    Text(
        text = "Required. Renters find your piece only for dates inside these.",
        style = MaterialTheme.typography.bodySmall,
        color = MaterialTheme.colorScheme.onSurfaceVariant
    )
    FlowRow(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
        ranges.sortedBy { it.start }.forEach { range ->
            val label = availabilityChipLabel(range, today)
            InputChip(
                selected = true,
                onClick = {},
                label = { Text(label) },
                trailingIcon = {
                    IconButton(onClick = { onRangesChange(ranges - range); error = null }, modifier = Modifier.size(24.dp)) {
                        Icon(Icons.Default.Close, contentDescription = "Remove $label", modifier = Modifier.size(16.dp))
                    }
                }
            )
        }
        AssistChip(
            onClick = { showPicker = true; error = null },
            label = { Text("Add") },
            leadingIcon = { Icon(Icons.Default.Add, contentDescription = null, modifier = Modifier.size(16.dp)) }
        )
    }
    error?.let { Text(text = it, color = MaterialTheme.colorScheme.error, style = MaterialTheme.typography.bodySmall) }
    if (showPicker) {
        DateRangePickerDialog(
            title = "Select available dates",
            allowPast = false,
            onDismiss = { showPicker = false },
            onConfirm = { picked ->
                showPicker = false
                val clash = firstOverlap(picked, ranges)
                if (clash != null) {
                    error = "Those dates overlap ${clash.format()}. Pick different dates or remove that range first."
                } else {
                    onRangesChange(ranges + picked)
                }
            }
        )
    }
}

/** The short ready list above "Publish listing". */
@OptIn(ExperimentalLayoutApi::class)
@Composable
fun ReadyList(step1Done: Boolean, input: Step2Input) {
    FlowRow(horizontalArrangement = Arrangement.spacedBy(12.dp)) {
        readyList(step1Done, input).forEach { item ->
            Row(
                verticalAlignment = Alignment.CenterVertically,
                modifier = Modifier.semantics(mergeDescendants = true) {
                    contentDescription = item.label + if (item.done) ", ready" else ", not ready"
                }
            ) {
                Icon(
                    if (item.done) Icons.Default.CheckCircle else Icons.Default.RadioButtonUnchecked,
                    contentDescription = null,
                    tint = if (item.done) DzirentPass else MaterialTheme.colorScheme.onSurfaceVariant,
                    modifier = Modifier.size(16.dp)
                )
                Spacer(modifier = Modifier.width(4.dp))
                Text(item.label, style = MaterialTheme.typography.labelMedium)
            }
        }
    }
}
