//
//  Copyright 2021 Phenix Real Time Solutions, Inc. Confidential and Proprietary. All rights reserved.
//

import Foundation
import os.log
import PhenixSdk

protocol TimeShiftDelegate: AnyObject {
    func timeShiftDidChangePlaybackHead(startDate: Date, currentDate: Date)
    func timeShiftDidChangeState(_ state: TimeShiftWorker.State)
}

public class TimeShiftWorker {
    private weak var renderer: PhenixRenderer!
    private let throttler: Throttler
    private let debouncer: Debouncer

    private var timeShift: PhenixTimeShift
    private var disposables = [PhenixDisposable]()
    private var playbackHeadDisposable: PhenixDisposable?
    private var endDisposable: PhenixDisposable?
    private var seekDisposable: PhenixDisposable?

    private(set) var state: State {
        didSet {
            if oldValue != state {
                os_log(.debug, log: .timeShift, "TimeShift state did change to %{PRIVATE}s", String(describing: state), streamDescription)
                delegate?.timeShiftDidChangeState(state)
            }
        }
    }

    internal weak var streamRepresentation: StreamRepresentation?
    internal weak var delegate: TimeShiftDelegate?

    init(renderer: PhenixRenderer, act: Act) throws {
        guard renderer.isSeekable else {
            os_log(.debug, log: .timeShift, "Renderer is not seek-able, do not create TimeShift worker")
            throw TimeShiftError.rendererNotSeekable
        }

        self.renderer = renderer
        self.throttler = Throttler(delay: 0.5)
        self.debouncer = Debouncer(delay: 0.5)

        self.state = .starting
        os_log(.debug, log: .timeShift, "TimeShiftWorker initialized, starting to seek the renderer")
        self.timeShift = renderer.seek(act.offset, .beginning)
    }

    func subscribeForStatusEvents() {
        os_log(.debug, log: .timeShift, "Subscribe for TimeShift status events, (%{PRIVATE}s)", streamDescription)
        timeShift.getObservableReadyForPlaybackStatus()?.subscribe(timeShiftReadyForPlaybackStatusDidChange)?.append(to: &disposables)
        timeShift.getObservableFailure()?.subscribe(timeShiftFailureDidChange)?.append(to: &disposables)
        endDisposable = timeShift.getObservableEnded()?.subscribe(timeShiftEndedDidChange)
    }

    func subscribeForPlaybackHeadEvents() {
        os_log(.debug, log: .timeShift, "Subscribe for TimeShift playback head change events, (%{PRIVATE}s)", streamDescription)
        playbackHeadDisposable = timeShift.getObservablePlaybackHead()?.subscribe(timeShiftPlaybackHeadDidChange)
    }

    func unsubscribeForPlaybackHeadEvents() {
        os_log(.debug, log: .timeShift, "Unsubscribe for TimeShift playback head change events, (%{PRIVATE}s)", streamDescription)
        playbackHeadDisposable = nil
        seekDisposable = nil
    }

    func start() {
        guard state == .readyToPlay else {
            os_log(.debug, log: .timeShift, "TimeShift is not ready, can't start - %{PRIVATE}s, (%{PRIVATE}s)", String(describing: state), streamDescription)
            return
        }
        os_log(.debug, log: .timeShift, "Play timeshift, (%{PRIVATE}s)", streamDescription)
        state = .playing
        timeShift.play()
    }

    func stop(forceFailure: Bool = false) {
        if case .failure = state {
            os_log(.debug, log: .timeShift, "TimeShift is not playing, can't stop - %{PRIVATE}s, (%{PRIVATE}s)", String(describing: state), streamDescription)
            return
        }

        if state == .ended {
            os_log(.debug, log: .timeShift, "TimeShift is not playing, can't stop - %{PRIVATE}s, (%{PRIVATE}s)", String(describing: state), streamDescription)
            return
        }

        os_log(.debug, log: .timeShift, "Stop timeshift, (%{PRIVATE}s)", streamDescription)
        state = forceFailure == true ? .failure(forced: true) : .readyToPlay
        timeShift.stop()
    }

    func set(_ act: Act) {
        // Pause and remove any of previous seek disposables
        state = .seeking

        timeShift.pause()
        seekDisposable = nil

        os_log(.debug, log: .timeShift, "Seek offset %{PRIVATE}s, (%{PRIVATE}s)", act.offset.description, streamDescription)
        seekDisposable = timeShift.seek(act.offset, .beginning)?.subscribe(timeShiftSeekRelativeTimeDidChange)
    }

    func dispose() {
        os_log(.debug, log: .timeShift, "Dispose, (%{PRIVATE}s)", streamDescription)
        disposables.removeAll()
        playbackHeadDisposable = nil
        seekDisposable = nil
    }
}

public extension TimeShiftWorker {
    enum TimeShiftError: Error {
        case rendererNotSeekable
    }

    enum State: Equatable {
        case starting
        case seeking
        case readyToPlay
        case playing
        case ended
        case failure(forced: Bool)
    }
}

private extension TimeShiftWorker {
    var streamDescription: String { streamRepresentation?.id ?? "-" }

    func timeShiftReadyForPlaybackStatusDidChange(_ changes: PhenixObservableChange<NSNumber>?) {
        guard let value = changes?.value else { return }
        let isAvailable = Bool(truncating: value)

        if case let .failure(forced) = state, forced == true {
            // Case when TimeShift has failed can mean that it failed to load the stream or it was "forced to stop" with failure state.
            // We do not want to update the state when the failure was forced.
            os_log(.debug, log: .timeShift, "TimeShift was forced to fail, to need for state change, (%{PRIVATE}s)", streamDescription)
            return
        }

        guard state != .seeking else {
            // Case when TimeShift is seeking another timestamp from which to start playing.
            // In this case we need to rely on the Seek Relative Time callback.
            os_log(.debug, log: .timeShift, "TimeShift is currently seeking, no need for state change, (%{PRIVATE}s)", streamDescription)
            return
        }

        if isAvailable == true && state == .playing {
            // If the TimeShift is already playing and the `isAvailable` is `true`, we do not need to change the state.
            os_log(.debug, log: .timeShift, "TimeShift is already playing, no need for state change, (%{PRIVATE}s)", streamDescription)
            return
        }

        if isAvailable {
            state = .readyToPlay
        } else {
            state = .starting
        }

        os_log(.debug, log: .timeShift, "Playback status changed. Available: %{PRIVATE}s, state: %{PRIVATE}s, (%{PRIVATE}s)", isAvailable.description, String(describing: state), streamDescription)
    }

    func timeShiftPlaybackHeadDidChange(_ changes: PhenixObservableChange<NSDate>?) {
        throttler.run {
            guard let date = changes?.value as Date? else { return }

            os_log(.debug, log: .timeShift, "TimeShift playback head callback with date: %{PRIVATE}s, (%{PRIVATE}s)", date.description, streamDescription)

            delegate?.timeShiftDidChangePlaybackHead(startDate: timeShift.startTime, currentDate: date)
        }
    }

    func timeShiftFailureDidChange(_ changes: PhenixObservableChange<PhenixRequestStatusObject>?) {
        guard let value = changes?.value else {
            return
        }

        os_log(.debug, log: .timeShift, "TimeShift failure callback, status: %{PRIVATE}d, (%{PRIVATE}s)", value.status.rawValue, streamDescription)

        guard value.status != .ok else {
            return
        }

        state = .failure(forced: false)
    }

    func timeShiftSeekRelativeTimeDidChange(_ changes: PhenixObservableChange<PhenixRequestStatusObject>?) {
        guard let value = changes?.value else {
            return
        }

        os_log(.debug, log: .timeShift, "TimeShift seek relative time callback, status: %{PRIVATE}d, (%{PRIVATE}s)", value.status.rawValue, streamDescription)

        switch value.status {
        case .ok:
            state = .readyToPlay

        default:
            state = .failure(forced: false)
        }
    }

    func timeShiftEndedDidChange(_ changes: PhenixObservableChange<NSNumber>?) {
        guard let value = changes?.value else { return }
        let didEnd = Bool(truncating: value)

        os_log(.debug, log: .timeShift, "TimeShift end callback, didEnd: %{PRIVATE}s, (%{PRIVATE}s)", String(describing: didEnd), streamDescription)

        if didEnd {
            state = .ended
        }
    }
}
