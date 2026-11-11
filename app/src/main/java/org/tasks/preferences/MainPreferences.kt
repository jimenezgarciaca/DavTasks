package org.tasks.preferences

import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.os.Bundle
import androidx.activity.viewModels
import dagger.hilt.android.AndroidEntryPoint
import org.tasks.LocalBroadcastManager
import org.tasks.R
import org.tasks.preferences.fragments.MainSettingsComposeFragment
import javax.inject.Inject

@AndroidEntryPoint
class MainPreferences : BasePreferences() {

    @Inject lateinit var localBroadcastManager: LocalBroadcastManager

    private val viewModel: PreferencesViewModel by viewModels()

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
    }

    override fun getRootTitle() = R.string.TLA_menu_settings

    override fun getRootPreference() = MainSettingsComposeFragment()

    override fun onResume() {
        super.onResume()

        localBroadcastManager.registerPreferenceReceiver(refreshReceiver)

        viewModel.updateBackups()
    }

    override fun onPause() {
        super.onPause()

        localBroadcastManager.unregisterReceiver(refreshReceiver)
    }

    private val refreshReceiver = object : BroadcastReceiver() {
        override fun onReceive(context: Context?, intent: Intent?) {
            viewModel.updateBackups()
        }
    }
}