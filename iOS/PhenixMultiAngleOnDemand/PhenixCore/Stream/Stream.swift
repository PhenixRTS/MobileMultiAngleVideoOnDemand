//
//  Copyright 2021 Phenix Real Time Solutions, Inc. Confidential and Proprietary. All rights reserved.
//

import Foundation
import os.log
import PhenixSdk

internal protocol StreamRepresentation: AnyObject {
    var id: String { get }
}

public class Stream: StreamRepresentation {
    internal var renderer: PhenixRenderer?
    internal var subscriber: PhenixExpressSubscriber?
    internal var observations = [ObjectIdentifier: StreamObservation]()

    public let id: String
    public private(set) var state: State = .loading {
        didSet { streamStateDidChange(state: state) }
    }
    public private(set) var actController: StreamActController?
    public private(set) var media: StreamMediaController?
    public private(set) var act: Act

    // MARK: - Video preview layers

    /// Renders the main video on provided layer (hero view)
    public private(set) var primaryPreviewLayer: VideoLayer

    /// Renders the frame-ready output on provided layer (thumbnail view)
    public private(set) var secondaryPreviewLayer: VideoLayer

    // MARK: - Initialization

    public init(id: String) {
        self.id = id
        self.act = .zero

        self.primaryPreviewLayer = VideoLayer()
        self.secondaryPreviewLayer = VideoLayer()
        self.secondaryPreviewLayer.videoGravity = .resizeAspectFill
    }

    /// Add primary preview layer to the provided layer as a sublayer
    ///
    /// Method automatically sets the destination size to the preview layer and creates a KVO observer for size changes.
    /// - Parameter layer: Destination layer provided by the app
    public func addPrimaryLayer(to layer: CALayer) {
        primaryPreviewLayer.add(to: layer)
    }

    /// Add primary preview layer to the provided layer as a sublayer
    ///
    /// Method automatically sets the destination size to the preview layer and creates a KVO observer for size changes.
    /// - Parameter layer: Destination layer provided by the app
    public func addSecondaryLayer(to layer: CALayer) {
        media?.requestLastVideoFrame()
        secondaryPreviewLayer.add(to: layer)
    }

    public func set(_ act: Act) {
        os_log(.debug, log: .stream, "Change act to: %{PRIVATE}s, (%{PRIVATE}s)", act.description, description)
        self.act = act
        actController?.set(act)
    }
}

public extension Stream {
    enum State {
        case loading
        case readyToPlay
        case playing
        case paused
        case ended
        case failure
    }
}

// MARK: - CustomStringConvertible
extension Stream: CustomStringConvertible {
    public var description: String {
        "Stream, id: \(id), stream: \(state), act: \(act), media: \(media?.description ?? "-"),  act controller: \(actController?.description ?? "-")"
    }
}

// MARK: - Private methods
private extension Stream {
    func setupRenderer() {
        guard let subscriber = subscriber else {
            return
        }

        renderer = subscriber.createRenderer()

        guard renderer?.isSeekable == true else {
            assertionFailure("Renderer is not seekable")
            state = .failure
            return
        }

        let status = renderer?.startSuspended(primaryPreviewLayer)

        if status != .ok {
            assertionFailure("Renderer could not start")
            state = .failure
            return
        }
    }

    func setupMediaController() {
        guard let subscriber = subscriber else {
            return
        }

        guard let videoTrack = subscriber.getVideoTracks()?.first else {
            return
        }

        guard let renderer = renderer else {
            return
        }

        media = StreamMediaController(subscriber: subscriber, renderer: renderer, secondaryPreviewLayer: secondaryPreviewLayer, streamRepresentation: self)
        media?.delegate = self
        media?.setAudio(enabled: false)
        media?.subscribe(videoTrack)
    }

    func setupActController() {
        guard let renderer = renderer else {
            return
        }

        actController = StreamActController(renderer: renderer, act: act, streamRepresentation: self)
        actController?.delegate = self
        actController?.subscribe()
    }
}

// MARK: - Handler methods
internal extension Stream {
    func subscriberHandler(status: PhenixRequestStatus, subscriber: PhenixExpressSubscriber?) {
        os_log(.debug, log: .stream, "Stream state did change with state %{PUBLIC}d, (%{PRIVATE}s)", status.rawValue, description)
        self.subscriber = subscriber

        switch status {
        case .ok:
            setupRenderer()
            setupMediaController()
            setupActController()

            os_log(.debug, log: .stream, "Stream set up finished, (%{PRIVATE}s)", description)

        default:
            state = .failure
        }
    }
}

// MARK: - ActDelegate
extension Stream: ActDelegate {
    func actDidChangeState(_ state: Stream.State) {
        self.state = state
    }

    func actDidChangePlaybackHead(startDate: Date, currentDate: Date) {
        streamPlaybackHeadDidChange(startDate: startDate, currentDate: currentDate)
    }
}

// MARK: - MediaDelegate
extension Stream: MediaDelegate {
    func mediaDidChangeVideoDisplayDimensions(_ dimensions: PhenixDimensions?) {
        // Workaround for the TimeShift end event.
        // If we do not receive the dimensions of the video, it can be that the video has ended.
        if dimensions == nil {
            self.state = .ended
        }
    }
}

// MARK: - Hashable
extension Stream: Hashable {
    public static func == (lhs: Stream, rhs: Stream) -> Bool {
        lhs.id == rhs.id
    }

    public func hash(into hasher: inout Hasher) {
        hasher.combine(id)
    }
}
