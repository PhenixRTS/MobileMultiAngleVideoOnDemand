//
//  Copyright 2022 Phenix Real Time Solutions, Inc. Confidential and Proprietary. All rights reserved.
//

import Combine
import PhenixCore
import UIKit

class StreamViewController: UIViewController, Storyboarded {
    // swiftlint:disable:next force_cast
    private var contentView: StreamView { view as! StreamView }
    private var cancellables = Set<AnyCancellable>()

    var viewModel: ViewModel!
    var collectionViewController: StreamCollectionViewController!

    override func viewDidLoad() {
        super.viewDidLoad()

        assert(viewModel != nil, "ViewModel should exist!")
        assert(collectionViewController != nil, "Stream Collection View Controller should exist!")

        contentView.delegate = self
        contentView.setup()

        viewModel.getPreviewLayer = { [weak self] in
            self?.contentView.previewLayer
        }

        setupStreamCollectionViewController()

        viewModel.subscribeForStreamListEvents()

        viewModel.selectedStreamTimeShiftStatePublisher
            .sink { [weak self] state in
                self?.selectedStreamReplayStateDidChange(state)
            }
            .store(in: &cancellables)

        viewModel.selectedStreamTimeShiftDatePublisher
            .sink { [weak self] dateString in
                self?.contentView.replayTimeTitle = dateString
            }
            .store(in: &cancellables)

        viewModel.selectedActPublisher
            .sink { [weak self] act in
                self?.contentView.replayConfigurationTitle = act.rawValue
            }
            .store(in: &cancellables)

        viewModel.joinToStreams()
    }

    private func selectedStreamReplayStateDidChange(_ state: PhenixCore.TimeShift.State) {
        switch state {
        case .idle, .starting, .ready, .seeking, .seekingSucceeded:
            contentView.setReplay(state: .loading)

        case .playing:
            contentView.setReplay(state: .playing)

        case .paused:
            contentView.setReplay(state: .paused)

        case .ended:
            contentView.setReplay(state: .ended)

        case .failed:
            contentView.setReplay(state: .failure)
        }
    }

    private func setupStreamCollectionViewController() {
        addChild(collectionViewController)
        contentView.addStreamCollectionView(collectionViewController.view)
        collectionViewController.didMove(toParent: self)
    }

    private func selectAct(_ act: Act) {
        viewModel.selectAct(act)
    }
}

extension StreamViewController {
    enum ReplayState {
        case loading
        case playing
        case paused
        case ended
        case failure
    }
}

extension StreamViewController: StreamViewDelegate {
    func streamViewDidTapStartReplayButton(_ view: StreamView) {
        viewModel.playStreams()
    }

    func streamViewDidTapPauseReplayButton(_ view: StreamView) {
        viewModel.pauseStreams()
    }

    func streamViewDidTapReplayFailedButton(_ view: StreamView) {
        viewModel.reloadAct()
    }

    func streamViewDidTapConfigureReplayButton(_ view: StreamView) {
        let alertController = UIAlertController(title: "Select Act", message: nil, preferredStyle: .actionSheet)
        for act in viewModel.availableActs {
            alertController.addAction(UIAlertAction(title: act.rawValue, style: .default) { [weak self] _ in
                guard let self = self else {
                    return
                }

                self.selectAct(act)
            })
        }
        alertController.addAction(UIAlertAction(title: "Cancel", style: .cancel))
        present(alertController, animated: true)
    }
}
