package org.tasks.caldav

import kotlinx.coroutines.runBlocking
import okhttp3.mockwebserver.MockResponse
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Before
import org.junit.Test
import org.mockito.kotlin.any
import org.mockito.kotlin.doReturn
import org.mockito.kotlin.mock
import org.tasks.DatabaseTest
import org.tasks.data.entity.CaldavAccount
import org.tasks.data.entity.CaldavCalendar
import org.tasks.data.entity.CaldavTask
import org.tasks.data.entity.Task

/**
 * Escritura condicional (DavTasks): PUT con If-Match / If-None-Match y gestión del 412.
 */
class ConditionalWriteTest : DatabaseTest() {
    private val server = failFastServer()
    private val caldavDao = db.caldavDao()
    private val dirtyDao = db.dirtyDao()
    private val taskDao = db.taskDao()
    private val encryption = testEncryption()
    private val iCal = mock<iCalendar> {
        onBlocking {
            toVtodo(any<CaldavAccount>(), any<CaldavCalendar>(), any<CaldavTask>(), any<Task>())
        } doReturn VTODO.encodeToByteArray()
    }
    private val synchronizer = CaldavSynchronizer(
        caldavDao = caldavDao,
        dirtyDao = dirtyDao,
        refreshBroadcaster = mock(),
        taskDeleter = mock(),
        reporting = mock(),
        provider = testClientProvider(encryption),
        iCal = iCal,
        principalDao = db.principalDao(),
        vtodoCache = mock(),
        tagMetadataSync = mock { onBlocking { isPrimary(any()) } doReturn false },
    )
    private lateinit var account: CaldavAccount

    @Before
    fun setUp() = runBlocking {
        server.start()
        account = CaldavAccount(
            uuid = "account",
            username = "user",
            password = encryption.encrypt("password"),
            url = server.url(HOME_SET).toString(),
        ).let { it.copy(id = caldavDao.insert(it)) }
    }

    @After
    fun tearDown() = server.shutdown()

    private suspend fun insertCalendar() = CaldavCalendar(
        account = account.uuid,
        uuid = CALENDAR,
        url = "${account.url}$LIST/".canonicalUrl(),
        ctag = CTAG,
    ).also { caldavDao.insert(it) }

    private suspend fun insertDirtyTask(calendar: CaldavCalendar, etag: String?): Long {
        val task = Task(title = "task")
        taskDao.createNew(task)
        caldavDao.insertOrUpdateAndMarkSynced(
            CaldavTask(task = task.id, calendar = calendar.uuid, remoteId = "task", etag = etag),
            markDirty = true,
        )
        return task.id
    }

    @Test
    fun `put sends If-Match with the stored etag`() = runBlocking {
        val task = insertDirtyTask(insertCalendar(), etag = "etag-1")
        server.enqueue(multiStatus(calendars("$HOME_SET$LIST/")))
        server.enqueue(MockResponse().setResponseCode(204).setHeader("ETag", "\"etag-2\""))

        synchronizer.sync(account)

        server.takeRequest() // PROPFIND
        val put = server.takeRequest()
        assertEquals("PUT", put.method)
        assertEquals("\"etag-1\"", put.getHeader("If-Match"))
        assertNull(put.getHeader("If-None-Match"))
        assertEquals("etag-2", caldavDao.getTask(task)!!.etag)
        assertEquals(0, dirtyDao.getTasksToPush(CALENDAR).size)
    }

    @Test
    fun `a new object is created with If-None-Match`() = runBlocking {
        insertDirtyTask(insertCalendar(), etag = null)
        server.enqueue(multiStatus(calendars("$HOME_SET$LIST/")))
        server.enqueue(MockResponse().setResponseCode(201).setHeader("ETag", "\"etag-1\""))

        synchronizer.sync(account)

        server.takeRequest() // PROPFIND
        val put = server.takeRequest()
        assertEquals("PUT", put.method)
        assertEquals("*", put.getHeader("If-None-Match"))
        assertNull(put.getHeader("If-Match"))
    }

    @Test
    fun `412 keeps the local change pending and forces a refetch`() = runBlocking {
        val task = insertDirtyTask(insertCalendar(), etag = "etag-1")
        server.enqueue(multiStatus(calendars("$HOME_SET$LIST/")))
        server.enqueue(MockResponse().setResponseCode(412))

        synchronizer.sync(account)

        assertFalse(caldavDao.getAccountByUuid(account.uuid!!)!!.hasError)
        assertNull(caldavDao.getCalendarByUuid(CALENDAR)!!.ctag)
        assertEquals(listOf(task), dirtyDao.getTasksToPush(CALENDAR).map { it.task.id })
        assertEquals("etag-1", caldavDao.getTask(task)!!.etag)
    }

    companion object {
        private const val HOME_SET = "/dav/calendars/user/"
        private const val LIST = "list"
        private const val CALENDAR = "calendar"
        private const val CTAG = "ctag-1"

        private const val VTODO =
            "BEGIN:VCALENDAR\r\nVERSION:2.0\r\nPRODID:-//DavTasks//EN\r\nBEGIN:VTODO\r\nUID:task\r\nSUMMARY:task\r\nEND:VTODO\r\nEND:VCALENDAR\r\n"

        private fun calendars(href: String) = """
            <?xml version="1.0"?>
            <d:multistatus xmlns:d="DAV:" xmlns:cal="urn:ietf:params:xml:ns:caldav" xmlns:cs="http://calendarserver.org/ns/">
                <d:response>
                    <d:href>$HOME_SET</d:href>
                    <d:propstat>
                        <d:prop><d:resourcetype><d:collection /></d:resourcetype></d:prop>
                        <d:status>HTTP/1.1 200 OK</d:status>
                    </d:propstat>
                </d:response>
                <d:response>
                    <d:href>$href</d:href>
                    <d:propstat>
                        <d:prop>
                            <d:resourcetype><d:collection /><cal:calendar /></d:resourcetype>
                            <d:displayname>List</d:displayname>
                            <cal:supported-calendar-component-set><cal:comp name="VTODO" /></cal:supported-calendar-component-set>
                            <cs:getctag>$CTAG</cs:getctag>
                        </d:prop>
                        <d:status>HTTP/1.1 200 OK</d:status>
                    </d:propstat>
                </d:response>
            </d:multistatus>
        """.trimIndent()

        init {
            CaldavClient.registerFactories()
        }
    }
}
