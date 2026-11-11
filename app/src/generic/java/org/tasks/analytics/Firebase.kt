package org.tasks.analytics

import android.content.Context
import dagger.hilt.android.qualifiers.ApplicationContext
import org.tasks.R
import org.tasks.preferences.TasksPreferences
import timber.log.Timber
import javax.inject.Inject

@Suppress("UNUSED_PARAMETER")
class Firebase @Inject constructor(
    @param:ApplicationContext val context: Context,
    override val tasksPreferences: TasksPreferences,
) : Reporting {
    override val analyticsEnabled: Boolean
        get() = false

    override fun reportException(t: Throwable, fatal: Boolean) = Timber.e(t)

    override fun logEvent(event: String, vararg params: Pair<String, Any>) {
        Timber.d("$event -> ${params.toMap()}")
    }

    fun logEvent(event: Int, vararg params: Pair<Int, Any>) {
        logEvent(
            event = context.getString(event),
            params = params.map { context.getString(it.first) to it.second }.toTypedArray()
        )
    }

    suspend fun logEventOncePerDay(event: Int, vararg params: Pair<Int, Any>) {
        if (!analyticsEnabled) {
            return
        }
        logEventOncePerDay(
            event = context.getString(event),
            params = params.map { context.getString(it.first) to it.second }.toTypedArray(),
        )
    }

    override fun addTask(source: String) =
        logEvent(R.string.event_add_task, R.string.param_type to source)

    override fun completeTask(source: String) =
        logEvent(R.string.event_complete_task, R.string.param_type to source)

    override fun setSku(sku: String?) {
        Timber.d("sku -> $sku")
    }
}
