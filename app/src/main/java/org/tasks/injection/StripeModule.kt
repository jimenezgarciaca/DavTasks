package org.tasks.injection

import android.content.Context
import android.content.Intent
import android.net.Uri
import dagger.Lazy
import dagger.Module
import dagger.Provides
import dagger.hilt.InstallIn
import dagger.hilt.android.qualifiers.ApplicationContext
import dagger.hilt.components.SingletonComponent
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.runBlocking
import kotlinx.serialization.json.Json
import org.tasks.analytics.Firebase
import org.tasks.analytics.logCloudOnboarding
import org.tasks.auth.TasksServerEnvironment
import org.tasks.billing.CompositeSubscriptionProvider
import org.tasks.billing.EntitlementSubscriptionProvider
import org.tasks.billing.PurchaseEntitlement
import org.tasks.billing.StoreSubscriptionProvider
import org.tasks.billing.SubscriptionProvider
import org.tasks.billing.debugProFlow
import org.tasks.caldav.CaldavClientProvider
import org.tasks.data.dao.CaldavDao
import org.tasks.extensions.bringTasksToForeground
import org.tasks.http.KtorClientFactory
import org.tasks.preferences.InstallId
import org.tasks.preferences.TasksPreferences
import org.tasks.security.KeyStoreEncryption
import org.tasks.sync.SyncAdapters
import javax.inject.Singleton

@Module
@InstallIn(SingletonComponent::class)
class StripeModule {

    @Provides
    @Singleton
    fun providesPurchaseEntitlement(
        @ApplicationContext context: Context,
        encryption: KeyStoreEncryption,
        httpClientFactory: KtorClientFactory,
        environment: TasksServerEnvironment,
        @ApplicationScope scope: CoroutineScope,
        json: Json,
        syncAdapters: Lazy<SyncAdapters>,
    ): PurchaseEntitlement = PurchaseEntitlement(
        dataDir = context.filesDir.absolutePath,
        encryption = encryption,
        httpClientFactory = httpClientFactory,
        serverEnvironment = environment,
        scope = scope,
        json = json,
        syncAdapters = { syncAdapters.get() },
    ).also {
        runBlocking { it.restore() }
        it.initialize()
    }

    @Provides
    @Singleton
    fun provideSubscriptionProvider(
        store: StoreSubscriptionProvider?,
        entitlement: PurchaseEntitlement,
        tasksPreferences: TasksPreferences,
    ): SubscriptionProvider {
        val entitlements = EntitlementSubscriptionProvider(
            entitlement = entitlement,
            debugPro = tasksPreferences.debugProFlow,
            verifyBeforeSync = true,
        )
        return when (store) {
            null -> entitlements
            else -> CompositeSubscriptionProvider(listOf(store, entitlements))
        }
    }

}
