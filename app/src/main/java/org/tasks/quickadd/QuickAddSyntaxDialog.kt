package org.tasks.quickadd

import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateListOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.input.PasswordVisualTransformation
import androidx.compose.ui.unit.dp
import org.tasks.R
import org.tasks.compose.settings.PreferenceRow
import org.tasks.compose.settings.SettingsContentPadding
import org.tasks.compose.settings.SettingsItemCard
import org.tasks.themes.TasksIcons

/** Fila de ajustes "Alta rápida" con el diálogo para cambiar los símbolos de los marcadores. */
@Composable
fun QuickAddSyntaxPreference(
    syntax: QuickAddSyntax,
    onChange: (QuickAddSyntax) -> Unit,
) {
    var editing by remember { mutableStateOf(false) }
    Spacer(modifier = Modifier.height(SettingsContentPadding))
    SettingsItemCard(modifier = Modifier.padding(horizontal = SettingsContentPadding)) {
        PreferenceRow(
            title = stringResource(R.string.quick_add_title),
            icon = TasksIcons.KEYBOARD,
            summary = stringResource(R.string.quick_add_summary, syntax.symbols.joinToString(" ")),
            onClick = { editing = true },
        )
    }
    if (editing) {
        QuickAddSyntaxDialog(
            initial = syntax,
            onSave = {
                onChange(it)
                editing = false
            },
            onDismiss = { editing = false },
        )
    }
}

@Composable
private fun QuickAddSyntaxDialog(
    initial: QuickAddSyntax,
    onSave: (QuickAddSyntax) -> Unit,
    onDismiss: () -> Unit,
) {
    val labels = listOf(
        R.string.quick_add_symbol_tag,
        R.string.quick_add_symbol_date,
        R.string.quick_add_symbol_list,
        R.string.quick_add_symbol_priority,
        R.string.quick_add_symbol_repeat,
    )
    val values = remember { mutableStateListOf(*initial.symbols.map { it.toString() }.toTypedArray()) }
    val candidate = values
        .takeIf { list -> list.all { it.length == 1 } }
        ?.let { QuickAddSyntax(it[0][0], it[1][0], it[2][0], it[3][0], it[4][0]) }
    val valid = candidate?.isValid == true
    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text(stringResource(R.string.quick_add_title)) },
        text = {
            Column {
                labels.forEachIndexed { index, label ->
                    Row(
                        verticalAlignment = Alignment.CenterVertically,
                        modifier = Modifier.fillMaxWidth().padding(vertical = 4.dp),
                    ) {
                        Text(
                            text = stringResource(label),
                            modifier = Modifier.weight(1f),
                            style = MaterialTheme.typography.bodyLarge,
                        )
                        Spacer(modifier = Modifier.width(8.dp))
                        OutlinedTextField(
                            value = values[index],
                            onValueChange = { values[index] = it.trim().takeLast(1) },
                            singleLine = true,
                            modifier = Modifier.width(64.dp),
                        )
                    }
                }
                if (!valid) {
                    Text(
                        text = stringResource(R.string.quick_add_invalid),
                        color = MaterialTheme.colorScheme.error,
                        style = MaterialTheme.typography.bodySmall,
                        modifier = Modifier.padding(top = 8.dp),
                    )
                }
            }
        },
        confirmButton = {
            TextButton(
                enabled = valid,
                onClick = { candidate?.let(onSave) },
            ) { Text(stringResource(R.string.ok)) }
        },
        dismissButton = {
            Row {
                TextButton(onClick = {
                    QuickAddSyntax().symbols.forEachIndexed { i, c -> values[i] = c.toString() }
                }) { Text(stringResource(R.string.quick_add_reset)) }
                TextButton(onClick = onDismiss) { Text(stringResource(R.string.cancel)) }
            }
        },
    )
}

/** Fila de ajustes de Mistral con el diálogo para la clave de API y el modelo. */
@Composable
fun MistralPreference(
    configured: Boolean,
    model: String,
    onSave: (apiKey: String?, model: String) -> Unit,
    onRemove: () -> Unit,
) {
    var editing by remember { mutableStateOf(false) }
    Spacer(modifier = Modifier.height(SettingsContentPadding))
    SettingsItemCard(modifier = Modifier.padding(horizontal = SettingsContentPadding)) {
        PreferenceRow(
            title = stringResource(R.string.mistral_title),
            icon = TasksIcons.BOLT,
            summary = if (configured) {
                stringResource(R.string.mistral_summary_on, model)
            } else {
                stringResource(R.string.mistral_summary_off)
            },
            onClick = { editing = true },
        )
    }
    if (editing) {
        var key by remember { mutableStateOf("") }
        var modelValue by remember { mutableStateOf(model) }
        AlertDialog(
            onDismissRequest = { editing = false },
            title = { Text(stringResource(R.string.mistral_title)) },
            text = {
                Column {
                    OutlinedTextField(
                        value = key,
                        onValueChange = { key = it.trim() },
                        label = { Text(stringResource(R.string.mistral_api_key)) },
                        placeholder = if (configured) {
                            { Text(stringResource(R.string.mistral_api_key_saved)) }
                        } else {
                            null
                        },
                        singleLine = true,
                        visualTransformation = PasswordVisualTransformation(),
                        keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Password),
                        modifier = Modifier.fillMaxWidth(),
                    )
                    Spacer(modifier = Modifier.height(8.dp))
                    OutlinedTextField(
                        value = modelValue,
                        onValueChange = { modelValue = it.trim() },
                        label = { Text(stringResource(R.string.mistral_model)) },
                        singleLine = true,
                        modifier = Modifier.fillMaxWidth(),
                    )
                    Text(
                        text = stringResource(R.string.mistral_privacy),
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                        modifier = Modifier.padding(top = 12.dp),
                    )
                }
            },
            confirmButton = {
                TextButton(
                    enabled = configured || key.isNotEmpty(),
                    onClick = {
                        onSave(key.takeIf { it.isNotEmpty() }, modelValue)
                        editing = false
                    },
                ) { Text(stringResource(R.string.ok)) }
            },
            dismissButton = {
                Row {
                    if (configured) {
                        TextButton(onClick = {
                            onRemove()
                            editing = false
                        }) { Text(stringResource(R.string.mistral_remove)) }
                    }
                    TextButton(onClick = { editing = false }) { Text(stringResource(R.string.cancel)) }
                }
            },
        )
    }
}
