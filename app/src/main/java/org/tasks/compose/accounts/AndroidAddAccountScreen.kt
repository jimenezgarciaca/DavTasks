package org.tasks.compose.accounts

import android.content.Context
import android.content.Intent
import androidx.activity.compose.BackHandler
import androidx.compose.runtime.Composable
import androidx.compose.ui.tooling.preview.PreviewFontScale
import androidx.compose.ui.tooling.preview.PreviewLightDark
import androidx.compose.ui.tooling.preview.PreviewScreenSizes
import org.tasks.R
import org.tasks.caldav.CaldavSignInActivity
import org.tasks.extensions.Context.openUri
import org.tasks.themes.TasksSettingsTheme

/** Pantalla de alta de cuentas con el comportamiento de Android para CalDAV y DAVx⁵. */
@Composable
fun AddAccountScreenWrapper(
    context: Context,
    onBack: () -> Unit,
    signInCaldav: (Intent) -> Unit,
) {
    BackHandler(onBack = onBack)
    AddAccountScreen(
        onBack = onBack,
        onCaldav = { signInCaldav(Intent(context, CaldavSignInActivity::class.java)) },
        onDavx5 = { context.openUri(context.getString(R.string.url_davx5)) },
    )
}

@PreviewLightDark
@PreviewScreenSizes
@PreviewFontScale
@Composable
fun AddAccountPreview() {
    TasksSettingsTheme {
        AddAccountScreen(onBack = {}, onCaldav = {}, onDavx5 = {})
    }
}
