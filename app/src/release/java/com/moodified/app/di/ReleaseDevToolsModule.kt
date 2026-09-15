package com.moodified.app.di

import androidx.navigation.NavController
import androidx.navigation.NavGraphBuilder
import com.moodified.app.core.devtools.DebugNavRegistrar
import com.moodified.app.core.devtools.MockDataSeeder
import dagger.Module
import dagger.Provides
import dagger.hilt.InstallIn
import dagger.hilt.components.SingletonComponent
import javax.inject.Singleton

/**
 * Release-only DI bindings: every debug surface is a no-op so debug code never
 * ships in production APKs / AABs.
 */
@Module
@InstallIn(SingletonComponent::class)
object ReleaseDevToolsModule {
    @Provides
    @Singleton
    fun provideMockDataSeeder(): MockDataSeeder =
        object : MockDataSeeder {
            override val isAvailable: Boolean = false

            override suspend fun seedMoodData() = Unit

            override suspend fun seedActivityData() = Unit
        }

    @Provides
    @Singleton
    fun provideDebugNavRegistrar(): DebugNavRegistrar =
        object : DebugNavRegistrar {
            override val isAvailable: Boolean = false
            override val routes: Set<String> = emptySet()
            override val drawerRoute: String? = null
            override val activityMonitorRoute: String? = null
            override val sleepMonitorRoute: String? = null
            override val interactionMonitorRoute: String? = null

            override fun register(
                graph: NavGraphBuilder,
                navController: NavController,
            ) = Unit
        }
}
