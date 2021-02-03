/*
 * Copyright 2021 Phenix Real Time Solutions, Inc. Confidential and Proprietary. All rights reserved.
 */

package com.phenixrts.suite.phenixmultiangleondemand.cache

import android.content.Context
import com.phenixrts.suite.phenixmultiangleondemand.MultiAngleOnDemandApp
import com.phenixrts.suite.phenixmultiangleondemand.common.ExpressConfiguration
import com.phenixrts.suite.phenixmultiangleondemand.common.asExpressConfiguration
import com.phenixrts.suite.phenixmultiangleondemand.common.toJson
import com.phenixrts.suite.phenixmultiangleondemand.models.Act
import com.phenixrts.suite.phenixmultiangleondemand.models.asAct
import com.phenixrts.suite.phenixmultiangleondemand.models.toJson

private const val APP_PREFERENCES = "app_preferences"
private const val STREAM_ACT = "stream_act"
private const val CONFIGURATION = "configuration"

class PreferenceProvider(private val context: MultiAngleOnDemandApp) {

    fun saveConfiguration(configuration: ExpressConfiguration?) {
        context.getSharedPreferences(APP_PREFERENCES, Context.MODE_PRIVATE).edit()
            .putString(CONFIGURATION, configuration?.toJson())
            .apply()
    }

    fun getConfiguration(): ExpressConfiguration? =
        context.getSharedPreferences(APP_PREFERENCES, Context.MODE_PRIVATE).getString(CONFIGURATION, null).let { cache ->
            cache?.asExpressConfiguration()
        }

    fun setAct(act: Act) {
        context.getSharedPreferences(APP_PREFERENCES, Context.MODE_PRIVATE).edit()
            .putString(STREAM_ACT, act.toJson())
            .apply()
    }

    fun getAct(): Act? =
        context.getSharedPreferences(APP_PREFERENCES, Context.MODE_PRIVATE).getString(STREAM_ACT, null).let { cache ->
            cache?.asAct()
        }

}
