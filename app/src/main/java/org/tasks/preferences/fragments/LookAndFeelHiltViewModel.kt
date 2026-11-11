package org.tasks.preferences.fragments

import android.content.ComponentName
import android.content.Context
import android.content.pm.PackageManager
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.setValue
import com.google.android.material.color.DynamicColors
import dagger.hilt.android.lifecycle.HiltViewModel
import dagger.hilt.android.qualifiers.ApplicationContext
import kotlinx.coroutines.CoroutineScope
import org.tasks.PlatformConfiguration
import org.tasks.R
import org.tasks.broadcast.RefreshBroadcaster
import org.tasks.filters.FilterPreferenceCodec
import org.tasks.injection.ApplicationScope
import org.tasks.preferences.Preferences
import org.tasks.themes.ThemeColor
import org.tasks.themes.ThemeColor.getLauncherColor
import org.tasks.viewmodel.LookAndFeelViewModel
import java.util.Locale
import javax.inject.Inject

@HiltViewModel
class LookAndFeelHiltViewModel @Inject constructor(
    @ApplicationContext private val context: Context,
    private val preferences: Preferences,
    private val locale: Locale,
    platformConfiguration: PlatformConfiguration,
    refreshBroadcaster: RefreshBroadcaster,
    @ApplicationScope persistenceScope: CoroutineScope,
    filterCodec: FilterPreferenceCodec,
) : LookAndFeelViewModel(
    appPreferences = preferences,
    platformConfiguration = platformConfiguration,
    refreshBroadcaster = refreshBroadcaster,
    persistenceScope = persistenceScope,
    filterCodec = filterCodec,
) {
    private var currentThemeBaseIndex by mutableIntStateOf(preferences.themeBase)

    private var currentThemeColor by mutableIntStateOf(preferences.defaultThemeColor)

    private var currentLauncherColor by mutableIntStateOf(launcherPickerColor())

    override val themeIndex: Int get() = currentThemeBaseIndex

    override val themeColor: Int get() = currentThemeColor

    override val launcherColor: Int get() = currentLauncherColor

    override val dynamicColorAvailable: Boolean get() = DynamicColors.isDynamicColorAvailable()

    override val dynamicColorEnabled: Boolean
        get() = dynamicColorAvailable && settings.dynamicColor

    override val localeName: String get() = locale.getDisplayName(locale)

    fun refreshState(themeBaseIndex: Int, themeColorPickerColor: Int) {
        currentThemeBaseIndex = themeBaseIndex
        currentThemeColor = themeColorPickerColor
        currentLauncherColor = launcherPickerColor()
        refreshState()
    }

    override fun setTheme(index: Int) {
        preferences.setInt(R.string.p_theme, index)
        refreshState()
    }

    override fun setThemeColor(color: Int) {
        preferences.setInt(R.string.p_theme_color, color)
        refreshState()
    }

    fun updateDynamicColor(enabled: Boolean) = setDynamicColor(enabled)

    fun setBaseTheme(index: Int): Boolean {
        setTheme(index)
        return currentThemeBaseIndex != index
    }


    fun handleColorPickerResult(selectedColor: Int): Boolean {
        if (preferences.defaultThemeColor == selectedColor) {
            return false
        }
        setThemeColor(selectedColor)
        return true
    }

    fun handleLauncherPickerResult(context: Context, selectedIndex: Int) {
        setLauncherIcon(context, selectedIndex)
        preferences.setInt(R.string.p_theme_launcher, selectedIndex)
        currentLauncherColor = getLauncherColor(context, selectedIndex).pickerColor
    }

    private fun launcherPickerColor() =
        getLauncherColor(context, preferences.getInt(R.string.p_theme_launcher, DEFAULT_LAUNCHER))
            .pickerColor

    private fun setLauncherIcon(context: Context, index: Int) {
        val packageManager = context.packageManager
        for (i in ThemeColor.LAUNCHERS.indices) {
            val componentName = ComponentName(
                context,
                "com.todoroo.astrid.activity.TaskListActivity" + ThemeColor.LAUNCHERS[i]
            )
            packageManager.setComponentEnabledSetting(
                componentName,
                if (index == i) PackageManager.COMPONENT_ENABLED_STATE_ENABLED else PackageManager.COMPONENT_ENABLED_STATE_DISABLED,
                PackageManager.DONT_KILL_APP
            )
        }
    }

    companion object {
        private const val DEFAULT_LAUNCHER = 7
    }
}
