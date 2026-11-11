package org.tasks.compose.chips

import org.tasks.themes.TasksIcons
import org.tasks.compose.components.SymbolIcon
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.defaultMinSize
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.material3.Icon
import androidx.compose.material3.LocalMinimumInteractiveComponentSize
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.luminance
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import org.tasks.compose.components.TasksIcon
import org.tasks.filters.Filter
import kotlin.math.max
import kotlin.math.min

@Composable
fun Chip(
    icon: String?,
    name: String?,
    theme: Int,
    showText: Boolean,
    showIcon: Boolean,
    onClick: () -> Unit,
    colorProvider: (Int) -> Int,
    clear: (() -> Unit)? = null,
) {
    Chip(
        color = remember(theme) { Color(colorProvider(theme)) },
        text = if (showText) name else null,
        icon = if (showIcon && icon != null) icon else null,
        onClick = onClick,
        clear = clear,
    )
}

@Composable
fun Chip(
    text: String? = null,
    icon: String? = null,
    color: Color,
    onClick: () -> Unit = {},
    enabled: Boolean = true,
    clear: (() -> Unit)? = null,
) {
    // DavTasks: chip tonal y compacto. El color de la lista o etiqueta se usa en el borde,
    // el fondo (muy atenuado) y el icono; el texto mantiene el contraste del tema.
    val onColor = MaterialTheme.colorScheme.onSurfaceVariant
    val surface = MaterialTheme.colorScheme.surface
    val hasContrast = remember(color, surface) {
        val l1 = color.luminance()
        val l2 = surface.luminance()
        (max(l1, l2) + 0.05f) / (min(l1, l2) + 0.05f) >= 3f
    }
    val accent = if (hasContrast) color else MaterialTheme.colorScheme.outline
    val iconColor = if (hasContrast) color else onColor
    CompositionLocalProvider(
        LocalMinimumInteractiveComponentSize provides Dp.Unspecified
    ) {
        Surface(
            onClick = onClick,
            enabled = enabled,
            shape = MaterialTheme.shapes.small,
            color = accent.copy(alpha = 0.14f),
            border = BorderStroke(1.dp, accent.copy(alpha = 0.5f)),
            modifier = Modifier.defaultMinSize(minHeight = 22.dp),
        ) {
            Row(
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(4.dp),
                modifier = Modifier.padding(horizontal = 6.dp, vertical = 2.dp),
            ) {
                icon?.let {
                    TasksIcon(label = it, tint = iconColor, size = 14.dp)
                }
                text?.let {
                    Text(
                        text = it,
                        style = MaterialTheme.typography.labelMedium,
                        color = onColor,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis,
                    )
                }
                clear?.let { onClearClick ->
                    SymbolIcon(
                        name = TasksIcons.CANCEL,
                        modifier = Modifier
                            .size(18.dp)
                            .clickable { onClearClick() },
                        contentDescription = null,
                        tint = onColor,
                    )
                }
            }
        }
    }
}

@Composable
fun FilterChip(
    filter: Filter,
    defaultIcon: String,
    showText: Boolean,
    showIcon: Boolean,
    onClick: (Filter) -> Unit,
    colorProvider: (Int) -> Int,
) {
    Chip(
        filter.icon ?: defaultIcon,
        filter.title,
        filter.tint,
        showText,
        showIcon,
        onClick = { onClick(filter) },
        colorProvider = colorProvider,
    )
}

@OptIn(ExperimentalLayoutApi::class)
@Composable
fun ChipGroup(modifier: Modifier = Modifier, content: @Composable () -> Unit) {
    FlowRow(
        horizontalArrangement = Arrangement.spacedBy(4.dp),
        verticalArrangement = Arrangement.spacedBy(4.dp),
        modifier = modifier,
    ) {
        content()
    }
}
