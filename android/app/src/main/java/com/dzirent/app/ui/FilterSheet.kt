package com.dzirent.app.ui

import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.unit.dp
import com.dzirent.app.data.model.AccessoryTypes
import com.dzirent.app.data.model.Categories
import com.dzirent.app.util.BrowseFilters
import com.dzirent.app.util.DISTANCE_STEPS
import com.dzirent.app.util.parsePrice
import com.dzirent.app.util.priceInputText

/**
 * Category, accessory item type, distance, price range and size. Nothing changes until
 * Apply; Reset puts everything back to the defaults.
 */
@OptIn(ExperimentalMaterial3Api::class, ExperimentalLayoutApi::class)
@Composable
fun FilterSheet(
    current: BrowseFilters,
    onApply: (BrowseFilters) -> Unit,
    onDismiss: () -> Unit
) {
    var draft by remember { mutableStateOf(current) }
    var minText by remember { mutableStateOf(priceInputText(current.minPrice)) }
    var maxText by remember { mutableStateOf(priceInputText(current.maxPrice)) }
    var sizeText by remember { mutableStateOf(current.size.orEmpty()) }
    val sheetState = rememberModalBottomSheetState(skipPartiallyExpanded = true)
    val settings = LocalAppSettings.current

    val minPrice = parsePrice(minText)
    val maxPrice = parsePrice(maxText)
    val priceError = when {
        minText.isNotBlank() && minPrice == null -> "Enter a price above \$0."
        maxText.isNotBlank() && maxPrice == null -> "Enter a price above \$0."
        minPrice != null && maxPrice != null && minPrice > maxPrice -> "The lowest price is above the highest."
        else -> null
    }

    ModalBottomSheet(onDismissRequest = onDismiss, sheetState = sheetState) {
        Column(
            modifier = Modifier
                .verticalScroll(rememberScrollState())
                .padding(horizontal = 24.dp)
                .padding(bottom = 24.dp)
        ) {
            Text("Filter", style = MaterialTheme.typography.titleLarge, fontWeight = FontWeight.Bold)

            SheetHeading("Category")
            FlowRow(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                FilterChip(
                    selected = draft.category == null,
                    onClick = { draft = draft.withCategory(null) },
                    label = { Text("All") }
                )
                Categories.ALL.forEach { category ->
                    FilterChip(
                        selected = draft.category == category,
                        onClick = { draft = draft.withCategory(category) },
                        label = { Text(category) }
                    )
                }
            }

            if (draft.category == Categories.ACCESSORIES) {
                SheetHeading("Item type")
                FlowRow(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    FilterChip(
                        selected = draft.itemType == null,
                        onClick = { draft = draft.copy(itemType = null) },
                        label = { Text("Any") }
                    )
                    AccessoryTypes.ALL.forEach { type ->
                        FilterChip(
                            selected = draft.itemType == type,
                            onClick = { draft = draft.copy(itemType = type) },
                            label = { Text(type) }
                        )
                    }
                }
            }

            SheetHeading("Distance")
            FlowRow(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                DISTANCE_STEPS.forEach { step ->
                    FilterChip(
                        selected = draft.distanceStep == step,
                        onClick = { draft = draft.copy(distanceStep = step) },
                        label = { Text(settings.distanceUnit.optionLabel(step)) }
                    )
                }
            }

            SheetHeading("Price per rental")
            Row(horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                PriceField("Min", minText, { minText = it }, Modifier.weight(1f))
                PriceField("Max", maxText, { maxText = it }, Modifier.weight(1f))
            }
            priceError?.let {
                Text(it, color = MaterialTheme.colorScheme.error, style = MaterialTheme.typography.bodySmall)
            }

            SheetHeading("Size")
            OutlinedTextField(
                value = sizeText,
                onValueChange = { if (it.length <= 20) sizeText = it },
                placeholder = { Text("e.g. M or US 8") },
                singleLine = true,
                modifier = Modifier.fillMaxWidth()
            )

            Spacer(modifier = Modifier.height(24.dp))
            Row(horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                OutlinedButton(
                    onClick = {
                        draft = BrowseFilters(distanceStep = settings.defaultDistanceStep)
                        minText = ""
                        maxText = ""
                        sizeText = ""
                    },
                    modifier = Modifier.weight(1f).height(50.dp)
                ) { Text("Reset") }
                Button(
                    onClick = {
                        onApply(
                            draft.copy(
                                minPrice = minPrice,
                                maxPrice = maxPrice,
                                size = sizeText.trim().ifBlank { null }
                            )
                        )
                    },
                    enabled = priceError == null,
                    modifier = Modifier.weight(1f).height(50.dp)
                ) { Text("Apply") }
            }
        }
    }
}

@Composable
private fun SheetHeading(text: String) {
    Text(
        text = text,
        style = MaterialTheme.typography.titleSmall,
        fontWeight = FontWeight.Bold,
        modifier = Modifier.padding(top = 20.dp, bottom = 8.dp)
    )
}

@Composable
private fun PriceField(label: String, value: String, onValueChange: (String) -> Unit, modifier: Modifier) {
    OutlinedTextField(
        value = value,
        onValueChange = { input -> onValueChange(input.filter { it.isDigit() || it == '.' }) },
        label = { Text(label) },
        prefix = { Text("\$") },
        keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Decimal),
        singleLine = true,
        modifier = modifier
    )
}
