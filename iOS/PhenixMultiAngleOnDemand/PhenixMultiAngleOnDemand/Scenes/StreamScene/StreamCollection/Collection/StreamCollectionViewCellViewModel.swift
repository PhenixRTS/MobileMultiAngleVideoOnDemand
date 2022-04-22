//
//  Copyright 2022 Phenix Real Time Solutions, Inc. Confidential and Proprietary. All rights reserved.
//

import Combine
import Foundation
import PhenixCore

extension StreamCollectionViewCell {
    class ViewModel {
        private let core: PhenixCore
        private let stream: PhenixCore.Stream

        private var cancellable = Set<AnyCancellable>()

        var getPreviewLayer: (() -> CALayer?)?
        var onStreamSelectionChange: ((Bool) -> Void)?

        // MARK: - Subscribers
        private let streamStateSubject = CurrentValueSubject<StreamState, Never>(.loading)

        // MARK: - Publishers
        lazy var streamStatePublisher = streamStateSubject.eraseToAnyPublisher()

        init(core: PhenixCore, stream: PhenixCore.Stream) {
            self.core = core
            self.stream = stream
        }

        func subscribeForEvents() {
            Publishers.CombineLatest(stream.statePublisher, stream.timeShiftStatePublisher)
                .sink { [weak self] (streamState, timeShiftState) in
                    guard let self = self else {
                        return
                    }

                    switch (streamState, timeShiftState) {
                    case (_, .ended):
                        self.streamStateSubject.send(.ended)

                    case (.offline, _):
                        self.streamStateSubject.send(.offline)

                    case (.joining, _):
                        self.streamStateSubject.send(.loading)

                    case (.ready, _), (.noStream, _):
                        self.streamStateSubject.send(.playing)
                    }
                }
                .store(in: &cancellable)

            stream.isSelectedPublisher
                .sink { [weak self] isSelected in
                    self?.streamSelectionDidChange(isSelected)
                }
                .store(in: &cancellable)
        }

        private func rendererPreview(layer: CALayer) {
            if stream.isSelected {
                core.renderThumbnailVideo(alias: stream.id, layer: layer)
            } else {
                core.renderVideo(alias: stream.id, layer: layer)
            }
        }

        private func streamSelectionDidChange(_ isSelected: Bool) {
            if let layer = getPreviewLayer?() {
                rendererPreview(layer: layer)
            }

            onStreamSelectionChange?(isSelected)
        }
    }
}

// MARK: - StreamState
extension StreamCollectionViewCell.ViewModel {
    enum StreamState {
        case offline, loading, playing, ended
    }
}
