//
//  Copyright 2021 Phenix Real Time Solutions, Inc. Confidential and Proprietary. All rights reserved.
//

import os.log
import PhenixSdk

internal protocol ActDelegate: AnyObject {
    func actDidChangeState(_ state: Stream.State)
    func actDidChangePlaybackHead(startDate: Date, currentDate: Date)
}

public class StreamActController {
    private static let timeout: TimeInterval = 20

    private weak var renderer: PhenixRenderer!
    private var worker: TimeShiftWorker?
    private var isSubscribed: Bool
    private var stateChangeTimeoutWorkItem: DispatchWorkItem?

    internal weak var streamRepresentation: StreamRepresentation?
    internal weak var delegate: ActDelegate?

    public private(set) var state: Stream.State = .loading {
        didSet { stateDidChange(state) }
    }

    init(renderer: PhenixRenderer, act: Act, streamRepresentation: StreamRepresentation? = nil) {
        self.renderer = renderer
        self.isSubscribed = false
        self.streamRepresentation = streamRepresentation

        self.setupTimeShift(with: act)
    }

    public func subscribe() {
        os_log(.debug, log: .actController, "Subscribe for time shift worker events, (%{PRIVATE}s)", streamDescription)
        isSubscribed = true
        worker?.subscribeForStatusEvents()
    }

    public func playAct() {
        guard state != .failure else { return }
        os_log(.debug, log: .actController, "Start act, (%{PRIVATE}s)", streamDescription)
        worker?.play()
    }

    public func stopAct() {
        os_log(.debug, log: .actController, "Stop act, (%{PRIVATE}s)", streamDescription)
        worker?.stop()
    }

    public func pauseAct() {
        os_log(.debug, log: .actController, "Pause act, (%{PRIVATE}s)", streamDescription)
        worker?.pause()
    }

    public func startObservingPlaybackHead() {
        guard state != .failure else { return }
        os_log(.debug, log: .actController, "Observe playback head, (%{PRIVATE}s)", streamDescription)
        worker?.subscribeForPlaybackHeadEvents()
    }

    public func stopObservingPlaybackHead() {
        os_log(.debug, log: .actController, "Stop observing playback head, (%{PRIVATE}s)", streamDescription)
        worker?.unsubscribeForPlaybackHeadEvents()
    }

    deinit {
        worker?.dispose()
        worker = nil
    }
}

// MARK: - CustomStringConvertible
extension StreamActController: CustomStringConvertible {
    public var description: String {
        "StreamActController, timeshift: \(worker != nil ? "exists" : "-")"
    }
}

// MARK: - Internal methods
internal extension StreamActController {
    func set(_ act: Act) {
        os_log(.debug, log: .actController, "Set Act: %{PRIVATE}s, (%{PRIVATE}s)", act.description, streamDescription)
        worker?.set(act)
    }
}

// MARK: - Private methods
private extension StreamActController {
    var streamDescription: String { streamRepresentation?.id ?? "-" }

    func setupTimeShift(with act: Act) {
        os_log(.debug, log: .actController, "Setup TimeShift worker, (%{PRIVATE}s)", streamDescription)
        do {
            state = .loading

            // Always before creating a new TimeShift worker, previous worker must call `dispose` method.
            worker?.dispose()

            os_log(.debug, log: .actController, "TimeShift worker options: %{PRIVATE}s, (%{PRIVATE}s)", act.description, streamDescription)

            let worker = try TimeShiftWorker(renderer: renderer, act: act)
            self.worker = worker
            worker.streamRepresentation = streamRepresentation
            worker.delegate = self

            if isSubscribed {
                // If app was already subscribed for the TimeShift previously, we need to automatically re-subscribe if TimeShift worker gets re-created.
                subscribe()
            }
        } catch {
            os_log(.debug, log: .actController, "Failed to setup TimeShift worker, (%{PRIVATE}s)", streamDescription)
            state = .failure
        }
    }

    func processTimeShiftFailure() {
        os_log(.debug, log: .actController, "Process TimeShift failure, (%{PRIVATE}s)", streamDescription)
        worker?.stop()
        state = .failure
    }

    func processTimeShiftEnd() {
        os_log(.debug, log: .actController, "Process TimeShift end, (%{PRIVATE}s)", streamDescription)
        state = .ended
    }

    func stateDidChange(_ state: Stream.State) {
        os_log(.debug, log: .actController, "TimeShift state did change to %{PRIVATE}s, (%{PRIVATE}s)", String(describing: state), streamDescription)
        // Reset timeout counter
        resetConnectionTimeoutCountdown()

        if state == .loading {
            // In case, if the state does not change to different state in specific amount of time, consider that the TimeShift has reached timeout and set the state to `.failure`.
            startConnectionTimeoutCountdown()
        }

        delegate?.actDidChangeState(state)
    }

    func startConnectionTimeoutCountdown() {
        os_log(.debug, log: .actController, "Start connection timer countdown, (%{PRIVATE}s)", streamDescription)
        let workItem = DispatchWorkItem { [weak self] in
            self?.connectionTimeoutReached()
        }

        stateChangeTimeoutWorkItem = workItem
        DispatchQueue.main.asyncAfter(deadline: .now() + Self.timeout, execute: workItem)
    }

    func resetConnectionTimeoutCountdown() {
        guard stateChangeTimeoutWorkItem != nil else { return }
        os_log(.debug, log: .actController, "Reset connection timer countdown, (%{PRIVATE}s)", streamDescription)
        stateChangeTimeoutWorkItem?.cancel()
        stateChangeTimeoutWorkItem = nil
    }

    func connectionTimeoutReached() {
        os_log(.debug, log: .actController, "Connection timeout reached, (%{PRIVATE}s)", streamDescription)
        stateChangeTimeoutWorkItem = nil
        worker?.stop(forceFailure: true)
    }
}

// MARK: - TimeShiftDelegate
extension StreamActController: TimeShiftDelegate {
    func timeShiftDidChangePlaybackHead(startDate: Date, currentDate: Date) {
        delegate?.actDidChangePlaybackHead(startDate: startDate, currentDate: currentDate)
    }

    func timeShiftDidChangeState(_ state: TimeShiftWorker.State) {
        switch state {
        case .starting,
             .seeking:
            self.state = .loading
        case .readyToPlay:
            self.state = .readyToPlay
        case .playing:
            self.state = .playing
        case .paused:
            self.state = .paused
        case .ended:
            self.state = .ended
        case .failure:
            self.state = .failure
        }
    }
}
