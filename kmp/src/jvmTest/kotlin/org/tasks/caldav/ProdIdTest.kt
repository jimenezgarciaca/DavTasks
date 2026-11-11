package org.tasks.caldav

import org.tasks.caldav.iCalendar.Companion.supportsReminders
import org.tasks.kmp.PROD_ID
import kotlin.test.Test
import kotlin.test.assertTrue

class ProdIdTest {
    @Test
    fun ownProdIdSupportsReminders() {
        assertTrue(PROD_ID.supportsReminders())
    }

    @Test
    fun androidProdIdSupportsReminders() {
        assertTrue("+//IDN davtasks//android-1//EN".supportsReminders())
    }

    @Test
    fun tasksOrgProdIdSupportsReminders() {
        assertTrue("+//IDN tasks.org//android-110500//EN".supportsReminders())
    }
}
