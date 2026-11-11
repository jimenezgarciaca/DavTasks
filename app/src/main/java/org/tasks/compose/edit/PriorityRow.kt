package org.tasks.compose.edit

import android.content.res.Configuration
import androidx.annotation.DrawableRes
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.material3.FilterChip
import androidx.compose.material3.Icon
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.dimensionResource
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.dp
import org.jetbrains.compose.resources.stringResource
import org.tasks.R
import org.tasks.compose.TaskEditRow
import org.tasks.data.entity.Task
import org.tasks.themes.TasksTheme
import tasks.kmp.generated.resources.Res
import tasks.kmp.generated.resources.none
import tasks.kmp.generated.resources.priority_high
import tasks.kmp.generated.resources.priority_low
import tasks.kmp.generated.resources.priority_medium

@Composable
fun PriorityRow(
    priority: Int,
    onChangePriority: (Int) -> Unit,
) {
    TaskEditRow(
        iconRes = R.drawable.ic_outline_flag_24px,
        content = {
            PriorityChips(
                selected = priority,
                onClick = onChangePriority,
                modifier = Modifier.padding(
                    top = 12.dp,
                    bottom = 12.dp,
                    end = dimensionResource(id = R.dimen.keyline_first),
                ),
            )
        },
    )
}

/**
 * Prioridad con chips de texto e icono de nivel, sin colores por prioridad. Sin [showNone],
 * pulsar la prioridad marcada la quita; con [showNone] se añade el chip "Ninguna".
 */
@OptIn(ExperimentalLayoutApi::class)
@Composable
fun PriorityChips(
    selected: Int,
    onClick: (Int) -> Unit,
    modifier: Modifier = Modifier,
    showNone: Boolean = false,
) {
    val priorities = if (showNone) {
        Task.Priority.NONE downTo Task.Priority.HIGH
    } else {
        Task.Priority.LOW downTo Task.Priority.HIGH
    }
    FlowRow(
        modifier = modifier.fillMaxWidth(),
        horizontalArrangement = Arrangement.spacedBy(8.dp),
        verticalArrangement = Arrangement.spacedBy(4.dp),
    ) {
        for (priority in priorities) {
            val isSelected = priority == selected
            val icon = priorityIconRes(priority)
            FilterChip(
                selected = isSelected,
                onClick = {
                    onClick(if (isSelected && !showNone) Task.Priority.NONE else priority)
                },
                label = { Text(text = stringResource(priorityLabel(priority))) },
                leadingIcon = if (icon != null) {
                    {
                        Icon(
                            painter = painterResource(id = icon),
                            contentDescription = null,
                            modifier = Modifier.size(18.dp),
                        )
                    }
                } else {
                    null
                },
            )
        }
    }
}

/** Icono de nivel de prioridad (una, dos o tres barras); null si no tiene prioridad. */
@DrawableRes
fun priorityIconRes(priority: Int): Int? = when (priority) {
    in Int.MIN_VALUE..Task.Priority.HIGH -> R.drawable.ic_priority_high
    Task.Priority.MEDIUM -> R.drawable.ic_priority_medium
    Task.Priority.LOW -> R.drawable.ic_priority_low
    else -> null
}

private fun priorityLabel(priority: Int) = when (priority) {
    in Int.MIN_VALUE..Task.Priority.HIGH -> Res.string.priority_high
    Task.Priority.MEDIUM -> Res.string.priority_medium
    Task.Priority.LOW -> Res.string.priority_low
    else -> Res.string.none
}

@Preview(showBackground = true)
@Preview(showBackground = true, uiMode = Configuration.UI_MODE_NIGHT_YES)
@Composable
fun PriorityPreview() {
    TasksTheme {
        PriorityRow(
            priority = Task.Priority.MEDIUM,
            onChangePriority = {},
        )
    }
}
