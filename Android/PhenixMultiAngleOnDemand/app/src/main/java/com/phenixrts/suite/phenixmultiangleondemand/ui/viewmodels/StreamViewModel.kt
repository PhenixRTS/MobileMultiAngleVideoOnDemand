/*
 * Copyright 2022 Phenix Real Time Solutions, Inc. Confidential and Proprietary. All rights reserved.
 */

package com.phenixrts.suite.phenixmultiangleondemand.ui.viewmodels

import android.view.SurfaceView
import androidx.lifecycle.*
import com.phenixrts.suite.phenixcore.PhenixCore
import com.phenixrts.suite.phenixcore.common.ConsumableSharedFlow
import com.phenixrts.suite.phenixcore.common.launchIO
import com.phenixrts.suite.phenixcore.common.launchMain
import com.phenixrts.suite.phenixcore.repositories.models.*
import com.phenixrts.suite.phenixmultiangleondemand.common.toMillis
import com.phenixrts.suite.phenixmultiangleondemand.models.Act
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.asSharedFlow
import kotlinx.coroutines.flow.asStateFlow
import timber.log.Timber

private const val REPLAY_BUTTON_CLICK_DELAY = 1000 * 2L

class StreamViewModel(private val phenixCore: PhenixCore) : ViewModel() {

    private val rawStreams = mutableListOf<PhenixStream>()
    private val rawActs = mutableListOf<Act>()

    private val _onReplayButtonClickable = ConsumableSharedFlow<Boolean>(canReplay = true)
    private val _streams = ConsumableSharedFlow<List<PhenixStream>>(canReplay = true)
    private val _acts = ConsumableSharedFlow<List<Act>>(canReplay = true)
    private val _onHeadTimeChanged = ConsumableSharedFlow<Long>(canReplay = true)
    private val _onTimeShiftStateChanged = ConsumableSharedFlow<PhenixTimeShiftState>(canReplay = true)
    private val _onStreamsJoined = MutableStateFlow(false)

    private var selectedAct: Act? = null
    private var timeShiftStart = 0L
    private var timeShiftsCreated = false
    private var streamSelected = false
    private var isReplayButtonEnabled = true
    private var lastTimeShiftState = PhenixTimeShiftState.STARTING
    private val streamCopy get() = rawStreams.map { it.copy() }

    val streams = _streams.asSharedFlow()
    val acts = _acts.asSharedFlow()
    val onButtonsClickable = _onReplayButtonClickable.asSharedFlow()
    val onHeadTimeChanged = _onHeadTimeChanged.asSharedFlow()
    val onTimeShiftStateChanged = _onTimeShiftStateChanged.asSharedFlow()
    val onStreamsJoined = _onStreamsJoined.asStateFlow()

    val isTimeShiftPaused get() = streamCopy.find { it.isSelected }?.timeShiftState == PhenixTimeShiftState.PAUSED

    init {
        Timber.d("Observing streams")
        launchIO {
            phenixCore.streams.collect { streams ->
                rawStreams.clear()
                rawStreams.addAll(streams)
                if (streams.isEmpty()) return@collect
                // Check if all streams joined
                val allStreamsJoined = streams.all { it.streamState == PhenixStreamState.STREAMING }
                _onStreamsJoined.tryEmit(allStreamsJoined)
                if (allStreamsJoined && !timeShiftsCreated) {
                    timeShiftsCreated = true
                    createTimeShift()
                }

                // Select a stream if none selected
                if (streams.none { it.isSelected } && !streamSelected) {
                    streamSelected = true
                    selectStream(streams.first())
                }
                // Chek if all time shifts are sought to the same timestamp
                if (streams.all { it.timeShiftState == PhenixTimeShiftState.SOUGHT }) {
                    streams.forEach { stream ->
                        phenixCore.playTimeShift(stream.id)
                    }
                }
                // Update selected stream time shift state
                val timeShiftState = streams.find { it.isSelected }?.timeShiftState ?: PhenixTimeShiftState.STARTING
                val areAllTimeShiftsReady = streams.all { it.timeShiftState == PhenixTimeShiftState.READY }
                if (lastTimeShiftState != timeShiftState) {
                    lastTimeShiftState = if (timeShiftState != PhenixTimeShiftState.READY || areAllTimeShiftsReady)
                        timeShiftState else PhenixTimeShiftState.STARTING
                    _onTimeShiftStateChanged.tryEmit(lastTimeShiftState)
                }
                // Update head time stamp for selected stream
                val head = streams.find { it.isSelected }?.timeShiftHead ?: 0L
                _onHeadTimeChanged.tryEmit(head)
                _streams.tryEmit(streams.map { it.copy() })
            }
        }
        rawActs.addAll(phenixCore.configuration?.acts?.map { Act(it, it.toMillis()) } ?: emptyList())
        Timber.d("Acts collected: $rawActs")
        _acts.tryEmit(rawActs.map { it.copy() })
    }

    fun joinStreams() = launchIO {
        Timber.d("Joining streams: ${phenixCore.configuration?.streamIDs}")
        phenixCore.joinAllStreams()
    }

    fun selectStream(selectedStream: PhenixStream) {
        streamCopy.forEach { stream ->
            val isSelected = stream.id == selectedStream.id
            phenixCore.selectStream(stream.id, isSelected)
            phenixCore.setAudioEnabled(stream.id, isSelected)
        }
    }

    fun renderActiveStream(surfaceView: SurfaceView) {
        streamCopy.find { it.isSelected }?.let { stream ->
            Timber.d("Render active stream: $stream")
            phenixCore.renderOnSurface(stream.id, surfaceView)
        }
    }

    fun selectAct(index: Int) {
        selectedAct = rawActs.getOrNull(index) ?: rawActs.firstOrNull()
    }

    fun seekToAct(index: Int) {
        selectedAct = rawActs.getOrNull(index) ?: rawActs.firstOrNull()
        selectedAct?.let { act ->
            isReplayButtonEnabled = false
            _onReplayButtonClickable.tryEmit(isReplayButtonEnabled)
            streamCopy.forEach { stream ->
                phenixCore.seekTimeShift(stream.id, act.offsetFromBeginning)
            }
            launchMain {
                delay(REPLAY_BUTTON_CLICK_DELAY)
                isReplayButtonEnabled = true
                _onReplayButtonClickable.tryEmit(isReplayButtonEnabled)
            }
        }
    }

    fun playTimeShift() {
        streamCopy.forEach { stream ->
            phenixCore.playTimeShift(stream.id)
        }
        Timber.d("Time shift ready - playing")
    }

    fun pauseReplay() {
        streamCopy.forEach { stream ->
            phenixCore.pauseTimeShift(stream.id)
        }
    }

    private fun createTimeShift() {
        Timber.d("Creating time shift")
        streamCopy.forEach { stream ->
            timeShiftStart = 0
            phenixCore.createTimeShift(stream.id, 0)
            phenixCore.limitBandwidth(stream.id, 1000 * 520L)
        }
    }
}
