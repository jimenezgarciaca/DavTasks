package org.tasks.caldav

import io.ktor.client.HttpClient
import io.ktor.client.plugins.HttpTimeout
import org.tasks.data.entity.CaldavAccount
import org.tasks.http.KtorClientFactory
import org.tasks.http.installBasicDigestAuth
import org.tasks.security.Encryption

/** Clientes CalDAV con autenticación Basic/Digest. */
class CaldavClientProvider(
    private val encryption: Encryption,
    private val httpClientFactory: KtorClientFactory,
) : CaldavClientFactory {

    private class BasicDigest(
        val user: String,
        val password: String,
        val url: String?,
    )

    override suspend fun forUrl(
        url: String?,
        username: String?,
        password: String?,
    ): CaldavClient = CaldavClient(
        createHttpClient(auth = getAuth(username, password, url), foreground = true),
        url?.toCaldavUrl(),
    )

    override suspend fun forAccount(account: CaldavAccount, url: String?): CaldavClient {
        val password = encryption.decrypt(account.password) ?: ""
        val auth = getAuth(account.username, password, account.url)
        return CaldavClient(createHttpClient(auth), url?.toCaldavUrl())
    }

    private fun getAuth(username: String?, password: String?, url: String?): BasicDigest? =
        if (username.isNullOrBlank() || password.isNullOrBlank()) null
        else BasicDigest(username, password, url)

    private suspend fun createHttpClient(
        auth: BasicDigest?,
        foreground: Boolean = false,
    ): HttpClient = httpClientFactory.newClient(foreground = foreground, cookieKey = auth?.user) {
        followRedirects = false
        install(HttpTimeout) {
            connectTimeoutMillis = 15_000
            socketTimeoutMillis = 120_000
        }
        if (auth != null) {
            installBasicDigestAuth(auth.user, auth.password, auth.url)
        }
    }
}
