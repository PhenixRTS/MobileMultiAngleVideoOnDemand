/*
 * Copyright 2021 Phenix Real Time Solutions, Inc. Confidential and Proprietary. All rights reserved.
 */

package com.phenixrts.suite.phenixmultiangleondemand.models

import android.graphics.Bitmap
import android.view.SurfaceView
import android.widget.ImageView
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
    private var thumbnailBitmapSurface: ImageView? = null
    private var mainSurface: SurfaceView? = null
    private var mainBitmapSurface: ImageView? = null
    private var renderer: Renderer? = null
    private var expressSubscriber: ExpressSubscriber? = null
    private var timeShift: TimeShift? = null
    private var timeShiftDisposables = mutableListOf<Disposable>()
    private var timeShiftSeekDisposables = mutableListOf<Disposable>()
    private var isFirstFrameDrawn = false
    private var timeShiftStartTime: Long = 0
    private var timeShiftCreateRetryCount = 0
    private var lastRenderedBitmap: Bitmap? = null
    private var isTimeShiftPaused = false
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
    private val lastFrameCallback = Renderer.LastFrameRenderedReceivedCallback { _, videoFrame ->
        (videoFrame as? AndroidVideoFrame)?.bitmap?.let { bitmap ->
            lastRenderedBitmap?.recycle()
            if (isTimeShiftPaused) {
                lastRenderedBitmap = bitmap.copy(bitmap.config, bitmap.isMutable)
                restoreLastBitmap()
            }
        }
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

    private fun setVideoFrameCallback() {
        expressSubscriber?.videoTracks?.getOrNull(0)?.let { videoTrack ->
            val callback = if (isMainRendered.value == false) null else frameCallback
            if (callback == null) isFirstFrameDrawn = false
            renderer?.setFrameReadyCallback(videoTrack, callback)
            renderer?.setLastVideoFrameRenderedReceivedCallback(lastFrameCallback)
            Timber.d("Frame callback ${if (callback != null) "set" else "removed"} for: ${toString()}")
        }
    }

    private fun drawFrameBitmap(bitmap: Bitmap) {
        try {
            launchMain {
                if (isMainRendered.value == false || isTimeShiftPaused || bitmap.isRecycled) return@launchMain
                if (isFirstFrameDrawn) delay(THUMBNAIL_DRAW_DELAY)
                thumbnailBitmapSurface?.setImageBitmap(bitmap.copy(bitmap.config, bitmap.isMutable))
                isFirstFrameDrawn = true
            }
        } catch (e: Exception) {
            Timber.d(e, "Failed to draw bitmap: ${toString()}")
        }
    }

    private fun updateSurfaces() {
        if (isMainRendered.value == true) {
            mainSurface?.changeVisibility(!isTimeShiftPaused)
            mainBitmapSurface?.changeVisibility(isTimeShiftPaused)
            thumbnailBitmapSurface?.changeVisibility(true)
        } else {
            thumbnailSurface?.changeVisibility(!isTimeShiftPaused)
            thumbnailBitmapSurface?.changeVisibility(isTimeShiftPaused)
        }
    }

    private fun restoreLastBitmap() = launchMain {
        updateSurfaces()
        if (isTimeShiftPaused) {
            lastRenderedBitmap?.takeIf { !it.isRecycled }?.let { bitmap ->
                Timber.d("Restoring bitmap for: ${asString()}")
                if (isMainRendered.value == true) {
                    mainBitmapSurface?.setImageBitmap(bitmap.copy(bitmap.config, bitmap.isMutable))
                }
                thumbnailBitmapSurface?.setImageBitmap(bitmap.copy(bitmap.config, bitmap.isMutable))
            }
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

    fun setThumbnailSurfaces(surfaceView: SurfaceView, imageView: ImageView) {
        thumbnailSurface = surfaceView
        thumbnailBitmapSurface = imageView
        if (isMainRendered.value == false) {
            videoRenderSurface.setSurfaceHolder(surfaceView.holder)
            setVideoFrameCallback()
        }
        Timber.d("Changed member thumbnail surface: ${asString()}")
        restoreLastBitmap()
    }

    fun setMainSurfaces(mainSurfaceView: SurfaceView?, mainBitmapSurfaceView: ImageView?) {
        mainSurface = mainSurfaceView
        mainBitmapSurface = mainBitmapSurfaceView
        videoRenderSurface.setSurfaceHolder(mainSurfaceView?.holder ?: thumbnailSurface?.holder)
        setVideoFrameCallback()
        Timber.d("Changed member main surface: ${asString()}")
        restoreLastBitmap()
    }

    fun seekToAct(act: Act) = launchMain {
        timeShiftSeekDisposables.forEach { it.dispose() }
        timeShiftSeekDisposables.clear()
        onTimeShiftEnded.value = false
        onLoading.value = true
        pauseTimeShift()
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

    fun pauseTimeShift() {
        Timber.d("Pausing time shift for: ${toString()}")
        isTimeShiftPaused = true
        timeShift?.pause()
        renderer?.requestLastVideoFrameRendered()
    }

    fun playTimeShift() {
        Timber.d("Playing time shift for: ${toString()}")
        isTimeShiftPaused = false
        lastRenderedBitmap?.recycle()
        updateSurfaces()
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
