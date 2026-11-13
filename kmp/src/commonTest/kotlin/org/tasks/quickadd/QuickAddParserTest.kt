package org.tasks.quickadd

import kotlinx.datetime.LocalDate
import kotlinx.datetime.LocalTime
import org.tasks.data.entity.Task
import org.tasks.repeats.ByDay
import org.tasks.repeats.Frequency
import org.tasks.repeats.Recur
import org.tasks.repeats.Weekday
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertNull
import kotlin.test.assertTrue

class QuickAddParserTest {
    // Viernes 13 de noviembre de 2026, 10:00
    private val today = LocalDate(2026, 11, 13)
    private val now = LocalTime(10, 0)

    private fun parse(text: String, syntax: QuickAddSyntax = QuickAddSyntax()) =
        parseQuickAdd(text, today, now, syntax)

    @Test
    fun plainTextIsOnlyATitle() {
        val result = parse("Comprar pan")

        assertEquals("Comprar pan", result.title)
        assertFalse(result.hasData)
    }

    @Test
    fun everyMarkerIsExtracted() {
        val result = parse("Llamar a Ana @mañana @17:30 #trabajo !! +Oficina *semanal")

        assertEquals("Llamar a Ana", result.title)
        assertEquals(listOf("trabajo"), result.tags)
        assertEquals("Oficina", result.list)
        assertEquals(LocalDate(2026, 11, 14), result.dueDate)
        assertEquals(LocalTime(17, 30), result.dueTime)
        assertEquals(Task.Priority.MEDIUM, result.priority)
        assertEquals(Recur(Frequency.WEEKLY), result.recurrence)
    }

    @Test
    fun markersCanAppearAnywhere() {
        val result = parse("#casa Pagar @hoy el alquiler")

        assertEquals("Pagar el alquiler", result.title)
        assertEquals(listOf("casa"), result.tags)
        assertEquals(today, result.dueDate)
    }

    @Test
    fun parenthesesAllowSpaces() {
        val result = parse("Revisar #(proyecto ENISA) +(Lista de la compra)")

        assertEquals("Revisar", result.title)
        assertEquals(listOf("proyecto ENISA"), result.tags)
        assertEquals("Lista de la compra", result.list)
    }

    @Test
    fun symbolsInsideWordsAreIgnored() {
        val result = parse("Escribir a ana@correo.es sobre C# y C++")

        assertEquals("Escribir a ana@correo.es sobre C# y C++", result.title)
        assertFalse(result.hasData)
    }

    @Test
    fun unknownValuesStayInTheTitle() {
        val result = parse("Hablar con @pepe *nunca")

        assertEquals("Hablar con @pepe *nunca", result.title)
        assertNull(result.dueDate)
        assertNull(result.recurrence)
    }

    @Test
    fun backslashKeepsTheSymbol() {
        assertEquals("Resolver #12", parse("Resolver \\#12").title)
    }

    @Test
    fun repeatedTagsAreKeptOnce() {
        assertEquals(listOf("a", "b"), parse("x #a #b #A").tags)
    }

    @Test
    fun relativeDates() {
        assertEquals(LocalDate(2026, 11, 15), parse("x @pasado").dueDate)
        assertEquals(LocalDate(2026, 11, 16), parse("x @3d").dueDate)
        assertEquals(LocalDate(2026, 11, 27), parse("x @2s").dueDate)
        assertEquals(LocalDate(2027, 1, 13), parse("x @2m").dueDate)
        assertEquals(LocalDate(2026, 11, 20), parse("x @semana").dueDate)
    }

    @Test
    fun weekdaysAreTheNextOneAfterToday() {
        assertEquals(LocalDate(2026, 11, 16), parse("x @lunes").dueDate)
        assertEquals(LocalDate(2026, 11, 20), parse("x @viernes").dueDate)
        assertEquals(LocalDate(2026, 11, 18), parse("x @miércoles").dueDate)
        assertEquals(LocalDate(2026, 11, 14), parse("x @finde").dueDate)
        assertEquals(LocalDate(2026, 11, 17), parse("x @tue").dueDate)
    }

    @Test
    fun numericDates() {
        assertEquals(LocalDate(2026, 12, 15), parse("x @15/12").dueDate)
        assertEquals(LocalDate(2027, 1, 5), parse("x @5/1").dueDate)
        assertEquals(LocalDate(2027, 3, 2), parse("x @2/3/27").dueDate)
        assertEquals(LocalDate(2026, 12, 1), parse("x @1.12").dueDate)
        assertEquals(LocalDate(2026, 12, 24), parse("x @2026-12-24").dueDate)
        assertEquals("x @31/2", parse("x @31/2").title)
    }

    @Test
    fun timeWithoutDateIsTodayOrTomorrow() {
        val later = parse("x @18:00")
        assertEquals(today, later.dueDate)
        assertEquals(LocalTime(18, 0), later.dueTime)

        assertEquals(LocalDate(2026, 11, 14), parse("x @9h").dueDate)
        assertEquals(LocalTime(21, 0), parse("x @9pm").dueTime)
        assertEquals(LocalTime(9, 15), parse("x @9h15").dueTime)
    }

    @Test
    fun priorities() {
        assertEquals(Task.Priority.LOW, parse("x !").priority)
        assertEquals(Task.Priority.MEDIUM, parse("x !!").priority)
        assertEquals(Task.Priority.HIGH, parse("x !!!").priority)
        assertEquals(Task.Priority.HIGH, parse("x !alta").priority)
        assertEquals(Task.Priority.LOW, parse("x !baja").priority)
        assertEquals("x !quizá", parse("x !quizá").title)
    }

    @Test
    fun recurrences() {
        assertEquals(Recur(Frequency.DAILY), parse("x *diaria").recurrence)
        assertEquals(Recur(Frequency.MONTHLY), parse("x *mensual").recurrence)
        assertEquals(Recur(Frequency.YEARLY), parse("x *anual").recurrence)
        assertEquals(Recur(Frequency.WEEKLY, interval = 2), parse("x *2s").recurrence)
        assertEquals(Recur(Frequency.DAILY, interval = 3), parse("x *cada-3d").recurrence)
        assertEquals(
            Recur(Frequency.WEEKLY, byDay = listOf(ByDay(Weekday.MO))),
            parse("x *lunes").recurrence,
        )
        assertEquals(
            Recur(Frequency.WEEKLY, byDay = listOf(ByDay(Weekday.MO), ByDay(Weekday.WE))),
            parse("x *miércoles,lunes").recurrence,
        )
        assertEquals("x *lunes,nunca", parse("x *lunes,nunca").title)
        assertEquals(
            listOf(Weekday.MO, Weekday.TU, Weekday.WE, Weekday.TH, Weekday.FR),
            parse("x *laborables").recurrence?.byDay?.map { it.day },
        )
    }

    @Test
    fun recurrenceWithoutDateStartsToday() {
        assertEquals(today, parse("Regar *semanal").dueDate)
    }

    @Test
    fun customSymbols() {
        val syntax = QuickAddSyntax(tag = '&', date = '=', list = '%', priority = '^', repeat = '~')
        val result = parse("Tarea &etiqueta =mañana %Casa ^^ ~diaria #no @no", syntax)

        assertEquals("Tarea #no @no", result.title)
        assertEquals(listOf("etiqueta"), result.tags)
        assertEquals("Casa", result.list)
        assertEquals(Task.Priority.MEDIUM, result.priority)
        assertEquals(Recur(Frequency.DAILY), result.recurrence)
    }

    @Test
    fun syntaxValidation() {
        assertTrue(QuickAddSyntax().isValid)
        assertFalse(QuickAddSyntax(tag = '@').isValid)
        assertFalse(QuickAddSyntax(tag = 'a').isValid)
        assertFalse(QuickAddSyntax(list = '(').isValid)
    }
}
