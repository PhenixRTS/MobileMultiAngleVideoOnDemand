/*
 * Copyright 2021 Phenix Real Time Solutions, Inc. Confidential and Proprietary. All rights reserved.
 */

package com.phenixrts.suite.phenixmultiangleondemand.models

import kotlinx.serialization.Serializable
import kotlinx.serialization.decodeFromString
import kotlinx.serialization.encodeToString
import kotlinx.serialization.json.Json

@Serializable
data class Act(val title: String, val offsetFromBeginning: Long)

fun String.asAct(): Act? = try {
    Json{ ignoreUnknownKeys = true }.decodeFromString(this)
} catch (e: Exception) {
    null
}

fun Act.toJson(): String = Json.encodeToString(this)
