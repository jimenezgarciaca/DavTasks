/*
 * Copyright (c) 2012 Todoroo Inc
 *
 * See the file "LICENSE" for the full license governing this code.
 */
package com.todoroo.astrid.adapter

import com.todoroo.astrid.core.SortHelper.SORT_DUE
import com.todoroo.astrid.core.SortHelper.SORT_IMPORTANCE
import com.todoroo.astrid.core.SortHelper.SORT_LIST
import com.todoroo.astrid.core.SortHelper.SORT_MANUAL
import com.todoroo.astrid.core.SortHelper.SORT_START
import org.tasks.data.dao.TaskDao
import org.tasks.data.TaskSaver
import org.tasks.data.TaskMover
import org.tasks.broadcast.RefreshBroadcaster
import org.tasks.data.TaskContainer
import org.tasks.data.createDueDate
import org.tasks.data.createHideUntil
import org.tasks.data.deepestNestingUnder
import org.tasks.data.findParentIndex
import org.tasks.data.dao.CaldavDao
import org.tasks.data.dao.CaldavDao.Companion.toAppleEpoch
import org.tasks.data.dao.DirtyDao
import org.tasks.data.entity.CaldavTask
import org.tasks.data.entity.Task
import org.tasks.data.entity.Task.Companion.HIDE_UNTIL_SPECIFIC_DAY
import org.tasks.date.DateTimeUtils.toDateTime
import org.tasks.time.millisOfDay
import kotlin.math.min

open class TaskAdapter(
    private val newTasksOnTop: Boolean,
    private val caldavDao: CaldavDao,
    private val taskDao: TaskDao,
    private val taskSaver: TaskSaver,
    private val dirtyDao: DirtyDao,
    private val refreshBroadcaster: RefreshBroadcaster,
    private val taskMover: TaskMover,
) {
    private val selected = HashSet<Long>()
    private lateinit var dataSource: TaskAdapterDataSource

    val count: Int
        get() = dataSource.getTaskCount()

    fun setDataSource(dataSource: TaskAdapterDataSource) {
        this.dataSource = dataSource
    }

    val numSelected: Int
        get() = selected.size

    fun getSelected(): ArrayList<Long> = ArrayList(selected)

    fun setSelected(ids: Collection<Long>) {
        clearSelections()
        selected.addAll(ids)
    }

    fun clearSelections() = selected.clear()

    open fun getIndent(task: TaskContainer): Int = task.indent

    open fun canMove(source: TaskContainer, from: Int, target: TaskContainer, to: Int): Boolean =
        !taskIsChild(source, to)

    open fun maxIndent(previousPosition: Int, task: TaskContainer): Int {
        val previous = getTask(previousPosition)
        var indent = deepestNestingUnder(previous.indent)
        while (!task.isCompleted && indent > 0 &&
            findParent(indent, previousPosition + 1)?.isCompleted == true
        ) {
            indent--
        }
        return indent
    }

    fun minIndent(nextPosition: Int, task: TaskContainer): Int {
        (nextPosition until count).forEach {
            if (isHeader(it)) {
                return 0
            }
            val next = getTask(it)
            if (!taskIsChild(task, it)) {
                return next.indent
            }
        }
        return 0
    }

    fun minIndent(nextPosition: Int, task: TaskContainer, maxIndent: Int): Int =
        min(minIndent(nextPosition, task), maxIndent)

    fun isSelected(task: TaskContainer): Boolean = selected.contains(task.id)

    fun toggleSelection(task: TaskContainer) {
        val id = task.id
        if (selected.contains(id)) {
            selected.remove(id)
        } else {
            selected.add(id)
        }
    }

    open fun supportsAstridSorting(): Boolean = false

    open suspend fun moved(from: Int, to: Int, indent: Int) {
        val task = getTask(from)
        val newParent = findParent(indent, to)
        if ((newParent?.id ?: 0) == task.parent || (indent > 0 && dataSource.subtaskSortMode == SORT_MANUAL)) {
            if (indent == 0) {
                changeSortGroup(task, if (from < to) to - 1 else to)
            } else if (dataSource.subtaskSortMode == SORT_MANUAL) {
                moveCaldavTask(from, to, indent)
            }
            return
        }
        if (newParent?.caldav != null && task.caldav != newParent.caldav) {
            taskMover.move(task = task.id, listUuid = newParent.caldav!!, newParent = newParent.id)
            return
        }
        when {
            newParent == null -> {
                moveToTopLevel(task)
                changeSortGroup(task, if (from < to) to - 1 else to)
            }
            newParent.isCaldavTask() -> changeCaldavParent(task, newParent)
        }
    }

    fun isHeader(position: Int): Boolean = dataSource.isHeader(position)

    fun getTask(position: Int): TaskContainer = dataSource.getItem(position)!!

    fun getItemUuid(position: Int): String = getTask(position).uuid

    open suspend fun onCompletedTask(uuid: String, newState: Boolean) {}

    open suspend fun onTaskCreated(uuid: String) {}

    open suspend fun onTaskDeleted(task: Task) {}

    open fun supportsHiddenTasks(): Boolean = true

    private fun taskIsChild(source: TaskContainer, destinationIndex: Int): Boolean {
        (destinationIndex downTo 0).forEach {
            if (isHeader(it)) {
                return false
            }
            when (getTask(it).parent) {
                0L -> return false
                source.parent -> return false
                source.id -> return true
            }
        }
        return false
    }

    private fun findParent(indent: Int, to: Int): TaskContainer? =
        findParentIndex(indent, to) { getTask(it).indent }?.let { getTask(it) }

    private suspend fun changeSortGroup(task: TaskContainer, pos: Int) {
        when(dataSource.sortMode) {
            SORT_IMPORTANCE -> {
                val newPriority = dataSource.nearestHeader(if (pos == 0) 1 else pos).toInt()
                if (newPriority != task.priority) {
                    taskSaver.save(task.task.copy(priority = newPriority), task.task)
                }
            }
            SORT_LIST -> taskMover.move(task.id, dataSource.nearestHeader(if (pos == 0) 1 else pos))
            SORT_DUE -> applyDueDate(task.task, dataSource.nearestHeader(if (pos == 0) 1 else pos))
            SORT_START -> applyStartDate(task.task, dataSource.nearestHeader(if (pos == 0) 1 else pos))
        }
    }

    private suspend fun applyDueDate(task: Task, date: Long) {
        val original = task.copy()
        task.setDueDateAdjustingHideUntil(when {
            date == 0L -> 0L
            task.hasDueTime() -> date.toDateTime().withMillisOfDay(original.dueDate.millisOfDay).millis
            else -> createDueDate(Task.URGENCY_SPECIFIC_DAY, date)
        })
        if (original.dueDate != task.dueDate) {
            taskSaver.save(task, original)
        }
    }

    private suspend fun applyStartDate(task: Task, date: Long) {
        val original = task.copy()
        task.hideUntil = when {
            date == 0L -> 0L
            task.hasStartDate() -> date.toDateTime().withMillisOfDay(original.hideUntil.millisOfDay).millis
            else -> task.createHideUntil(HIDE_UNTIL_SPECIFIC_DAY, date)
        }
        if (original.hideUntil != task.hideUntil) {
            taskSaver.save(task, original)
        }
    }

    private suspend fun moveToTopLevel(task: TaskContainer) {
        if (task.isCaldavTask()) {
            changeCaldavParent(task, null)
        }
    }

    private suspend fun changeCaldavParent(task: TaskContainer, newParent: TaskContainer?) {
        val list = newParent?.caldav ?: task.caldav!!
        val caldavTask = task.caldavTask.takeIf { list == task.caldav } ?: CaldavTask(
            task = task.id,
            calendar = list,
        )
        val newParentId = newParent?.id ?: 0
        if (newParentId == 0L) {
            caldavTask.remoteParent = ""
        } else {
            caldavTask.calendar = list
            caldavTask.remoteParent = newParent?.caldavTask?.remoteId ?: return
        }
        task.task.order = if (newTasksOnTop) {
            caldavDao.findFirstTask(list, newParentId)
                    ?.takeIf { task.creationDate.toAppleEpoch() >= it}
                    ?.minus(1)
        } else {
            caldavDao.findLastTask(list, newParentId)
                    ?.takeIf { task.creationDate.toAppleEpoch() <= it }
                    ?.plus(1)
        }
        taskDao.inTransaction {
            if (caldavTask.id == 0L) {
                caldavDao.insert(
                    CaldavTask(
                        task = task.id,
                        calendar = list,
                        remoteParent = caldavTask.remoteParent,
                    )
                )
            } else {
                caldavDao.update(caldavTask)
            }
            taskDao.setOrder(task.id, task.task.order)
            taskDao.setParent(newParentId, listOf(task.id))
            dirtyDao.setDirty(listOf(task.id))
        }
        refreshBroadcaster.broadcastRefresh()
    }

    protected suspend fun moveCaldavTask(from: Int, to: Int, indent: Int) {
        val task = getTask(from)
        val oldParent = task.parent
        val newParent = changeCaldavParent(task, indent, to)

        if (oldParent == newParent && from == to) {
            return
        }

        val previous = if (to > 0) getTask(to - 1) else null
        val next = if (to < count) getTask(to) else null

        val newPosition = when {
            previous == null -> next!!.caldavSortOrder - 1
            indent > previous.indent && next?.indent == indent -> next.caldavSortOrder - 1
            indent > previous.indent -> null
            indent == previous.indent -> previous.caldavSortOrder + 1
            else -> getTask((to - 1 downTo 0).find { getTask(it).indent == indent }!!).caldavSortOrder + 1
        }
        caldavDao.move(
            task = task,
            previousParent = oldParent,
            newParent = newParent,
            newPosition = newPosition,
        )
        refreshBroadcaster.broadcastRefresh()
    }

    private suspend fun changeCaldavParent(task: TaskContainer, indent: Int, to: Int): Long {
        val newParent = findParent(indent, to)?.id ?: 0
        if (task.parent != newParent) {
            changeCaldavParent(task, newParent)
        }
        return newParent
    }

    private suspend fun changeCaldavParent(task: TaskContainer, newParent: Long) {
        val caldavTask = task.caldavTask ?: return
        if (newParent == 0L) {
            caldavTask.remoteParent = ""
            caldavDao.update(caldavTask.id, caldavTask.remoteParent)
        } else {
            val parentTask = caldavDao.getTask(newParent) ?: return
            if (parentTask.calendar == caldavTask.calendar) {
                caldavTask.remoteParent = parentTask.remoteId
                caldavDao.update(caldavTask.id, caldavTask.remoteParent)
            } else {
                taskMover.move(task = task.id, listUuid = parentTask.calendar!!, newParent = newParent)
                return
            }
        }
        task.parent = newParent
        taskSaver.save(task.task, null)
    }
}