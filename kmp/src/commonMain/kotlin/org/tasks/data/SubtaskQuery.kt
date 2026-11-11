package org.tasks.data

import org.tasks.filters.SubtaskFilter
import org.tasks.preferences.SubtaskQueryPreferences

fun subtaskQuery(parentId: Long): String =
    TaskListQuery.getQuery(
        preferences = SubtaskQueryPreferences(),
        filter = SubtaskFilter(parentId),
        showCollapsed = true,
    )
