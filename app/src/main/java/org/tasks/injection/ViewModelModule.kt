package org.tasks.injection

import dagger.Lazy
import dagger.Module
import dagger.Provides
import dagger.hilt.InstallIn
import dagger.hilt.android.components.ViewModelComponent
import dagger.hilt.android.scopes.ViewModelScoped
import org.tasks.billing.Inventory
import org.tasks.location.PlaceSearch
import org.tasks.location.PlaceSearchMapbox

@Module
@InstallIn(ViewModelComponent::class)
class ViewModelModule {
    @Provides
    @ViewModelScoped
    fun getPlaceSearchProvider(
            mapbox: Lazy<PlaceSearchMapbox>
    ): PlaceSearch = mapbox.get()
}