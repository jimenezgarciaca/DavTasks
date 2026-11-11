package org.tasks.caldav

import kotlinx.coroutines.runBlocking
import okhttp3.mockwebserver.MockResponse
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test
import org.mockito.kotlin.any
import org.mockito.kotlin.doReturn
import org.mockito.kotlin.mock
import org.tasks.DatabaseTest
import org.tasks.data.entity.CaldavAccount

class CaldavSyncErrorTest : DatabaseTest() {
    private val server = failFastServer()
    private val caldavDao = db.caldavDao()
    private val encryption = testEncryption()
    private val synchronizer = CaldavSynchronizer(
        caldavDao = caldavDao,
        dirtyDao = db.dirtyDao(),
        refreshBroadcaster = mock(),
        taskDeleter = mock(),
        provider = testClientProvider(encryption),
        iCal = mock(),
        principalDao = db.principalDao(),
        vtodoCache = mock(),
        tagMetadataSync = mock { on { isPrimary(any()) } doReturn false },
    )
    private lateinit var account: CaldavAccount

    @Before
    fun setUp() = runBlocking {
        server.start()
        account = CaldavAccount(
            uuid = "account",
            username = "user",
            password = encryption.encrypt("password"),
            url = server.url("/dav/").toString(),
        ).let { it.copy(id = caldavDao.insert(it)) }
    }

    @After
    fun tearDown() = server.shutdown()

    @Test
    fun `a propfind the server refuses is shown`() = runBlocking {
        server.enqueue(MockResponse().setResponseCode(405))

        synchronizer.sync(account)

        assertEquals("HTTP 405 - Method Not Allowed", caldavDao.getAccountByUuid("account")!!.error)
    }

    @Test
    fun `a request the server rejects as malformed is shown`() = runBlocking {
        server.enqueue(MockResponse().setResponseCode(400))

        synchronizer.sync(account)

        assertEquals("HTTP 400 - Bad Request", caldavDao.getAccountByUuid("account")!!.error)
    }

    @Test
    fun `a home set the server no longer has is shown`() = runBlocking {
        server.enqueue(MockResponse().setResponseCode(404))

        synchronizer.sync(account)

        assertEquals("HTTP 404 - Not Found", caldavDao.getAccountByUuid("account")!!.error)
    }

    @Test
    fun `an unnamed status still names the code`() = runBlocking {
        server.enqueue(MockResponse().setResponseCode(499))

        synchronizer.sync(account)

        assertEquals("HTTP 499", caldavDao.getAccountByUuid("account")!!.error)
    }

    @Test
    fun `payment and terms statuses keep driving their screens`() = runBlocking {
        server.enqueue(MockResponse().setResponseCode(402))

        synchronizer.sync(account)

        assertTrue(caldavDao.getAccountByUuid("account")!!.isPaymentRequired())

        server.enqueue(MockResponse().setResponseCode(451))

        synchronizer.sync(account)

        assertTrue(caldavDao.getAccountByUuid("account")!!.isTosRequired())
    }
}
