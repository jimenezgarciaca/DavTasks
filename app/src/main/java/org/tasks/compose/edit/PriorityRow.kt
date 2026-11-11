package org.tasks.compose.edit

import android.content.res.Configuration
import androidx.compose.foundation.background
import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material3.FilterChip
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.RowScope
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.wrapContentHeight
import androidx.compose.material3.LocalMinimumInteractiveComponentSize
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.RadioButton
import androidx.compose.material3.RadioButtonDefaults
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.ui.Alignment
import androidx.compose.ui.ExperimentalComposeUiApi
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.res.dimensionResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import org.tasks.R
import org.tasks.compose.TaskEditRow
import org.tasks.data.entity.Task
import org.tasks.kmp.org.tasks.themes.ColorProvider.priorityColor
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
            PriorityLabeled(
                selected = priority,
                onClick = { onChangePriority(it) },
            )
        },
    )
}

@Composable
fun Priority(
    selected: Int,
    onClick: (Int) -> Unit = {},
) {
    Row(horizontalArrangement = Arrangement.SpaceBetween) {
        for (i in Task.Priority.NONE downTo Task.Priority.HIGH) {
            PriorityButton(
                priority = i,
                selected = selected,
                onClick = onClick,
            )
        }
    }
}

/**
 * DavTasks: la prioridad se elige con chips con texto (Ninguna, Baja, Media, Alta) y un
 * punto de color, en lugar de cuatro círculos de color sin etiqueta.
 */
@OptIn(ExperimentalLayoutApi::class)
@Composable
fun PriorityLabeled(
    selected: Int,
    onClick: (Int) -> Unit = {},
) {
    val isDark = isSystemInDarkTheme()
    FlowRow(
        modifier = Modifier
            .fillMaxWidth()
            .padding(
                top = 12.dp,
                bottom = 12.dp,
                end = dimensionResource(id = R.dimen.keyline_first),
            ),
        horizontalArrangement = Arrangement.spacedBy(8.dp),
        verticalArrangement = Arrangement.spacedBy(4.dp),
    ) {
        for (priority in Task.Priority.NONE downTo Task.Priority.HIGH) {
            val color = Color(priorityColor(priority = priority, isDarkMode = isDark))
            FilterChip(
                selected = priority == selected,
                onClick = { onClick(priority) },
                label = { Text(text = priorityLabel(priority)) },
                leadingIcon = {
                    Box(
                        modifier = Modifier
                            .size(10.dp)
                            .background(color = color, shape = CircleShape)
                    )
                },
            )
        }
    }
}

@Composable
private fun priorityLabel(priority: Int): String = org.jetbrains.compose.resources.stringResource(
    when (priority) {
        Task.Priority.HIGH -> Res.string.priority_high
        Task.Priority.MEDIUM -> Res.string.priority_medium
        Task.Priority.LOW -> Res.string.priority_low
        else -> Res.string.none
    }
)

@Composable
fun RowScope.PriorityButton(
    @Task.Priority priority: Int,
    selected: Int,
    onClick: (Int) -> Unit,
) {
    val color = Color(
        priorityColor(
            priority = priority,
            isDarkMode = isSystemInDarkTheme(),
        )
    )
    CompositionLocalProvider(
        LocalMinimumInteractiveComponentSize provides Dp.Unspecified,
    ) {
        RadioButton(
            selected = priority == selected,
            onClick = { onClick(priority) },
            colors = RadioButtonDefaults.colors(
                selectedColor = color,
                unselectedColor = color,
            ),
            modifier = Modifier
                .weight(1f)
                .padding(vertical = 20.dp)
        )
    }
}

@ExperimentalComposeUiApi
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

@ExperimentalComposeUiApi
@Preview(showBackground = true, uiMode = Configuration.UI_MODE_NIGHT_YES)
@Composable
fun PriorityPreviewNoDesaturate() {
    TasksTheme {
        PriorityRow(
            priority = Task.Priority.MEDIUM,
            onChangePriority = {},
        )
    }
}

@ExperimentalComposeUiApi
@Preview(locale = "de", widthDp = 320, showBackground = true)
@Composable
fun PriorityNarrowWidth() {
    TasksTheme {
        PriorityRow(
            priority = Task.Priority.MEDIUM,
            onChangePriority = {},
        )
    }
}
