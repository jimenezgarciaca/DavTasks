package org.tasks.data

import co.touchlab.kermit.Logger
import org.tasks.broadcast.RefreshBroadcaster
import org.tasks.data.dao.CaldavDao
import org.tasks.data.dao.DirtyDao
import org.tasks.data.dao.TaskDao
import org.tasks.data.db.DbUtils.dbchunk
import org.tasks.data.entity.CaldavTask
import org.tasks.data.entity.Task
import org.tasks.filters.CaldavFilter
import org.tasks.filters.Filter
import org.tasks.preferences.AppPreferences
import org.tasks.service.TaskDeleter

class TaskMover(
    private val taskDao: TaskDao,
    private val caldavDao: CaldavDao,
    private val dirtyDao: DirtyDao,
    private val appPreferences: AppPreferences,
    private val refreshBroadcaster: RefreshBroadcaster,
    private val taskDeleter: TaskDeleter,
) {
    private val log = Logger.withTag("TaskMover")

    suspend fun getSingleFilter(tasks: List<Long>): Filter? {
        val caldavCalendars = caldavDao.getCalendars(tasks)
        return if (caldavCalendars.size == 1) {
            val list = caldavCalendars.first()
            val account = list.account?.let { caldavDao.getAccountByUuid(it) }
            account?.let { CaldavFilter(calendar = list, account = it) }
        } else {
            null
        }
    }

    suspend fun move(task: Long, list: Long) {
        val calendar = caldavDao.getCalendarById(list) ?: return
        val account = calendar.account?.let { caldavDao.getAccountByUuid(it) } ?: return
        move(
            ids = listOf(task),
            selectedList = CaldavFilter(calendar = calendar, account = account),
        )
    }

    suspend fun move(task: Long, listUuid: String, newParent: Long) {
        val calendar = caldavDao.getCalendar(listUuid) ?: return
        val account = calendar.account?.let { caldavDao.getAccountByUuid(it) } ?: return
        move(
            ids = listOf(task),
            selectedList = CaldavFilter(calendar = calendar, account = account),
            newParent = newParent,
        )
    }

    suspend fun move(ids: List<Long>, selectedList: CaldavFilter, newParent: Long = 0L) {
        val tasks = ids
            .dbchunk()
            .flatMap { taskDao.getChildren(it) }
            .let { taskDao.fetch(ids.minus(it.toSet())) }
            .filterNot { it.readOnly }
        val taskIds = tasks.map { it.id }
        val unparented = tasks.filter { it.parent > 0 }.map { it.id }
        taskDao.inTransaction {
            taskDao.setParent(0, taskIds)
            val moved = tasks.filter { performMove(it, selectedList) }.map { it.id }
            if (newParent != 0L) {
                nestUnderParent(taskIds, newParent, selectedList)
            }
            log.d { "Updating parents for ${selectedList.uuid}" }
            caldavDao.updateParents(selectedList.uuid, force = true)
            val changed = if (newParent != 0L) taskIds else (moved + unparented).distinct()
            dirtyDao.setDirty(changed + taskDao.getChildren(changed))
        }
        refreshBroadcaster.broadcastRefresh()
    }

    private suspend fun nestUnderParent(roots: List<Long>, newParent: Long, selectedList: CaldavFilter) {
        val parentRemoteId = caldavDao.getTask(newParent)?.remoteId
        roots.forEach { root ->
            caldavDao.getTask(root)?.let { caldavDao.update(it.id, parentRemoteId) }
        }
    }

    suspend fun migrateLocalTasks() {
        val list = caldavDao.getLocalList()
        val account = list.account?.let { caldavDao.getAccountByUuid(it) } ?: return
        move(taskDao.getLocalTasks(), CaldavFilter(calendar = list, account = account))
    }

    private suspend fun performMove(task: Task, selectedList: CaldavFilter): Boolean {
        caldavDao.getTask(task.id)?.let {
            return moveCaldavTask(task, it, selectedList)
        }
        moveLocalTask(task, selectedList)
        return true
    }

    private suspend fun moveCaldavTask(task: Task, caldavTask: CaldavTask, selected: CaldavFilter): Boolean {
        if (caldavTask.calendar == selected.uuid) {
            return false
        }
        val id = task.id
        val childIds = taskDao.getChildren(id)
        taskDeleter.markMoved(childIds + id)
        createCaldavSubtree(task, childIds, selected)
        return true
    }

    private suspend fun moveLocalTask(task: Task, selected: CaldavFilter) {
        createCaldavSubtree(task, taskDao.getChildren(task.id), selected)
    }

    private suspend fun createCaldavSubtree(task: Task, childIds: List<Long>, selected: CaldavFilter) {
        val listId = selected.uuid
        val root = CaldavTask(task = task.id, calendar = listId)
        val byId = taskDao.fetch(childIds).associateBy { it.id }
        val remoteIds = hashMapOf(task.id to root.remoteId)
        val caldavChildren = childIds.mapNotNull { byId[it] }.map { child ->
            CaldavTask(task = child.id, calendar = listId)
                .apply { remoteParent = remoteIds[child.parent] }
                .also { remoteIds[child.id] = it.remoteId }
        }
        caldavDao.insert(task, root, appPreferences.addTasksToTop())
        caldavChildren.takeIf { it.isNotEmpty() }?.let { caldavDao.insert(it) }
    }
}
