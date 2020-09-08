/*
 * Copyright 2020 Phenix Real Time Solutions, Inc. Confidential and Proprietary. All rights reserved.
 */

package com.phenixrts.suite.phenixmultiangleondemand.ui.viewmodels

import android.view.SurfaceView
import androidx.lifecycle.*
import androidx.lifecycle.Observer
import com.phenixrts.suite.phenixmultiangleondemand.common.SingleLiveEvent
import com.phenixrts.suite.phenixmultiangleondemand.models.Act
import com.phenixrts.suite.phenixmultiangleondemand.common.launchMain
import com.phenixrts.suite.phenixmultiangleondemand.models.Stream
import com.phenixrts.suite.phenixmultiangleondemand.repository.PCastExpressRepository
import kotlinx.coroutines.delay
import timber.log.Timber

private const val REPLAY_BUTTON_CLICK_DELAY = 1000 * 2L

class ChannelViewModel(pCastExpressRepository: PCastExpressRepository) : ViewModel() {

    private val timeShiftReadyObserver = Observer<Boolean> {
        updateReadyState()
    }

    private val timeShiftEndedObserver = Observer<Boolean> {
        updateEndedState()
    }

    private val playbackHeadObserver = Observer<Long> { head ->
        headTimeStamp.value = head
    }

    private var selectedAct: Act? = null
    val streams = MutableLiveData<List<Stream>>()
    val acts = MutableLiveData<List<Act>>()
    val headTimeStamp = MutableLiveData<Long>()
    val onTimeShiftReady = MutableLiveData<Boolean>().apply { value = false }
    val onTimeShiftEnded = MutableLiveData<Boolean>().apply { value = false }
    val onReplayButtonClickable = MutableLiveData<Boolean>().apply { value = true }
    val onStreamsReady = SingleLiveEvent<Unit>()

    init {
        Timber.d("Observing streams")
        pCastExpressRepository.streams.observeForever { streamList ->
            launchMain {
                Timber.d("Stream list changed $streamList")
                streams.value = streamList
                streamList?.forEach { stream ->
                    stream.subscribeToStream()
                    stream.onTimeShiftReady.observeForever(timeShiftReadyObserver)
                    stream.onTimeShiftEnded.observeForever(timeShiftEndedObserver)
                }
                onStreamsReady.call()
                Timber.d("Streams started $streamList")
            }
        }
        pCastExpressRepository.acts.observeForever { actList ->
            acts.value = actList
        }
    }

    private fun updateEndedState() = launchMain {
        val hasEnded = streams.value?.none { it.onTimeShiftEnded.value == false } ?: false
        Timber.d("Time shift ended for all: $hasEnded")
        if (onTimeShiftEnded.value != hasEnded) {
            onTimeShiftEnded.value = hasEnded
        }
    }

    private fun updateReadyState() = launchMain {
        val isReady = streams.value?.none { it.onTimeShiftReady.value == false } ?: false
        // Start playing when all streams are ready
        if (isReady) {
            streams.value?.forEach {  stream ->
                stream.onLoading.value = false
                stream.playTimeShift()
            }
            Timber.d("Time shift ready - playing")
        }
        onTimeShiftReady.value = isReady
    }

    fun updateActiveStream(surfaceView: SurfaceView, stream: Stream) = launchMain {
        val streams = streams.value?.toMutableList() ?: mutableListOf()
        streams.filter { it.isMainRendered.value == true && it.streamId != stream.streamId }.forEach { stream ->
            stream.isMainRendered.value = false
            stream.setMainSurface(null)
            stream.muteAudio()
            stream.onPlaybackHead.removeObserver(playbackHeadObserver)
        }
        streams.find { it.streamId == stream.streamId }?.apply {
            isMainRendered.value = true
            setMainSurface(surfaceView)
            unmuteAudio()
            onPlaybackHead.observeForever(playbackHeadObserver)
        }
        Timber.d("Updated active stream: $stream")
        updateReadyState()
        updateEndedState()
    }

    fun selectAct(index: Int) {
        selectedAct = acts.value?.getOrNull(index) ?: acts.value?.first()
    }

    fun playAct(index: Int) {
        selectedAct = acts.value?.getOrNull(index) ?: acts.value?.first()
        selectedAct?.let { act ->
            onReplayButtonClickable.value = false
            streams.value?.forEach { stream ->
                stream.seekToAct(act)
            }
            launchMain {
                delay(REPLAY_BUTTON_CLICK_DELAY)
                onReplayButtonClickable.value = true
            }
        }
    }
}