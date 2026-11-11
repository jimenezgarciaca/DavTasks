package org.tasks.preferences

import android.content.ContentResolver
import android.content.Context
import android.net.Uri
import androidx.documentfile.provider.DocumentFile
import androidx.lifecycle.MutableLiveData
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import dagger.hilt.android.lifecycle.HiltViewModel
import dagger.hilt.android.qualifiers.ApplicationContext
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import org.tasks.R
import org.tasks.backup.BackupConstants
import org.tasks.data.dao.CaldavDao
import org.tasks.data.entity.CaldavAccount
import org.tasks.date.DateTimeUtils.newDateTime
import java.io.File
import javax.inject.Inject

@HiltViewModel
class PreferencesViewModel @Inject constructor(
    @ApplicationContext private val context: Context,
    private val preferences: Preferences,
    private val caldavDao: CaldavDao,
) : ViewModel() {
    val lastBackup = MutableLiveData<Long?>()
    val lastAndroidBackup = MutableLiveData<Long>()
    val caldavAccounts: Flow<List<CaldavAccount>>
        get() = caldavDao.watchAccounts()

    private fun isStale(timestamp: Long?) =
            timestamp != null
                    && preferences.showBackupWarnings()
                    && timestamp < newDateTime().startOfDay().minusDays(2).millis

    val staleLocalBackup: Boolean
        get() = isStale(lastBackup.value)

    val staleRemoteBackup: Boolean
        get() = isStale(lastAndroidBackup.value)

    val usingPrivateStorage: Boolean
        get() = preferences.backupDirectory.let { backupDir ->
            val backupDirStr = backupDir?.toString() ?: return true
            context
                .getExternalFilesDir(null)
                ?.let {
                    if (backupDirStr.startsWith(Uri.fromFile(it).toString())) {
                        return true
                    }
                }
            return backupDirStr.startsWith(Uri.fromFile(context.filesDir).toString())
        }

    fun updateLocalBackup() = viewModelScope.launch {
        val uri = preferences.backupDirectory
        val timestamps: List<Long>? = withContext(Dispatchers.IO) {
            when (uri?.scheme) {
                ContentResolver.SCHEME_CONTENT -> {
                    DocumentFile.fromTreeUri(context, uri)
                            ?.listFiles()
                            ?.filter { BackupConstants.isBackupFile(it.name) }
                            ?.map { BackupConstants.getTimestamp(it) }
                }
                ContentResolver.SCHEME_FILE -> {
                    File(uri.path!!)
                            .listFiles()
                            ?.filter { BackupConstants.isBackupFile(it.name) }
                            ?.map { BackupConstants.getTimestamp(it) }
                }
                else -> emptyList()
            }
        }
        lastBackup.value = timestamps?.maxOrNull() ?: -1L
    }

    private fun updateAndroidBackup() {
        lastAndroidBackup.value = preferences
                .getLong(R.string.p_backups_android_backup_last, -1L)
    }

    fun updateBackups() {
        updateLocalBackup()
        updateAndroidBackup()
    }
}