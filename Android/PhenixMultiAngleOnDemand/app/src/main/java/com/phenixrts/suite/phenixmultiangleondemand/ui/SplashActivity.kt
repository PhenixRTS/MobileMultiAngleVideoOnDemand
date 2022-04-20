/*
 * Copyright 2022 Phenix Real Time Solutions, Inc. Confidential and Proprietary. All rights reserved.
 */

package com.phenixrts.suite.phenixmultiangleondemand.ui

import android.annotation.SuppressLint
import android.content.Intent
import android.os.Bundle
import android.os.Handler
import android.os.Looper
import com.phenixrts.suite.phenixcore.PhenixCore
import com.phenixrts.suite.phenixcore.common.launchUI
import com.phenixrts.suite.phenixcore.repositories.models.PhenixError
import com.phenixrts.suite.phenixcore.repositories.models.PhenixEvent
import com.phenixrts.suite.phenixdeeplinks.DeepLinkActivity
import com.phenixrts.suite.phenixdeeplinks.common.init
import com.phenixrts.suite.phenixdeeplinks.models.DeepLinkStatus
import com.phenixrts.suite.phenixdeeplinks.models.PhenixDeepLinkConfiguration
import com.phenixrts.suite.phenixmultiangleondemand.MultiAngleOnDemandApp
import com.phenixrts.suite.phenixmultiangleondemand.R
import com.phenixrts.suite.phenixmultiangleondemand.cache.PreferenceProvider
import com.phenixrts.suite.phenixmultiangleondemand.common.*
import com.phenixrts.suite.phenixmultiangleondemand.common.enums.ExpressError
import com.phenixrts.suite.phenixmultiangleondemand.databinding.ActivitySplashBinding
import timber.log.Timber
import javax.inject.Inject

private const val TIMEOUT_DELAY = 10000L

@SuppressLint("CustomSplashScreen")
class SplashActivity : DeepLinkActivity() {

    @Inject lateinit var phenixCore: PhenixCore
    @Inject lateinit var preferenceProvider: PreferenceProvider

    private lateinit var binding: ActivitySplashBinding

    private val timeoutHandler = Handler(Looper.getMainLooper())
    private val timeoutRunnable by lazy {
        Runnable {
            launchUI {
                binding.root.showSnackBar(getString(R.string.err_network_problems))
            }
        }
    }

    override fun isAlreadyInitialized() = phenixCore.isInitialized

    override val additionalConfiguration = hashMapOf<String, String>()

    override fun onCreate(savedInstanceState: Bundle?) {
        MultiAngleOnDemandApp.component.inject(this)
        binding = ActivitySplashBinding.inflate(layoutInflater)
        setContentView(binding.root)
        launchUI {
            phenixCore.onError.collect { error ->
                if (error == PhenixError.FAILED_TO_INITIALIZE) {
                    Timber.d("Splash: Failed to initialize Phenix Core: $error")
                    showErrorDialog(error.message)
                }
            }
        }
        launchUI {
            phenixCore.onEvent.collect { event ->
                Timber.d("Splash: Phenix core event: $event")
                if (event == PhenixEvent.PHENIX_CORE_INITIALIZED) {
                    showLandingScreen()
                }
            }
        }
        super.onCreate(savedInstanceState)
    }

    override fun onDeepLinkQueried(
        status: DeepLinkStatus,
        configuration: PhenixDeepLinkConfiguration,
        rawConfiguration: Map<String, String>,
        deepLink: String
    ) {
        launchUI {
            Timber.d("Deep link queried: $status")
            when (status) {
                DeepLinkStatus.RELOAD -> showErrorDialog(getErrorMessage(ExpressError.CONFIGURATION_CHANGED_ERROR))
                DeepLinkStatus.READY -> initializePhenixCore(configuration)
            }
        }
    }

    private fun initializePhenixCore(configuration: PhenixDeepLinkConfiguration) {
        Timber.d("Channel viewer started with configuration: $configuration")
        if (configuration.streams.isEmpty()) {
            showErrorDialog(getErrorMessage(ExpressError.DEEP_LINK_ERROR))
            return
        }
        timeoutHandler.postDelayed(timeoutRunnable, TIMEOUT_DELAY)
        phenixCore.init(configuration)
    }

    private fun showLandingScreen() {
        timeoutHandler.removeCallbacks(timeoutRunnable)
        Timber.d("Navigating to Landing Screen")
        startActivity(Intent(this@SplashActivity, MainActivity::class.java))
        finish()
    }
}
