//
//  Copyright 2021 Phenix Real Time Solutions, Inc. Confidential and Proprietary. All rights reserved.
//

import os.log
import PhenixSdk

internal protocol MediaDelegate: AnyObject {
    func mediaDidChangeVideoDisplayDimensions(_ dimensions: PhenixDimensions?)
}

public class StreamMediaController {
    private weak var renderer: PhenixRenderer!
    private weak var subscriber: PhenixExpressSubscriber!
    private weak var secondaryPreviewLayer: VideoLayer!
    private var isSecondaryLayerFlushed = true

    internal weak var streamRepresentation: StreamRepresentation?
    internal weak var delegate: MediaDelegate?

    init(subscriber: PhenixExpressSubscriber, renderer: PhenixRenderer, secondaryPreviewLayer: VideoLayer, streamRepresentation: StreamRepresentation? = nil) {
        self.subscriber = subscriber
        self.renderer = renderer
        self.secondaryPreviewLayer = secondaryPreviewLayer
        self.streamRepresentation = streamRepresentation
    }

    /// Change stream audio state
    /// - Parameter enabled: True means that audio will be unmuted, false - muted
    public func setAudio(enabled: Bool) {
        if enabled {
            os_log(.debug, log: .mediaController, "Unmute audio, (%{PRIVATE}s)", streamDescription)
            renderer.unmuteAudio()
        } else {
            os_log(.debug, log: .mediaController, "Mute audio, (%{PRIVATE}s)", streamDescription)
            renderer.muteAudio()
        }
    }
}

// MARK: - CustomStringConvertible
extension StreamMediaController: CustomStringConvertible {
    public var description: String {
        "StreamMediaController, audio muted: \(renderer.isAudioMuted)"
    }
}

// MARK: - Internal methods
internal extension StreamMediaController {
    func subscribe(_ videoTrack: PhenixMediaStreamTrack) {
        os_log(.debug, log: .mediaController, "Subscribe to video, (%{PRIVATE}s)", streamDescription)
        renderer.setFrameReadyCallback(videoTrack, didReceiveVideoFrame)
        renderer.setLastVideoFrameRenderedReceivedCallback(didReceiveLastVideoFrame)
        renderer.setVideoDisplayDimensionsChangedCallback(didReceiveVideoDisplayDimensionsChange)
    }

    func requestLastVideoFrame() {
        renderer.requestLastVideoFrameRendered()
    }
}

// MARK: - Private methods
private extension StreamMediaController {
    var streamDescription: String { streamRepresentation?.id ?? "-" }

    func modify(_ sampleBuffer: CMSampleBuffer) {
        if let attachmentArray = CMSampleBufferGetSampleAttachmentsArray(sampleBuffer, createIfNecessary: true) {
            let count = CFArrayGetCount(attachmentArray)
            for index in 0..<count {
                if let unsafeRawPointer = CFArrayGetValueAtIndex(attachmentArray, index) {
                    let attachments = unsafeBitCast(unsafeRawPointer, to: CFMutableDictionary.self)
                    // Need to set the sample buffer to display frame immediately and ignore whatever timestamps are included.
                    // Without this, iOS 14 will not render the frames.
                    CFDictionarySetValue(attachments,
                                         unsafeBitCast(kCMSampleAttachmentKey_DisplayImmediately, to: UnsafeRawPointer.self),
                                         unsafeBitCast(kCFBooleanTrue, to: UnsafeRawPointer.self))
                }
            }
        }
    }
}

// MARK: - Observable callbacks
private extension StreamMediaController {
    func didReceiveVideoFrame(_ frameNotification: PhenixFrameNotification?) {
        // If layer is not added to the view hierarchy, there is no need to render the media on it.
        guard secondaryPreviewLayer.superlayer != nil else {
            if isSecondaryLayerFlushed == false {
                secondaryPreviewLayer.flushAndRemoveImage()
                isSecondaryLayerFlushed = true
            }
            return
        }

        isSecondaryLayerFlushed = false

        frameNotification?.read { [weak self] sampleBuffer in
            guard let self = self else {
                return
            }

            guard let sampleBuffer = sampleBuffer else {
                return
            }

            self.modify(sampleBuffer)

            if self.secondaryPreviewLayer.isReadyForMoreMediaData {
                self.secondaryPreviewLayer.enqueue(sampleBuffer)
            }
        }
    }

    func didReceiveLastVideoFrame(_ renderer: PhenixRenderer?, _ nativeVideoFrame: CVPixelBuffer?) {
        guard let nativeVideoFrame = nativeVideoFrame else {
            return
        }

        if secondaryPreviewLayer.isReadyForMoreMediaData {
            if let frame = nativeVideoFrame.createSampleBufferFrame() {
                secondaryPreviewLayer.enqueue(frame)
            }
        }
    }

    func didReceiveVideoDisplayDimensionsChange(_ renderer: PhenixRenderer?, _ dimensions: UnsafePointer<PhenixDimensions>?) {
        guard let dimensions = dimensions?.pointee else {
            delegate?.mediaDidChangeVideoDisplayDimensions(nil)
            return
        }

        os_log(.debug, log: .mediaController, "Frame dimensions changed - width: %{PRIVATE}d, height: %{PRIVATE}d, (%{PRIVATE}s)", dimensions.width, dimensions.height, streamDescription)

        delegate?.mediaDidChangeVideoDisplayDimensions(dimensions)
    }
}
