package org.tasks.compose.settings

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.MaterialTheme
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import org.jetbrains.compose.resources.stringResource
import org.tasks.themes.TasksIcons
import tasks.kmp.generated.resources.Res
import tasks.kmp.generated.resources.documentation
import tasks.kmp.generated.resources.ic_octocat
import tasks.kmp.generated.resources.issue_tracker
import tasks.kmp.generated.resources.open_source
import tasks.kmp.generated.resources.send_application_logs
import tasks.kmp.generated.resources.source_code
import tasks.kmp.generated.resources.support
import tasks.kmp.generated.resources.version_string

/**
 * Ayuda de DavTasks: documentación (la de Tasks.org sigue siendo válida), incidencias y código
 * fuente en el repositorio de DavTasks, y envío de registros con el destinatario que elija el
 * usuario.
 */
@Composable
fun HelpAndFeedbackScreen(
    versionName: String,
    onDocumentation: () -> Unit,
    onIssueTracker: () -> Unit,
    onSendLogs: () -> Unit,
    onSourceCode: () -> Unit,
    bottomInsets: @Composable () -> Unit = {},
) {
    Column(
        modifier = Modifier
            .fillMaxSize()
            .background(MaterialTheme.colorScheme.surface)
            .verticalScroll(rememberScrollState())
    ) {
        SectionHeader(
            stringResource(Res.string.support),
            modifier = Modifier.padding(horizontal = SettingsContentPadding),
        )
        Column(
            modifier = Modifier.padding(horizontal = SettingsContentPadding),
            verticalArrangement = Arrangement.spacedBy(SettingsCardGap),
        ) {
            SettingsItemCard(position = CardPosition.First) {
                PreferenceRow(
                    title = stringResource(Res.string.documentation),
                    icon = TasksIcons.HELP,
                    onClick = onDocumentation,
                )
            }
            SettingsItemCard(position = CardPosition.Middle) {
                PreferenceRow(
                    title = stringResource(Res.string.issue_tracker),
                    icon = TasksIcons.BUG_REPORT,
                    onClick = onIssueTracker,
                )
            }
            SettingsItemCard(position = CardPosition.Last) {
                PreferenceRow(
                    title = stringResource(Res.string.send_application_logs),
                    icon = TasksIcons.ATTACHMENT,
                    onClick = onSendLogs,
                )
            }
        }

        SectionHeader(
            stringResource(Res.string.open_source),
            modifier = Modifier.padding(horizontal = SettingsContentPadding),
        )
        SettingsItemCard(
            position = CardPosition.Only,
            modifier = Modifier.padding(horizontal = SettingsContentPadding),
        ) {
            PreferenceRow(
                title = stringResource(Res.string.source_code),
                iconDrawable = Res.drawable.ic_octocat,
                summary = stringResource(Res.string.version_string, versionName),
                onClick = onSourceCode,
            )
        }

        Spacer(modifier = Modifier.height(SettingsContentPadding))
        bottomInsets()
    }
}
