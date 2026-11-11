package org.tasks.compose.accounts

import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBar
import androidx.compose.material3.TopAppBarDefaults
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import org.jetbrains.compose.resources.DrawableResource
import org.jetbrains.compose.resources.painterResource
import org.jetbrains.compose.resources.stringResource
import org.tasks.compose.components.SymbolIcon
import org.tasks.compose.settings.CardPosition
import org.tasks.compose.settings.SettingsCardGap
import org.tasks.compose.settings.SettingsContentPadding
import org.tasks.compose.settings.SettingsItemCard
import org.tasks.compose.settings.SettingsRowPadding
import org.tasks.compose.settings.SettingsSectionGap
import org.tasks.themes.TasksIcons
import tasks.kmp.generated.resources.Res
import tasks.kmp.generated.resources.add_account
import tasks.kmp.generated.resources.back
import tasks.kmp.generated.resources.caldav
import tasks.kmp.generated.resources.caldav_selection_description
import tasks.kmp.generated.resources.davx5
import tasks.kmp.generated.resources.davx5_selection_description
import tasks.kmp.generated.resources.ic_davx5_icon_green_bg
import tasks.kmp.generated.resources.ic_webdav_logo

/** Alta de cuentas de DavTasks: CalDAV directo o mediante DAVx⁵ (OpenTasks). */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun AddAccountScreen(
    onBack: () -> Unit,
    onCaldav: () -> Unit,
    onDavx5: () -> Unit,
) {
    Scaffold(
        containerColor = MaterialTheme.colorScheme.surface,
        contentWindowInsets = WindowInsets(0, 0, 0, 0),
        topBar = {
            TopAppBar(
                navigationIcon = {
                    IconButton(onClick = onBack) {
                        SymbolIcon(
                            name = TasksIcons.ARROW_BACK,
                            contentDescription = stringResource(Res.string.back),
                        )
                    }
                },
                title = {
                    Text(text = stringResource(Res.string.add_account))
                },
                colors = TopAppBarDefaults.topAppBarColors(
                    containerColor = MaterialTheme.colorScheme.surface,
                ),
            )
        }
    ) { paddingValues ->
        Box(
            modifier = Modifier
                .padding(paddingValues)
                .fillMaxSize(),
            contentAlignment = Alignment.TopCenter,
        ) {
            Column(
                modifier = Modifier
                    .widthIn(max = 480.dp)
                    .fillMaxWidth()
                    .verticalScroll(rememberScrollState())
                    .padding(horizontal = SettingsContentPadding),
                verticalArrangement = Arrangement.spacedBy(SettingsCardGap),
            ) {
                Spacer(modifier = Modifier.height(SettingsSectionGap))
                SettingsItemCard(position = CardPosition.First) {
                    AccountTypeRow(
                        title = stringResource(Res.string.caldav),
                        icon = Res.drawable.ic_webdav_logo,
                        tint = MaterialTheme.colorScheme.onSurface.copy(alpha = .8f),
                        description = stringResource(Res.string.caldav_selection_description),
                        onClick = onCaldav,
                    )
                }
                SettingsItemCard(position = CardPosition.Last) {
                    AccountTypeRow(
                        title = stringResource(Res.string.davx5),
                        icon = Res.drawable.ic_davx5_icon_green_bg,
                        description = stringResource(Res.string.davx5_selection_description),
                        onClick = onDavx5,
                    )
                }
                Spacer(modifier = Modifier.height(48.dp)) // margen para la barra de navegación
            }
        }
    }
}

@Composable
private fun AccountTypeRow(
    title: String,
    icon: DrawableResource,
    description: String,
    tint: Color? = null,
    onClick: () -> Unit,
) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .clickable(onClick = onClick)
            .padding(horizontal = SettingsContentPadding, vertical = SettingsRowPadding),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(16.dp),
    ) {
        Icon(
            painter = painterResource(icon),
            contentDescription = null,
            tint = tint ?: Color.Unspecified,
            modifier = Modifier.size(40.dp)
        )
        Column(modifier = Modifier.weight(1f)) {
            Text(
                text = title,
                style = MaterialTheme.typography.bodyLarge,
                fontWeight = FontWeight.Medium,
            )
            Text(
                text = description,
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                modifier = Modifier.padding(top = 4.dp),
            )
        }
    }
}
