package org.tasks.viewmodel

import androidx.datastore.preferences.core.booleanPreferencesKey
import org.tasks.data.dao.CaldavDao
import org.tasks.filters.NavigationDrawerSubheader
import org.tasks.kmp.org.tasks.themes.ColorProvider
import org.tasks.preferences.TasksPreferences

suspend fun toggleCollapsed(
    subheader: NavigationDrawerSubheader,
    caldavDao: CaldavDao,
    tasksPreferences: TasksPreferences,
) {
    val collapsed = !subheader.isCollapsed
    when (subheader.subheaderType) {
        NavigationDrawerSubheader.SubheaderType.PREFERENCE ->
            tasksPreferences.set(booleanPreferencesKey(subheader.id), collapsed)
        NavigationDrawerSubheader.SubheaderType.CALDAV ->
            caldavDao.setCollapsed(subheader.id, collapsed)
    }
}

fun resolveColor(tint: Int, isDark: Boolean): Int? =
    if (tint == 0) null else ColorProvider.getColor(tint, isDark, adjust = true)
