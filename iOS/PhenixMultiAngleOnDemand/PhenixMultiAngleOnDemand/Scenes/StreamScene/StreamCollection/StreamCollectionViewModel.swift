//
//  Copyright 2022 Phenix Real Time Solutions, Inc. Confidential and Proprietary. All rights reserved.
//

import Combine
import PhenixCore

extension StreamCollectionViewController {
    final class ViewModel {
        private let core: PhenixCore
        private let session: AppSession

        private var device: UIDevice = .current
        private var streamListEventCancellable: AnyCancellable?
        private var streamEventsCancellables: Set<AnyCancellable> = []

        // MARK: - Publishers
        let streamsPublisher: AnyPublisher<[PhenixCore.Stream], Never>

        init(core: PhenixCore, session: AppSession) {
            self.core = core
            self.session = session
            self.streamsPublisher = core.streamsPublisher
        }

        func select(_ stream: PhenixCore.Stream) {
            if let previousStream = core.streams.first(where: \.isSelected) {
                core.selectStream(id: previousStream.id, isSelected: false)
            }

            core.selectStream(id: stream.id, isSelected: true)
        }

        func subscribeForStreamListEvents() {
            streamListEventCancellable = core.streamsPublisher
                .sink { [weak self] streams in
                    guard let self = self else {
                        return
                    }

                    self.streamEventsCancellables.removeAll()

                    streams.forEach { stream in
                        Publishers.CombineLatest(stream.statePublisher, stream.isSelectedPublisher)
                            .sink { [weak self, weak stream] _ in
                                guard let self = self, let stream = stream else {
                                    return
                                }

                                self.refreshStreamState(stream)
                            }
                            .store(in: &self.streamEventsCancellables)
                    }
                }
        }

        func refreshBandwidthLimitation() {
            core.streams.forEach(setStreamBandwidthLimitation)
        }

        // MARK: - Private methods

        private func setStreamAudio(_ stream: PhenixCore.Stream, enabled: Bool) {
            core.setAudioEnabled(alias: stream.id, enabled: enabled)
        }

        private func refreshStreamState(_ stream: PhenixCore.Stream) {
            setStreamAudio(stream, enabled: stream.state == .ready && stream.isSelected)
            setStreamBandwidthLimitation(stream)
        }

        private func setStreamBandwidthLimitation(_ stream: PhenixCore.Stream) {
            switch (stream.isSelected, device.orientation.isLandscape) {
            case (true, true):
                core.removeBandwidthLimitation(alias: stream.id)

            case (true, false):
                core.setBandwidthLimitation(alias: stream.id, bandwidth: Self.mainPreviewBandwidth)

            case (false, true):
                core.setBandwidthLimitation(alias: stream.id, bandwidth: Self.offscreenBandwidth)

            case (false, false):
                core.setBandwidthLimitation(alias: stream.id, bandwidth: Self.thumbnailBandwidth)
            }
        }
    }
}

fileprivate extension StreamCollectionViewController.ViewModel {
    static var mainPreviewBandwidth: UInt64 = 1_200_000
    static let thumbnailBandwidth: UInt64 = 735_000
    static let offscreenBandwidth: UInt64 = 1_000
}
