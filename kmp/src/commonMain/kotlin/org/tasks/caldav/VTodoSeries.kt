package org.tasks.caldav

import kotlinx.datetime.TimeZone
import org.tasks.icalendar.ICalDate
import org.tasks.icalendar.ICalProperty
import org.tasks.icalendar.TodoStatus
import org.tasks.icalendar.VTodo
import org.tasks.repeats.Frequency
import org.tasks.repeats.nextOccurrence
import org.tasks.time.DateTime

/*
 * Un recurso CalDAV de una tarea recurrente puede contener varios VTODO con el mismo UID (RFC 4791,
 * sección 4.1): la serie, que lleva la RRULE, y una ocurrencia modificada por cada RECURRENCE-ID.
 * Thunderbird completa así las ocurrencias: añade un VTODO con RECURRENCE-ID y STATUS:COMPLETED y
 * deja la serie sin cambios.
 *
 * La app trabaja con un único VTODO por tarea (la serie), como Cfait:
 * - Al leer, la tarea es el VTODO sin RECURRENCE-ID. Si las primeras ocurrencias de la serie están
 *   completadas o canceladas en ocurrencias modificadas (o excluidas con EXDATE), la tarea se muestra
 *   en la primera ocurrencia pendiente. Si no queda ninguna, la tarea se muestra completada.
 * - Al escribir, las ocurrencias modificadas se conservan tal cual, con sus VTIMEZONE, también las
 *   de fechas anteriores al inicio de la serie, que otros clientes usan como historial.
 * - Las ocurrencias modificadas pendientes (por ejemplo, con otro título) no cambian la tarea: la app
 *   muestra los datos de la serie.
 */

private const val RECURRENCE_ID = "RECURRENCE-ID"

/** Límite de ocurrencias que se saltan al leer una serie, para no iterar sin fin. */
private const val MAX_SKIPPED_OCCURRENCES = 1000

private val FINISHED = setOf(TodoStatus.COMPLETED, TodoStatus.CANCELLED)

private val SUB_DAILY = setOf(Frequency.HOURLY, Frequency.MINUTELY, Frequency.SECONDLY)

private const val ONE_DAY_MILLIS = 86_400_000L

private val VTodo.recurrenceId: ICalProperty?
    get() = unknownProperties.firstOrNull { it.name.equals(RECURRENCE_ID, ignoreCase = true) }

internal val VTodo.isRecurrenceOverride: Boolean
    get() = recurrenceId != null

/**
 * Devuelve el VTODO que representa la tarea: el único que no es una ocurrencia modificada,
 * situado en su primera ocurrencia pendiente. Si no hay un candidato único (recurso vacío, solo
 * ocurrencias modificadas, o varios VTODO independientes) devuelve null.
 */
internal fun List<VTodo>.seriesMaster(): VTodo? {
    val master = filterNot { it.isRecurrenceOverride }.singleOrNull() ?: return null
    val overrides = filter { it.isRecurrenceOverride && (it.uid == null || it.uid == master.uid) }
    return if (overrides.isEmpty() && master.exDates.isEmpty()) {
        master
    } else {
        master.skipFinishedOccurrences(overrides)
    }
}

/**
 * Si la ocurrencia en la que está la serie (DTSTART, o DUE si no hay DTSTART) está completada o
 * cancelada en una ocurrencia modificada, o excluida con EXDATE, avanza DTSTART y DUE a la primera
 * ocurrencia que no lo esté. El cálculo no depende de la fecha actual, de modo que el mismo recurso
 * da siempre el mismo resultado y la fusión a tres bandas de la sincronización no se altera.
 */
internal fun VTodo.skipFinishedOccurrences(overrides: List<VTodo>): VTodo {
    val recur = rRule ?: return this
    val anchor = dtStart ?: due ?: return this
    val finished = overrides
        .filter { override -> override.status?.uppercase() in FINISHED }
        .mapNotNull { override -> override.recurrenceId?.let(::occurrenceIds)?.firstOrNull() }
    val skipped = finished + exDates.flatMap(::occurrenceIds)
    if (skipped.isEmpty()) return this

    val hasTime = anchor is ICalDate.DateTime
    val start = anchor.toDateTime()
    var current = start
    var steps = 0
    while (skipped.any { it.matches(current, hasTime) }) {
        val count = recur.count
        if (steps >= MAX_SKIPPED_OCCURRENCES) return this
        if (count != null && steps + 1 >= count) return finishedSeries(overrides)
        current = recur.nextOccurrence(current, hasTime) ?: return finishedSeries(overrides)
        steps++
    }
    if (steps == 0) return this

    val shift = Shift(
        days = daysBetween(start, current),
        millis = current.millis - start.millis,
        subDaily = recur.frequency in SUB_DAILY,
    )
    // COUNT incluye la ocurrencia inicial: al empezar en una posterior quedan menos repeticiones.
    val count = recur.count
    return copy(
        dtStart = dtStart?.let(shift::apply),
        due = due?.let(shift::apply),
        rRule = if (count != null) recur.copy(count = count - steps) else recur,
    )
}

/** Todas las ocurrencias de la serie están completadas o canceladas. */
private fun VTodo.finishedSeries(overrides: List<VTodo>): VTodo = copy(
    status = TodoStatus.COMPLETED,
    percentComplete = 100,
    completedAt = overrides.mapNotNull { it.completedAt }.maxOrNull() ?: completedAt,
)

/** Identifica una ocurrencia por su fecha (yyyyMMdd) y, si tiene hora, por su instante. */
private class OccurrenceId(val date: String, val instant: Long?) {
    fun matches(occurrence: DateTime, hasTime: Boolean): Boolean =
        if (hasTime && instant != null) {
            instant / 1000 == occurrence.millis / 1000
        } else {
            date == occurrence.dateKey()
        }
}

/** Valores de un RECURRENCE-ID o de un EXDATE (que puede llevar varios separados por comas). */
private fun occurrenceIds(property: ICalProperty): List<OccurrenceId> {
    val tzId = property.parameters
        .firstOrNull { (name, _) -> name.equals("TZID", ignoreCase = true) }
        ?.second
    return property.value.split(',').mapNotNull { parseOccurrenceId(it.trim(), tzId) }
}

private fun parseOccurrenceId(value: String, tzId: String?): OccurrenceId? {
    val date = value.take(8)
    if (date.length < 8 || date.any { !it.isDigit() }) return null
    if (value.length == 8) return OccurrenceId(date, null)
    val time = value.substringAfter('T', "").removeSuffix("Z").removeSuffix("z")
    if (time.length < 6 || time.take(6).any { !it.isDigit() }) return OccurrenceId(date, null)
    val zone = when {
        value.endsWith("Z", ignoreCase = true) -> TimeZone.UTC
        tzId != null -> timeZoneOrNull(tzId) ?: TimeZone.currentSystemDefault()
        else -> TimeZone.currentSystemDefault()
    }
    val instant = DateTime(
        year = date.substring(0, 4).toInt(),
        month = date.substring(4, 6).toInt(),
        day = date.substring(6, 8).toInt(),
        hour = time.substring(0, 2).toInt(),
        minute = time.substring(2, 4).toInt(),
        second = time.substring(4, 6).toInt(),
        timeZone = zone,
    ).millis
    return OccurrenceId(date, instant)
}

private fun timeZoneOrNull(id: String): TimeZone? =
    try {
        TimeZone.of(id.trim('"'))
    } catch (e: Exception) {
        null
    }

/** Zona en la que se interpreta una fecha con hora; las flotantes, en la del dispositivo. */
private fun ICalDate.DateTime.zone(): TimeZone = when {
    floating -> TimeZone.currentSystemDefault()
    tzId != null -> timeZoneOrNull(tzId) ?: TimeZone.currentSystemDefault()
    else -> TimeZone.UTC
}

/**
 * Las fechas sin hora se representan a medianoche en la zona del dispositivo, como hace la app al
 * calcular la siguiente ocurrencia de una tarea.
 */
private fun ICalDate.toDateTime(): DateTime = when (this) {
    is ICalDate.Date -> DateTime(year = year, month = month, day = day)
    is ICalDate.DateTime -> DateTime(millis, zone())
}

private fun DateTime.dateKey(): String =
    year.toString().padStart(4, '0') +
            monthOfYear.toString().padStart(2, '0') +
            dayOfMonth.toString().padStart(2, '0')

/** Días de calendario entre dos fechas, contados sobre su fecha local (sin efecto del horario de verano). */
private fun daysBetween(from: DateTime, to: DateTime): Int {
    val fromDay = DateTime(from.year, from.monthOfYear, from.dayOfMonth, timeZone = TimeZone.UTC).millis
    val toDay = DateTime(to.year, to.monthOfYear, to.dayOfMonth, timeZone = TimeZone.UTC).millis
    return ((toDay - fromDay) / ONE_DAY_MILLIS).toInt()
}

/**
 * Desplazamiento que se aplica a DTSTART y DUE para llevarlos a la nueva ocurrencia. Con frecuencia
 * diaria o mayor se suman días de calendario y se conserva la hora local; con frecuencia inferior al
 * día se suma el intervalo exacto.
 */
private class Shift(val days: Int, val millis: Long, val subDaily: Boolean) {
    fun apply(date: ICalDate): ICalDate = when (date) {
        is ICalDate.Date -> {
            val shifted = DateTime(date.year, date.month, date.day, timeZone = TimeZone.UTC)
                .plusDays(if (subDaily) (millis / ONE_DAY_MILLIS).toInt() else days)
            ICalDate.Date(shifted.year, shifted.monthOfYear, shifted.dayOfMonth)
        }
        is ICalDate.DateTime -> date.copy(
            millis = if (subDaily) {
                date.millis + millis
            } else {
                DateTime(date.millis, date.zone()).plusDays(days).millis
            }
        )
    }
}

/**
 * Añade a [serialized] (el VCALENDAR de la serie recién generado) los VTODO con RECURRENCE-ID que
 * había en [original] (el recurso tal y como lo devolvió el servidor), tal cual, junto con los
 * VTIMEZONE que esas ocurrencias necesiten y que [serialized] no incluya.
 */
internal fun preserveRecurrenceOverrides(serialized: String, original: String?): String {
    if (original.isNullOrBlank()) return serialized
    val originalLines = original.replace("\r\n", "\n").replace('\r', '\n').split("\n")
    val overrides = originalLines.components("VTODO").filter { it.isRecurrenceOverrideBlock() }
    if (overrides.isEmpty()) return serialized
    val end = serialized.lastIndexOf("END:VCALENDAR")
    if (end < 0) return serialized

    val present = serialized.replace("\r\n", "\n").split("\n").unfolded()
        .map { it.trim().uppercase() }
        .toSet()
    val overrideLines = overrides.map { it.unfolded() }
    val zones = originalLines.components("VTIMEZONE").filter { zone ->
        val id = zone.unfolded()
            .firstOrNull { it.startsWith("TZID:", ignoreCase = true) }
            ?.substringAfter(':')
            ?.trim()
            ?.takeIf { it.isNotEmpty() }
        id != null &&
                "TZID:${id.uppercase()}" !in present &&
                overrideLines.any { lines -> lines.any { it.referencesTimeZone(id) } }
    }
    val insertion = (overrides + zones).joinToString("") { block ->
        block.joinToString("") { "$it\r\n" }
    }
    return serialized.substring(0, end) + insertion + serialized.substring(end)
}

/** Indica si una línea de contenido lleva el parámetro TZID con el identificador [id]. */
private fun String.referencesTimeZone(id: String): Boolean =
    substringBefore(':').split(';').drop(1).any { parameter ->
        parameter.substringBefore('=').equals("TZID", ignoreCase = true) &&
                parameter.substringAfter('=').trim('"') == id
    }

private fun List<String>.isRecurrenceOverrideBlock(): Boolean = unfolded().any { line ->
    line.substringBefore(':').substringBefore(';').equals(RECURRENCE_ID, ignoreCase = true)
}

/** Une las líneas plegadas (RFC 5545, sección 3.1) para analizarlas; no se usa para reescribir. */
private fun List<String>.unfolded(): List<String> {
    val result = mutableListOf<String>()
    for (line in this) {
        if ((line.startsWith(" ") || line.startsWith("\t")) && result.isNotEmpty()) {
            result[result.lastIndex] = result.last() + line.substring(1)
        } else {
            result.add(line)
        }
    }
    return result
}

/** Bloques BEGIN:[name] ... END:[name] de primer nivel, con sus líneas sin modificar. */
private fun List<String>.components(name: String): List<List<String>> {
    val begin = "BEGIN:$name"
    val end = "END:$name"
    val result = mutableListOf<List<String>>()
    var current: MutableList<String>? = null
    for (line in this) {
        val open = current
        if (open == null) {
            if (line.trimEnd().equals(begin, ignoreCase = true)) {
                current = mutableListOf(line)
            }
        } else {
            open.add(line)
            if (line.trimEnd().equals(end, ignoreCase = true)) {
                result.add(open)
                current = null
            }
        }
    }
    return result
}
