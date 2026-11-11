package org.tasks.preferences.fragments

import android.content.Intent
import android.os.Bundle
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import androidx.fragment.app.Fragment
import androidx.fragment.app.activityViewModels
import androidx.fragment.app.viewModels
import androidx.fragment.compose.content
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.lifecycleScope
import dagger.hilt.android.AndroidEntryPoint
import kotlinx.coroutines.launch
import org.tasks.R
import org.tasks.compose.accounts.AddAccountActivity
import org.tasks.compose.settings.AndroidMainSettingsScreen
import org.tasks.compose.settings.SettingsDestination
import org.tasks.data.entity.CaldavAccount
import org.tasks.preferences.BasePreferences
import org.tasks.preferences.MainPreferences
import org.tasks.preferences.MainSettingsViewModel
import org.tasks.preferences.Preferences
import org.tasks.preferences.PreferencesViewModel
import org.tasks.preferences.fragments.CaldavAccountFragment.Companion.newCaldavAccountFragment
import org.tasks.preferences.fragments.LocalAccount.Companion.newLocalAccountPreference
import org.tasks.preferences.fragments.OpenTaskAccountFragment.Companion.newOpenTaskAccountFragment
import org.tasks.resources.getString
import org.tasks.themes.TasksSettingsTheme
import org.tasks.themes.Theme
import javax.inject.Inject

@AndroidEntryPoint
class MainSettingsComposeFragment : Fragment() {

    @Inject lateinit var theme: Theme
    @Inject lateinit var preferences: Preferences

    private val viewModel: MainSettingsViewModel by viewModels()
    private val preferencesViewModel: PreferencesViewModel by activityViewModels()

    override fun onCreateView(
        inflater: LayoutInflater,
        container: ViewGroup?,
        savedInstanceState: Bundle?
    ) = content {
        TasksSettingsTheme(
            theme = theme.themeBase.index,
            primary = theme.themeColor.primaryColor,
        ) {
            val accounts = viewModel.accounts.collectAsStateWithLifecycle().value
            val showBackupWarning = preferences.showBackupWarnings() &&
                    (preferencesViewModel.usingPrivateStorage ||
                            preferencesViewModel.staleLocalBackup ||
                            preferencesViewModel.staleRemoteBackup)

            AndroidMainSettingsScreen(
                accounts = accounts,
                showBackupWarning = showBackupWarning,
                showWidgets = viewModel.supportsWidgets,
                onAccountClick = { account -> handleAccountClick(account) },
                onAddAccountClick = {
                    startActivity(Intent(requireContext(), AddAccountActivity::class.java))
                },
                onSettingsClick = { destination -> navigateToSettings(destination) },
            )
        }
    }

    override fun onResume() {
        super.onResume()
        preferencesViewModel.updateBackups()
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

    private fun handleAccountClick(account: CaldavAccount) {
        val activity = activity as? MainPreferences ?: return
        when {
            account.isLocalList -> {
                activity.startPreference(
                    newLocalAccountPreference(account),
                    getString(R.string.local_lists)
                )
            }
            account.isCaldavAccount -> {
                activity.startPreference(
                    newCaldavAccountFragment(account),
                    getString(R.string.caldav)
                )
            }
            account.isOpenTasks -> {
                activity.startPreference(
                    newOpenTaskAccountFragment(account),
                    account.name ?: ""
                )
            }
            else -> {}
        }
    }

    private fun navigateToSettings(destination: SettingsDestination) {
        val activity = activity as? MainPreferences ?: return
        val fragment: Fragment = when (destination) {
            SettingsDestination.LookAndFeel -> LookAndFeel()
            SettingsDestination.Notifications -> Notifications()
            SettingsDestination.TaskDefaults -> TaskDefaults()
            SettingsDestination.TaskList -> TaskListPreferences()
            SettingsDestination.TaskEdit -> TaskEditPreferences()
            SettingsDestination.DateAndTime -> DateAndTime()
            SettingsDestination.NavigationDrawer -> NavigationDrawer()
            SettingsDestination.Backups -> Backups()
            SettingsDestination.Widgets -> Widgets()
            SettingsDestination.Advanced -> Advanced()
            SettingsDestination.WorksWith -> WorksWith()
            SettingsDestination.HelpAndFeedback -> HelpAndFeedback()
            SettingsDestination.Debug -> Debug()
        }
        viewLifecycleOwner.lifecycleScope.launch {
            activity.startPreference(
                fragment,
                getString(destination.titleRes)
            )
        }
    }
}
