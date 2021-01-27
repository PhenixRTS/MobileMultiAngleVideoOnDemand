/*
 * Copyright 2020 Phenix Real Time Solutions, Inc. Confidential and Proprietary. All rights reserved.
 */

package com.phenixrts.suite.phenixmultiangleondemand.models

import android.graphics.Bitmap
import android.view.SurfaceHolder
import android.view.SurfaceView
import androidx.lifecycle.MutableLiveData
import com.phenixrts.common.Disposable
import com.phenixrts.common.RequestStatus
import com.phenixrts.express.ExpressSubscriber
import com.phenixrts.express.PCastExpress
import com.phenixrts.media.video.android.AndroidVideoFrame
import com.phenixrts.pcast.Renderer
import com.phenixrts.pcast.RendererStartStatus
import com.phenixrts.pcast.SeekOrigin
import com.phenixrts.pcast.TimeShift
import com.phenixrts.pcast.android.AndroidReadVideoFrameCallback
import com.phenixrts.pcast.android.AndroidVideoRenderSurface
import com.phenixrts.suite.phenixmultiangleondemand.common.*
import com.phenixrts.suite.phenixmultiangleondemand.common.enums.StreamStatus
import kotlinx.coroutines.delay
import timber.log.Timber

private const val TIME_SHIFT_RETRY_DELAY = 1000 * 10L
private const val MAX_RETRY_COUNT = 10

data class Stream(
    private val pCastExpress: PCastExpress,
    val streamId: String
) {

    private val videoRenderSurface = AndroidVideoRenderSurface()
    private var thumbnailSurface: SurfaceView? = null
    private var bitmapSurface: SurfaceView? = null
    private var renderer: Renderer? = null
    private var expressSubscriber: ExpressSubscriber? = null
    private var timeShift: TimeShift? = null

    private var timeShiftDisposables = mutableListOf<Disposable>()
    private var timeShiftSeekDisposables = mutableListOf<Disposable>()
    private var isBitmapSurfaceAvailable = false
    private var isFirstFrameDrawn = false
    private var timeShiftStartTime: Long = 0
    private var timeShiftCreateRetryCount = 0

    private var bitmapCallback: SurfaceHolder.Callback? = null
    private val frameCallback = Renderer.FrameReadyForProcessingCallback { frameNotification ->
        if (isMainRendered.value == false) return@FrameReadyForProcessingCallback
        frameNotification?.read(object : AndroidReadVideoFrameCallback() {
            override fun onVideoFrameEvent(videoFrame: AndroidVideoFrame?) {
                videoFrame?.bitmap?.let { bitmap ->
                    drawFrameBitmap(bitmap)
                }
            }
        })
    }

    val onTimeShiftReady = MutableLiveData<Boolean>().apply { value = false }
    val onTimeShiftEnded = MutableLiveData<Boolean>().apply { value = false }
    val onStreamSubscribed = MutableLiveData<Boolean>().apply { value = false }
    val isMainRendered= MutableLiveData<Boolean>().apply { value = false }
    var onLoading = MutableLiveData<Boolean>().apply { value = true }
    val onPlaybackHead = MutableLiveData<Long>().apply { value = 0 }
    val status = MutableLiveData<StreamStatus>()

    private fun createTimeShift() {
        Timber.d("Subscribing to time shift observables")
        releaseTimeShiftObservers()
        renderer?.seek(0, SeekOrigin.BEGINNING)?.let { shift ->
            timeShift = shift
            timeShiftStartTime = shift.startTime.time
            shift.observableReadyForPlaybackStatus?.subscribe { isReady ->
                launchMain {
                    if (onTimeShiftReady.value != isReady) {
                        Timber.d("Time shift ready: $isReady, ${this@Stream.asString()}")
                        onTimeShiftReady.value = isReady
                    }
                }
            }?.run { timeShiftDisposables.add(this) }
            shift.observablePlaybackHead?.subscribe { head ->
                launchMain {
                    if (onPlaybackHead.value != head.time) {
                        onPlaybackHead.value = head.time - timeShiftStartTime
                    }
                }
            }?.run { timeShiftDisposables.add(this) }
            shift.observableFailure?.subscribe { status ->
                launchMain {
                    Timber.d("Time shift failure: $status, retryCount: $timeShiftCreateRetryCount")
                    releaseTimeShift()
                    if (timeShiftCreateRetryCount < MAX_RETRY_COUNT) {
                        timeShiftCreateRetryCount++
                        delay(TIME_SHIFT_RETRY_DELAY)
                        createTimeShift()
                    } else {
                        timeShiftCreateRetryCount = 0
                        onTimeShiftReady.value = false
                    }
                }
            }?.run { timeShiftDisposables.add(this) }
            shift.observableEnded?.subscribe { hasEnded ->
                launchMain {
                    if (hasEnded) {
                        Timber.d("Time shift ended: $hasEnded, ${asString()}")
                        onTimeShiftEnded.value = hasEnded
                    }
                }
            }?.run { timeShiftDisposables.add(this) }
        }
    }

    private fun updateSurfaces() {
        thumbnailSurface?.changeVisibility(isMainRendered.value == false)
        bitmapSurface?.changeVisibility(isMainRendered.value == true)
    }

    private fun setVideoFrameCallback() {
        expressSubscriber?.videoTracks?.getOrNull(0)?.let { videoTrack ->
            val callback = if (isMainRendered.value == false) null else frameCallback
            if (callback == null) isFirstFrameDrawn = false
            renderer?.setFrameReadyCallback(videoTrack, callback)
            Timber.d("Frame callback ${if (callback != null) "set" else "removed"} for: ${toString()}")
        }
    }

    private fun drawFrameBitmap(bitmap: Bitmap) {
        try {
            launchIO {
                if (isMainRendered.value == false || !isBitmapSurfaceAvailable) return@launchIO
                if (isFirstFrameDrawn) delay(THUMBNAIL_DRAW_DELAY)
                bitmapSurface?.drawBitmap(bitmap)
                isFirstFrameDrawn = true
            }
        } catch (e: Exception) {
            Timber.d(e, "Failed to draw bitmap: ${toString()}")
        }
    }

    private fun releaseTimeShiftObservers() {
        val disposed = timeShiftDisposables.isNotEmpty() || timeShiftSeekDisposables.isNotEmpty()
        timeShiftDisposables.forEach { it.dispose() }
        timeShiftDisposables.clear()
        timeShiftSeekDisposables.forEach { it.dispose() }
        timeShiftSeekDisposables.clear()
        if (disposed) {
            Timber.d("Time shift disposables released: ${toString()}")
        }
    }

    private fun releaseTimeShift() {
        releaseTimeShiftObservers()
        timeShift?.dispose()
        timeShift = null
        Timber.d("Time shift released")
    }

    fun muteAudio() = renderer?.muteAudio()

    fun unmuteAudio() = renderer?.unmuteAudio()

    fun subscribeToStream() = launchIO {
        Timber.d("Subscribing stream with ID: $streamId")
        val options = getStreamOptions(streamId)
        pCastExpress.subscribe(options) { requestStatus, subscriber, _ ->
            if (requestStatus != RequestStatus.OK) {
                launchMain {
                    status.value = StreamStatus.OFFLINE
                    onStreamSubscribed.value = false
                    Timber.d("Failed to subscribe: ${asString()}")
                }
                return@subscribe
            }
            expressSubscriber?.dispose()
            expressSubscriber = subscriber
            renderer?.stop()
            renderer?.dispose()
            renderer = subscriber?.createRenderer()
            if (renderer?.isSeekable == false) {
                launchMain {
                    status.value = StreamStatus.OFFLINE
                    onStreamSubscribed.value = false
                    Timber.d("Stream is not seakable: ${asString()}")
                }
                return@subscribe
            }

            val rendererStartStatus = renderer?.startSuspended(videoRenderSurface)
            if (rendererStartStatus != RendererStartStatus.OK) {
                launchMain {
                    status.value = StreamStatus.OFFLINE
                    onStreamSubscribed.value = false
                    Timber.d("Failed to start renderer: ${asString()}")
                }
                return@subscribe
            }
            createTimeShift()
            setVideoFrameCallback()
            if (isMainRendered.value == true) {
                unmuteAudio()
            } else {
                muteAudio()
            }
            launchMain {
                status.value = StreamStatus.ONLINE
                onStreamSubscribed.value = true
                Timber.d("Started subscriber renderer: ${asString()}")
            }
        }
    }

    fun setThumbnailSurfaces(thumbnailSurfaceView: SurfaceView, bitmapSurfaceView: SurfaceView) {
        thumbnailSurface = thumbnailSurfaceView
        bitmapSurface = bitmapSurfaceView
        bitmapSurface?.holder?.removeCallback(bitmapCallback)
        bitmapCallback = bitmapSurface?.setCallback { available ->
            isBitmapSurfaceAvailable = available
        }
        if (isMainRendered.value == false) {
            videoRenderSurface.setSurfaceHolder(thumbnailSurfaceView.holder)
            updateSurfaces()
            setVideoFrameCallback()
        }
        Timber.d("Changed member thumbnail surface: ${asString()}")
    }

    fun setMainSurface(surfaceView: SurfaceView?) {
        videoRenderSurface.setSurfaceHolder(surfaceView?.holder ?: thumbnailSurface?.holder)
        updateSurfaces()
        setVideoFrameCallback()
        Timber.d("Changed member main surface: ${asString()}")
    }

    fun seekToAct(act: Act) = launchMain {
        timeShiftSeekDisposables.forEach { it.dispose() }
        timeShiftSeekDisposables.clear()
        onTimeShiftEnded.value = false
        onLoading.value = true
        timeShift?.pause()
        Timber.d("Paused time shift: $act")
        timeShift?.seek(act.offsetFromBeginning, SeekOrigin.BEGINNING)?.subscribe { requestStatus ->
            launchMain scope@{
                if (requestStatus != RequestStatus.OK) {
                    status.value = StreamStatus.OFFLINE
                    return@scope
                }
                Timber.d("Time shift resumed: $act")
                onLoading.value = false
                status.value = StreamStatus.ONLINE
            }
        }?.run { timeShiftSeekDisposables.add(this) }
    }

    fun playTimeShift() {
        Timber.d("Playing time shift")
        timeShift?.play()
    }

    override fun toString(): String {
        return "{\"name\":\"$streamId\"," +
                "\"hasRenderer\":\"${renderer != null}\"," +
                "\"surfaceId\":\"${thumbnailSurface?.id}\"," +
                "\"isSeekable\":\"${renderer?.isSeekable}\"," +
                "\"isTimeShiftReady\":\"${onTimeShiftReady.value}\"," +
                "\"isSubscribed\":\"${expressSubscriber != null}\"," +
                "\"isMainRendered\":\"${isMainRendered.value}\"}"
    }
}
