package org.tasks.compose.accounts

import android.app.Activity
import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.activity.result.contract.ActivityResultContracts
import androidx.activity.viewModels
import androidx.compose.runtime.LaunchedEffect
import dagger.hilt.android.AndroidEntryPoint
import org.tasks.themes.TasksSettingsTheme
import org.tasks.themes.Theme
import javax.inject.Inject

@AndroidEntryPoint
class AddAccountActivity : ComponentActivity() {
    @Inject lateinit var theme: Theme

    private val viewModel: AddAccountViewModel by viewModels()

    // El resultado del alta llega por accountAdded; aquí no hace falta tratarlo.
    private val syncLauncher = registerForActivityResult(
        ActivityResultContracts.StartActivityForResult()
    ) {}

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        enableEdgeToEdge()

        setContent {
            LaunchedEffect(Unit) {
                viewModel.accountAdded.collect {
                    setResult(Activity.RESULT_OK)
                    finish()
                }
            }
            TasksSettingsTheme(
                theme = theme.themeBase.index,
                primary = theme.themeColor.primaryColor,
            ) {
                AddAccountScreenWrapper(
                    context = this,
                    onBack = { finish() },
                    signInCaldav = { syncLauncher.launch(it) },
                )
            }
        }
    }
}
