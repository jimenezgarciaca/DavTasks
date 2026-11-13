package org.tasks.quickadd

import kotlinx.datetime.LocalDate
import kotlinx.datetime.LocalTime
import kotlinx.serialization.Serializable
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.add
import kotlinx.serialization.json.buildJsonObject
import kotlinx.serialization.json.jsonArray
import kotlinx.serialization.json.jsonObject
import kotlinx.serialization.json.jsonPrimitive
import kotlinx.serialization.json.put
import kotlinx.serialization.json.putJsonArray
import kotlinx.serialization.json.putJsonObject

/**
 * Interpretación de texto libre con la API de Mistral. La IA no crea la tarea: devuelve los campos
 * en JSON y [toQuickAddText] los convierte en una línea con los marcadores de la app, que el usuario
 * revisa en la caja de alta rápida antes de guardar.
 */
object MistralQuickAdd {
    const val ENDPOINT = "https://api.mistral.ai/v1/chat/completions"
    const val DEFAULT_MODEL = "mistral-small-latest"

    private val json = Json {
        ignoreUnknownKeys = true
        isLenient = true
        coerceInputValues = true
        explicitNulls = false
    }

    private val SYSTEM_PROMPT = """
        Extract a to-do task from the user's text (Spanish or English). Reply only with a JSON object:
        {"title": string, "tags": [string], "list": string|null, "date": "YYYY-MM-DD"|null,
         "time": "HH:MM"|null, "priority": "high"|"medium"|"low"|null,
         "repeat": {"frequency": "daily"|"weekly"|"monthly"|"yearly", "interval": integer,
                    "days": ["MO","TU","WE","TH","FR","SA","SU"]}|null}
        Rules:
        - title: the action to do, short, in the user's language, without the date, time, list,
          tags, priority or repetition words. Keep names and objects. Capitalize the first letter.
        - Resolve relative dates ("mañana", "el jueves", "next week") from the given current date.
          A weekday name means its next occurrence after today.
        - time only if the text gives a time of day; 24-hour format.
        - list: only if the text clearly names one of the available lists; use its exact name.
        - tags: only words the user explicitly presents as tags or labels.
        - priority: only if the text says it is urgent/important (high) or similar.
        - repeat.days only for weekly repetition on specific weekdays; interval defaults to 1.
        - Use null or [] for anything not stated. Never invent data.
    """.trimIndent()

    /** Cuerpo JSON de la petición a la API de chat. */
    fun request(
        text: String,
        today: LocalDate,
        now: LocalTime,
        timeZone: String,
        lists: List<String>,
        model: String = DEFAULT_MODEL,
    ): String = buildJsonObject {
        put("model", model.ifBlank { DEFAULT_MODEL })
        put("temperature", 0)
        putJsonObject("response_format") { put("type", "json_object") }
        putJsonArray("messages") {
            add(buildJsonObject {
                put("role", "system")
                put("content", SYSTEM_PROMPT)
            })
            add(buildJsonObject {
                put("role", "user")
                put(
                    "content",
                    buildString {
                        appendLine("Current date: $today (${today.dayOfWeek.name.lowercase()})")
                        appendLine("Current time: ${now.hour.pad()}:${now.minute.pad()} ($timeZone)")
                        appendLine("Available lists: ${lists.joinToString(" | ").ifEmpty { "none" }}")
                        appendLine("Text:")
                        append(text.trim())
                    },
                )
            })
        }
    }.toString()

    /** Extrae la tarea de la respuesta de la API; null si no tiene el formato esperado. */
    fun parseResponse(body: String): AiTask? = runCatching {
        val content = json.parseToJsonElement(body)
            .jsonObject["choices"]!!.jsonArray[0]
            .jsonObject["message"]!!.jsonObject["content"]!!.jsonPrimitive.content
        json.decodeFromString(AiTask.serializer(), content.stripCodeFence())
    }.getOrNull()

    /** Mensaje de error de la API, si lo trae. */
    fun parseError(body: String): String? = runCatching {
        val obj = json.parseToJsonElement(body).jsonObject
        (obj["message"] ?: obj["detail"] ?: obj["error"]?.jsonObject?.get("message"))
            ?.jsonPrimitive?.content
    }.getOrNull()

    private fun String.stripCodeFence(): String =
        trim().removePrefix("```json").removePrefix("```").removeSuffix("```").trim()
}

@Serializable
data class AiTask(
    val title: String = "",
    val tags: List<String> = emptyList(),
    val list: String? = null,
    val date: String? = null,
    val time: String? = null,
    val priority: String? = null,
    val repeat: AiRepeat? = null,
)

@Serializable
data class AiRepeat(
    val frequency: String = "",
    val interval: Int = 1,
    val days: List<String> = emptyList(),
)

private val DAY_CODES = listOf("MO", "TU", "WE", "TH", "FR", "SA", "SU")
private val DAY_NAMES_ES = listOf("lun", "mar", "mie", "jue", "vie", "sab", "dom")
private val DAY_NAMES_EN = listOf("mon", "tue", "wed", "thu", "fri", "sat", "sun")
private val TIME = Regex("""^(\d{1,2}):(\d{2})$""")

private fun Int.pad() = toString().padStart(2, '0')

/**
 * Convierte la respuesta de la IA en una línea de alta rápida con [syntax]. Los datos que no son
 * válidos se descartan; las palabras del título que empiezan por un símbolo se escapan con `\`.
 */
fun AiTask.toQuickAddText(syntax: QuickAddSyntax, spanish: Boolean): String {
    val parts = mutableListOf<String>()
    title.trim().split(Regex("\\s+")).filter { it.isNotEmpty() }.forEach { word ->
        parts.add(if (word.first() in syntax.symbols) "\\$word" else word)
    }
    tags.map { it.clean() }.filter { it.isNotEmpty() }.distinct().forEach { parts.add(marker(syntax.tag, it)) }
    list?.clean()?.takeIf { it.isNotEmpty() }?.let { parts.add(marker(syntax.list, it)) }
    val day = date?.trim()?.let { runCatching { LocalDate.parse(it) }.getOrNull() }
    val hour = time?.trim()?.let { TIME.find(it) }?.let { m ->
        val h = m.groupValues[1].toInt()
        val min = m.groupValues[2].toInt()
        if (h in 0..23 && min in 0..59) "${h.pad()}:${min.pad()}" else null
    }
    day?.let { parts.add("${syntax.date}$it") }
    hour?.let { parts.add("${syntax.date}$it") }
    when (priority?.trim()?.lowercase()) {
        "high" -> "${syntax.priority}".repeat(3)
        "medium" -> "${syntax.priority}".repeat(2)
        "low" -> "${syntax.priority}"
        else -> null
    }?.let { parts.add(it) }
    repeat?.toMarker(spanish)?.let { parts.add("${syntax.repeat}$it") }
    return parts.joinToString(" ")
}

private fun String.clean() = replace("(", "").replace(")", "").trim()

private fun marker(symbol: Char, value: String) =
    if (value.any { it.isWhitespace() }) "$symbol($value)" else "$symbol$value"

private fun AiRepeat.toMarker(spanish: Boolean): String? {
    val n = interval.coerceIn(1, 999)
    val days = days.map { it.trim().uppercase().take(2) }.filter { it in DAY_CODES }.distinct()
        .sortedBy { DAY_CODES.indexOf(it) }
    return when (frequency.trim().lowercase()) {
        "daily" -> if (n > 1) "cada-${n}d" else if (spanish) "diaria" else "daily"
        "weekly" -> when {
            // Con días concretos se conservan los días; la sintaxis no admite además un intervalo.
            days == DAY_CODES.take(5) -> if (spanish) "laborables" else "weekdays"
            days.isNotEmpty() -> days.joinToString(",") {
                (if (spanish) DAY_NAMES_ES else DAY_NAMES_EN)[DAY_CODES.indexOf(it)]
            }
            n > 1 -> "cada-${n}s"
            else -> if (spanish) "semanal" else "weekly"
        }
        "monthly" -> if (n > 1) "cada-${n}m" else if (spanish) "mensual" else "monthly"
        "yearly" -> if (n > 1) "cada-${n}a" else if (spanish) "anual" else "yearly"
        else -> null
    }
}
