package com.dzirent.app.ui

import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Clear
import androidx.compose.material.icons.filled.LocationCity
import androidx.compose.material.icons.filled.LocationOn
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalFocusManager
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import com.dzirent.app.data.model.UsCity
import com.dzirent.app.data.repository.DzirentRepository
import kotlinx.coroutines.delay

private const val MIN_QUERY_LENGTH = 2
private const val DEBOUNCE_MS = 300L

private sealed class CitySearch {
    object Idle : CitySearch()
    object Loading : CitySearch()
    data class Results(val cities: List<UsCity>) : CitySearch()
    data class Failed(val message: String) : CitySearch()
}

/**
 * City picker that only accepts a city from the us_cities list. After [MIN_QUERY_LENGTH]
 * characters it searches (debounced) and lists matches as "City, ST"; cities that share a
 * name (Springfield) are all listed so the user picks the right state. Typing again clears
 * the pick, so [selected] is null until a suggestion is tapped. [onTextChange] reports the
 * typed text, so a form can tell "left empty" from "typed but not picked".
 */
@Composable
fun CityField(
    repository: DzirentRepository,
    selected: UsCity?,
    onSelected: (UsCity?) -> Unit,
    modifier: Modifier = Modifier,
    onTextChange: (String) -> Unit = {},
    label: String = "City",
    enabled: Boolean = true,
    showPickHint: Boolean = false
) {
    val focusManager = LocalFocusManager.current
    var query by remember { mutableStateOf(selected?.label.orEmpty()) }
    var search by remember { mutableStateOf<CitySearch>(CitySearch.Idle) }
    var retryCount by remember { mutableIntStateOf(0) }
    val trimmed = query.trim()

    // Keep the text in step when the caller sets a city (e.g. a saved one).
    LaunchedEffect(selected) {
        if (selected != null) query = selected.label
    }

    fun setQuery(text: String) {
        query = text
        onTextChange(text)
        if (selected != null) onSelected(null)
    }

    LaunchedEffect(trimmed, selected, retryCount) {
        if (selected != null || trimmed.length < MIN_QUERY_LENGTH) {
            search = CitySearch.Idle
            return@LaunchedEffect
        }
        search = CitySearch.Loading
        delay(DEBOUNCE_MS) // a newer keystroke cancels this effect before the request goes out
        search = try {
            CitySearch.Results(repository.searchCities(trimmed))
        } catch (e: Exception) {
            CitySearch.Failed(e.userMessage("Couldn't load cities. Please try again."))
        }
    }

    Column(modifier = modifier) {
        OutlinedTextField(
            value = query,
            onValueChange = { setQuery(it) },
            label = { Text(label) },
            placeholder = { Text("Start typing, e.g. Austin") },
            leadingIcon = { Icon(Icons.Default.LocationCity, contentDescription = null) },
            trailingIcon = {
                if (query.isNotEmpty()) {
                    IconButton(onClick = { setQuery("") }, enabled = enabled) {
                        Icon(Icons.Default.Clear, contentDescription = "Clear city")
                    }
                }
            },
            supportingText = when {
                selected == null && showPickHint && trimmed.isNotEmpty() -> {
                    { Text("Pick your city from the list.") }
                }
                selected == null && trimmed.length in 1 until MIN_QUERY_LENGTH -> {
                    { Text("Type at least $MIN_QUERY_LENGTH letters.") }
                }
                else -> null
            },
            isError = selected == null && showPickHint && trimmed.isNotEmpty(),
            singleLine = true,
            enabled = enabled,
            modifier = Modifier.fillMaxWidth()
        )

        when (val state = search) {
            CitySearch.Idle -> Unit
            CitySearch.Loading -> Row(
                verticalAlignment = Alignment.CenterVertically,
                modifier = Modifier.padding(horizontal = 4.dp, vertical = 8.dp)
            ) {
                CircularProgressIndicator(modifier = Modifier.size(16.dp), strokeWidth = 2.dp)
                Spacer(modifier = Modifier.width(8.dp))
                Text("Searching...", style = MaterialTheme.typography.bodySmall)
            }
            is CitySearch.Failed -> Row(verticalAlignment = Alignment.CenterVertically) {
                Text(
                    text = state.message,
                    color = MaterialTheme.colorScheme.error,
                    style = MaterialTheme.typography.bodySmall,
                    modifier = Modifier.weight(1f)
                )
                TextButton(onClick = { retryCount++ }) { Text("Retry") }
            }
            is CitySearch.Results -> if (state.cities.isEmpty()) {
                Text(
                    text = "No matching city",
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    modifier = Modifier.padding(horizontal = 4.dp, vertical = 8.dp)
                )
            } else {
                Surface(
                    tonalElevation = 2.dp,
                    shape = MaterialTheme.shapes.small,
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(top = 4.dp)
                ) {
                    // Bounded height so it can sit inside scrolling screens and dialogs.
                    Column(
                        modifier = Modifier
                            .heightIn(max = 240.dp)
                            .verticalScroll(rememberScrollState())
                    ) {
                        state.cities.forEach { city ->
                            Row(
                                verticalAlignment = Alignment.CenterVertically,
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .clickable(enabled = enabled) {
                                        query = city.label
                                        onTextChange(city.label)
                                        onSelected(city)
                                        focusManager.clearFocus()
                                    }
                                    .padding(horizontal = 12.dp, vertical = 12.dp)
                            ) {
                                Icon(
                                    Icons.Default.LocationOn,
                                    contentDescription = null,
                                    tint = MaterialTheme.colorScheme.onSurfaceVariant,
                                    modifier = Modifier.size(18.dp)
                                )
                                Spacer(modifier = Modifier.width(12.dp))
                                Text(
                                    text = city.label,
                                    style = MaterialTheme.typography.bodyLarge,
                                    maxLines = 1,
                                    overflow = TextOverflow.Ellipsis
                                )
                            }
                        }
                    }
                }
            }
        }
    }
}
