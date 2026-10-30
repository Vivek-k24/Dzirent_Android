package com.dzirent.app.ui

import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.selection.toggleable
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Close
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.KeyboardCapitalization
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.ui.unit.dp
import androidx.compose.ui.window.Dialog
import androidx.compose.ui.window.DialogProperties
import com.dzirent.app.util.AccessoryDeclaration
import kotlinx.coroutines.launch

/**
 * The accessory declaration, full screen, signed once per user: every statement ticked and the
 * full legal name typed. The typed name and the ticks are the signature (no drawing, no GPS).
 * [onSign] records it on the server; if that fails the screen stays, with the reason.
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun AccessoryDeclarationScreen(onSign: suspend (String) -> Unit, onDismiss: () -> Unit) {
    val scope = rememberCoroutineScope()
    val checked = remember { mutableStateListOf(*Array(AccessoryDeclaration.STATEMENTS.size) { false }) }
    var name by remember { mutableStateOf("") }
    var saving by remember { mutableStateOf(false) }
    var error by remember { mutableStateOf<String?>(null) }

    Dialog(
        onDismissRequest = { if (!saving) onDismiss() },
        properties = DialogProperties(usePlatformDefaultWidth = false)
    ) {
        Surface(modifier = Modifier.fillMaxSize(), color = MaterialTheme.colorScheme.background) {
            Column(modifier = Modifier.fillMaxSize()) {
                TopAppBar(
                    title = { Text(AccessoryDeclaration.TITLE) },
                    navigationIcon = {
                        IconButton(onClick = onDismiss, enabled = !saving) {
                            Icon(Icons.Default.Close, contentDescription = "Close")
                        }
                    }
                )
                Column(
                    modifier = Modifier
                        .fillMaxSize()
                        .verticalScroll(rememberScrollState())
                        .padding(horizontal = 24.dp, vertical = 8.dp)
                ) {
                    Text(AccessoryDeclaration.INTRO, style = MaterialTheme.typography.bodyLarge)
                    Spacer(modifier = Modifier.height(12.dp))
                    AccessoryDeclaration.STATEMENTS.forEachIndexed { i, statement ->
                        Row(
                            verticalAlignment = Alignment.Top,
                            modifier = Modifier
                                .fillMaxWidth()
                                .toggleable(value = checked[i], role = Role.Checkbox, enabled = !saving) { checked[i] = it }
                                .padding(vertical = 6.dp)
                        ) {
                            Checkbox(checked = checked[i], onCheckedChange = null)
                            Spacer(modifier = Modifier.width(12.dp))
                            Text(statement, style = MaterialTheme.typography.bodyMedium)
                        }
                    }
                    Spacer(modifier = Modifier.height(12.dp))
                    val nameError = AccessoryDeclaration.nameError(name).takeIf { name.isNotEmpty() }
                    OutlinedTextField(
                        value = name,
                        onValueChange = { if (it.length <= AccessoryDeclaration.NAME_MAX) name = it },
                        label = { Text(AccessoryDeclaration.NAME_LABEL) },
                        isError = nameError != null,
                        supportingText = nameError?.let { { Text(it) } },
                        singleLine = true,
                        enabled = !saving,
                        keyboardOptions = KeyboardOptions(capitalization = KeyboardCapitalization.Words),
                        modifier = Modifier.fillMaxWidth()
                    )
                    Spacer(modifier = Modifier.height(8.dp))
                    Text(
                        AccessoryDeclaration.SIGNATURE_NOTE,
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                    error?.let {
                        Spacer(modifier = Modifier.height(8.dp))
                        Text(it, color = MaterialTheme.colorScheme.error, style = MaterialTheme.typography.bodyMedium)
                    }
                    Spacer(modifier = Modifier.height(16.dp))
                    Button(
                        onClick = {
                            saving = true
                            error = null
                            scope.launch {
                                try {
                                    onSign(name.trim())
                                } catch (e: Exception) {
                                    error = e.userMessage("Couldn't save your declaration. Please try again.")
                                } finally {
                                    saving = false
                                }
                            }
                        },
                        enabled = !saving && AccessoryDeclaration.canSign(checked.toList(), name),
                        modifier = Modifier.fillMaxWidth().height(50.dp)
                    ) {
                        if (saving) CircularProgressIndicator(strokeWidth = 2.dp, modifier = Modifier.size(20.dp))
                        else Text(AccessoryDeclaration.SIGN_BUTTON, fontWeight = FontWeight.Bold)
                    }
                    Spacer(modifier = Modifier.height(24.dp))
                }
            }
        }
    }
}
