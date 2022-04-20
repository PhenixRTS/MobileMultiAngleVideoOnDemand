/*
 * Copyright 2022 Phenix Real Time Solutions, Inc. Confidential and Proprietary. All rights reserved.
 */

package com.phenixrts.suite.phenixmultiangleondemand.injection

import com.phenixrts.suite.phenixcore.PhenixCore
import com.phenixrts.suite.phenixmultiangleondemand.MultiAngleOnDemandApp
import com.phenixrts.suite.phenixmultiangleondemand.cache.PreferenceProvider
import dagger.Module
import dagger.Provides
import javax.inject.Singleton

@Module
class InjectionModule(private val context: MultiAngleOnDemandApp) {

    @Singleton
    @Provides
    fun providePhenixCore() = PhenixCore(context)

    @Provides
    @Singleton
    fun providePreferencesProvider() = PreferenceProvider(context)
}
