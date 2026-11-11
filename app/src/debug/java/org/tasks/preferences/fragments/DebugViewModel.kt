package org.tasks.preferences.fragments

import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.launch
import org.tasks.R
import org.tasks.data.createDueDate
import org.tasks.data.entity.Task
import org.tasks.preferences.Preferences
import org.tasks.preferences.TasksPreferences
import org.tasks.time.DateTimeUtils2.currentTimeMillis
import java.util.concurrent.TimeUnit
import javax.inject.Inject
import kotlin.math.min

@HiltViewModel
class DebugViewModel @Inject constructor(
    private val preferences: Preferences,
    private val tasksPreferences: TasksPreferences,
    private val taskCreator: com.todoroo.astrid.service.TaskCreator,
    private val taskDao: org.tasks.data.dao.TaskDao,
    private val taskSaver: org.tasks.data.TaskSaver,
) : ViewModel() {

    var strictModeVmEnabled by mutableStateOf(false)
        private set
    var strictModeThreadEnabled by mutableStateOf(false)
        private set
    var crashOnViolationEnabled by mutableStateOf(false)
        private set
    var showDebugFilters by mutableStateOf(false)
        private set
    var showRestartDialog by mutableStateOf(false)
        private set

    fun refreshState() {
        strictModeVmEnabled = preferences.getBoolean(R.string.p_strict_mode_vm, false)
        strictModeThreadEnabled = preferences.getBoolean(R.string.p_strict_mode_thread, false)
        crashOnViolationEnabled = preferences.getBoolean(R.string.p_crash_main_queries, false)
        viewModelScope.launch {
            showDebugFilters = tasksPreferences.get(TasksPreferences.showDebugFilters, false)
        }
    }

    fun updateStrictModeVm(enabled: Boolean) {
        preferences.setBoolean(R.string.p_strict_mode_vm, enabled)
        strictModeVmEnabled = enabled
        showRestartDialog = true
    }

    fun updateStrictModeThread(enabled: Boolean) {
        preferences.setBoolean(R.string.p_strict_mode_thread, enabled)
        strictModeThreadEnabled = enabled
        showRestartDialog = true
    }

    fun updateCrashOnViolation(enabled: Boolean) {
        preferences.setBoolean(R.string.p_crash_main_queries, enabled)
        crashOnViolationEnabled = enabled
        showRestartDialog = true
    }

    fun updateShowDebugFilters(enabled: Boolean) {
        viewModelScope.launch {
            tasksPreferences.set(TasksPreferences.showDebugFilters, enabled)
            showDebugFilters = enabled
        }
    }

    fun dismissRestartDialog() {
        showRestartDialog = false
    }

    fun clearHints() {
        preferences.installDate = min(
            preferences.installDate,
            currentTimeMillis() - TimeUnit.DAYS.toMillis(14),
        )
        preferences.setBoolean(R.string.p_just_updated, true)
        preferences.setBoolean(R.string.p_local_list_banner_dismissed, false)
        preferences.warnAlarmsDisabled = true
        preferences.warnNotificationsDisabled = true
    }

    fun createTasks(onComplete: (Int) -> Unit) {
        viewModelScope.launch {
            val count = 5000
            for (i in 1..count) {
                val task = taskCreator.createWithValues("")
                taskDao.createNew(task)
                task.title = "Task ${task.id}"
                task.dueDate = createDueDate(
                    Task.URGENCY_SPECIFIC_DAY,
                    currentTimeMillis(),
                )
                taskSaver.save(task, null)
            }
            onComplete(count)
        }
    }
}
