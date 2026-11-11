package org.tasks

data class PlatformConfiguration(
    val versionCode: Int = 0,

    val supportsGeofences: Boolean = false,
    val supportsCalendarEvents: Boolean = false,
    val appStore: AppStore = AppStore.NONE,
    val supportsWidgets: Boolean = false,

    val showNotificationsEnabledSwitch: Boolean = false,

    val supportsSystemNotificationSettings: Boolean = false,
    val supportsOngoingNotifications: Boolean = false,
    val supportsBundledNotifications: Boolean = false,
    val supportsVoiceReminders: Boolean = false,
    val supportsCompletionSound: Boolean = false,
    val supportsSwipeToSnooze: Boolean = false,
    val supportsRingMode: Boolean = false,

    val supportsDynamicColor: Boolean = false,
    val supportsLauncherIcon: Boolean = false,
    val supportsMarkdownToggle: Boolean = false,
    val supportsWallpaperTheme: Boolean = false,
    val supportsAutoNightTheme: Boolean = false,
    val supportsLanguageSelection: Boolean = false,
    val localeChangeRequiresRestart: Boolean = false,
)
