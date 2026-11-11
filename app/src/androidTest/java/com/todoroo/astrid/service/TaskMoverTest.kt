package com.todoroo.astrid.service

import com.natpryce.makeiteasy.MakeItEasy.with
import dagger.hilt.android.testing.HiltAndroidTest
import kotlinx.coroutines.runBlocking
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotEquals
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test
import org.tasks.data.TaskMover
import org.tasks.data.dao.CaldavDao
import org.tasks.data.dao.TaskDao
import org.tasks.data.entity.CaldavAccount
import org.tasks.data.entity.CaldavAccount.Companion.TYPE_CALDAV
import org.tasks.data.entity.CaldavCalendar
import org.tasks.filters.CaldavFilter
import org.tasks.injection.InjectingTestCase
import org.tasks.makers.CaldavTaskMaker.CALENDAR
import org.tasks.makers.CaldavTaskMaker.REMOTE_ID
import org.tasks.makers.CaldavTaskMaker.REMOTE_PARENT
import org.tasks.makers.CaldavTaskMaker.TASK
import org.tasks.makers.CaldavTaskMaker.newCaldavTask
import org.tasks.makers.TaskMaker.ID
import org.tasks.makers.TaskMaker.PARENT
import org.tasks.makers.TaskMaker.newTask
import javax.inject.Inject

@HiltAndroidTest
class TaskMoverTest : InjectingTestCase() {
    @Inject lateinit var taskDao: TaskDao
    @Inject lateinit var caldavDao: CaldavDao
    @Inject lateinit var taskMover: TaskMover

    @Before
    fun setup() {
        runBlocking {
            caldavDao.insert(CaldavCalendar(uuid = "1", account = "account1"))
            caldavDao.insert(CaldavCalendar(uuid = "2", account = "account2"))
        }
    }

    @Test
    fun moveBetweenCaldavList() = runBlocking {
        createTasks(1)
        caldavDao.insert(newCaldavTask(with(TASK, 1L), with(CALENDAR, "1")))
        moveToCaldavList("2", 1)
        assertEquals("2", caldavDao.getTask(1)!!.calendar)
    }

    @Test
    fun deleteCaldavTaskAfterMove() = runBlocking {
        createTasks(1)
        caldavDao.insert(newCaldavTask(with(TASK, 1L), with(CALENDAR, "1")))
        moveToCaldavList("2", 1)
        val deleted = caldavDao.getMoved("1")
        assertEquals(1, deleted.size.toLong())
        assertEquals(1, deleted[0].task)
        assertTrue(deleted[0].deleted > 0)
    }

    @Test
    fun moveRecursiveCaldavChildren() = runBlocking {
        createTasks(1)
        createSubtask(2, 1)
        createSubtask(3, 2)
        caldavDao.insert(
                listOf(
                        newCaldavTask(
                                with(TASK, 1L), with(CALENDAR, "1"), with(REMOTE_ID, "a")),
                        newCaldavTask(
                                with(TASK, 2L),
                                with(CALENDAR, "1"),
                                with(REMOTE_ID, "b"),
                                with(REMOTE_PARENT, "a")),
                        newCaldavTask(
                                with(TASK, 3L),
                                with(CALENDAR, "1"),
                                with(REMOTE_PARENT, "b"))))
        moveToCaldavList("2", 1)
        val deleted = caldavDao.getMoved("1")
        assertEquals(3, deleted.size.toLong())
        val task = caldavDao.getTask(3)
        assertEquals("2", task!!.calendar)
        assertEquals(2, taskDao.fetch(3)!!.parent)
    }

    @Test
    fun moveSubtreeUnderParentInAnotherList() = runBlocking {
        // existing parent in list "2"
        createTasks(1)
        caldavDao.insert(newCaldavTask(with(TASK, 1L), with(CALENDAR, "2"), with(REMOTE_ID, "p")))
        // task with a subtask in list "1"
        createTasks(2)
        createSubtask(3, 2)
        caldavDao.insert(
                listOf(
                        newCaldavTask(with(TASK, 2L), with(CALENDAR, "1"), with(REMOTE_ID, "x")),
                        newCaldavTask(with(TASK, 3L), with(CALENDAR, "1"), with(REMOTE_PARENT, "x"))))
        // drag task 2 (with subtask 3) under task 1 in the other list
        taskMover.move(
            ids = listOf(2L),
            selectedList = CaldavFilter(CaldavCalendar(name = "", uuid = "2"), CaldavAccount(accountType = TYPE_CALDAV)),
            newParent = 1L,
        )
        // whole subtree moved to the destination list
        assertEquals("2", caldavDao.getTask(2)!!.calendar)
        assertEquals("2", caldavDao.getTask(3)!!.calendar)
        // task 2 nested under task 1, and its own subtree preserved
        assertEquals(1L, taskDao.fetch(2)!!.parent)
        assertEquals(2L, taskDao.fetch(3)!!.parent)
    }

    @Test
    fun rewriteUidsWhenMovingCaldavSubtree() = runBlocking {
        createTasks(1)
        createSubtask(2, 1)
        createSubtask(3, 2)
        caldavDao.insert(
                listOf(
                        newCaldavTask(
                                with(TASK, 1L), with(CALENDAR, "1"), with(REMOTE_ID, "a")),
                        newCaldavTask(
                                with(TASK, 2L),
                                with(CALENDAR, "1"),
                                with(REMOTE_ID, "b"),
                                with(REMOTE_PARENT, "a")),
                        newCaldavTask(
                                with(TASK, 3L),
                                with(CALENDAR, "1"),
                                with(REMOTE_ID, "c"),
                                with(REMOTE_PARENT, "b"))))
        moveToCaldavList("2", 1)
        val newParent = caldavDao.getTask(1)!!
        val newChild = caldavDao.getTask(2)!!
        val newGrandchild = caldavDao.getTask(3)!!
        // UIDs are rewritten so the moved tasks don't transiently share a UID with the
        // about-to-be-deleted copies in the source list, which corrupts the parent
        // relations in the dmfs provider and flattens the hierarchy locally
        assertNotEquals("a", newParent.remoteId)
        assertNotEquals("b", newChild.remoteId)
        assertNotEquals("c", newGrandchild.remoteId)
        // remoteParent is rewired to the new UIDs at every level of the subtree
        assertEquals(newParent.remoteId, newChild.remoteParent)
        assertEquals(newChild.remoteId, newGrandchild.remoteParent)
        // and the local hierarchy is preserved
        assertEquals(1L, taskDao.fetch(2)!!.parent)
        assertEquals(2L, taskDao.fetch(3)!!.parent)
    }

    @Test
    fun flattenLocalSubtasksWhenMovingToGoogleTasks() = runBlocking {
        createTasks(1)
        createSubtask(2, 1)
        createSubtask(3, 2)
        moveToGoogleTasks("1", 1)
        assertEquals(1L, taskDao.fetch(3)?.parent)
    }

    @Test
    fun moveLocalChildToGoogleTasks() = runBlocking {
        createTasks(1)
        createSubtask(2, 1)
        moveToGoogleTasks("1", 2)
        assertEquals(0L, taskDao.fetch(2)?.parent)
    }

    @Test
    fun moveLocalChildToCaldav() = runBlocking {
        createTasks(1)
        createSubtask(2, 1)
        moveToCaldavList("1", 2)
        assertEquals(0, taskDao.fetch(2)!!.parent)
    }

    @Test
    fun flattenCaldavSubtasksWhenMovingToGoogleTasks() = runBlocking {
        createTasks(1)
        createSubtask(2, 1)
        createSubtask(3, 2)
        caldavDao.insert(
                listOf(
                        newCaldavTask(
                                with(TASK, 1L), with(CALENDAR, "1"), with(REMOTE_ID, "a")),
                        newCaldavTask(
                                with(TASK, 2L),
                                with(CALENDAR, "1"),
                                with(REMOTE_ID, "b"),
                                with(REMOTE_PARENT, "a")),
                        newCaldavTask(
                                with(TASK, 3L),
                                with(CALENDAR, "1"),
                                with(REMOTE_PARENT, "b"))))
        moveToGoogleTasks("2", 1)
        val task = taskDao.fetch(3L)
        assertEquals(1L, task?.parent)
    }

    @Test
    fun moveCaldavChildWithoutParent() = runBlocking {
        createTasks(1)
        createSubtask(2, 1)
        caldavDao.insert(
                listOf(
                        newCaldavTask(
                                with(TASK, 1L), with(CALENDAR, "1"), with(REMOTE_ID, "a")),
                        newCaldavTask(
                                with(TASK, 2L),
                                with(CALENDAR, "1"),
                                with(REMOTE_PARENT, "a"))))
        moveToCaldavList("2", 2)
        assertEquals("2", caldavDao.getTask(2)!!.calendar)
        assertEquals(0, taskDao.fetch(2)!!.parent)
    }

    @Test
    fun moveLocalToCaldav() = runBlocking {
        createTasks(1)
        createSubtask(2, 1)
        createSubtask(3, 2)
        moveToCaldavList("1", 1)
        assertEquals("1", caldavDao.getTask(3)?.calendar)
        assertEquals(2L, taskDao.fetch(3)?.parent)
    }

    @Test
    fun moveToSameCaldavListIsNoop() = runBlocking {
        createTasks(1)
        caldavDao.insert(newCaldavTask(with(TASK, 1L), with(CALENDAR, "1")))
        moveToCaldavList("1", 1)
        assertTrue(caldavDao.getMoved("1").isEmpty())
        assertEquals(1, caldavDao.getTasks(1).size.toLong())
    }

    @Test
    fun dontDuplicateWhenParentAndChildCaldavMoved() = runBlocking {
        createTasks(1)
        createSubtask(2, 1)
        caldavDao.insert(
                listOf(
                        newCaldavTask(
                                with(TASK, 1L), with(CALENDAR, "1"), with(REMOTE_ID, "a")),
                        newCaldavTask(
                                with(TASK, 2L),
                                with(CALENDAR, "1"),
                                with(REMOTE_PARENT, "a"))))
        moveToCaldavList("2", 1, 2)
        assertEquals(1, caldavDao.getTasks(2).filter { it.deleted == 0L }.size)
    }

    private suspend fun createTasks(vararg ids: Long) {
        for (id in ids) {
            taskDao.createNew(newTask(with(ID, id)))
        }
    }

    private suspend fun createSubtask(id: Long, parent: Long) {
        taskDao.createNew(newTask(with(ID, id), with(PARENT, parent)))
    }

    private suspend fun moveToCaldavList(calendar: String, vararg tasks: Long) {
        taskMover.move(
            tasks.toList(),
            CaldavFilter(
                CaldavCalendar(name = "", uuid = calendar),
                account = CaldavAccount(accountType = TYPE_CALDAV)
            )
        )
    }

    private suspend fun setAccountType(account: String, type: Int) {
        caldavDao.insert(
            CaldavAccount(
                uuid = account,
                accountType = type,
            )
        )
    }
}