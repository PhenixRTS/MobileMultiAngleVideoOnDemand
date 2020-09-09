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
import kotlinx.coroutines.suspendCancellableCoroutine
import timber.log.Timber
import kotlin.coroutines.resume

private const val BANDWIDTH_LIMIT = 1000 * 520L

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

    private var bandwidthLimiter: Disposable? = null
    private var timeShiftLimiter: Disposable? = null
    private var timeShiftDisposables = mutableListOf<Disposable>()
    private var timeShiftSeekDisposables = mutableListOf<Disposable>()
    private var isBitmapSurfaceAvailable = false
    private var timeShiftStartTime: Long = 0

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
    val isMainRendered= MutableLiveData<Boolean>().apply { value = false }
    var onLoading = MutableLiveData<Boolean>().apply { value = true }
    val onPlaybackHead = MutableLiveData<Long>().apply { value = 0 }
    val status = MutableLiveData<StreamStatus>()

    private fun createTimeShift() {
        onTimeShiftReady.value = false
        Timber.d("Subscribing to time shift observables")
        releaseTimeShiftObservers()
        renderer?.seek(0, SeekOrigin.BEGINNING)?.let { shift ->
            timeShift = shift
            timeShiftStartTime = shift.startTime.time
            limitTimeShiftBandwidth()
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
                    Timber.d("Time shift failure: $status")
                    releaseTimeShift()
                    onTimeShiftReady.value = false
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
        if (isMainRendered.value == false) {
            limitBandwidth()
            limitTimeShiftBandwidth()
        } else {
            releaseBandwidthLimiter()
            releaseTimeShiftLimiter()
        }
        thumbnailSurface?.setVisible(isMainRendered.value == false)
        bitmapSurface?.setVisible(isMainRendered.value == true)
    }

    private fun setVideoFrameCallback() {
        expressSubscriber?.videoTracks?.getOrNull(0)?.let { videoTrack ->
            renderer?.setFrameReadyCallback(videoTrack, if (isMainRendered.value == false) null else frameCallback)
        }
    }

    private fun drawFrameBitmap(bitmap: Bitmap) {
        try {
            if (isMainRendered.value == false || !isBitmapSurfaceAvailable) return
            launchIO {
                delay(THUMBNAIL_DRAW_DELAY)
                bitmapSurface?.holder?.let { holder ->
                    holder.lockCanvas()?.let { canvas ->
                        val targetWidth = bitmapSurface?.measuredWidth ?: 0
                        val targetHeight = bitmapSurface?.measuredHeight ?: 0
                        canvas.drawScaledBitmap(bitmap, targetWidth, targetHeight)
                        holder.unlockCanvasAndPost(canvas)
                    }
                }
            }
        } catch (e: Exception) {
            Timber.d(e, "Failed to draw bitmap")
        }
    }

    private fun limitTimeShiftBandwidth() {
        Timber.d("Limiting TimeShift Bandwidth: ${toString()}")
        timeShiftLimiter = timeShift?.limitBandwidth(BANDWIDTH_LIMIT)
    }

    private fun releaseTimeShiftLimiter() {
        Timber.d("Releasing TimeShift limiter: ${toString()}")
        timeShiftLimiter?.dispose()
        timeShiftLimiter = null
    }

    private fun limitBandwidth() {
        Timber.d("Limiting Bandwidth: ${toString()}")
        bandwidthLimiter = expressSubscriber?.videoTracks?.getOrNull(0)?.limitBandwidth(BANDWIDTH_LIMIT)
    }

    private fun releaseBandwidthLimiter() {
        Timber.d("Releasing Bandwidth limiter: ${toString()}")
        bandwidthLimiter?.dispose()
        bandwidthLimiter = null
    }

    private fun releaseTimeShiftObservers() {
        timeShiftDisposables.forEach { it.dispose() }
        timeShiftDisposables.clear()
        timeShiftSeekDisposables.forEach { it.dispose() }
        timeShiftSeekDisposables.clear()
        Timber.d("Time shift disposables released")
    }

    private fun releaseTimeShift() {
        releaseTimeShiftObservers()
        timeShift?.stop()
        timeShift?.dispose()
        timeShift = null
        Timber.d("Time shift released")
    }

    fun muteAudio() = renderer?.muteAudio()

    fun unmuteAudio() = renderer?.unmuteAudio()

    suspend fun subscribeToStream() = suspendCancellableCoroutine<Unit> { continuation ->
        Timber.d("Subscribing stream with ID: $streamId")
        val options = getStreamOptions(streamId)
        pCastExpress.subscribe(options) { requestStatus, subscriber, _ ->
            launchMain {
                if (requestStatus != RequestStatus.OK) {
                    status.value = StreamStatus.OFFLINE
                    Timber.d("Failed to subscribe: ${asString()}")
                    if (continuation.isActive) continuation.resume(Unit)
                    return@launchMain
                }
                expressSubscriber?.dispose()
                expressSubscriber = subscriber
                renderer?.stop()
                renderer?.dispose()
                renderer = subscriber?.createRenderer()
                if (renderer?.isSeekable == false) {
                    status.value = StreamStatus.OFFLINE
                    Timber.d("Stream is not seakable: ${asString()}")
                    if (continuation.isActive) continuation.resume(Unit)
                    return@launchMain
                }

                val rendererStartStatus = renderer?.startSuspended(videoRenderSurface)
                if (rendererStartStatus != RendererStartStatus.OK) {
                    status.value = StreamStatus.OFFLINE
                    Timber.d("Failed to start renderer: ${asString()}")
                    if (continuation.isActive) continuation.resume(Unit)
                    return@launchMain
                }
                createTimeShift()
                setVideoFrameCallback()
                if (isMainRendered.value == true) {
                    unmuteAudio()
                } else {
                    muteAudio()
                }
                status.value = StreamStatus.ONLINE
                Timber.d("Started subscriber renderer: ${asString()}")
                if (continuation.isActive) continuation.resume(Unit)
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
        limitTimeShiftBandwidth()
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
        if (isMainRendered.value == true) {
            releaseTimeShiftLimiter()
        }
    }

    override fun toString(): String {
        return "{\"name\":\"$streamId\"," +
                "\"hasRenderer\":\"${renderer != null}\"," +
                "\"surfaceId\":\"${thumbnailSurface?.id}\"," +
                "\"isSekable\":\"${renderer?.isSeekable}\"," +
                "\"isTimeShiftReady\":\"${onTimeShiftReady.value}\"," +
                "\"isSubscribed\":\"${expressSubscriber != null}\"," +
                "\"isMainRendered\":\"${isMainRendered.value}\"}"
    }
}
