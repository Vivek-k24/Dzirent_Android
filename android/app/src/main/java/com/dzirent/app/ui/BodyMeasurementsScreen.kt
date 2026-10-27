package com.dzirent.app.ui

import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.unit.dp
import com.dzirent.app.data.model.PrivateProfile
import com.dzirent.app.data.repository.DzirentRepository
import com.dzirent.app.util.BodyMeasurement
import com.dzirent.app.util.FitProfile
import com.dzirent.app.util.bodyInputToCm
import com.dzirent.app.util.cmToBodyInput
import com.dzirent.app.util.isValidBodyCm
import kotlinx.coroutines.launch

private const val MEASUREMENTS_PRIVACY_NOTE =
    "We only use your measurements to make your search easier and show pieces likely to fit " +
        "you. They're private, so no one else can see them. If you're a lender, your listings " +
        "only show the garment's measurements, never your body measurements."

private const val HOW_TO_MEASURE =
    "Use a soft tape measure over light clothing. Stand straight, breathe normally, and keep " +
        "the tape level and snug but not tight. Ask a friend for shoulder and inseam."

/** Profile > My measurements: optional body measurements, visible only to me. */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun BodyMeasurementsScreen(
    repository: DzirentRepository,
    userId: String,
    onBack: () -> Unit
) {
    var loadState by remember { mutableStateOf<UiState<PrivateProfile>>(UiState.Loading) }
    val scope = rememberCoroutineScope()

    fun load() {
        scope.launch {
            loadState = UiState.Loading
            loadState = try {
                UiState.Success(repository.getPrivateProfile(userId))
            } catch (e: Exception) {
                UiState.Error(e.userMessage("Couldn't load your measurements. Please try again."))
            }
        }
    }

    LaunchedEffect(userId) { load() }

    Column(modifier = Modifier.fillMaxSize()) {
        TopAppBar(
            title = { Text("My measurements") },
            navigationIcon = {
                IconButton(onClick = onBack) {
                    Icon(Icons.AutoMirrored.Filled.ArrowBack, contentDescription = "Back")
                }
            }
        )
        when (val state = loadState) {
            is UiState.Loading -> LoadingView(message = "Loading your measurements...")
            is UiState.Error -> ErrorView(message = state.message, onRetry = { load() })
            is UiState.Success -> MeasurementsForm(
                repository = repository,
                userId = userId,
                saved = state.data,
                onSaved = onBack
            )
        }
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun MeasurementsForm(
    repository: DzirentRepository,
    userId: String,
    saved: PrivateProfile,
    onSaved: () -> Unit
) {
    val scope = rememberCoroutineScope()
    // Inches by default for the US pilot; values are stored in cm either way.
    var unit by rememberSaveable { mutableStateOf("in") }
    // "I'm a": optional, nothing preselected. Only changes the Chest/Bust wording and the fit rule.
    var fit by rememberSaveable { mutableStateOf(saved.fit) }
    val texts = remember {
        mutableStateMapOf<BodyMeasurement, String>().apply {
            BodyMeasurement.entries.forEach { put(it, cmToBodyInput(saved.bodyCm(it), "in")) }
        }
    }
    var showErrors by remember { mutableStateOf(false) }
    var isSaving by remember { mutableStateOf(false) }
    var saveError by remember { mutableStateOf<String?>(null) }

    fun errorFor(measurement: BodyMeasurement): String? {
        val text = texts[measurement].orEmpty()
        if (text.isBlank()) return null
        val cm = bodyInputToCm(text, unit) ?: return "Enter a number, like 36 or 36.5."
        return if (isValidBodyCm(cm)) null else "That doesn't look right. Check the number and unit."
    }

    fun changeUnit(newUnit: String) {
        if (newUnit == unit) return
        // Convert what's typed so switching units never changes a measurement.
        BodyMeasurement.entries.forEach { measurement ->
            val cm = bodyInputToCm(texts[measurement].orEmpty(), unit)
            if (cm != null) texts[measurement] = cmToBodyInput(cm, newUnit)
        }
        unit = newUnit
    }

    fun save() {
        if (BodyMeasurement.entries.any { errorFor(it) != null }) {
            showErrors = true
            return
        }
        scope.launch {
            isSaving = true
            saveError = null
            try {
                repository.saveBodyMeasurements(
                    userId,
                    BodyMeasurement.entries.associateWith { bodyInputToCm(texts[it].orEmpty(), unit) },
                    fit
                )
                onSaved()
            } catch (e: Exception) {
                saveError = e.userMessage("Couldn't save your measurements. Please try again.")
            }
            isSaving = false
        }
    }

    Column(
        modifier = Modifier
            .fillMaxSize()
            .verticalScroll(rememberScrollState())
            .padding(horizontal = 24.dp, vertical = 8.dp)
    ) {
        Text("I'm a", style = MaterialTheme.typography.titleSmall, fontWeight = FontWeight.Bold)
        Spacer(modifier = Modifier.height(8.dp))
        val fitOptions = FitProfile.entries
        SingleChoiceSegmentedButtonRow(modifier = Modifier.fillMaxWidth()) {
            fitOptions.forEachIndexed { index, option ->
                SegmentedButton(
                    selected = fit == option,
                    // Tapping the chosen one again clears it, since the choice is optional.
                    onClick = { fit = if (fit == option) null else option },
                    shape = SegmentedButtonDefaults.itemShape(index = index, count = fitOptions.size)
                ) {
                    Text(option.label)
                }
            }
        }
        Spacer(modifier = Modifier.height(16.dp))
        Text(
            text = MEASUREMENTS_PRIVACY_NOTE,
            style = MaterialTheme.typography.bodyMedium,
            color = MaterialTheme.colorScheme.onSurfaceVariant
        )
        Spacer(modifier = Modifier.height(16.dp))
        Text("How to measure", style = MaterialTheme.typography.titleSmall, fontWeight = FontWeight.Bold)
        Spacer(modifier = Modifier.height(4.dp))
        Text(text = HOW_TO_MEASURE, style = MaterialTheme.typography.bodyMedium)
        Spacer(modifier = Modifier.height(16.dp))

        val units = listOf("in", "cm")
        SingleChoiceSegmentedButtonRow(modifier = Modifier.fillMaxWidth()) {
            units.forEachIndexed { index, option ->
                SegmentedButton(
                    selected = unit == option,
                    onClick = { changeUnit(option) },
                    shape = SegmentedButtonDefaults.itemShape(index = index, count = units.size)
                ) {
                    Text(if (option == "in") "Inches" else "Centimeters")
                }
            }
        }
        Spacer(modifier = Modifier.height(12.dp))

        BodyMeasurement.entries.forEach { measurement ->
            val error = if (showErrors) errorFor(measurement) else null
            OutlinedTextField(
                value = texts[measurement].orEmpty(),
                onValueChange = { input -> texts[measurement] = input.filter { it.isDigit() || it == '.' } },
                label = { Text(measurement.labelFor(fit)) },
                suffix = { Text(unit) },
                supportingText = { Text(error ?: measurement.howToFor(fit)) },
                isError = error != null,
                keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Decimal),
                singleLine = true,
                modifier = Modifier.fillMaxWidth()
            )
            Spacer(modifier = Modifier.height(4.dp))
        }

        saveError?.let {
            Text(text = it, color = MaterialTheme.colorScheme.error, style = MaterialTheme.typography.bodySmall)
            Spacer(modifier = Modifier.height(8.dp))
        }
        Spacer(modifier = Modifier.height(8.dp))
        Button(
            onClick = { save() },
            enabled = !isSaving,
            modifier = Modifier.fillMaxWidth().height(50.dp)
        ) {
            Text(if (isSaving) "Saving..." else "Save")
        }
        Spacer(modifier = Modifier.height(16.dp))
    }
}
