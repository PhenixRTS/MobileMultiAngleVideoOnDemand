/*
 * Copyright 2020 Phenix Real Time Solutions, Inc. Confidential and Proprietary. All rights reserved.
 */

package com.phenixrts.suite.phenixmultiangleondemand.common

import com.phenixrts.express.*
import com.phenixrts.suite.phenixmultiangleondemand.BuildConfig
import java.io.Serializable

// The delay before starting to draw bitmaps on surface
const val THUMBNAIL_DRAW_DELAY = 100L

const val QUERY_URI = "uri"
const val QUERY_BACKEND = "backend"
const val QUERY_EDGE_AUTH = "edgeauth"
const val QUERY_STREAM_IDS = "streamIDs"
const val QUERY_ACTS = "acts"

data class ExpressConfiguration(
    val uri: String = BuildConfig.PCAST_URL,
    val backend: String = BuildConfig.BACKEND_URL,
    val edgeAuth: String? = null,
    val streamIDs: List<String> = listOf(),
    val acts: List<String> = listOf()
) : Serializable

fun getStreamOptions(streamId: String): SubscribeOptions =
    PCastExpressFactory.createSubscribeOptionsBuilder()
        .withCapabilities(arrayOf("on-demand"))
        .withStreamId(streamId)
        .buildSubscribeOptions()
