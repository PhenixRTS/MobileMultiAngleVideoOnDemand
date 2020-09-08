/*
 * Copyright 2020 Phenix Real Time Solutions, Inc. Confidential and Proprietary. All rights reserved.
 */

package com.phenixrts.suite.phenixmultiangleondemand

import android.app.Application
import androidx.lifecycle.ViewModelStore
import androidx.lifecycle.ViewModelStoreOwner
import com.phenixrts.suite.phenixmultiangleondemand.common.LineNumberDebugTree
import com.phenixrts.suite.phenixmultiangleondemand.injection.DaggerInjectionComponent
import com.phenixrts.suite.phenixmultiangleondemand.injection.InjectionComponent
import com.phenixrts.suite.phenixmultiangleondemand.injection.InjectionModule
import timber.log.Timber

class MultiAngleOnDemandApp : Application(), ViewModelStoreOwner {

    private val appViewModelStore: ViewModelStore by lazy {
        ViewModelStore()
    }

    override fun onCreate() {
        super.onCreate()

        if (BuildConfig.DEBUG) {
            Timber.plant(LineNumberDebugTree("MultiAngleOnDemandApp"))
        }

        component = DaggerInjectionComponent.builder().injectionModule(InjectionModule(this)).build()
    }

    override fun getViewModelStore() = appViewModelStore

    companion object {
        lateinit var component: InjectionComponent
            private set
    }
}
