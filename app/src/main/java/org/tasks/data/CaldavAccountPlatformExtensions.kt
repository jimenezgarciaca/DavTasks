package org.tasks.data

import android.app.Activity
import org.tasks.caldav.CaldavCalendarSettingsActivity
import org.tasks.caldav.LocalListSettingsActivity
import org.tasks.data.entity.CaldavAccount
import org.tasks.opentasks.OpenTasksListSettingsActivity

fun CaldavAccount.listSettingsClass(): Class<out Activity> = when(accountType) {
    CaldavAccount.TYPE_LOCAL -> LocalListSettingsActivity::class.java
    CaldavAccount.TYPE_OPENTASKS -> OpenTasksListSettingsActivity::class.java
    else -> CaldavCalendarSettingsActivity::class.java
}
