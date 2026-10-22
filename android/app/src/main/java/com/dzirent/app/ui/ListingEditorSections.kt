package com.dzirent.app.ui

import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.DateRange
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.unit.dp
import com.dzirent.app.data.model.Categories
import com.dzirent.app.util.DateRange
import com.dzirent.app.util.MEASUREMENT_LABEL_MAX
import com.dzirent.app.util.MeasurementInput
import com.dzirent.app.util.firstOverlap
import com.dzirent.app.util.validateMeasurements
import java.time.Instant
import java.time.LocalDate
import java.time.ZoneOffset
import java.util.UUID

/** One measurement row in the editor; [key] doubles as the row id when saved. */
data class MeasurementRow(
    val key: String = UUID.randomUUID().toString(),
    val label: String = "",
    val valueText: String = ""
) {
    fun toInput() = MeasurementInput(label, valueText)
}

/**
 * Dynamic measurement rows with one cm/in unit for the whole listing and quick-add chips
 * for the category. Errors show once a value is typed, or for every row after [showAllErrors].
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun MeasurementsSection(
    category: String,
    unit: String,
    onUnitChange: (String) -> Unit,
    rows: List<MeasurementRow>,
    onRowsChange: (List<MeasurementRow>) -> Unit,
    showAllErrors: Boolean
) {
    val errors = validateMeasurements(rows.map { it.toInput() })

    Row(verticalAlignment = Alignment.CenterVertically) {
        Column(modifier = Modifier.weight(1f)) {
            Text(text = "Measurements", style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.Bold)
            Text(
                text = "Optional, but renters love them.",
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )
        }
        val units = listOf("cm", "in")
        SingleChoiceSegmentedButtonRow {
            units.forEachIndexed { index, option ->
                SegmentedButton(
                    selected = unit == option,
                    onClick = { onUnitChange(option) },
                    shape = SegmentedButtonDefaults.itemShape(index = index, count = units.size)
                ) { Text(option) }
            }
        }
    }
    Spacer(modifier = Modifier.height(8.dp))

    rows.forEachIndexed { index, row ->
        val error = errors[index]?.takeIf { showAllErrors || row.valueText.isNotBlank() }
        Row(verticalAlignment = Alignment.Top) {
            OutlinedTextField(
                value = row.label,
                onValueChange = { text ->
                    if (text.length <= MEASUREMENT_LABEL_MAX) {
                        onRowsChange(rows.map { if (it.key == row.key) it.copy(label = text) else it })
                    }
                },
                label = { Text("Label") },
                singleLine = true,
                isError = error != null,
                modifier = Modifier.weight(1f)
            )
            Spacer(modifier = Modifier.width(8.dp))
            OutlinedTextField(
                value = row.valueText,
                onValueChange = { text ->
                    val cleaned = text.filter { it.isDigit() || it == '.' }
                    onRowsChange(rows.map { if (it.key == row.key) it.copy(valueText = cleaned) else it })
                },
                label = { Text("Value") },
                suffix = { Text(unit) },
                singleLine = true,
                isError = error != null,
                keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Decimal),
                modifier = Modifier.width(112.dp)
            )
            IconButton(
                onClick = { onRowsChange(rows.filterNot { it.key == row.key }) },
                modifier = Modifier.padding(top = 8.dp)
            ) {
                Icon(Icons.Default.Close, contentDescription = "Remove ${row.label.ifBlank { "measurement" }}")
            }
        }
        if (error != null) {
            Text(
                text = error,
                color = MaterialTheme.colorScheme.error,
                style = MaterialTheme.typography.bodySmall,
                modifier = Modifier.padding(start = 4.dp, bottom = 4.dp)
            )
        }
        Spacer(modifier = Modifier.height(4.dp))
    }

    val present = rows.map { it.label.trim().lowercase() }.toSet()
    val suggestions = Categories.SUGGESTIONS[category].orEmpty().filter { it.lowercase() !in present }
    if (suggestions.isNotEmpty()) {
        LazyRow(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            items(suggestions) { suggestion ->
                AssistChip(
                    onClick = { onRowsChange(rows + MeasurementRow(label = suggestion)) },
                    label = { Text(suggestion) },
                    leadingIcon = { Icon(Icons.Default.Add, contentDescription = null, modifier = Modifier.size(16.dp)) }
                )
            }
        }
    }
    TextButton(onClick = { onRowsChange(rows + MeasurementRow()) }) {
        Icon(Icons.Default.Add, contentDescription = null, modifier = Modifier.size(18.dp))
        Spacer(modifier = Modifier.width(4.dp))
        Text("Add measurement")
    }
}

/** Availability ranges picked with a DateRangePicker; no past days and no overlaps. */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun AvailabilitySection(
    ranges: List<DateRange>,
    onRangesChange: (List<DateRange>) -> Unit
) {
    var showPicker by remember { mutableStateOf(false) }
    var error by remember { mutableStateOf<String?>(null) }

    Text(text = "Available dates", style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.Bold)
    Text(
        text = "Optional. Renters see these on your listing.",
        style = MaterialTheme.typography.bodySmall,
        color = MaterialTheme.colorScheme.onSurfaceVariant
    )
    Spacer(modifier = Modifier.height(4.dp))

    ranges.sortedBy { it.start }.forEach { range ->
        Row(verticalAlignment = Alignment.CenterVertically) {
            Icon(
                imageVector = Icons.Default.DateRange,
                contentDescription = null,
                tint = MaterialTheme.colorScheme.primary,
                modifier = Modifier.size(18.dp)
            )
            Spacer(modifier = Modifier.width(8.dp))
            Text(text = range.format(), modifier = Modifier.weight(1f))
            IconButton(onClick = { onRangesChange(ranges - range); error = null }) {
                Icon(Icons.Default.Close, contentDescription = "Remove ${range.format()}")
            }
        }
    }
    error?.let {
        Text(text = it, color = MaterialTheme.colorScheme.error, style = MaterialTheme.typography.bodySmall)
    }
    OutlinedButton(onClick = { showPicker = true; error = null }) {
        Icon(Icons.Default.Add, contentDescription = null, modifier = Modifier.size(18.dp))
        Spacer(modifier = Modifier.width(8.dp))
        Text("Add dates")
    }

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

/**
 * Material 3 DateRangePicker in a dialog. A single tapped day counts as a one-day range.
 * With [allowPast] false, days before today can't be picked.
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun DateRangePickerDialog(
    title: String,
    allowPast: Boolean,
    initial: DateRange? = null,
    confirmLabel: String = "Add",
    onDismiss: () -> Unit,
    onConfirm: (DateRange) -> Unit
) {
    val todayUtcMillis = remember { LocalDate.now().atStartOfDay(ZoneOffset.UTC).toInstant().toEpochMilli() }
    val currentYear = remember { LocalDate.now().year }
    val state = rememberDateRangePickerState(
        initialSelectedStartDateMillis = initial?.start?.atStartOfDay(ZoneOffset.UTC)?.toInstant()?.toEpochMilli(),
        initialSelectedEndDateMillis = initial?.end?.atStartOfDay(ZoneOffset.UTC)?.toInstant()?.toEpochMilli(),
        selectableDates = object : SelectableDates {
            override fun isSelectableDate(utcTimeMillis: Long) = allowPast || utcTimeMillis >= todayUtcMillis
            override fun isSelectableYear(year: Int) = allowPast || year >= currentYear
        }
    )
    val startMillis = state.selectedStartDateMillis

    DatePickerDialog(
        onDismissRequest = onDismiss,
        confirmButton = {
            TextButton(
                onClick = {
                    val start = startMillis?.toUtcDate() ?: return@TextButton
                    val end = state.selectedEndDateMillis?.toUtcDate() ?: start
                    onConfirm(DateRange(start, end))
                },
                enabled = startMillis != null
            ) { Text(confirmLabel) }
        },
        dismissButton = {
            TextButton(onClick = onDismiss) { Text("Cancel") }
        }
    ) {
        DateRangePicker(
            state = state,
            title = { Text(title, modifier = Modifier.padding(start = 24.dp, end = 12.dp, top = 16.dp)) },
            showModeToggle = false,
            modifier = Modifier.weight(1f)
        )
    }
}

private fun Long.toUtcDate(): LocalDate = Instant.ofEpochMilli(this).atZone(ZoneOffset.UTC).toLocalDate()
