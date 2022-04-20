/*
 * Copyright 2022 Phenix Real Time Solutions, Inc. Confidential and Proprietary. All rights reserved.
 */

package com.phenixrts.suite.phenixmultiangleondemand.cache

import android.content.Context
import com.phenixrts.suite.phenixmultiangleondemand.MultiAngleOnDemandApp
import com.phenixrts.suite.phenixmultiangleondemand.models.Act
import com.phenixrts.suite.phenixmultiangleondemand.models.asAct
import com.phenixrts.suite.phenixmultiangleondemand.models.toJson
import kotlin.properties.ReadWriteProperty
import kotlin.reflect.KProperty

private const val APP_PREFERENCES = "app_preferences"

class PreferenceProvider(private val context: MultiAngleOnDemandApp) {

    var selectedAct by actPreference()

    private val sharedPreferences by lazy { context.getSharedPreferences(APP_PREFERENCES, Context.MODE_PRIVATE) }

    private fun actPreference() = object : ReadWriteProperty<Any?, Act?> {
        override fun getValue(thisRef: Any?, property: KProperty<*>) =
            sharedPreferences.getString(property.name, null)?.asAct()

        override fun setValue(thisRef: Any?, property: KProperty<*>, value: Act?) {
            sharedPreferences.edit().putString(property.name, value?.toJson()).apply()
        }
    }

}
