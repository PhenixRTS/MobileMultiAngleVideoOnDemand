/*
 * Copyright 2022 Phenix Real Time Solutions, Inc. Confidential and Proprietary. All rights reserved.
 */

package com.phenixrts.suite.phenixmultiangleondemand.injection

import com.phenixrts.suite.phenixmultiangleondemand.ui.MainActivity
import com.phenixrts.suite.phenixmultiangleondemand.ui.SplashActivity
import dagger.Component
import javax.inject.Singleton

@Singleton
@Component(modules = [InjectionModule::class])
interface InjectionComponent {
    fun inject(target: SplashActivity)
    fun inject(target: MainActivity)
}
