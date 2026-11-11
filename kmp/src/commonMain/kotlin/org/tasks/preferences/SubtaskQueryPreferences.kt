package org.tasks.preferences

import com.todoroo.astrid.core.SortHelper

class SubtaskQueryPreferences : QueryPreferences by DefaultQueryPreferences() {
    override var sortMode = SortHelper.SORT_CALDAV
    override var subtaskMode = SortHelper.SORT_CALDAV
    override var groupMode = SortHelper.GROUP_NONE
    override var completedMode = SortHelper.SORT_COMPLETED
    override var subtaskAscending = true
    override var showHidden = true

    override var completedTasksAtBottom = false
}
