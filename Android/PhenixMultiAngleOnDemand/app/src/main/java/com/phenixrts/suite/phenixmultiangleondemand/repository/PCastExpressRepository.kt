/*
 * Copyright 2021 Phenix Real Time Solutions, Inc. Confidential and Proprietary. All rights reserved.
 */

package com.phenixrts.suite.phenixmultiangleondemand.repository

import androidx.lifecycle.MutableLiveData
import com.phenixrts.common.RequestStatus
import com.phenixrts.environment.android.AndroidContext
import com.phenixrts.express.*
import com.phenixrts.suite.phenixmultiangleondemand.BuildConfig
import com.phenixrts.suite.phenixmultiangleondemand.MultiAngleOnDemandApp
import com.phenixrts.suite.phenixmultiangleondemand.common.*
import com.phenixrts.suite.phenixmultiangleondemand.common.enums.ExpressError
import com.phenixrts.suite.phenixmultiangleondemand.models.Act
import com.phenixrts.suite.phenixmultiangleondemand.models.Stream
import kotlinx.coroutines.delay
import timber.log.Timber
import kotlin.coroutines.resume
import kotlin.coroutines.suspendCoroutine

private const val REINITIALIZATION_DELAY = 1000L

class PCastExpressRepository(private val context: MultiAngleOnDemandApp) {

    private var expressConfiguration: ExpressConfiguration = ExpressConfiguration()
    private var pCastExpress: PCastExpress? = null
    val streams = MutableLiveData<List<Stream>>()
    val acts = MutableLiveData<List<Act>>()

    val onChannelExpressError = MutableLiveData<ExpressError>()

    private fun hasConfigurationChanged(configuration: ExpressConfiguration): Boolean = expressConfiguration != configuration

    private fun initializePCast() {
        Timber.d("Creating Channel Express with configuration: $expressConfiguration")
        AndroidContext.setContext(context)
        var pcastBuilder = PCastExpressFactory.createPCastExpressOptionsBuilder()
            .withMinimumConsoleLogLevel(BuildConfig.SDK_DEBUG_LEVEL)
            .withBackendUri(expressConfiguration.backend)
            .withPCastUri(expressConfiguration.uri)
            .withUnrecoverableErrorCallback { status: RequestStatus, description: String ->
                launchMain {
                    Timber.e("Unrecoverable error in PhenixSDK. Error status: [$status]. Description: [$description]")
                    onChannelExpressError.value = ExpressError.UNRECOVERABLE_ERROR
                }
            }
        if (expressConfiguration.edgeAuth != null) {
            pcastBuilder = pcastBuilder.withAuthenticationToken(expressConfiguration.edgeAuth)
        }

        PCastExpressFactory.createPCastExpress(pcastBuilder.buildPCastExpressOptions())?.let { express ->
            pCastExpress = express
            streams.value = expressConfiguration.streamIDs.map { Stream(express, it) }.apply {
                firstOrNull()?.isMainRendered?.value = true
            }
            acts.value = expressConfiguration.acts.map { Act(it, it.toMillis()) }
            Timber.d("Channel express initialized")
        } ?: run {
            Timber.e("Unrecoverable error in PhenixSDK")
            onChannelExpressError.value = ExpressError.UNRECOVERABLE_ERROR
        }
    }

    suspend fun setupChannelExpress(configuration: ExpressConfiguration) {
        if (hasConfigurationChanged(configuration)) {
            Timber.d("Room Express configuration has changed: $configuration")
            expressConfiguration = configuration
            pCastExpress?.dispose()
            pCastExpress = null
            Timber.d("Room Express disposed")
            delay(REINITIALIZATION_DELAY)
            initializePCast()
        }
    }

    suspend fun waitForPCast(): Unit = suspendCoroutine { continuation ->
        launchMain {
            Timber.d("Waiting for pCast")
            if (pCastExpress == null) {
                initializePCast()
            }
            pCastExpress?.waitForOnline()
            continuation.resume(Unit)
        }
    }

    fun isPCastInitialized(): Boolean = pCastExpress != null

}
