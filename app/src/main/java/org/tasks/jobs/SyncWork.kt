package org.tasks.jobs

import android.accounts.AccountManager
import android.content.ContentResolver
import android.content.Context
import android.net.ConnectivityManager
import android.os.Bundle
import androidx.core.content.ContextCompat
import androidx.core.net.ConnectivityManagerCompat
import androidx.hilt.work.HiltWorker
import androidx.work.WorkerParameters
import dagger.Lazy
import dagger.assisted.Assisted
import dagger.assisted.AssistedInject
import kotlinx.coroutines.Deferred
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.async
import kotlinx.coroutines.awaitAll
import kotlinx.coroutines.coroutineScope
import org.tasks.broadcast.RefreshBroadcaster
import org.tasks.caldav.CaldavSynchronizer
import org.tasks.data.OpenTaskDao
import org.tasks.data.dao.CaldavDao
import org.tasks.data.entity.CaldavAccount.Companion.TYPE_CALDAV
import org.tasks.extensions.Context.hasNetworkConnectivity
import org.tasks.injection.BaseWorker
import org.tasks.opentasks.OpenTasksSynchronizer
import org.tasks.preferences.Preferences
import org.tasks.preferences.TasksPreferences
import org.tasks.sync.SyncSource
import org.tasks.time.DateTimeUtils2.currentTimeMillis
import timber.log.Timber

@HiltWorker
class SyncWork @AssistedInject constructor(
    @Assisted context: Context,
    @Assisted workerParams: WorkerParameters,
    private val refreshBroadcaster: RefreshBroadcaster,
    private val preferences: Preferences,
    private val caldavDao: CaldavDao,
    private val caldavSynchronizer: Lazy<CaldavSynchronizer>,
    private val openTasksSynchronizer: Lazy<OpenTasksSynchronizer>,
    private val openTaskDao: OpenTaskDao,
    private val tasksPreferences: TasksPreferences,
) : BaseWorker(context, workerParams) {

    override suspend fun run(): Result {
        Timber.d("Starting...")

        if (source == SyncSource.BACKGROUND) {
            ContextCompat.getSystemService(context, ConnectivityManager::class.java)?.apply {
                if (restrictBackgroundStatus == ConnectivityManagerCompat.RESTRICT_BACKGROUND_STATUS_ENABLED) {
                    Timber.w("Background restrictions enabled, skipping sync")
                    return Result.failure()
                }
            }
        }

        val alreadySyncing = tasksPreferences.getAndSet(TasksPreferences.syncOngoing, true) ?: false
        if (alreadySyncing) {
            Timber.d("Sync ongoing, source=$source")
            setSyncSource(getSyncSource().upgrade(source))
            refreshBroadcaster.broadcastRefresh()
            return Result.retry()
        }
        setSyncSource(source)
        Timber.d("Sync started, source=$source")
        refreshBroadcaster.broadcastRefresh()
        try {
            doSync()
            preferences.lastSync = currentTimeMillis()
        } catch (e: Exception) {
            Timber.e(e)
        } finally {
            tasksPreferences.set(TasksPreferences.syncOngoing, false)
            setSyncSource(SyncSource.NONE)
            refreshBroadcaster.broadcastRefresh()
        }
        return Result.success()
    }

    private val source: SyncSource
        get() = SyncSource.fromString(inputData.getString(EXTRA_SOURCE))

    private suspend fun getSyncSource(): SyncSource =
        SyncSource.fromString(tasksPreferences.get(TasksPreferences.syncSource, SyncSource.NONE.name))

    private suspend fun setSyncSource(source: SyncSource) =
        tasksPreferences.set(TasksPreferences.syncSource, source.name)

    private suspend fun doSync() {
        if (context.hasNetworkConnectivity()) {
            caldavJobs().awaitAll()
        }
        if (openTaskDao.shouldSync()) {
            openTasksSynchronizer.get().sync()

            if (source == SyncSource.USER_INITIATED) {
                AccountManager
                    .get(context)
                    .accounts
                    .filter { OpenTaskDao.SUPPORTED_TYPES.contains(it.type) }
                    .forEach {
                        Timber.d("Requesting sync for $it")
                        ContentResolver.requestSync(
                            it,
                            openTaskDao.authority,
                            Bundle().apply {
                                putBoolean(ContentResolver.SYNC_EXTRAS_MANUAL, true)
                                putBoolean(ContentResolver.SYNC_EXTRAS_EXPEDITED, true)
                            }
                        )
                    }
            }
        }
    }

    private suspend fun caldavJobs(): List<Deferred<Unit>> = coroutineScope {
        getCaldavAccounts().map {
            async(Dispatchers.IO) {
                Thread.currentThread().contextClassLoader = context.classLoader
                if (it.accountType == TYPE_CALDAV) {
                    caldavSynchronizer.get().sync(it)
                }
            }
        }
    }

    private suspend fun getCaldavAccounts() =
            caldavDao.getAccounts(TYPE_CALDAV)

    companion object {
        const val EXTRA_SOURCE = "extra_source"
    }
}
