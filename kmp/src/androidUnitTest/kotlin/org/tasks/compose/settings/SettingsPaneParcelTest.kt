package org.tasks.compose.settings

import android.os.Bundle
import android.os.Parcel
import org.junit.Assert.assertEquals
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner

@RunWith(RobolectricTestRunner::class)
class SettingsPaneParcelTest {

    @Test
    fun panesSurviveSavedInstanceState() {
        panes.forEach { pane ->
            assertEquals(pane, pane.roundTrip())
        }
    }

    private fun SettingsPane.roundTrip(): SettingsPane {
        val parcel = Parcel.obtain()
        try {
            parcel.writeBundle(Bundle().apply { putParcelable(KEY, this@roundTrip) })
            parcel.setDataPosition(0)
            val restored = parcel.readBundle(SettingsPane::class.java.classLoader)!!
            @Suppress("DEPRECATION")
            return restored.getParcelable(KEY)!!
        } finally {
            parcel.recycle()
        }
    }

    companion object {
        private const val KEY = "pane"

        private val panes = listOf<SettingsPane>(
            SettingsDestination.LookAndFeel,
            SettingsDestination.Notifications,
            SettingsDestination.TaskDefaults,
            SettingsDestination.TaskList,
            SettingsDestination.TaskEdit,
            SettingsDestination.DateAndTime,
            SettingsDestination.NavigationDrawer,
            SettingsDestination.Backups,
            SettingsDestination.Widgets,
            SettingsDestination.Advanced,
            SettingsDestination.WorksWith,
            SettingsDestination.HelpAndFeedback,
            SettingsDestination.Debug,
            LocalAccountSettingsPane(accountId = 1),
            CaldavAccountSettingsPane(accountId = 2),
            OpenTaskAccountSettingsPane(accountId = 6),
        )
    }
}
