package org.tasks.compose

import android.app.Activity
import android.content.Intent
import androidx.activity.compose.BackHandler
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.lifecycle.compose.collectAsStateWithLifecycle

@Composable
fun WelcomeScreen(
    importViewModel: ImportTasksViewModel,
    filePickerIntent: Intent,
    onBack: () -> Unit,
    onSignIn: () -> Unit,
    onContinueWithoutSync: () -> Unit,
) {
    val importUri by importViewModel.importUri.collectAsStateWithLifecycle()

    val importBackupLauncher = rememberLauncherForActivityResult(
        ActivityResultContracts.StartActivityForResult()
    ) { result ->
        if (result.resultCode == Activity.RESULT_OK) {
            importViewModel.setImportUri(result.data?.data)
        }
    }

    importUri?.let { uri ->
        ImportTasksDialog(
            uri = uri,
            viewModel = importViewModel,
            onFinished = { importViewModel.reset() }
        )
    }

    BackHandler(onBack = onBack)

    WelcomeScreenLayout(
        onSignIn = onSignIn,
        onContinueWithoutSync = onContinueWithoutSync,
        onImportBackup = { importBackupLauncher.launch(filePickerIntent) },
    )
}
