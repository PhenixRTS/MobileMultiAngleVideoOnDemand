/*
 * Copyright 2021 Phenix Real Time Solutions, Inc. Confidential and Proprietary. All rights reserved.
 */

package com.phenixrts.suite.phenixmultiangleondemand.ui.viewmodels

import android.view.SurfaceView
import android.widget.ImageView
import androidx.lifecycle.*
import androidx.lifecycle.Observer
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

    private val streamSubscribedObserver = Observer<Boolean> {
        updateSubscribedState()
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
    val onStreamsSubscribed = MutableLiveData<Boolean>().apply { value = true }
    val onButtonsClickable = MutableLiveData<Boolean>().apply { value = true }
    var isTimeShiftPaused = false

    init {
        Timber.d("Observing streams")
        pCastExpressRepository.streams.observeForever { streamList ->
            launchMain {
                Timber.d("Stream list changed $streamList")
                streams.value = streamList
                streamList?.forEach { stream ->
                    stream.onStreamSubscribed.observeForever(streamSubscribedObserver)
                    stream.onTimeShiftReady.observeForever(timeShiftReadyObserver)
                    stream.onTimeShiftEnded.observeForever(timeShiftEndedObserver)
                    stream.subscribeToStream()
                }
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
        onTimeShiftReady.value = isReady
    }

    private fun updateSubscribedState() = launchMain {
        val isSubscribed = streams.value?.none { it.onStreamSubscribed.value == false } ?: false
        onStreamsSubscribed.value = isSubscribed
    }

    fun updateActiveStream(surfaceView: SurfaceView, bitmapView: ImageView?,  stream: Stream) = launchMain {
        val streams = streams.value?.toMutableList() ?: mutableListOf()
        streams.filter { it.isMainRendered.value == true && it.streamId != stream.streamId }.forEach { stream ->
            stream.isMainRendered.value = false
            stream.setMainSurfaces(null, null)
            stream.muteAudio()
            stream.onPlaybackHead.removeObserver(playbackHeadObserver)
        }
        streams.find { it.streamId == stream.streamId }?.apply {
            isMainRendered.value = true
            setMainSurfaces(surfaceView, bitmapView)
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

    fun seekToAct(index: Int) {
        selectedAct = acts.value?.getOrNull(index) ?: acts.value?.first()
        selectedAct?.let { act ->
            onButtonsClickable.value = false
            streams.value?.forEach { stream ->
                stream.seekToAct(act)
            }
            launchMain {
                delay(REPLAY_BUTTON_CLICK_DELAY)
                onButtonsClickable.value = true
            }
        }
    }

    fun playTimeShift() {
        isTimeShiftPaused = false
        streams.value?.forEach {  stream ->
            stream.onLoading.value = false
            stream.playTimeShift()
        }
        Timber.d("Time shift ready - playing")
    }

    fun pauseReplay() {
        isTimeShiftPaused = true
        streams.value?.forEach { stream ->
            stream.pauseTimeShift()
        }
    }
}
