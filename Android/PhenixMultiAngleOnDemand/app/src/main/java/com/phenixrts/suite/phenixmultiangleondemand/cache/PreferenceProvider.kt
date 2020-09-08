/*
 * Copyright 2020 Phenix Real Time Solutions, Inc. Confidential and Proprietary. All rights reserved.
 */

package com.phenixrts.suite.phenixmultiangleondemand.cache

import android.content.Context
import com.google.gson.Gson
import com.phenixrts.suite.phenixmultiangleondemand.MultiAngleOnDemandApp
import com.phenixrts.suite.phenixmultiangleondemand.common.ExpressConfiguration
import com.phenixrts.suite.phenixmultiangleondemand.models.Act
import timber.log.Timber

private const val APP_PREFERENCES = "app_preferences"
private const val STREAM_ACT = "stream_act"
private const val CONFIGURATION = "configuration"

class PreferenceProvider(private val context: MultiAngleOnDemandApp) {

    fun saveConfiguration(configuration: ExpressConfiguration?) {
        context.getSharedPreferences(APP_PREFERENCES, Context.MODE_PRIVATE).edit()
            .putString(CONFIGURATION, Gson().toJson(configuration))
            .apply()
    }

    fun getConfiguration(): ExpressConfiguration? =
        context.getSharedPreferences(APP_PREFERENCES, Context.MODE_PRIVATE).getString(CONFIGURATION, null)?.let { cache ->
            Gson().fromJson(cache, ExpressConfiguration::class.java)
        }

    fun setAct(act: Act) {
        Timber.d("Saving selected act: $act")
        context.getSharedPreferences(APP_PREFERENCES, Context.MODE_PRIVATE).edit()
            .putString(STREAM_ACT, Gson().toJson(act))
            .apply()
    }

    fun getAct(): Act? =
        context.getSharedPreferences(APP_PREFERENCES, Context.MODE_PRIVATE).getString(STREAM_ACT, null)?.let { cache ->
            Gson().fromJson(cache, Act::class.java)
        }

}
