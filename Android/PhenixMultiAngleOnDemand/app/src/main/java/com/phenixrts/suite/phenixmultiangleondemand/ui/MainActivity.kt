/*
 * Copyright 2020 Phenix Real Time Solutions, Inc. Confidential and Proprietary. All rights reserved.
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
import com.phenixrts.suite.phenixmultiangleondemand.repository.PCastExpressRepository
import com.phenixrts.suite.phenixmultiangleondemand.ui.adapters.StreamAdapter
import com.phenixrts.suite.phenixmultiangleondemand.ui.viewmodels.ChannelViewModel
import kotlinx.android.synthetic.main.activity_main.*
import timber.log.Timber
import java.util.*
import javax.inject.Inject

const val SPAN_COUNT_PORTRAIT = 2
const val SPAN_COUNT_LANDSCAPE = 1

class MainActivity : FragmentActivity() {

    @Inject lateinit var pCastExpress: PCastExpressRepository
    @Inject lateinit var preferences: PreferenceProvider

    private val viewModel: ChannelViewModel by lazyViewModel({ application as MultiAngleOnDemandApp }, { ChannelViewModel(pCastExpress) })

    private val streamAdapter: StreamAdapter by lazy {
        StreamAdapter { roomMember ->
            Timber.d("Stream clicked: $roomMember")
            viewModel.updateActiveStream(main_stream_surface, roomMember)
            main_stream_loading.setVisible(roomMember.onLoading.value == true)
        }
    }

    private val actAdapter by lazy {
        ArrayAdapter(this, R.layout.row_spinner_selector, arrayListOf<String>())
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        MultiAngleOnDemandApp.component.inject(this)
        setContentView(R.layout.activity_main)
        initViews()
    }

    private fun initViews() {
        val rotation = resources.configuration.orientation
        val spanCount = if (rotation == Configuration.ORIENTATION_PORTRAIT) SPAN_COUNT_PORTRAIT else SPAN_COUNT_LANDSCAPE
        main_stream_holder.setVisible(true)
        main_stream_list.setVisible(true)
        main_stream_list.layoutManager = GridLayoutManager(this, spanCount)
        main_stream_list.setHasFixedSize(true)
        main_stream_list.adapter = streamAdapter

        actAdapter.setDropDownViewResource(R.layout.row_spinner_item)
        spinner_acts.adapter = actAdapter
        spinner_acts.onSelectionChanged { index ->
            Timber.d("Act selected: $index")
            viewModel.selectAct(index)
        }

        play_act_button.setOnClickListener {
            Timber.d("Play act button clicked")
            viewModel.seekToAct(spinner_acts.selectedItemPosition)
        }

        viewModel.acts.observe(this, { acts ->
            Timber.d("Mime Type list updated: $acts")
            actAdapter.clear()
            actAdapter.addAll(acts.map { it.title })

            preferences.getAct().let { act ->
                acts.indexOf(act).takeIf { it >= 0 && spinner_acts.selectedItemPosition != it }?.let { index ->
                    spinner_acts.setSelection(index)
                }
            } ?: run {
                acts.getOrNull(0)?.let { act ->
                    preferences.setAct(act)
                }
            }
        })
        viewModel.streams.observe(this, { streams ->
            Timber.d("Stream list updated: $streams")
            streamAdapter.data = streams
        })
        viewModel.onStreamsSubscribed.observe(this, { isSubscribed ->
            Timber.d("On all streams ready: $isSubscribed")
            if (isSubscribed) {
                viewModel.streams.value?.find { it.isMainRendered.value == true }?.let { stream ->
                    viewModel.updateActiveStream(main_stream_surface, stream)
                }
            }
        })
        viewModel.headTimeStamp.observe(this, { head ->
            stream_timestamp_overlay.text = Date(head).toDateString()
        })
        viewModel.onTimeShiftEnded.observe(this, { hasEnded ->
            Timber.d("Time shift has ended: $hasEnded")
            stream_ended_overlay.setVisible(hasEnded)
        })
        viewModel.onTimeShiftReady.observe(this, { ready ->
            Timber.d("Time shift ready: $ready")
            main_stream_loading.setVisible(!ready)
            play_act_holder.setVisible(ready)
            if(ready) viewModel.playTimeShift()
        })
        viewModel.onReplayButtonClickable.observe(this, { isClickable ->
            play_act_button.setBackgroundResource(
                if (isClickable) R.drawable.bg_play_act_button else R.drawable.bg_play_act_button_disabled
            )
        })
        Timber.d("Initializing Main Activity: $rotation")
    }
}
