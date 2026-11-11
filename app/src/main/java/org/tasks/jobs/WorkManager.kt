package org.tasks.jobs

import org.tasks.data.entity.Task
import org.tasks.sync.SyncSource

interface WorkManager : BackgroundWork {

    override fun updateCalendar(task: Task)

    override suspend fun sync(source: SyncSource)

    suspend fun startEnqueuedSync()

    override fun updateBackgroundSync()

    override suspend fun scheduleRefresh(timestamp: Long)

    fun triggerNotifications(expedited: Boolean = false)

    fun scheduleNotification(scheduledTime: Long)

    fun scheduleBackup()

    companion object {
        const val TAG_BACKUP = "tag_backup"
        const val TAG_REFRESH = "tag_refresh"
        const val TAG_SYNC = "tag_sync"
        const val TAG_BACKGROUND_SYNC = "tag_background_sync"
        const val TAG_NOTIFICATIONS = "tag_notifications"
    }
}
