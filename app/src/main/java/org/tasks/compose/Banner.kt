package org.tasks.compose

import android.content.res.Configuration
import androidx.compose.runtime.Composable
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.tooling.preview.Preview
import org.jetbrains.compose.resources.stringResource as composeStringResource
import org.tasks.R
import org.tasks.compose.components.Banner
import org.tasks.themes.TasksTheme
import tasks.kmp.generated.resources.Res
import tasks.kmp.generated.resources.enable_reminders
import tasks.kmp.generated.resources.enable_reminders_description

@Composable
fun NotificationsDisabledBanner(
    settings: () -> Unit,
    dismiss: () -> Unit,
) {
    Banner(
        title = composeStringResource(Res.string.enable_reminders),
        body = composeStringResource(Res.string.enable_reminders_description),
        dismissText = stringResource(id = R.string.dismiss),
        onDismiss = dismiss,
        action = stringResource(id = R.string.TLA_menu_settings),
        onAction = settings,
    )
}

@Composable
fun AlarmsDisabledBanner(
    settings: () -> Unit,
    dismiss: () -> Unit,
) {
    Banner(
        title = stringResource(id = R.string.enable_alarms),
        body = stringResource(id = R.string.enable_alarms_description),
        dismissText = stringResource(id = R.string.dismiss),
        onDismiss = dismiss,
        action = stringResource(id = R.string.TLA_menu_settings),
        onAction = settings,
    )
}

@Preview(showBackground = true)
@Preview(showBackground = true, backgroundColor = 0x202124, uiMode = Configuration.UI_MODE_NIGHT_YES)
@Composable
private fun NotificationsDisabledPreview() = TasksTheme {
    NotificationsDisabledBanner(settings = {}, dismiss = {})
}

@Preview(showBackground = true)
@Preview(showBackground = true, backgroundColor = 0x202124, uiMode = Configuration.UI_MODE_NIGHT_YES)
@Composable
private fun AlarmsDisabledPreview() = TasksTheme {
    AlarmsDisabledBanner(settings = {}, dismiss = {})
}
