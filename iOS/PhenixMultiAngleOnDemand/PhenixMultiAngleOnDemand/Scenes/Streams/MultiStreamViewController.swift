//
//  Copyright 2021 Phenix Real Time Solutions, Inc. Confidential and Proprietary. All rights reserved.
//

import os.log
import PhenixCore
import UIKit

class MultiStreamViewController: UIViewController, Storyboarded {
    typealias Stream = PhenixCore.Stream

    private var collectionViewManager: MultiStreamPreviewCollectionViewManager!
    private var selectedAct: Act = .zero
    private var selectedStream: Stream? {
        didSet { selectedStreamDidChange(oldStream: oldValue, newStream: selectedStream) }
    }
    private(set) var state: Stream.State = .loading {
        didSet {
            if oldValue != state {
                os_log(.debug, log: .ui, "State change: %{PRIVATE}s", String(describing: state))
                multiStreamView.setAct(state: state)
            }
        }
    }

    var acts: [Act] = []
    var streams: [Stream] = []
    var device: UIDevice = .current
    var phenixManager: PhenixStreamJoining!

    var multiStreamView: MultiStreamView {
        view as! MultiStreamView
    }

    override func viewDidLoad() {
        super.viewDidLoad()

        assert(phenixManager != nil, "Phenix Manager is required")

        updateStreamSelection()

        collectionViewManager = MultiStreamPreviewCollectionViewManager()
        collectionViewManager.streams = streams
        collectionViewManager.isStreamSelected = { [weak self] stream in
            self?.selectedStream == stream
        }
        collectionViewManager.itemSelectionHandler = { [weak self] selectedIndexPath in
            guard let self = self else { return }
            let stream = self.streams[selectedIndexPath.row]
            self.select(stream)
        }

        multiStreamView.delegate = self
        multiStreamView.configureUIElements()
        multiStreamView.configurePreviewCollectionView(with: collectionViewManager)

        for stream in streams {
            join(stream)
        }
    }

    override func viewDidAppear(_ animated: Bool) {
        super.viewDidAppear(animated)
        updateStreamSelection()
    }

    override func viewWillTransition(to size: CGSize, with coordinator: UIViewControllerTransitionCoordinator) {
        super.viewWillTransition(to: size, with: coordinator)
        multiStreamView.invalidateLayout()
    }
}

// MARK: - Private methods
private extension MultiStreamViewController {
    func join(_ stream: Stream) {
        stream.addStreamObserver(self)
        stream.set(selectedAct)
        phenixManager.join(stream)
    }

    func select(_ stream: Stream) {
        guard selectedStream != stream else { return }

        os_log(.debug, log: .ui, "Select stream: %{PRIVATE}s", stream.description)

        selectedStream = stream
        stream.addPrimaryLayer(to: multiStreamView.previewLayer)
    }

    /// Sets selected layer on the currently selected stream or the first stream in the list
    func updateStreamSelection() {
        if let stream = selectedStream ?? streams.first {
            select(stream)
        } else {
            assertionFailure("No streams provided.")
        }
    }

    func set(_ act: Act) {
        for stream in streams {
            stream.set(act)
        }

        selectedAct = act
        state = .loading
    }

    /// Try to play streams
    ///
    /// If at least one stream is still in *Stream.State.loading* state, it will not start playing streams.
    /// This is necessary to make streams synchronous.
    func playStreamsIfPossible() {
        guard streams.contains(where: { $0.state == .loading }) == false else {
            state = .loading
            return
        }

        playStreams()
    }

    /// Play streams
    ///
    /// Will play those streams, which are in *Stream.State.readyToPlay* state.
    func playStreams() {
        streams.forEachAct(withState: .readyToPlay) { $0.startAct() }
        selectedStream?.actController?.startObservingPlaybackHead()
        state = .playing
    }

    func showAvailableActs() {
        let ac = UIAlertController(title: "Select act", message: nil, preferredStyle: .actionSheet)
        for act in acts {
            ac.addAction(UIAlertAction(title: act.title, style: .default) { [weak self] _ in
                self?.multiStreamView.setActButtonTitle(act.title)
                self?.set(act)
            })
        }
        ac.addAction(UIAlertAction(title: "Cancel", style: .cancel))
        present(ac, animated: true)
    }

    // MARK: - Property Observers

    func selectedStreamDidChange(oldStream: Stream?, newStream: Stream?) {
        oldStream?.actController?.stopObservingPlaybackHead()

        guard let stream = newStream else { return }

        multiStreamView.setAct(state: stream.state)

        if stream.state == .playing {
            stream.actController?.startObservingPlaybackHead()
        }
    }
}

// MARK: - StreamObserver
extension MultiStreamViewController: StreamObserver {
    func stream(_ stream: Stream, didChangePlaybackHeadDate date: Date, startDate: Date) {
        DispatchQueue.main.async { [weak self] in
            self?.multiStreamView.setDate(beginning: startDate, current: date)
        }
    }

    func stream(_ stream: Stream, didChange state: Stream.State) {
        DispatchQueue.main.async { [weak self] in
            guard let self = self else { return }

            if state == .ended {
                self.state = .ended
                return
            }

            self.playStreamsIfPossible()

            guard state == .playing else { return }
            guard stream == self.selectedStream else { return }

            stream.media?.setAudio(enabled: true)
            stream.actController?.startObservingPlaybackHead()
        }
    }
}

// MARK: - MultiStreamViewDelegate
extension MultiStreamViewController: MultiStreamViewDelegate {
    func actConfigurationButtonTapped() {
        showAvailableActs()
    }

    func restartActConfiguration() {
        // Do nothing
    }
}

// MARK: - Helpers
fileprivate extension Sequence where Element == PhenixCore.Stream {
    func forEachAct(withState state: Element.State, do handle: (StreamActController) -> Void) {
        let acts = compactMap { $0.actController }

        for act in acts where act.state == state {
            handle(act)
        }
    }
}
