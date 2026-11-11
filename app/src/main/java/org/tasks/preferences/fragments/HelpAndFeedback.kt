package org.tasks.preferences.fragments

import android.content.Intent
import android.os.Bundle
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.navigationBars
import androidx.compose.foundation.layout.windowInsetsBottomHeight
import androidx.compose.ui.Modifier
import androidx.core.content.FileProvider
import androidx.fragment.app.Fragment
import androidx.fragment.compose.content
import androidx.lifecycle.lifecycleScope
import com.todoroo.astrid.utility.Constants
import dagger.hilt.android.AndroidEntryPoint
import kotlinx.coroutines.launch
import org.tasks.BuildConfig
import org.tasks.R
import org.tasks.TasksUrls
import org.tasks.compose.settings.HelpAndFeedbackScreen
import org.tasks.extensions.Context.openUri
import org.tasks.logging.FileLogger
import org.tasks.preferences.BasePreferences
import org.tasks.preferences.DiagnosticInfo
import org.tasks.themes.TasksSettingsTheme
import org.tasks.themes.Theme
import javax.inject.Inject

@AndroidEntryPoint
class HelpAndFeedback : Fragment() {

    @Inject lateinit var theme: Theme
    @Inject lateinit var diagnosticInfo: DiagnosticInfo
    @Inject lateinit var fileLogger: FileLogger

    override fun onCreateView(
        inflater: LayoutInflater,
        container: ViewGroup?,
        savedInstanceState: Bundle?
    ) = content {
        TasksSettingsTheme(
            theme = theme.themeBase.index,
            primary = theme.themeColor.primaryColor,
        ) {
            HelpAndFeedbackScreen(
                versionName = BuildConfig.VERSION_NAME,
                onDocumentation = { context?.openUri(TasksUrls.DOCUMENTATION) },
                onIssueTracker = { context?.openUri(TasksUrls.ISSUE_TRACKER) },
                onSendLogs = { sendLogs() },
                onSourceCode = { context?.openUri(TasksUrls.SOURCE_CODE) },
                bottomInsets = {
                    Spacer(modifier = Modifier.windowInsetsBottomHeight(WindowInsets.navigationBars))
                },
            )
        }
    }

    /** Comparte el zip de registros; el usuario elige la aplicación y el destinatario. */
    private fun sendLogs() = lifecycleScope.launch {
        val file = FileProvider.getUriForFile(
            requireContext(),
            Constants.FILE_PROVIDER_AUTHORITY,
            fileLogger.getZipFile()
        )
        val intent = Intent(Intent.ACTION_SEND)
            .setType("application/zip")
            .putExtra(Intent.EXTRA_SUBJECT, "DavTasks logs")
            .putExtra(Intent.EXTRA_TEXT, diagnosticInfo.debugInfo)
            .putExtra(Intent.EXTRA_STREAM, file)
            .addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION)
        startActivity(Intent.createChooser(intent, getString(R.string.share)))
    }

    override fun onResume() {
        super.onResume()
        val surfaceColor = theme.themeBase.getSettingsSurfaceColor(requireActivity())
        (activity as? BasePreferences)?.toolbar?.let { toolbar ->
            toolbar.setBackgroundColor(surfaceColor)
            (toolbar.parent as? View)?.setBackgroundColor(surfaceColor)
        }
    }

    override fun onDestroyView() {
        super.onDestroyView()
        val defaultColor = androidx.core.content.ContextCompat.getColor(requireContext(), R.color.content_background)
        (activity as? BasePreferences)?.toolbar?.let { toolbar ->
            toolbar.setBackgroundColor(defaultColor)
            (toolbar.parent as? View)?.setBackgroundColor(defaultColor)
        }
    }
}
