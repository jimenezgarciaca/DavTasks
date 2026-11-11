package org.tasks.caldav

import at.bitfire.dav4jvm.Property
import at.bitfire.dav4jvm.QuotedStringUtils
import at.bitfire.dav4jvm.ktor.DavCalendar
import at.bitfire.dav4jvm.ktor.DavCalendar.Companion.MIME_ICALENDAR
import at.bitfire.dav4jvm.ktor.DavResource
import at.bitfire.dav4jvm.ktor.Response
import at.bitfire.dav4jvm.ktor.exception.HttpException
import at.bitfire.dav4jvm.ktor.exception.PreconditionFailedException
import at.bitfire.dav4jvm.ktor.exception.ServiceUnavailableException
import at.bitfire.dav4jvm.ktor.exception.UnauthorizedException
import at.bitfire.dav4jvm.property.caldav.CalendarColor
import at.bitfire.dav4jvm.property.caldav.CalendarData
import at.bitfire.dav4jvm.property.webdav.CurrentUserPrincipal
import at.bitfire.dav4jvm.property.webdav.CurrentUserPrivilegeSet
import at.bitfire.dav4jvm.property.caldav.GetCTag
import at.bitfire.dav4jvm.property.webdav.DisplayName
import at.bitfire.dav4jvm.property.webdav.GetETag
import at.bitfire.dav4jvm.property.webdav.GetETag.Companion.fromHttpResponse
import at.bitfire.dav4jvm.property.webdav.SyncToken
import org.tasks.resources.getString
import org.tasks.service.TaskDeleter
import org.tasks.data.dao.DirtyDao
import io.ktor.client.HttpClient
import io.ktor.http.Headers
import io.ktor.http.HttpHeaders
import io.ktor.http.headersOf
import io.ktor.http.HttpStatusCode
import io.ktor.http.URLBuilder
import io.ktor.http.Url
import io.ktor.http.appendPathSegments
import io.ktor.http.content.ByteArrayContent
import tasks.kmp.generated.resources.Res
import tasks.kmp.generated.resources.password_required
import org.tasks.time.DateTimeUtils2.currentTimeMillis
import org.tasks.broadcast.RefreshBroadcaster
import org.tasks.caldav.iCalendar.Companion.fromVtodo
import org.tasks.caldav.metadata.TagMetadataSync
import org.tasks.caldav.property.CalendarIcon
import org.tasks.caldav.property.Invite
import org.tasks.caldav.property.OCAccess
import org.tasks.caldav.property.OCInvite
import org.tasks.caldav.property.OCOwnerPrincipal
import org.tasks.caldav.property.OCUser
import org.tasks.caldav.property.ShareAccess
import org.tasks.caldav.property.ShareAccess.Companion.NOT_SHARED
import org.tasks.caldav.property.ShareAccess.Companion.NO_ACCESS
import org.tasks.caldav.property.ShareAccess.Companion.READ
import org.tasks.caldav.property.ShareAccess.Companion.READ_WRITE
import org.tasks.caldav.property.ShareAccess.Companion.SHARED_OWNER
import org.tasks.caldav.property.Sharee
import org.tasks.data.UUIDHelper
import org.tasks.data.dao.CaldavDao
import org.tasks.data.dao.PrincipalDao
import org.tasks.data.entity.CaldavAccount
import org.tasks.data.entity.CaldavAccount.Companion.SERVER_NEXTCLOUD
import org.tasks.data.entity.CaldavAccount.Companion.SERVER_OPEN_XCHANGE
import org.tasks.data.entity.CaldavAccount.Companion.SERVER_OWNCLOUD
import org.tasks.data.entity.CaldavAccount.Companion.SERVER_SABREDAV
import org.tasks.data.entity.CaldavAccount.Companion.SERVER_UNKNOWN
import org.tasks.data.entity.CaldavCalendar
import org.tasks.data.entity.CaldavCalendar.Companion.ACCESS_OWNER
import org.tasks.data.entity.CaldavCalendar.Companion.ACCESS_READ_ONLY
import org.tasks.data.entity.CaldavCalendar.Companion.ACCESS_READ_WRITE
import org.tasks.data.entity.CaldavCalendar.Companion.ACCESS_UNKNOWN
import org.tasks.data.entity.CaldavCalendar.Companion.INVITE_ACCEPTED
import org.tasks.data.entity.CaldavCalendar.Companion.INVITE_DECLINED
import org.tasks.data.entity.CaldavCalendar.Companion.INVITE_INVALID
import org.tasks.data.entity.CaldavCalendar.Companion.INVITE_NO_RESPONSE
import org.tasks.data.entity.CaldavCalendar.Companion.INVITE_UNKNOWN
import org.tasks.data.entity.CaldavTask
import org.tasks.data.entity.PrincipalAccess
import org.tasks.data.entity.Task
import org.tasks.http.NetworkException
import co.touchlab.kermit.Logger
import kotlinx.io.IOException

private const val TAG = "CaldavSync"

private class HomeSetNotFound(val notFound: HttpException) : Exception(notFound)

class CaldavSynchronizer(
    private val caldavDao: CaldavDao,
    private val dirtyDao: DirtyDao,
    private val refreshBroadcaster: RefreshBroadcaster,
    private val taskDeleter: TaskDeleter,
    private val provider: CaldavClientProvider,
    private val iCal: iCalendar,
    private val principalDao: PrincipalDao,
    private val vtodoCache: VtodoCache,
    private val tagMetadataSync: TagMetadataSync,
) {
    suspend fun sync(account: CaldavAccount) {
        Logger.d(TAG) { "Synchronizing $account" }
        if (account.password.isNullOrBlank()) {
            setError(account, getString(Res.string.password_required))
            return
        }
        try {
            synchronize(account)
            account.lastSync = currentTimeMillis()
            setError(account, "")
        } catch (e: IOException) {
            setError(account, e)
        } catch (e: UnauthorizedException) {
            setError(account, e)
        } catch (e: ServiceUnavailableException) {
            setError(account, e)
        } catch (e: NetworkException) {
            setError(account, e)
        } catch (e: HomeSetNotFound) {
            Logger.e(e.notFound) { "$account: ${e.notFound.message}" }
            setError(account, e.notFound.displayMessage)
        } catch (e: HttpException) {
            Logger.e(e) { "$account: ${e.message}" }
            setError(account, e.displayMessage)
        } catch (e: Exception) {
            setError(account, e)
            if (!e.isTlsSetupFailure()) {
                Logger.e(e) { "$account: ${e.message}" }
            }
        }
    }

    private suspend fun synchronize(account: CaldavAccount) = provider.forAccount(account).use { caldavClient ->
        var serverType = account.serverType

        // DavTasks no incluye cuentas de Tasks.org (ni cuentas invitadas).
        val isGuest = false

        val resources = try {
            caldavClient.calendars { headers ->
                if (serverType == SERVER_UNKNOWN) {
                    serverType = getServerType(account, headers)
                }
            }
        } catch (e: HttpException) {
            throw if (e.statusCode == 404) HomeSetNotFound(e) else e
        }
        if (serverType != account.serverType) {
            account.serverType = serverType
            caldavDao.update(account)
        }
        val urls = resources.map { it.href.toString() }.toHashSet()
        for (calendar in caldavDao.findDeletedCalendars(account.uuid!!, ArrayList(urls))) {
            if (isDeletedRemotely(caldavClient, calendar)) {
                taskDeleter.delete(calendar)
            }
        }
        val metadataPulled = if (tagMetadataSync.isPrimary(account)) {
            tagMetadataSync.pullMetadata(account, caldavClient)
        } else {
            null
        }
        for (resource in resources) {
            val url = resource.href.toString()
            var calendar = caldavDao.getCalendarByUrl(account.uuid!!, url)
            val remoteName = resource[DisplayName::class]!!.displayName
            val color = resource[CalendarColor::class]?.color ?: 0
            val rawAccess = resource.accessLevel
            val guestOwned = isGuest && rawAccess == ACCESS_OWNER
            val access = if (guestOwned) ACCESS_READ_ONLY else rawAccess
            val icon = resource[CalendarIcon::class]?.icon?.takeIf { it.isNotBlank() }

            if (calendar == null) {
                calendar = CaldavCalendar(
                    name = remoteName,
                    account = account.uuid,
                    url = url,
                    uuid = UUIDHelper.newUUID(),
                    color = color,
                    access = access,
                    icon = icon,
                )
                caldavDao.insert(calendar)
            } else if (calendar.name != remoteName
                || calendar.color != color
                || calendar.access != access
                || (icon != null && calendar.icon != icon)
            ) {
                calendar = calendar.copy(
                    color = color,
                    name = remoteName,
                    access = access,
                    icon = icon ?: calendar.icon,
                )
                caldavDao.update(calendar)
                refreshBroadcaster.broadcastRefresh()
            }
            resource
                .principals(account, calendar)
                .let { principalDao.deleteRemoved(calendar.id, it.map(PrincipalAccess::id)) }
            fetchChanges(account, calendar, resource, caldavClient.httpClient)
            when {
                guestOwned -> pushLocalChanges(
                    account, calendar, caldavClient.httpClient, resource.href,
                    deleteOnly = true
                )
                calendar.access != ACCESS_READ_ONLY -> pushLocalChanges(
                    account, calendar, caldavClient.httpClient, resource.href
                )
            }
        }
        if (metadataPulled != null &&
            tagMetadataSync.pushAndReap(account, caldavClient, metadataPulled)
        ) {
            refreshBroadcaster.broadcastRefresh()
        }
    }

    private suspend fun isDeletedRemotely(client: CaldavClient, calendar: CaldavCalendar): Boolean {
        val url = calendar.url?.takeIf { it.isNotBlank() } ?: return true
        return try {
            val response = client.calendar(url.toCaldavUrl())
            if (response?.status?.value in GONE_STATUSES) {
                Logger.d(TAG) { "${calendar.uuid} is gone (${response?.status})" }
                return true
            }
            if (response?.isSuccess() != true) {
                Logger.w(TAG) { "Failed to verify ${calendar.uuid} (${response?.status})" }
                return false
            }
            val href = response.href.canonical().toString()
            if (href != calendar.url) {
                if (caldavDao.getCalendarByUrl(calendar.account!!, href) == null) {
                    Logger.d(TAG) { "Recanonicalizing ${calendar.uuid}" }
                    caldavDao.update(calendar.copy(url = href))
                } else {
                    Logger.w(TAG) { "$href is already tracked, leaving ${calendar.uuid} alone" }
                }
            }
            false
        } catch (e: HttpException) {
            val gone = e.statusCode in GONE_STATUSES
            if (!gone) {
                Logger.w(e, tag = TAG) { "Failed to verify ${calendar.uuid}" }
            }
            gone
        } catch (e: Exception) {
            Logger.w(e, tag = TAG) { "Failed to verify ${calendar.uuid}" }
            false
        }
    }

    private fun getServerType(account: CaldavAccount, headers: Headers): Int {
        val dav = headers.getAll("DAV")?.joinToString(",")
        return when {
            dav?.contains("oc-resource-sharing") == true ->
                if (dav.contains("nextcloud-") || dav.contains("nc-"))
                    SERVER_NEXTCLOUD
                else
                    SERVER_OWNCLOUD
            headers["x-sabre-version"]?.isNotBlank() == true -> SERVER_SABREDAV
            headers["server"] == "Openexchange WebDAV" -> SERVER_OPEN_XCHANGE
            else -> SERVER_UNKNOWN
        }
    }

    private suspend fun setError(account: CaldavAccount, throwable: Throwable) {
        Logger.e(throwable) { "$account: ${throwable.message}" }
        setError(account, throwable.message)
    }

    private suspend fun setError(account: CaldavAccount, message: String?) {
        if (!message.isNullOrBlank()) {
            Logger.e(TAG) { "$account: $message" }
        }
        account.error = message
        caldavDao.update(account)
        refreshBroadcaster.broadcastRefresh()
        if (!message.isNullOrBlank()) {
            Logger.e(TAG) { message.orEmpty() }
        }
    }

    private suspend fun fetchChanges(
        account: CaldavAccount,
        caldavCalendar: CaldavCalendar,
        resource: Response,
        httpClient: HttpClient
    ) {
        val httpUrl = resource.href
        val remoteCtag = resource.ctag
        if (caldavCalendar.ctag?.equals(remoteCtag) == true) {
            Logger.d(TAG) { "up to date: $caldavCalendar" }
            return
        }
        Logger.d(TAG) { "updating $caldavCalendar" }
        val davCalendar = DavCalendar(httpClient, httpUrl)
        val members = davCalendar.calendarQuery("VTODO", null, null).members()
        val changed = members.filter { vCard: Response ->
            val eTag = vCard[GetETag::class]?.eTag
            if (eTag.isNullOrBlank()) {
                Logger.w(TAG) { "Skipping ${vCard.href}, no etag in the calendar-query response" }
                return@filter false
            }
            eTag != caldavDao.getTask(caldavCalendar.uuid!!, vCard.hrefName())?.etag
        }
        Logger.d(TAG) {
            "calendar-query returned ${members.size} member(s), ${changed.size} changed"
        }
        val unusable = mutableListOf<Url>()
        for (items in changed.chunked(30)) {
            val urls = items.map { it.href }
            val queriedETags = items.associate { it.hrefName() to it[GetETag::class]?.eTag }
            val responses = davCalendar.multiget(urls).members()
            Logger.d(TAG) { "MULTI $urls" }
            for (vCard in responses) {
                val url = vCard.href
                val fileName = vCard.hrefName()
                if (!vCard.isSuccess()) {
                    Logger.e(TAG) { "Skipping $url (${vCard.status})" }
                    unusable += url
                    continue
                }
                val eTag = vCard[GetETag::class]?.eTag?.takeIf { it.isNotBlank() }
                    ?: queriedETags[fileName]
                if (eTag.isNullOrBlank()) {
                    Logger.e(TAG) { "Received CalDAV GET response without ETag for $url" }
                    unusable += url
                    continue
                }
                val vtodo = vCard[CalendarData::class]?.iCalendar
                if (vtodo.isNullOrBlank()) {
                    Logger.e(TAG) { "Received CalDAV GET response without CalendarData for $url" }
                    unusable += url
                    continue
                }
                val remote = fromVtodo(vtodo)
                if (remote == null) {
                    Logger.e(TAG) { "Invalid VCALENDAR: $fileName" }
                    continue
                }
                val caldavTask = caldavDao.getTask(caldavCalendar.uuid!!, fileName)
                iCal.fromVtodo(account, caldavCalendar, caldavTask, remote, vtodo, fileName, eTag)
            }
        }
        if (unusable.isNotEmpty()) {
            Logger.e(TAG) {
                "Unusable multiget responses for ${unusable.size} resources: ${unusable.joinToString(limit = 10)}"
            }
        }
        caldavDao
                .getRemoteObjects(caldavCalendar.uuid!!)
                .subtract(members.map { it.hrefName() })
                .takeIf { it.isNotEmpty() }
                ?.let {
                    Logger.d(TAG) { "DELETED $it" }
                    val tasks = caldavDao.getTasks(caldavCalendar.uuid!!, it.toList())
                    taskDeleter.delete(tasks.map { it.task })
                }
        caldavCalendar.ctag = remoteCtag
        Logger.d(TAG) { "UPDATE $caldavCalendar" }
        caldavDao.update(caldavCalendar)
        Logger.d(TAG) { "Updating parents for ${caldavCalendar.uuid}" }
        caldavDao.updateParents(caldavCalendar.uuid!!)
        refreshBroadcaster.broadcastRefresh()
    }

    private suspend fun pushLocalChanges(
        account: CaldavAccount,
        caldavCalendar: CaldavCalendar,
        httpClient: HttpClient,
        httpUrl: Url,
        deleteOnly: Boolean = false,
    ) {
        for (task in caldavDao.getMoved(caldavCalendar.uuid!!)) {
            deleteRemoteResource(httpClient, httpUrl, caldavCalendar, task)
        }
        for (toPush in dirtyDao.getTasksToPush(caldavCalendar.uuid!!)) {
            if (deleteOnly && !toPush.task.isDeleted) continue
            pushTask(account, caldavCalendar, toPush.task, toPush.caldavTaskId, toPush.dirtyVersion, httpClient, httpUrl)
        }
    }

    private suspend fun deleteRemoteResource(
        httpClient: HttpClient,
        httpUrl: Url,
        calendar: CaldavCalendar,
        caldavTask: CaldavTask
    ): Boolean {
        try {
            val objectId = caldavTask.obj
                ?: run {
                    Logger.e(TAG) { "null obj for caldavTask.id=${caldavTask.id} task.id=${caldavTask.task}" }
                    caldavTask.obj = caldavTask.remoteId?.let { CaldavTask.objectName(it) }
                    caldavTask.obj
                }
            if (objectId?.isNotBlank() == true) {
                val remote = DavResource(
                    httpClient = httpClient,
                    location = httpUrl.child(objectId),
                )
                remote.delete(additionalHeaders = ifMatch(caldavTask.etag)) {}
            }
        } catch (e: PreconditionFailedException) {
            // El servidor tiene una versión más reciente que la última que sincronizamos:
            // no borramos a ciegas. Se vuelve a descargar en la siguiente sincronización
            // y el borrado se reintenta con la ETag actualizada.
            Logger.w(TAG) { "412 al borrar ${caldavTask.obj}; se reintentará tras volver a descargar" }
            forceRefetch(calendar)
            return false
        } catch (e: HttpException) {
            if (e.statusCode != 404) {
                Logger.e(e) { e.message.orEmpty() }
                return false
            }
        } catch (e: IOException) {
            Logger.e(e) { e.message.orEmpty() }
            return false
        }
        caldavDao.delete(caldavTask)
        return true
    }

    private suspend fun pushTask(
        account: CaldavAccount,
        calendar: CaldavCalendar,
        task: Task,
        caldavTaskId: Long,
        dirtyVersion: Long?,
        httpClient: HttpClient,
        httpUrl: Url
    ) {
        val caldavTask = caldavDao.getCaldavTaskById(caldavTaskId) ?: return
        Logger.d(TAG) { "pushing caldavTask=$caldavTask task=$task" }
        if (task.isDeleted) {
            if (deleteRemoteResource(httpClient, httpUrl, calendar, caldavTask)) {
                taskDeleter.delete(task)
            }
            return
        }
        try {
            pushTaskConditionally(account, calendar, task, caldavTask, caldavTaskId, dirtyVersion, httpClient, httpUrl)
        } catch (e: PreconditionFailedException) {
            // Otro cliente modificó la tarea después de nuestra última descarga. No se
            // sobrescribe: la tarea sigue marcada como pendiente, se fuerza la descarga del
            // calendario en la siguiente sincronización, se fusionan los cambios con
            // iCalendarMerge y se vuelve a subir con la ETag nueva.
            Logger.w(TAG) { "412 al subir ${caldavTask.obj}; se fusionará en la siguiente sincronización" }
            forceRefetch(calendar)
            return
        }
        Logger.d(TAG) { "SENT $caldavTask" }
    }

    private suspend fun pushTaskConditionally(
        account: CaldavAccount,
        calendar: CaldavCalendar,
        task: Task,
        caldavTask: CaldavTask,
        caldavTaskId: Long,
        dirtyVersion: Long?,
        httpClient: HttpClient,
        httpUrl: Url
    ) {
        // Si el bloque lanza una excepción, withDirtyVersion no marca la tarea como subida.
        dirtyDao.withDirtyVersion(caldavTaskId, dirtyVersion) {
            val data = iCal.toVtodo(account, calendar, caldavTask, task)
            val requestBody = ByteArrayContent(data, MIME_ICALENDAR)
            val objPath = caldavTask.obj
                ?: run {
                    Logger.e(TAG) { "null obj for caldavTask.id=${caldavTask.id} task.id=${task.id}" }
                    caldavTask.obj = caldavTask.remoteId?.let { CaldavTask.objectName(it) }
                    caldavTask.obj
                }
                ?: throw IllegalStateException("Push failed - missing UUID")

            try {
                val remote = DavResource(
                    httpClient = httpClient,
                    location = httpUrl.child(objPath),
                )
                // If-Match con la última ETag conocida; If-None-Match: * si el objeto es nuevo,
                // para no pisar un recurso que ya exista en el servidor.
                val conditions = ifMatch(caldavTask.etag)
                    ?: headersOf(HttpHeaders.IfNoneMatch, "*")
                remote.put(requestBody, additionalHeaders = conditions) {
                    fromHttpResponse(it)?.eTag?.takeIf(String::isNotBlank)?.let { etag ->
                        caldavTask.etag = etag
                    }
                    vtodoCache.putVtodo(calendar, caldavTask, data.decodeToString())
                }
            } catch (e: HttpException) {
                Logger.e(e) { e.message.orEmpty() }
                throw e
            }
            caldavDao.update(caldavTask)
        }
    }

    private suspend fun forceRefetch(calendar: CaldavCalendar) {
        calendar.ctag = null
        caldavDao.update(calendar)
    }

    suspend fun Response.principals(
        account: CaldavAccount,
        list: CaldavCalendar
    ): List<PrincipalAccess> {
        val access = ArrayList<PrincipalAccess>()
        this[Invite::class]
            ?.sharees
            ?.filter { it.href?.let { href -> !isCurrentUser(href) } ?: false }
            ?.map {
                val principal = principalDao.getOrCreatePrincipal(
                    account,
                    it.href!!,
                    it.properties
                        .find { p -> p is DisplayName }
                        ?.let { name -> (name as DisplayName).displayName }
                )
                principalDao.getOrCreateAccess(
                    list,
                    principal,
                    invite = it.response?.toStatus ?: INVITE_UNKNOWN,
                    access = it.access?.access?.toAccess ?: ACCESS_UNKNOWN
                )
            }
            ?.let { access.addAll(it) }
        this[OCInvite::class]?.users
            ?.map {
                val principal = principalDao.getOrCreatePrincipal(account, it.href)
                principalDao.getOrCreateAccess(
                    list,
                    principal,
                    it.response.toStatus,
                    it.access.access.toAccess
                )
            }
            ?.let {
                if (!isOwncloudOwner) {
                    this@principals[OCOwnerPrincipal::class]?.owner?.let { href ->
                        val principal = principalDao.getOrCreatePrincipal(account, href)
                        access.add(principalDao.getOrCreateAccess(
                            list,
                            principal,
                            INVITE_ACCEPTED,
                            ACCESS_OWNER
                        ))
                    }
                }
                access.addAll(it)
            }
        return access
    }

    companion object {
        private val GONE_STATUSES = setOf(403, 404, 410)

        private val HttpException.displayMessage: String
            get() = HttpStatusCode
                .fromValue(statusCode)
                .description
                .takeIf { it.isNotBlank() && it != UNKNOWN_STATUS }
                ?.let { "HTTP $statusCode - $it" }
                ?: "HTTP $statusCode"

        private const val UNKNOWN_STATUS = "Unknown Status Code"

        /**
         * Cabecera If-Match en forma fuerte (RFC 9110 §8.8.3): sin el prefijo W/ y entre
         * comillas. dav4jvm guarda la ETag ya sin W/ ni comillas. Devuelve null si no hay ETag.
         */
        internal fun ifMatch(etag: String?): Headers? =
            etag?.takeIf { it.isNotBlank() }?.let {
                headersOf(HttpHeaders.IfMatch, QuotedStringUtils.asQuotedString(it))
            }

        val Response.ctag: String?
            get() = this[SyncToken::class]?.token ?: this[GetCTag::class]?.cTag

        private fun Url.child(segment: String): Url =
            URLBuilder(this).appendPathSegments(segment, encodeSlash = true).build()

        val Response.accessLevel: Int
            get() {
                this[ShareAccess::class]?.access?.let {
                    return when (it) {
                        NOT_SHARED, SHARED_OWNER -> ACCESS_OWNER
                        READ_WRITE -> ACCESS_READ_WRITE
                        NO_ACCESS, READ -> ACCESS_READ_ONLY
                        else -> ACCESS_UNKNOWN
                    }
                }
                if (isOwncloudOwner) {
                    return ACCESS_OWNER
                }
                return when (this[CurrentUserPrivilegeSet::class]?.mayWriteContent) {
                    false -> ACCESS_READ_ONLY
                    else -> ACCESS_READ_WRITE
                }
        }

        private val Response.isOwncloudOwner: Boolean
            get() = this[OCOwnerPrincipal::class]?.owner?.let { isCurrentUser(it) } ?: false

        private fun Response.isCurrentUser(href: String) =
            this[CurrentUserPrincipal::class]?.href?.endsWith("$href/") == true

        private val Property.Name.toAccess: Int
            get() = when (this) {
                SHARED_OWNER, OCAccess.SHARED_OWNER -> ACCESS_OWNER
                READ_WRITE, OCAccess.READ_WRITE -> ACCESS_READ_WRITE
                READ, OCAccess.READ -> ACCESS_READ_ONLY
                else -> ACCESS_UNKNOWN
            }

        private val Property.Name.toStatus: Int
            get() = when (this) {
                Sharee.INVITE_ACCEPTED, OCUser.INVITE_ACCEPTED -> INVITE_ACCEPTED
                Sharee.INVITE_NORESPONSE, OCUser.INVITE_NORESPONSE -> INVITE_NO_RESPONSE
                Sharee.INVITE_DECLINED, OCUser.INVITE_DECLINED -> INVITE_DECLINED
                Sharee.INVITE_INVALID, OCUser.INVITE_INVALID -> INVITE_INVALID
                else -> INVITE_UNKNOWN
            }
    }
}
