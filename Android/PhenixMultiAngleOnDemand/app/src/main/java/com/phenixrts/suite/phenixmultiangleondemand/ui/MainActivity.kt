/*
 * Copyright 2022 Phenix Real Time Solutions, Inc. Confidential and Proprietary. All rights reserved.
 */

package com.phenixrts.suite.phenixmultiangleondemand.ui

import android.content.res.Configuration
import android.os.Bundle
import android.widget.ArrayAdapter
import androidx.fragment.app.FragmentActivity
import androidx.recyclerview.widget.GridLayoutManager
import com.phenixrts.suite.phenixcore.PhenixCore
import com.phenixrts.suite.phenixcore.common.launchUI
import com.phenixrts.suite.phenixcore.repositories.models.PhenixStreamState
import com.phenixrts.suite.phenixcore.repositories.models.PhenixTimeShiftState
import com.phenixrts.suite.phenixmultiangleondemand.MultiAngleOnDemandApp
import com.phenixrts.suite.phenixmultiangleondemand.R
import com.phenixrts.suite.phenixmultiangleondemand.cache.PreferenceProvider
import com.phenixrts.suite.phenixmultiangleondemand.common.*
import com.phenixrts.suite.phenixmultiangleondemand.databinding.ActivityMainBinding
import com.phenixrts.suite.phenixmultiangleondemand.ui.adapters.StreamAdapter
import com.phenixrts.suite.phenixmultiangleondemand.ui.viewmodels.StreamViewModel
import kotlinx.coroutines.flow.distinctUntilChanged
import timber.log.Timber
import java.util.*
import javax.inject.Inject

const val SPAN_COUNT_PORTRAIT = 2
const val SPAN_COUNT_LANDSCAPE = 1

class MainActivity : FragmentActivity() {

    @Inject lateinit var phenixCore: PhenixCore
    @Inject lateinit var preferences: PreferenceProvider

    private lateinit var binding: ActivityMainBinding
    private val viewModel: StreamViewModel by lazyViewModel({ application as MultiAngleOnDemandApp }, {
        StreamViewModel(phenixCore)
    })

    private val streamAdapter: StreamAdapter by lazy {
        StreamAdapter(phenixCore) { stream ->
            Timber.d("Stream clicked: $stream")
            viewModel.selectStream(stream)
        }
    }

    private val actAdapter by lazy {
        ArrayAdapter(this, R.layout.row_spinner_selector, arrayListOf<String>())
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        MultiAngleOnDemandApp.component.inject(this)
        binding = ActivityMainBinding.inflate(layoutInflater)
        setContentView(binding.root)
        initViews()
    }

    private fun initViews() = with(binding) {
        val rotation = resources.configuration.orientation
        val spanCount = if (rotation == Configuration.ORIENTATION_PORTRAIT) SPAN_COUNT_PORTRAIT else SPAN_COUNT_LANDSCAPE
        mainStreamList.layoutManager = GridLayoutManager(this@MainActivity, spanCount)
        mainStreamList.setHasFixedSize(true)
        mainStreamList.itemAnimator?.changeDuration = 0
        mainStreamList.adapter = streamAdapter

        actAdapter.setDropDownViewResource(R.layout.row_spinner_item)
        spinnerActs.adapter = actAdapter
        spinnerActs.onSelectionChanged { index ->
            Timber.d("Act selected: $index")
            viewModel.selectAct(index)
        }

        playActButton.setOnClickListener {
            Timber.d("Play act button clicked")
            viewModel.seekToAct(spinnerActs.selectedItemPosition)
        }

        pauseButtonIcon.setImageResource(if (viewModel.isTimeShiftPaused) R.drawable.ic_play else R.drawable.ic_pause)
        pauseButton.setOnClickListener {
            Timber.d("Pause clicked")
            if (viewModel.isTimeShiftPaused) {
                viewModel.playTimeShift()
                pauseButtonIcon.setImageResource(R.drawable.ic_pause)
            } else {
                viewModel.pauseReplay()
                pauseButtonIcon.setImageResource(R.drawable.ic_play)
            }
        }

        launchUI {
            viewModel.acts.collect { acts ->
                Timber.d("Act list updated: $acts")
                actAdapter.clear()
                actAdapter.addAll(acts.map { it.title })

                preferences.selectedAct.let { act ->
                    acts.indexOf(act).takeIf { it >= 0 && spinnerActs.selectedItemPosition != it }?.let { index ->
                        spinnerActs.setSelection(index)
                    }
                } ?: run {
                    acts.getOrNull(0)?.let { act ->
                        preferences.selectedAct = act
                    }
                }
            }
        }
        launchUI {
            viewModel.streams.distinctUntilChanged().collect { streams ->
                streamAdapter.data = streams
                streamEndedOverlay.setVisibleOr(streams.find { it.isSelected }?.streamState != PhenixStreamState.STREAMING)
                viewModel.renderActiveStream(mainStreamSurface)
            }
        }
        launchUI {
            viewModel.onStreamsJoined.collect { isSubscribed ->
                Timber.d("On all streams ready: $isSubscribed")
                if (isSubscribed) {
                    viewModel.renderActiveStream(mainStreamSurface)
                }
            }
        }
        launchUI {
            viewModel.onHeadTimeChanged.collect { head ->
                streamTimestampOverlay.text = Date(head).toDateString()
            }
        }
        launchUI {
            viewModel.onTimeShiftStateChanged.collect { state ->
                Timber.d("Time shift state changed: $state")
                when (state) {
                    PhenixTimeShiftState.READY,
                    PhenixTimeShiftState.REPLAYING,
                    PhenixTimeShiftState.PAUSED -> {
                        mainStreamLoading.setVisibleOr(false)
                        playActHolder.setVisibleOr(true)
                        playActButton.isEnabled = true
                        pauseButton.isEnabled = true
                        if (!viewModel.isTimeShiftPaused) viewModel.playTimeShift()
                    }
                    PhenixTimeShiftState.IDLE -> {
                        viewModel.pauseReplay()
                    }
                    else -> { /* Ignored */ }
                }
            }
        }
        launchUI {
            viewModel.onButtonsClickable.collect { isClickable ->
                playActButton.isEnabled = isClickable
                pauseButton.isEnabled = isClickable
            }
        }
        playActButton.isEnabled = false
        pauseButton.isEnabled = false
        viewModel.joinStreams()
        Timber.d("Initializing Main Activity: $rotation")
    }
}
