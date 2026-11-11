package org.tasks.data

import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test
import org.tasks.filters.SubtaskFilter
import org.tasks.preferences.SubtaskQueryPreferences

class SubtaskQueryTest {
    @Test
    fun theTaskListsOwnQueryStillFoldsRowsAway() {
        val query = TaskListQuery.getQuery(SubtaskQueryPreferences(), SubtaskFilter(PARENT))

        assertTrue(query, query.contains(COLLAPSED))
    }

    @Test
    fun theEditorsQueryReturnsWhatIsFoldedAway() {
        val query = subtaskQuery(parentId = PARENT)

        assertFalse(query, query.contains(COLLAPSED))
        assertTrue(query, query.contains(PARENT.toString()))
    }

    companion object {
        private const val PARENT = 42L
        private const val COLLAPSED = "recursive_tasks.collapsed > 0"
    }
}
