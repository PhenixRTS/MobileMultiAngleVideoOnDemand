/*
 * Copyright 2021 Phenix Real Time Solutions, Inc. Confidential and Proprietary. All rights reserved.
 */

package com.phenixrts.suite.phenixmultiangleondemand.ui

import android.content.res.Configuration
import android.os.Bundle
import android.widget.ArrayAdapter
import androidx.fragment.app.FragmentActivity
import androidx.recyclerview.widget.GridLayoutManager
import com.phenixrts.suite.phenixmultiangleondemand.MultiAngleOnDemandApp
import com.phenixrts.suite.phenixmultiangleondemand.R
import com.phenixrts.suite.phenixmultiangleondemand.cache.PreferenceProvider
import com.phenixrts.suite.phenixmultiangleondemand.common.*
import com.phenixrts.suite.phenixmultiangleondemand.databinding.ActivityMainBinding
import com.phenixrts.suite.phenixmultiangleondemand.repository.PCastExpressRepository
import com.phenixrts.suite.phenixmultiangleondemand.ui.adapters.StreamAdapter
import com.phenixrts.suite.phenixmultiangleondemand.ui.viewmodels.ChannelViewModel
import timber.log.Timber
import java.util.*
import javax.inject.Inject

const val SPAN_COUNT_PORTRAIT = 2
const val SPAN_COUNT_LANDSCAPE = 1

class MainActivity : FragmentActivity() {

    @Inject lateinit var pCastExpress: PCastExpressRepository
    @Inject lateinit var preferences: PreferenceProvider

    private lateinit var binding: ActivityMainBinding
    private val viewModel: ChannelViewModel by lazyViewModel({ application as MultiAngleOnDemandApp }, {
        ChannelViewModel(pCastExpress)
    })

    private val streamAdapter: StreamAdapter by lazy {
        StreamAdapter { roomMember ->
            Timber.d("Stream clicked: $roomMember")
            viewModel.updateActiveStream(binding.mainStreamSurface, roomMember)
            binding.mainStreamLoading.changeVisibility(roomMember.onLoading.value == true)
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
        mainStreamHolder.changeVisibility(true)
        mainStreamList.changeVisibility(true)
        mainStreamList.layoutManager = GridLayoutManager(this@MainActivity, spanCount)
        mainStreamList.setHasFixedSize(true)
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

        viewModel.acts.observe(this@MainActivity, { acts ->
            Timber.d("Mime Type list updated: $acts")
            actAdapter.clear()
            actAdapter.addAll(acts.map { it.title })

            preferences.getAct().let { act ->
                acts.indexOf(act).takeIf { it >= 0 && spinnerActs.selectedItemPosition != it }?.let { index ->
                    spinnerActs.setSelection(index)
                }
            } ?: run {
                acts.getOrNull(0)?.let { act ->
                    preferences.setAct(act)
                }
            }
        })
        viewModel.streams.observe(this@MainActivity, { streams ->
            Timber.d("Stream list updated: $streams")
            streamAdapter.data = streams
        })
        viewModel.onStreamsSubscribed.observe(this@MainActivity, { isSubscribed ->
            Timber.d("On all streams ready: $isSubscribed")
            if (isSubscribed) {
                viewModel.streams.value?.find { it.isMainRendered.value == true }?.let { stream ->
                    viewModel.updateActiveStream(mainStreamSurface, stream)
                }
            }
        })
        viewModel.headTimeStamp.observe(this@MainActivity, { head ->
            streamTimestampOverlay.text = Date(head).toDateString()
        })
        viewModel.onTimeShiftEnded.observe(this@MainActivity, { hasEnded ->
            Timber.d("Time shift has ended: $hasEnded")
            streamEndedOverlay.changeVisibility(hasEnded)
        })
        viewModel.onTimeShiftReady.observe(this@MainActivity, { ready ->
            Timber.d("Time shift ready: $ready")
            mainStreamLoading.changeVisibility(!ready)
            playActHolder.changeVisibility(ready)
            playActButton.isEnabled = ready
            pauseButton.isEnabled = ready
            if(ready && !viewModel.isTimeShiftPaused) viewModel.playTimeShift()
        })
        viewModel.onButtonsClickable.observe(this@MainActivity, { isClickable ->
            playActButton.isEnabled = isClickable
            pauseButton.isEnabled = isClickable
        })
        playActButton.isEnabled = false
        pauseButton.isEnabled = false
        Timber.d("Initializing Main Activity: $rotation")
    }
}
