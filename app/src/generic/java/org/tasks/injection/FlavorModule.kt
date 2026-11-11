package org.tasks.injection

import com.google.android.material.color.DynamicColors
import com.todoroo.andlib.utility.AndroidUtilities
import dagger.Module
import dagger.Provides
import dagger.hilt.InstallIn
import dagger.hilt.components.SingletonComponent
import org.tasks.AppStore
import org.tasks.PlatformConfiguration
import org.tasks.location.Geocoder
import org.tasks.location.GeocoderNominatim
import org.tasks.location.LocationService
import org.tasks.location.LocationServiceAndroid
import org.tasks.location.MapFragment
import org.tasks.location.OsmMapFragment

@Module
@InstallIn(SingletonComponent::class)
class FlavorModule {
    @Provides
    fun getPlatformConfiguration() = PlatformConfiguration(
        supportsGeofences = true,
        supportsCalendarEvents = true,
        appStore = AppStore.FDROID,
        supportsWidgets = true,
        supportsNotificationTroubleshooting = true,
        supportsSystemNotificationSettings = true,
        supportsOngoingNotifications = AndroidUtilities.preUpsideDownCake(),
        supportsBundledNotifications = true,
        supportsVoiceReminders = true,
        supportsCompletionSound = true,
        supportsSwipeToSnooze = true,
        supportsRingMode = true,
        supportsDynamicColor = DynamicColors.isDynamicColorAvailable(),
        supportsLauncherIcon = true,
        supportsMarkdownToggle = true,
        supportsWallpaperTheme = true,
        supportsAutoNightTheme = true,
        supportsLanguageSelection = true,
    )

    @Provides
    fun getLocationService(service: LocationServiceAndroid): LocationService = service

    @Provides
    fun getMapFragment(osm: OsmMapFragment): MapFragment = osm

    @Provides
    fun getGeocoder(nominatim: GeocoderNominatim): Geocoder = nominatim
}
