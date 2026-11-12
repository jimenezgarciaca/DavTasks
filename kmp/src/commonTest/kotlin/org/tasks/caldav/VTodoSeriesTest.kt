package org.tasks.caldav

import kotlinx.datetime.TimeZone
import org.tasks.icalendar.ICalDate
import org.tasks.icalendar.TodoStatus
import org.tasks.icalendar.parseVTodos
import org.tasks.icalendar.serialize
import org.tasks.time.DateTime
import kotlin.test.Test
import kotlin.test.assertContains
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertNotNull
import kotlin.test.assertNull
import kotlin.test.assertTrue

class VTodoSeriesTest {
    // Lectura: qué VTODO representa la tarea

    @Test
    fun seriesIsTheTodoWithoutRecurrenceId() {
        val todos = parseVTodos(SERIES_WITH_OVERRIDE)

        assertEquals(2, todos.size)
        val master = todos.seriesMaster()
        assertNotNull(master)
        assertEquals("Enviar acta", master.summary)
        assertNotNull(master.rRule)
        assertFalse(master.isRecurrenceOverride)
        assertEquals(1, todos.count { it.isRecurrenceOverride })
    }

    @Test
    fun singleTodoIsItsOwnSeries() {
        val todos = parseVTodos(vcalendar(todo("a", "A")))

        assertEquals("A", todos.seriesMaster()?.summary)
    }

    @Test
    fun unrelatedTodosHaveNoSeries() {
        val todos = parseVTodos(vcalendar(todo("a", "A"), todo("b", "B")))

        assertEquals(2, todos.size)
        assertNull(todos.seriesMaster())
    }

    @Test
    fun overridesWithoutSeriesHaveNoSeries() {
        val todos = parseVTodos(
            vcalendar(
                todo("a", "A1", "RECURRENCE-ID;VALUE=DATE:20261007"),
                todo("a", "A2", "RECURRENCE-ID;VALUE=DATE:20261014"),
            )
        )

        assertEquals(2, todos.size)
        assertNull(todos.seriesMaster())
    }

    @Test
    fun aLoneOverrideHasNoSeries() {
        val todos = parseVTodos(vcalendar(todo("a", "A1", "RECURRENCE-ID;VALUE=DATE:20261007")))

        assertNull(todos.seriesMaster())
    }

    // Lectura: ocurrencias completadas en otro cliente

    @Test
    fun completedOccurrencesAtTheStartAreSkipped() {
        val master = parseVTodos(
            weekly(
                "20261007",
                override("20261007", TodoStatus.COMPLETED),
                override("20261014", TodoStatus.CANCELLED),
            )
        ).seriesMaster()!!

        assertEquals(ICalDate.Date(2026, 10, 21), master.dtStart)
        assertEquals(ICalDate.Date(2026, 10, 21), master.due)
        assertEquals(TodoStatus.NEEDS_ACTION, master.status)
    }

    @Test
    fun dueKeepsItsDistanceFromStart() {
        val master = parseVTodos(
            weekly(
                "20261007",
                due = "20261009",
                override("20261007", TodoStatus.COMPLETED),
            )
        ).seriesMaster()!!

        assertEquals(ICalDate.Date(2026, 10, 14), master.dtStart)
        assertEquals(ICalDate.Date(2026, 10, 16), master.due)
    }

    @Test
    fun pendingOverrideDoesNotMoveTheSeries() {
        val master = parseVTodos(
            weekly("20261007", override("20261007", TodoStatus.NEEDS_ACTION))
        ).seriesMaster()!!

        assertEquals(ICalDate.Date(2026, 10, 7), master.dtStart)
    }

    @Test
    fun completedLaterOccurrenceDoesNotMoveTheSeries() {
        val master = parseVTodos(
            weekly("20261007", override("20261014", TodoStatus.COMPLETED))
        ).seriesMaster()!!

        assertEquals(ICalDate.Date(2026, 10, 7), master.dtStart)
    }

    @Test
    fun excludedOccurrencesAreSkippedToo() {
        val master = parseVTodos(
            weekly(
                "20261007",
                override("20261007", TodoStatus.COMPLETED),
                extra = listOf("EXDATE;VALUE=DATE:20261014"),
            )
        ).seriesMaster()!!

        assertEquals(ICalDate.Date(2026, 10, 21), master.dtStart)
    }

    @Test
    fun seriesWithEveryOccurrenceFinishedIsCompleted() {
        val master = parseVTodos(
            weekly(
                "20261007",
                override("20261007", TodoStatus.COMPLETED, completed = "20261007T100000Z"),
                override("20261014", TodoStatus.COMPLETED, completed = "20261014T100000Z"),
                rrule = "RRULE:FREQ=WEEKLY;COUNT=2",
            )
        ).seriesMaster()!!

        assertEquals(TodoStatus.COMPLETED, master.status)
        assertEquals(DateTime(2026, 10, 14, 10, timeZone = TimeZone.UTC).millis, master.completedAt)
    }

    @Test
    fun utcOccurrencesMatchByInstant() {
        val master = parseVTodos(
            vcalendar(
                todo(UID, "Daily", "RRULE:FREQ=DAILY", "DTSTART:20261007T090000Z"),
                todo(
                    UID, "Daily", "RECURRENCE-ID:20261007T090000Z",
                    "STATUS:COMPLETED", "DTSTART:20261007T090000Z",
                ),
            )
        ).seriesMaster()!!

        val start = master.dtStart as ICalDate.DateTime
        assertEquals(DateTime(2026, 10, 8, 9, timeZone = TimeZone.UTC).millis, start.millis)
    }

    @Test
    fun localTimeIsKeptAcrossDaylightSavingChange() {
        val madrid = TimeZone.of("Europe/Madrid")
        val master = parseVTodos(
            vcalendar(
                todo(UID, "Weekly", "RRULE:FREQ=WEEKLY", "DTSTART;TZID=Europe/Madrid:20261021T090000"),
                todo(
                    UID, "Weekly", "RECURRENCE-ID;TZID=Europe/Madrid:20261021T090000",
                    "STATUS:COMPLETED", "DTSTART;TZID=Europe/Madrid:20261021T090000",
                ),
            )
        ).seriesMaster()!!

        val start = master.dtStart as ICalDate.DateTime
        assertEquals("Europe/Madrid", start.tzId)
        assertEquals(DateTime(2026, 10, 28, 9, timeZone = madrid).millis, start.millis)
    }

    // Escritura: conservación de las ocurrencias

    @Test
    fun overridesAreAppendedWhenWritingTheSeries() {
        val master = parseVTodos(SERIES_WITH_OVERRIDE).seriesMaster()!!

        val written = preserveRecurrenceOverrides(master.serialize(), SERIES_WITH_OVERRIDE)

        val parsed = parseVTodos(written)
        assertEquals(2, parsed.size)
        assertEquals("Enviar acta", parsed.seriesMaster()?.summary)
        val override = parsed.single { it.isRecurrenceOverride }
        assertEquals("Enviar acta (7 oct)", override.summary)
        assertEquals("COMPLETED", override.status)
        assertEquals(1, written.lines().count { it.trim() == "END:VCALENDAR" })
    }

    @Test
    fun overridesAreNotDuplicatedOnSuccessiveWrites() {
        val master = parseVTodos(SERIES_WITH_OVERRIDE).seriesMaster()!!

        val first = preserveRecurrenceOverrides(master.serialize(), SERIES_WITH_OVERRIDE)
        val second = preserveRecurrenceOverrides(
            parseVTodos(first).seriesMaster()!!.serialize(),
            first,
        )

        assertEquals(2, parseVTodos(second).size)
    }

    @Test
    fun overridesAreKeptByteForByte() {
        val master = parseVTodos(SERIES_WITH_OVERRIDE).seriesMaster()!!

        val written = preserveRecurrenceOverrides(master.serialize(), SERIES_WITH_OVERRIDE)

        assertContains(written, "RECURRENCE-ID;VALUE=DATE:20261007\r\n")
        assertContains(written, "X-MOZ-GENERATION:1\r\n")
    }

    @Test
    fun nothingIsAddedWithoutOverrides() {
        val original = vcalendar(todo("a", "A"))
        val serialized = parseVTodos(original).single().serialize()

        assertEquals(serialized, preserveRecurrenceOverrides(serialized, original))
        assertEquals(serialized, preserveRecurrenceOverrides(serialized, null))
        assertEquals(serialized, preserveRecurrenceOverrides(serialized, ""))
    }

    @Test
    fun timeZoneUsedByAnOverrideIsCopied() {
        val master = parseVTodos(SERIES_WITH_OVERRIDE).seriesMaster()!!
        val original = SERIES_WITH_OVERRIDE
            .replace("RECURRENCE-ID;VALUE=DATE:20261007", "RECURRENCE-ID;TZID=Europe/Madrid:20261007T090000")
            .replace("END:VCALENDAR", MADRID + "END:VCALENDAR")

        val written = preserveRecurrenceOverrides(master.serialize(), original)

        assertContains(written, "BEGIN:VTIMEZONE\r\nTZID:Europe/Madrid\r\n")
        assertEquals(1, written.lines().count { it.trim() == "TZID:Europe/Madrid" })
    }

    @Test
    fun timeZoneIsFoundInAFoldedLine() {
        val master = parseVTodos(SERIES_WITH_OVERRIDE).seriesMaster()!!
        val original = SERIES_WITH_OVERRIDE
            .replace("RECURRENCE-ID;VALUE=DATE:20261007", "RECURRENCE-ID;TZID=Europe/Ma\n drid:20261007T090000")
            .replace("END:VCALENDAR", MADRID + "END:VCALENDAR")

        val written = preserveRecurrenceOverrides(master.serialize(), original)

        assertContains(written, "BEGIN:VTIMEZONE\r\nTZID:Europe/Madrid\r\n")
    }

    @Test
    fun timeZoneWithSimilarNameIsNotCopied() {
        val master = parseVTodos(SERIES_WITH_OVERRIDE).seriesMaster()!!
        val original = SERIES_WITH_OVERRIDE
            .replace("RECURRENCE-ID;VALUE=DATE:20261007", "RECURRENCE-ID;TZID=Europe/Madrid2:20261007T090000")
            .replace("END:VCALENDAR", MADRID + "END:VCALENDAR")

        val written = preserveRecurrenceOverrides(master.serialize(), original)

        assertFalse(written.contains("VTIMEZONE"))
    }

    @Test
    fun unusedTimeZonesAreNotCopied() {
        val master = parseVTodos(SERIES_WITH_OVERRIDE).seriesMaster()!!
        val original = SERIES_WITH_OVERRIDE.replace("END:VCALENDAR", MADRID + "END:VCALENDAR")

        val written = preserveRecurrenceOverrides(master.serialize(), original)

        assertFalse(written.contains("VTIMEZONE"))
    }

    @Test
    fun crlfInTheOriginalIsHandled() {
        val master = parseVTodos(SERIES_WITH_OVERRIDE).seriesMaster()!!

        val written = preserveRecurrenceOverrides(
            master.serialize(),
            SERIES_WITH_OVERRIDE.replace("\n", "\r\n"),
        )

        assertEquals(2, parseVTodos(written).size)
        assertTrue(written.contains("RECURRENCE-ID"))
    }

    private companion object {
        const val UID = "39987091-26f4-474e-86e0-78baf7f5234c"

        fun todo(uid: String, summary: String, vararg extra: String) =
            (listOf("BEGIN:VTODO", "UID:$uid", "SUMMARY:$summary") + extra + "END:VTODO")
                .joinToString("\n")

        fun vcalendar(vararg todos: String) =
            "BEGIN:VCALENDAR\nVERSION:2.0\nPRODID:-//E//EN\n" + todos.joinToString("\n") + "\nEND:VCALENDAR\n"

        fun override(date: String, status: String, completed: String? = null) = todo(
            UID,
            "Ocurrencia $date",
            *listOfNotNull(
                "RECURRENCE-ID;VALUE=DATE:$date",
                "STATUS:$status",
                "DTSTART;VALUE=DATE:$date",
                completed?.let { "COMPLETED:$it" },
            ).toTypedArray(),
        )

        fun weekly(
            start: String,
            vararg overrides: String,
            due: String = start,
            rrule: String = "RRULE:FREQ=WEEKLY",
            extra: List<String> = emptyList(),
        ) = vcalendar(
            todo(
                UID,
                "Serie",
                *(listOf(
                    "STATUS:NEEDS-ACTION",
                    rrule,
                    "DTSTART;VALUE=DATE:$start",
                    "DUE;VALUE=DATE:$due",
                ) + extra).toTypedArray(),
            ),
            *overrides,
        )

        val SERIES_WITH_OVERRIDE = vcalendar(
            todo(
                UID,
                "Enviar acta",
                "PRIORITY:5",
                "STATUS:NEEDS-ACTION",
                "RRULE:FREQ=WEEKLY",
                "DTSTART;VALUE=DATE:20261021",
                "DUE;VALUE=DATE:20261104",
            ),
            todo(
                UID,
                "Enviar acta (7 oct)",
                "STATUS:COMPLETED",
                "RECURRENCE-ID;VALUE=DATE:20261007",
                "DTSTART;VALUE=DATE:20261007",
                "DUE;VALUE=DATE:20261007",
                "COMPLETED:20261008T124607Z",
                "PERCENT-COMPLETE:100",
                "X-MOZ-GENERATION:1",
            ),
        )

        const val MADRID =
            "BEGIN:VTIMEZONE\nTZID:Europe/Madrid\nBEGIN:STANDARD\nDTSTART:19701025T030000\n" +
                    "TZOFFSETFROM:+0200\nTZOFFSETTO:+0100\nEND:STANDARD\nEND:VTIMEZONE\n"
    }
}
