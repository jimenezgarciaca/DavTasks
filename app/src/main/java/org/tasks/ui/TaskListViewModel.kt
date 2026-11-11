package org.tasks.ui

import android.content.Context
import androidx.lifecycle.viewModelScope
import dagger.hilt.android.lifecycle.HiltViewModel
import dagger.hilt.android.qualifiers.ApplicationContext
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.NonCancellable
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch
import org.tasks.R
import org.tasks.broadcast.ComposeRefreshBroadcaster
import org.tasks.data.TaskSaver
import org.tasks.data.dao.DeletionDao
import org.tasks.data.dao.TaskDao
import org.tasks.extensions.Context.canScheduleExactAlarms
import org.tasks.filters.SearchFilter
import org.tasks.preferences.PermissionChecker
import org.tasks.preferences.Preferences
import org.tasks.preferences.TasksPreferences
import org.tasks.service.TaskCompleter
import org.tasks.service.TaskDeleter
import org.tasks.tasklist.HeaderFormatter
import javax.inject.Inject

/** Avisos de la lista de tareas: solo los de permisos de notificaciones y alarmas. */
sealed class Banner {
    data object NotificationsDisabled : Banner()
    data object AlarmsDisabled : Banner()
}

@HiltViewModel
class TaskListViewModel @Inject constructor(
    @ApplicationContext private val applicationContext: Context,
    private val preferences: Preferences,
    taskDao: TaskDao,
    taskDeleter: TaskDeleter,
    deletionDao: DeletionDao,
    taskSaver: TaskSaver,
    taskCompleter: TaskCompleter,
    private val tasksPreferences: TasksPreferences,
    composeRefreshBroadcaster: ComposeRefreshBroadcaster,
    private val permissionChecker: PermissionChecker,
    headerFormatter: HeaderFormatter,
) : org.tasks.viewmodel.TaskListViewModel(
    taskDao = taskDao,
    taskDeleter = taskDeleter,
    deletionDao = deletionDao,
    taskSaver = taskSaver,
    taskCompleter = taskCompleter,
    tasksPreferences = tasksPreferences,
    headerFormatter = headerFormatter,
    queryPreferences = preferences,
    isPerListSortEnabled = preferences.isPerListSortEnabled,
    createSearchFilter = { query ->
        SearchFilter(applicationContext.getString(R.string.FLA_search_filter, query), query)
    },
    refreshFlow = composeRefreshBroadcaster.refreshes,
) {
    private val _banner = MutableStateFlow<Banner?>(null)
    val banner = _banner.asStateFlow()

    fun updateBannerState() {
        viewModelScope.launch(Dispatchers.IO) {
            _banner.value = when {
                preferences.warnNotificationsDisabled && !permissionChecker.hasNotificationPermission() ->
                    Banner.NotificationsDisabled
                preferences.warnAlarmsDisabled && !applicationContext.canScheduleExactAlarms() ->
                    Banner.AlarmsDisabled
                else -> null
            }
        }
    }

    fun dismissBanner(tookAction: Boolean = false) {
        val currentBanner = _banner.value ?: return
        viewModelScope.launch(NonCancellable) {
            when (currentBanner) {
                Banner.NotificationsDisabled -> preferences.warnNotificationsDisabled = tookAction
                Banner.AlarmsDisabled -> preferences.warnAlarmsDisabled = tookAction
            }
            updateBannerState()
        }
    }
}
