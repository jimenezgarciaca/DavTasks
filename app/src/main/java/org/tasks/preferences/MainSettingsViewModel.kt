package org.tasks.preferences

import android.content.Context
import androidx.core.content.pm.ShortcutManagerCompat
import androidx.lifecycle.viewModelScope
import dagger.hilt.android.lifecycle.HiltViewModel
import dagger.hilt.android.qualifiers.ApplicationContext
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.stateIn
import org.tasks.PlatformConfiguration
import org.tasks.data.dao.CaldavDao
import org.tasks.data.entity.CaldavAccount
import org.tasks.widget.AppWidgetManager
import javax.inject.Inject
import android.appwidget.AppWidgetManager as SystemAppWidgetManager

@HiltViewModel
class MainSettingsViewModel @Inject constructor(
    @param:ApplicationContext private val context: Context,
    private val appWidgetManager: AppWidgetManager,
    caldavDao: CaldavDao,
    platformConfiguration: PlatformConfiguration,
) : org.tasks.viewmodel.MainSettingsViewModel(
    platformConfiguration = platformConfiguration,
) {

    val accounts: StateFlow<List<CaldavAccount>> = caldavDao.watchAccounts()
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), emptyList())

    override val supportsWidgets: Boolean
        get() = appWidgetManager.widgetIds.isNotEmpty()
                || ShortcutManagerCompat.isRequestPinShortcutSupported(context)
                || context.getSystemService(SystemAppWidgetManager::class.java)
                    .isRequestPinAppWidgetSupported
}
