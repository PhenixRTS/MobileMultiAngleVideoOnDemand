/*
 * Copyright 2020 Phenix Real Time Solutions, Inc. Confidential and Proprietary. All rights reserved.
 */

package com.phenixrts.suite.phenixmultiangleondemand.ui

import android.content.Intent
import android.os.Bundle
import android.os.Handler
import android.os.Looper
import androidx.appcompat.app.AppCompatActivity
import com.phenixrts.suite.phenixmultiangleondemand.BuildConfig
import com.phenixrts.suite.phenixmultiangleondemand.MultiAngleOnDemandApp
import com.phenixrts.suite.phenixmultiangleondemand.R
import com.phenixrts.suite.phenixmultiangleondemand.cache.PreferenceProvider
import com.phenixrts.suite.phenixmultiangleondemand.common.*
import com.phenixrts.suite.phenixmultiangleondemand.common.enums.ExpressError
import com.phenixrts.suite.phenixmultiangleondemand.databinding.ActivitySplashBinding
import com.phenixrts.suite.phenixmultiangleondemand.repository.PCastExpressRepository
import timber.log.Timber
import javax.inject.Inject

private const val TIMEOUT_DELAY = 10000L

class SplashActivity : AppCompatActivity() {

    @Inject
    lateinit var pCastExpressRepository: PCastExpressRepository
    @Inject
    lateinit var preferenceProvider: PreferenceProvider

    private lateinit var binding: ActivitySplashBinding
    private val timeoutHandler = Handler(Looper.getMainLooper())
    private val timeoutRunnable = Runnable {
        launchMain {
            binding.root.showSnackBar(getString(R.string.err_network_problems))
        }
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        MultiAngleOnDemandApp.component.inject(this)
        binding = ActivitySplashBinding.inflate(layoutInflater)
        setContentView(binding.root)
        pCastExpressRepository.onChannelExpressError.observe(this, { error ->
            Timber.d("Room express failed")
            showErrorDialog(error)
        })
        checkDeepLink(intent)
    }

    override fun onNewIntent(intent: Intent?) {
        super.onNewIntent(intent)
        Timber.d("On new intent $intent")
        checkDeepLink(intent)
    }

    private fun checkDeepLink(intent: Intent?) {
        launchMain {
            Timber.d("Checking deep link: ${intent?.data}")
            var configuration: ExpressConfiguration? = null
            preferenceProvider.getConfiguration()?.let { savedConfiguration ->
                Timber.d("Loading saved configuration: $savedConfiguration")
                configuration = savedConfiguration
                reloadConfiguration(savedConfiguration)
            } ?: run {
                val streamIDs = (intent?.data?.getQueryParameter(QUERY_STREAM_IDS) ?: BuildConfig.STREAM_IDS).split(",")
                val acts = (intent?.data?.getQueryParameter(QUERY_ACTS) ?: BuildConfig.ACTS).split(",")
                val edgeAuth = intent?.data?.getQueryParameter(QUERY_EDGE_AUTH)
                val uri = intent?.data?.getQueryParameter(QUERY_URI) ?: BuildConfig.PCAST_URL
                val backend = intent?.data?.getQueryParameter(QUERY_BACKEND) ?: BuildConfig.BACKEND_URL
                ExpressConfiguration(uri, backend, edgeAuth, streamIDs, acts).let { createdConfiguration ->
                    Timber.d("Checking deep link: $streamIDs $createdConfiguration")
                    configuration = createdConfiguration
                    if (pCastExpressRepository.isPCastInitialized()) {
                        Timber.d("New configuration detected")
                        preferenceProvider.saveConfiguration(createdConfiguration)
                        showErrorDialog(ExpressError.CONFIGURATION_CHANGED_ERROR)
                        return@launchMain
                    }
                    reloadConfiguration(createdConfiguration)
                }
            }
            preferenceProvider.saveConfiguration(null)
            showLandingScreen(configuration)
        }
    }

    private suspend fun reloadConfiguration(configuration: ExpressConfiguration) {
        timeoutHandler.postDelayed(timeoutRunnable, TIMEOUT_DELAY)
        pCastExpressRepository.setupChannelExpress(configuration)
    }

    private fun showLandingScreen(configuration: ExpressConfiguration?) = launchMain {
        if (configuration == null) {
            showErrorDialog(ExpressError.DEEP_LINK_ERROR)
            return@launchMain
        }
        Timber.d("Waiting for pCast")
        pCastExpressRepository.waitForPCast()
        timeoutHandler.removeCallbacks(timeoutRunnable)
        Timber.d("Navigating to Landing Screen")
        startActivity(Intent(this@SplashActivity, MainActivity::class.java))
        finish()
    }
}
