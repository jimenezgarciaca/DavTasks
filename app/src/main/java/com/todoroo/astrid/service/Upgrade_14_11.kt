@file:Suppress("ClassName")

package com.todoroo.astrid.service

import org.tasks.data.entity.Alarm
import org.tasks.data.entity.Alarm.Companion.whenDue
import org.tasks.data.entity.Alarm.Companion.whenOverdue
import org.tasks.data.entity.Alarm.Companion.whenStarted
import org.tasks.data.entity.Task.Companion.NOTIFY_AFTER_DEADLINE
import org.tasks.data.entity.Task.Companion.NOTIFY_AT_DEADLINE
import org.tasks.data.entity.Task.Companion.NOTIFY_AT_START

class Upgrade_14_11 {
    companion object {
        const val VERSION = 141100

        fun fromLegacyFlags(flags: Int, task: Long = 0): List<Alarm> = buildList {
            if (flags and NOTIFY_AT_START != 0) add(whenStarted(task))
            if (flags and NOTIFY_AT_DEADLINE != 0) add(whenDue(task))
            if (flags and NOTIFY_AFTER_DEADLINE != 0) add(whenOverdue(task))
        }
    }
}
