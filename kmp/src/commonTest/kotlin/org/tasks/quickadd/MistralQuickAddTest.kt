package org.tasks.quickadd

import kotlinx.datetime.LocalDate
import kotlinx.datetime.LocalTime
import kotlinx.serialization.builtins.serializer
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.jsonArray
import kotlinx.serialization.json.jsonObject
import kotlinx.serialization.json.jsonPrimitive
import org.tasks.data.entity.Task
import org.tasks.repeats.ByDay
import org.tasks.repeats.Frequency
import org.tasks.repeats.Recur
import org.tasks.repeats.Weekday
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNull
import kotlin.test.assertTrue

class MistralQuickAddTest {
    private val today = LocalDate(2026, 11, 13)
    private val now = LocalTime(10, 0)
    private val syntax = QuickAddSyntax()

    private fun roundTrip(task: AiTask) =
        parseQuickAdd(task.toQuickAddText(syntax, spanish = true), today, now, syntax)

    private fun response(content: String) = """
        {"id":"x","choices":[{"index":0,"message":{"role":"assistant","content":${Json.encodeToString(String.serializer(), content)}}}]}
    """.trimIndent()

    @Test
    fun requestCarriesContext() {
        val body = Json.parseToJsonElement(
            MistralQuickAdd.request("llamar a Ana mañana", today, now, "Europe/Madrid", listOf("Casa", "Oficina"))
        ).jsonObject
        assertEquals(MistralQuickAdd.DEFAULT_MODEL, body["model"]!!.jsonPrimitive.content)
        assertEquals("json_object", body["response_format"]!!.jsonObject["type"]!!.jsonPrimitive.content)
        val user = body["messages"]!!.jsonArray[1].jsonObject["content"]!!.jsonPrimitive.content
        assertTrue("2026-11-13 (friday)" in user)
        assertTrue("Europe/Madrid" in user)
        assertTrue("Casa | Oficina" in user)
        assertTrue(user.endsWith("llamar a Ana mañana"))
    }

    @Test
    fun responseIsParsed() {
        val task = MistralQuickAdd.parseResponse(
            response("""{"title":"Llamar a Ana","tags":[],"list":null,"date":"2026-11-14","time":"17:30","priority":"high","repeat":null}""")
        )
        assertEquals(AiTask(title = "Llamar a Ana", date = "2026-11-14", time = "17:30", priority = "high"), task)
    }

    @Test
    fun codeFenceIsTolerated() {
        val task = MistralQuickAdd.parseResponse(response("```json\n{\"title\":\"x\"}\n```"))
        assertEquals("x", task?.title)
    }

    @Test
    fun malformedResponseIsNull() {
        assertNull(MistralQuickAdd.parseResponse("""{"choices":[]}"""))
        assertNull(MistralQuickAdd.parseResponse(response("no json")))
    }

    @Test
    fun errorMessageIsRead() {
        assertEquals("Unauthorized", MistralQuickAdd.parseError("""{"message":"Unauthorized","request_id":"1"}"""))
    }

    @Test
    fun everyFieldSurvivesTheLocalParser() {
        val result = roundTrip(
            AiTask(
                title = "Revisar contrato",
                tags = listOf("trabajo", "dos palabras"),
                list = "Lista de la compra",
                date = "2026-12-01",
                time = "9:05",
                priority = "medium",
                repeat = AiRepeat("monthly"),
            )
        )
        assertEquals("Revisar contrato", result.title)
        assertEquals(listOf("trabajo", "dos palabras"), result.tags)
        assertEquals("Lista de la compra", result.list)
        assertEquals(LocalDate(2026, 12, 1), result.dueDate)
        assertEquals(LocalTime(9, 5), result.dueTime)
        assertEquals(Task.Priority.MEDIUM, result.priority)
        assertEquals(Recur(Frequency.MONTHLY), result.recurrence)
    }

    @Test
    fun titleSymbolsAreEscaped() {
        val result = roundTrip(AiTask(title = "Comprar #3 y @Ana"))
        assertEquals("Comprar #3 y @Ana", result.title)
        assertTrue(!result.hasData)
    }

    @Test
    fun invalidValuesAreDropped() {
        val text = AiTask(title = "x", date = "mañana", time = "25:00", priority = "urgent", repeat = AiRepeat("hourly"))
            .toQuickAddText(syntax, spanish = true)
        assertEquals("x", text)
    }

    @Test
    fun repeats() {
        assertEquals(Recur(Frequency.DAILY, interval = 3), roundTrip(AiTask("x", repeat = AiRepeat("daily", 3))).recurrence)
        assertEquals(Recur(Frequency.WEEKLY, interval = 2), roundTrip(AiTask("x", repeat = AiRepeat("weekly", 2))).recurrence)
        assertEquals(Recur(Frequency.YEARLY), roundTrip(AiTask("x", repeat = AiRepeat("yearly"))).recurrence)
        assertEquals(
            Recur(Frequency.WEEKLY, byDay = listOf(ByDay(Weekday.MO), ByDay(Weekday.TH))),
            roundTrip(AiTask("x", repeat = AiRepeat("weekly", days = listOf("TH", "MO")))).recurrence,
        )
        assertEquals(
            listOf(Weekday.MO, Weekday.TU, Weekday.WE, Weekday.TH, Weekday.FR),
            roundTrip(AiTask("x", repeat = AiRepeat("weekly", days = listOf("MO", "TU", "WE", "TH", "FR"))))
                .recurrence?.byDay?.map { it.day },
        )
    }

    @Test
    fun customSymbolsAreUsed() {
        val custom = QuickAddSyntax(tag = '&', date = '=', list = '%', priority = '^', repeat = '~')
        val text = AiTask(title = "x", tags = listOf("t"), date = "2026-11-20", priority = "low", repeat = AiRepeat("daily"))
            .toQuickAddText(custom, spanish = true)
        assertEquals("x &t =2026-11-20 ^ ~diaria", text)
    }
}
