package org.tasks.quickadd

import io.ktor.client.request.header
import io.ktor.client.request.post
import io.ktor.client.request.setBody
import io.ktor.client.statement.bodyAsText
import io.ktor.http.ContentType
import io.ktor.http.HttpHeaders
import io.ktor.http.HttpStatusCode
import io.ktor.http.content.TextContent
import io.ktor.http.isSuccess
import kotlinx.coroutines.delay
import kotlinx.datetime.LocalDate
import kotlinx.datetime.LocalTime
import org.tasks.data.dao.CaldavDao
import org.tasks.http.HttpClientFactory
import org.tasks.http.toKtor
import org.tasks.preferences.Preferences
import org.tasks.security.KeyStoreEncryption
import org.tasks.time.DateTime
import java.util.Locale
import java.util.TimeZone
import java.util.concurrent.TimeUnit
import timber.log.Timber
import javax.inject.Inject

/** Llama a Mistral para convertir texto libre en una línea de alta rápida con marcadores. */
class MistralClient @Inject constructor(
    private val httpClientFactory: HttpClientFactory,
    private val preferences: Preferences,
    private val encryption: KeyStoreEncryption,
    private val caldavDao: CaldavDao,
) {
    val isConfigured: Boolean
        get() = preferences.mistralApiKeyEncrypted != null

    open class MistralException(message: String) : Exception(message)

    /** El plan de la cuenta no permite ninguna petición con este modelo (límite 0). */
    class ModelUnavailableException(val model: String) : MistralException(model)

    private companion object {
        val RETRY_DELAYS = longArrayOf(2_000, 5_000)
        const val MAX_RETRY_AFTER_MS = 10_000L
    }

    /** Devuelve el texto con marcadores o lanza [MistralException] con un mensaje para el usuario. */
    suspend fun interpret(text: String): String {
        val apiKey = preferences.mistralApiKeyEncrypted
            ?.let { encryption.decrypt(it) }
            ?.takeIf { it.isNotBlank() }
            ?: throw MistralException("API key")
        val now = DateTime()
        val body = MistralQuickAdd.request(
            text = text,
            today = LocalDate(now.year, now.monthOfYear, now.dayOfMonth),
            now = LocalTime(now.hourOfDay, now.minuteOfHour),
            timeZone = TimeZone.getDefault().id,
            lists = caldavDao.getCalendars().mapNotNull { it.name?.takeIf(String::isNotBlank) },
            model = preferences.mistralModel,
        )
        val client = httpClientFactory
            .newClient(foreground = true, cookieKey = null) { it.callTimeout(30, TimeUnit.SECONDS) }
            .toKtor()
        var status = HttpStatusCode.OK
        var responseBody = ""
        var attempt = 0
        try {
            while (true) {
                val response = client.post(MistralQuickAdd.ENDPOINT) {
                    header(HttpHeaders.Authorization, "Bearer $apiKey")
                    header(HttpHeaders.Accept, ContentType.Application.Json.toString())
                    setBody(TextContent(body, ContentType.Application.Json))
                }
                status = response.status
                responseBody = response.bodyAsText()
                // Los límites del plan gratuito son por modelo y muy bajos (p. ej. 2 por minuto) y se
                // comparten con otras aplicaciones que usen la misma cuenta: se espera y se reintenta.
                if (status == HttpStatusCode.TooManyRequests &&
                    response.headers["x-ratelimit-limit-req-minute"] == "0"
                ) {
                    throw ModelUnavailableException(preferences.mistralModel)
                }
                if (status != HttpStatusCode.TooManyRequests || attempt >= RETRY_DELAYS.size) break
                val wait = response.headers[HttpHeaders.RetryAfter]?.toLongOrNull()
                    ?.let { (it * 1000).coerceAtMost(MAX_RETRY_AFTER_MS) }
                    ?: RETRY_DELAYS[attempt]
                Timber.d("Mistral 429, reintento ${attempt + 1} en $wait ms")
                delay(wait)
                attempt++
            }
        } finally {
            client.close()
        }
        if (!status.isSuccess()) {
            Timber.w("Mistral ${status.value} (${preferences.mistralModel}): $responseBody")
            val message = MistralQuickAdd.parseError(responseBody) ?: "HTTP ${status.value}"
            throw MistralException("$message (${status.value}, ${preferences.mistralModel})")
        }
        val task = MistralQuickAdd.parseResponse(responseBody)
            ?: throw MistralException("")
        return task.toQuickAddText(
            syntax = preferences.quickAddSyntax,
            spanish = Locale.getDefault().language == "es",
        )
    }
}
