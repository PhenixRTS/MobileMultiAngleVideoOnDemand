//
//  Copyright 2022 Phenix Real Time Solutions, Inc. Confidential and Proprietary. All rights reserved.
//

import Combine
import Foundation
import os.log
import PhenixCore

extension StreamViewController {
    class ViewModel {
        private static let logger = OSLog(identifier: "StreamViewController.ViewModel")

        private static var dateFormatter: DateComponentsFormatter = {
            let formatter = DateComponentsFormatter()
            formatter.allowedUnits = [.minute, .second]
            formatter.unitsStyle = .positional
            formatter.zeroFormattingBehavior = .pad
            return formatter
        }()

        private let core: PhenixCore
        private let session: AppSession

        private var streamsCancellable: AnyCancellable?
        private var streamStateCancellables: Set<AnyCancellable> = []
        private var streamSelectionCancellables: Set<AnyCancellable> = []
        private var streamTimeShiftStatesCancellable: AnyCancellable?
        private var selectedStreamTimeShiftHeadCancellable: AnyCancellable?
        private var selectedStreamTimeShiftStateCancellable: AnyCancellable?

        // MARK: - Subscribers
        private let selectedActSubject: CurrentValueSubject<Act, Never>
        private let selectedStreamTimeShiftDateSubject: PassthroughSubject<String, Never>
        private let selectedStreamTimeShiftStateSubject: CurrentValueSubject<PhenixCore.TimeShift.State, Never>

        // MARK: - Publishers
        lazy var selectedActPublisher = selectedActSubject.eraseToAnyPublisher()
        lazy var selectedStreamTimeShiftDatePublisher = selectedStreamTimeShiftDateSubject.eraseToAnyPublisher()
        lazy var selectedStreamTimeShiftStatePublisher = selectedStreamTimeShiftStateSubject.eraseToAnyPublisher()

        var availableActs: [Act] { session.acts }

        var getPreviewLayer: (() -> CALayer?)?

        init(core: PhenixCore, session: AppSession) {
            self.core = core
            self.session = session

            self.selectedActSubject = .init(session.selectedAct)
            self.selectedStreamTimeShiftDateSubject = .init()
            self.selectedStreamTimeShiftStateSubject = .init(.idle)
        }

        func joinToStreams() {
            for configuration in session.configurations {
                core.joinToStream(configuration: configuration)
            }
        }

        func subscribeForStreamListEvents() {
            streamsCancellable = core.streamsPublisher
                .sink { [weak self] streams in
                    guard let self = self else {
                        return
                    }

                    self.streamStateCancellables.removeAll()
                    self.streamSelectionCancellables.removeAll()

                    self.streamTimeShiftStatesCancellable = Publishers.MergeMany(streams.map(\.timeShiftStatePublisher))
                        .debounce(for: .milliseconds(500), scheduler: DispatchQueue.main)
                        .sink { [weak self] _ in
                            guard let self = self else {
                                return
                            }

                            self.streamTimeShiftStateDidChange(self.core.streams)
                        }

                    if let stream = streams.first {
                        self.core.selectStream(id: stream.id, isSelected: true)
                    }

                    streams.forEach {
                        self.subscribeForStreamStateEvents($0)
                        self.subscribeForStreamSelectionEvents($0)
                    }
                }
        }

        func selectAct(_ act: Act) {
            os_log(.debug, log: Self.logger, "Select act: %{private}s", act.description)

            session.selectedAct = act
            selectedActSubject.send(act)

            reloadAct()
        }

        func reloadAct() {
            os_log(.debug, log: Self.logger, "Reload act: %{private}s", selectedActSubject.value.description)

            core.streams.forEach { stream in
                seekTimeShift(stream, offset: selectedActSubject.value.offset)
            }
        }

        func playStreams() {
            os_log(.debug, log: Self.logger, "Play streams")

            core.streams
                .filter {
                    return $0.timeShiftState == .ready
                    || $0.timeShiftState == .seekingSucceeded
                    || $0.timeShiftState == .paused
                    || $0.timeShiftState == .ended
                }
                .forEach(playTimeShift)
        }

        func pauseStreams() {
            os_log(.debug, log: Self.logger, "Pause streams")

            core.streams
                .filter { $0.timeShiftState == .playing }
                .forEach(pauseTimeShift)
        }

        // MARK: - Private methods

        private func date(from timeInterval: TimeInterval) -> String {
            Self.dateFormatter.string(from: timeInterval) ?? ""
        }

        // MARK: - Subscriptions

        private func subscribeForStreamSelectionEvents(_ stream: PhenixCore.Stream) {
            stream.isSelectedPublisher
                .sink { [weak self, weak stream] isSelected in
                    guard let self = self, let stream = stream else {
                        return
                    }

                    if isSelected {
                        self.selectedStreamDidChange(stream)
                    }
                }
                .store(in: &streamSelectionCancellables)
        }

        private func subscribeForStreamStateEvents(_ stream: PhenixCore.Stream) {
            stream.statePublisher
                .sink { [weak self, weak stream] state in
                    guard let self = self, let stream = stream else {
                        return
                    }

                    self.streamStateDidChange(state, stream: stream)
                }
                .store(in: &streamStateCancellables)
        }

        private func subscribeForStreamTimeShiftStateEvents(_ stream: PhenixCore.Stream) {
            selectedStreamTimeShiftStateCancellable = stream.timeShiftStatePublisher
                .sink { [weak self] state in
                    self?.selectedStreamTimeShiftStateSubject.send(state)
                }
        }

        private func subscribeForStreamTimeShiftHeadEvents(_ stream: PhenixCore.Stream) {
            selectedStreamTimeShiftHeadCancellable = stream.timeShiftHeadPublisher
                .sink { [weak self] timeInterval in
                    guard let self = self else {
                        return
                    }

                    let dateString = self.date(from: timeInterval)
                    self.selectedStreamTimeShiftDateSubject.send(dateString)
                }
        }

        // MARK: - Callbacks

        private func selectedStreamDidChange(_ stream: PhenixCore.Stream) {
            let layer = getPreviewLayer?()
            core.renderVideo(alias: stream.id, layer: layer)
            subscribeForStreamTimeShiftStateEvents(stream)
            subscribeForStreamTimeShiftHeadEvents(stream)
        }

        private func streamStateDidChange(_ state: PhenixCore.Stream.State, stream: PhenixCore.Stream) {
            if state == .ready {
                createTimeShift(stream, offset: selectedActSubject.value.offset)
            }
        }

        private func streamTimeShiftStateDidChange(_ streams: [PhenixCore.Stream]) {
            // Do not proceed if at least one of the streams time shift isn't started yet.
            guard streams.contains(where: { $0.timeShiftState == .idle }) == false else {
                return
            }

            // Do not proceed if at least one of the streams time shift is still loading.
            guard streams.contains(where: { $0.timeShiftState == .starting }) == false else {
                return
            }

            // Gather all of the streams which are ready to play and start playing.
            streams
                .filter { $0.timeShiftState == .ready || $0.timeShiftState == .seekingSucceeded }
                .forEach(self.playTimeShift)
        }

        // MARK: - Time Shift

        private func createTimeShift(_ stream: PhenixCore.Stream, offset: TimeInterval) {
            core.createTimeShift(alias: stream.id, on: .seek(offset: offset, from: .beginning))
        }

        private func playTimeShift(_ stream: PhenixCore.Stream) {
            core.playTimeShift(alias: stream.id)
        }

        private func pauseTimeShift(_ stream: PhenixCore.Stream) {
            core.pauseTimeShift(alias: stream.id)
        }

        private func seekTimeShift(_ stream: PhenixCore.Stream, offset: TimeInterval) {
            core.seekTimeShift(alias: stream.id, offset: offset)
        }
    }
}
