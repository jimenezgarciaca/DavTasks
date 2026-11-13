package org.tasks.quickadd

import kotlinx.datetime.DatePeriod
import kotlinx.datetime.DayOfWeek
import kotlinx.datetime.LocalDate
import kotlinx.datetime.LocalTime
import kotlinx.datetime.isoDayNumber
import kotlinx.datetime.plus
import org.tasks.data.entity.Task
import org.tasks.repeats.ByDay
import org.tasks.repeats.Frequency
import org.tasks.repeats.Recur
import org.tasks.repeats.Weekday
import org.tasks.repeats.toWeekday

/**
 * Símbolos que marcan cada dato en el texto de alta rápida. Solo se interpretan al principio de una
 * palabra, de modo que "correo@dominio.es" o "C#" no se tocan.
 */
data class QuickAddSyntax(
    val tag: Char = '#',
    val date: Char = '@',
    val list: Char = '+',
    val priority: Char = '!',
    val repeat: Char = '*',
) {
    val symbols: List<Char>
        get() = listOf(tag, date, list, priority, repeat)

    /** Los cinco símbolos son distintos y no son letras, dígitos, espacios ni paréntesis. */
    val isValid: Boolean
        get() = symbols.distinct().size == symbols.size &&
                symbols.none { it.isLetterOrDigit() || it.isWhitespace() || it in "()\\" }
}

data class QuickAddResult(
    val title: String,
    val tags: List<String> = emptyList(),
    val list: String? = null,
    val dueDate: LocalDate? = null,
    val dueTime: LocalTime? = null,
    /** Valores de [Task.Priority]. */
    val priority: Int? = null,
    val recurrence: Recur? = null,
) {
    val hasData: Boolean
        get() = tags.isNotEmpty() || list != null || dueDate != null || priority != null ||
                recurrence != null
}

/**
 * Interpreta el texto de alta rápida. Ejemplos con la sintaxis por defecto:
 *
 * - `Llamar a Ana @mañana @17:30 #trabajo !!` → vence mañana a las 17:30, etiqueta "trabajo",
 *   prioridad media.
 * - `Pagar alquiler @1/12 *mensual +Casa` → vence el 1 de diciembre, se repite cada mes, lista "Casa".
 * - `#(dos palabras)` y `+(Lista larga)` admiten espacios entre paréntesis.
 * - Una palabra que empieza por `\` se deja tal cual, sin la barra: `\#1` queda como "#1".
 *
 * Un marcador cuyo valor no se entiende (por ejemplo `@pepe`) se deja en el título.
 */
fun parseQuickAdd(
    text: String,
    today: LocalDate,
    now: LocalTime,
    syntax: QuickAddSyntax = QuickAddSyntax(),
): QuickAddResult {
    val title = StringBuilder()
    val tags = mutableListOf<String>()
    var list: String? = null
    var date: LocalDate? = null
    var time: LocalTime? = null
    var priority: Int? = null
    var recurrence: Recur? = null

    for (token in tokenize(text)) {
        val prefix = token.firstOrNull()
        val raw = token.drop(1)
        val value = raw.removeSurrounding("(", ")").trim()
        val consumed = when {
            prefix == '\\' && raw.firstOrNull() in syntax.symbols -> {
                title.appendWord(raw)
                true
            }
            prefix == syntax.tag && value.isNotEmpty() -> {
                if (tags.none { it.equals(value, ignoreCase = true) }) tags.add(value)
                true
            }
            prefix == syntax.list && value.isNotEmpty() -> {
                list = value
                true
            }
            prefix == syntax.priority ->
                parsePriority(raw, syntax.priority)?.let { priority = it } != null
            prefix == syntax.date -> {
                val parsedTime = parseTime(value)
                if (parsedTime != null) {
                    time = parsedTime
                    true
                } else {
                    parseDate(value, today)?.let { date = it } != null
                }
            }
            prefix == syntax.repeat -> parseRecurrence(value)?.let { recurrence = it } != null
            else -> false
        }
        if (!consumed) title.appendWord(token)
    }

    if (time != null && date == null) {
        date = if (time!! > now) today else today.plus(DatePeriod(days = 1))
    }
    if (recurrence != null && date == null) {
        date = today
    }
    return QuickAddResult(
        title = title.toString(),
        tags = tags,
        list = list,
        dueDate = date,
        dueTime = time,
        priority = priority,
        recurrence = recurrence,
    )
}

private fun StringBuilder.appendWord(word: String) {
    if (isNotEmpty()) append(' ')
    append(word)
}

/** Palabras separadas por espacios; un paréntesis abierto justo tras un símbolo agrupa hasta el cierre. */
private fun tokenize(text: String): List<String> {
    val tokens = mutableListOf<String>()
    var i = 0
    while (i < text.length) {
        while (i < text.length && text[i].isWhitespace()) i++
        if (i >= text.length) break
        val start = i
        if (i + 1 < text.length && text[i + 1] == '(' && !text[i].isLetterOrDigit()) {
            val close = text.indexOf(')', i + 2)
            if (close != -1) {
                tokens.add(text.substring(start, close + 1))
                i = close + 1
                continue
            }
        }
        while (i < text.length && !text[i].isWhitespace()) i++
        tokens.add(text.substring(start, i))
    }
    return tokens
}

private fun String.normalized(): String = lowercase()
    .replace('á', 'a').replace('é', 'e').replace('í', 'i')
    .replace('ó', 'o').replace('ú', 'u').replace('ü', 'u').replace('ñ', 'n')

private fun parsePriority(raw: String, symbol: Char): Int? {
    val value = raw.removeSurrounding("(", ")").normalized()
    if (value.all { it == symbol }) {
        return when (value.length) {
            0 -> Task.Priority.LOW
            1 -> Task.Priority.MEDIUM
            else -> Task.Priority.HIGH
        }
    }
    return when (value) {
        "alta", "a", "high", "h" -> Task.Priority.HIGH
        "media", "m", "medium" -> Task.Priority.MEDIUM
        "baja", "b", "low", "l" -> Task.Priority.LOW
        "ninguna", "no", "none" -> Task.Priority.NONE
        else -> null
    }
}

private val WEEKDAYS: Map<String, DayOfWeek> = buildMap {
    fun add(day: DayOfWeek, vararg names: String) = names.forEach { put(it, day) }
    add(DayOfWeek.MONDAY, "lunes", "lun", "lu", "monday", "mon")
    add(DayOfWeek.TUESDAY, "martes", "mar", "ma", "tuesday", "tue")
    add(DayOfWeek.WEDNESDAY, "miercoles", "mie", "mi", "wednesday", "wed")
    add(DayOfWeek.THURSDAY, "jueves", "jue", "ju", "thursday", "thu")
    add(DayOfWeek.FRIDAY, "viernes", "vie", "vi", "friday", "fri")
    add(DayOfWeek.SATURDAY, "sabado", "sab", "sa", "saturday", "sat")
    add(DayOfWeek.SUNDAY, "domingo", "dom", "do", "sunday", "sun")
}

private val RELATIVE = Regex("""^(\d{1,3})\s*(d|dias?|days?|s|w|semanas?|weeks?|m|meses|mes|months?|a|y|anos?|years?)$""")
private val DAY_MONTH = Regex("""^(\d{1,2})[/.\-](\d{1,2})(?:[/.\-](\d{2}|\d{4}))?$""")
private val ISO = Regex("""^(\d{4})-(\d{1,2})-(\d{1,2})$""")

internal fun parseDate(value: String, today: LocalDate): LocalDate? {
    val v = value.normalized()
    when (v) {
        "hoy", "today" -> return today
        "manana", "tomorrow" -> return today.plus(DatePeriod(days = 1))
        "pasado", "pasadomanana", "pasado-manana" -> return today.plus(DatePeriod(days = 2))
        "semana", "proxima-semana", "nextweek", "next-week" -> return today.plus(DatePeriod(days = 7))
        "finde", "weekend" -> return next(today, DayOfWeek.SATURDAY)
    }
    WEEKDAYS[v]?.let { return next(today, it) }
    RELATIVE.find(v)?.let { m ->
        val n = m.groupValues[1].toInt()
        val period = when (m.groupValues[2].first()) {
            'd' -> DatePeriod(days = n)
            's', 'w' -> DatePeriod(days = 7 * n)
            'm' -> DatePeriod(months = n)
            else -> DatePeriod(years = n)
        }
        return today.plus(period)
    }
    ISO.find(v)?.let { m ->
        return dateOrNull(m.groupValues[1].toInt(), m.groupValues[2].toInt(), m.groupValues[3].toInt())
    }
    DAY_MONTH.find(v)?.let { m ->
        val day = m.groupValues[1].toInt()
        val month = m.groupValues[2].toInt()
        val year = m.groupValues[3].takeIf { it.isNotEmpty() }?.toInt()?.let { if (it < 100) 2000 + it else it }
        if (year != null) return dateOrNull(year, month, day)
        val thisYear = dateOrNull(today.year, month, day) ?: return null
        return if (thisYear < today) dateOrNull(today.year + 1, month, day) else thisYear
    }
    return null
}

/** Próximo [day] estrictamente posterior a [today]; para hoy está `@hoy`. */
private fun next(today: LocalDate, day: DayOfWeek): LocalDate {
    val diff = (day.isoDayNumber - today.dayOfWeek.isoDayNumber + 7) % 7
    return today.plus(DatePeriod(days = if (diff == 0) 7 else diff))
}

private fun dateOrNull(year: Int, month: Int, day: Int): LocalDate? =
    try {
        LocalDate(year, month, day)
    } catch (e: IllegalArgumentException) {
        null
    }

private val TIME_COLON = Regex("""^(\d{1,2})[:h](\d{2})$""")
private val TIME_HOUR = Regex("""^(\d{1,2})\s*(h|am|pm)$""")
private val TIME_AMPM = Regex("""^(\d{1,2}):(\d{2})\s*(am|pm)$""")

internal fun parseTime(value: String): LocalTime? {
    val v = value.normalized()
    TIME_AMPM.find(v)?.let { m ->
        return time(to24(m.groupValues[1].toInt(), m.groupValues[3]), m.groupValues[2].toInt())
    }
    TIME_COLON.find(v)?.let { m -> return time(m.groupValues[1].toInt(), m.groupValues[2].toInt()) }
    TIME_HOUR.find(v)?.let { m ->
        val hour = m.groupValues[1].toInt()
        return time(if (m.groupValues[2] == "h") hour else to24(hour, m.groupValues[2]), 0)
    }
    return null
}

private fun to24(hour: Int, suffix: String): Int = when {
    hour == 12 -> if (suffix == "am") 0 else 12
    suffix == "pm" -> hour + 12
    else -> hour
}

private fun time(hour: Int, minute: Int): LocalTime? =
    if (hour in 0..23 && minute in 0..59) LocalTime(hour, minute) else null

private val REPEAT_INTERVAL = Regex("""^(?:cada-?)?(\d{1,3})\s*(d|dias?|days?|s|w|semanas?|weeks?|m|meses|mes|months?|a|y|anos?|years?)$""")

internal fun parseRecurrence(value: String): Recur? {
    val v = value.normalized()
    when (v) {
        "diaria", "diario", "dia", "daily", "day" -> return Recur(Frequency.DAILY)
        "semanal", "semana", "weekly", "week" -> return Recur(Frequency.WEEKLY)
        "mensual", "mes", "monthly", "month" -> return Recur(Frequency.MONTHLY)
        "anual", "ano", "yearly", "year" -> return Recur(Frequency.YEARLY)
        "laborables", "laborable", "weekdays" -> return Recur(
            Frequency.WEEKLY,
            byDay = listOf(Weekday.MO, Weekday.TU, Weekday.WE, Weekday.TH, Weekday.FR).map { ByDay(it) },
        )
    }
    WEEKDAYS[v]?.let { return Recur(Frequency.WEEKLY, byDay = listOf(ByDay(it.toWeekday()))) }
    REPEAT_INTERVAL.find(v)?.let { m ->
        val n = m.groupValues[1].toInt().takeIf { it > 0 } ?: return null
        val frequency = when (m.groupValues[2].first()) {
            'd' -> Frequency.DAILY
            's', 'w' -> Frequency.WEEKLY
            'm' -> Frequency.MONTHLY
            else -> Frequency.YEARLY
        }
        return Recur(frequency, interval = n.takeIf { it > 1 })
    }
    return null
}

/** Texto de ayuda con la sintaxis en uso. */
fun QuickAddSyntax.helpLines(spanish: Boolean): List<String> = if (spanish) listOf(
    "${tag}etiqueta  ·  ${tag}(dos palabras)",
    "${date}hoy  ${date}mañana  ${date}pasado  ${date}lunes  ${date}finde  ${date}3d  ${date}2s  ${date}15/12  ${date}17:30",
    "${list}Lista  ·  ${list}(Lista con espacios)",
    "${priority}  ${priority}${priority}  ${priority}${priority}${priority}  (baja, media, alta)  ·  ${priority}alta",
    "${repeat}diaria  ${repeat}semanal  ${repeat}mensual  ${repeat}anual  ${repeat}laborables  ${repeat}lunes  ${repeat}2s",
    "\\${tag}texto deja el símbolo en el título",
) else listOf(
    "${tag}tag  ·  ${tag}(two words)",
    "${date}today  ${date}tomorrow  ${date}monday  ${date}weekend  ${date}3d  ${date}2w  ${date}15/12  ${date}17:30",
    "${list}List  ·  ${list}(List with spaces)",
    "${priority}  ${priority}${priority}  ${priority}${priority}${priority}  (low, medium, high)  ·  ${priority}high",
    "${repeat}daily  ${repeat}weekly  ${repeat}monthly  ${repeat}yearly  ${repeat}weekdays  ${repeat}monday  ${repeat}2w",
    "\\${tag}text keeps the symbol in the title",
)
