package org.tasks.compose

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.Button
import androidx.compose.material3.Icon
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import org.jetbrains.compose.resources.painterResource
import org.jetbrains.compose.resources.stringResource
import org.tasks.themes.TestTags
import tasks.kmp.generated.resources.Res
import tasks.kmp.generated.resources.add_account
import tasks.kmp.generated.resources.continue_without_sync
import tasks.kmp.generated.resources.ic_round_icon
import tasks.kmp.generated.resources.returning_user_import_backup

/** Primera pantalla sin cuentas: añadir cuenta, usar listas locales o importar una copia. */
@Composable
fun WelcomeScreenLayout(
    onSignIn: () -> Unit,
    onContinueWithoutSync: () -> Unit,
    onImportBackup: () -> Unit,
) {
    Scaffold { paddingValues ->
        BoxWithConstraints(
            modifier = Modifier
                .padding(paddingValues)
                .fillMaxSize()
        ) {
            if (maxHeight < 500.dp) {
                Row(
                    modifier = Modifier
                        .fillMaxSize()
                        .padding(24.dp),
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(24.dp),
                ) {
                    Box(
                        modifier = Modifier.weight(1f),
                        contentAlignment = Alignment.Center,
                    ) {
                        AppIcon(size = 120)
                    }
                    WelcomeContent(
                        onSignIn = onSignIn,
                        onContinueWithoutSync = onContinueWithoutSync,
                        onImportBackup = onImportBackup,
                        modifier = Modifier
                            .weight(1f)
                            .verticalScroll(rememberScrollState()),
                    )
                }
            } else {
                Column(
                    modifier = Modifier
                        .fillMaxSize()
                        .padding(32.dp),
                    horizontalAlignment = Alignment.CenterHorizontally,
                    verticalArrangement = Arrangement.Center,
                ) {
                    Spacer(modifier = Modifier.weight(1f))
                    AppIcon(size = 152)
                    Spacer(modifier = Modifier.weight(1f))
                    WelcomeContent(
                        onSignIn = onSignIn,
                        onContinueWithoutSync = onContinueWithoutSync,
                        onImportBackup = onImportBackup,
                        modifier = Modifier.fillMaxWidth(),
                    )
                }
            }
        }
    }
}

@Composable
private fun AppIcon(size: Int) {
    Icon(
        painter = painterResource(Res.drawable.ic_round_icon),
        contentDescription = null,
        tint = Color.Unspecified,
        modifier = Modifier.size(size.dp),
    )
}

@Composable
private fun WelcomeContent(
    onSignIn: () -> Unit,
    onContinueWithoutSync: () -> Unit,
    onImportBackup: () -> Unit,
    modifier: Modifier = Modifier,
) {
    val buttonModifier = Modifier
        .widthIn(max = 400.dp)
        .fillMaxWidth()

    Column(
        modifier = modifier,
        horizontalAlignment = Alignment.CenterHorizontally,
    ) {
        Button(
            onClick = onSignIn,
            modifier = buttonModifier
        ) {
            Text(
                text = stringResource(Res.string.add_account),
                modifier = Modifier.fillMaxWidth(),
                textAlign = TextAlign.Center,
            )
        }

        Spacer(modifier = Modifier.height(8.dp))

        OutlinedButton(
            onClick = onContinueWithoutSync,
            modifier = buttonModifier.testTag(TestTags.CONTINUE_WITHOUT_SYNC)
        ) {
            Text(
                text = stringResource(Res.string.continue_without_sync),
                modifier = Modifier.fillMaxWidth(),
                textAlign = TextAlign.Center,
            )
        }

        Spacer(modifier = Modifier.height(8.dp))

        OutlinedButton(
            onClick = onImportBackup,
            modifier = buttonModifier
        ) {
            Text(
                text = stringResource(Res.string.returning_user_import_backup),
                modifier = Modifier.fillMaxWidth(),
                textAlign = TextAlign.Center,
            )
        }
    }
}
