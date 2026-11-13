package org.tasks.quickadd

import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
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
