//
//  Copyright 2022 Phenix Real Time Solutions, Inc. Confidential and Proprietary. All rights reserved.
//

import UIKit

class StreamView: UIView {
    typealias ReplayState = StreamViewController.ReplayState

    private static let offlineBackgroundColor = UIColor(patternImage: UIImage(named: "OfflineNoise")!)

    @IBOutlet private var previewView: UIView!
    @IBOutlet private var streamCollectionContainerView: UIView!
    @IBOutlet private var timeLabel: UILabel!
    @IBOutlet private var timeContainerView: UIView!

    @IBOutlet private var configureButton: UIButton!
    @IBOutlet private var configurationFailedButton: UIButton!
    @IBOutlet private var loadingButton: UIButton!
    @IBOutlet private var playButton: UIButton!
    @IBOutlet private var pauseButton: UIButton!

    weak var delegate: StreamViewDelegate?

    var previewLayer: CALayer {
        previewView.layer
    }

    var replayTimeTitle: String {
        get { timeLabel.text ?? "" }
        set { timeLabel.text = newValue }
    }

    var replayConfigurationTitle: String {
        get { configureButton.title(for: .normal) ?? "" }
        set { configureButton.setTitle(newValue, for: .normal) }
    }

    func setup() {
        setupElements()
        previewView.backgroundColor = Self.offlineBackgroundColor
    }

    func setReplay(state: ReplayState) {
        setControlVisibility(replayState: state)
        setControlInteraction(replayState: state)
    }

    func addStreamCollectionView(_ view: UIView) {
        view.translatesAutoresizingMaskIntoConstraints = false
        streamCollectionContainerView.addSubview(view)

        NSLayoutConstraint.activate([
            view.topAnchor.constraint(equalTo: streamCollectionContainerView.topAnchor),
            view.leadingAnchor.constraint(equalTo: streamCollectionContainerView.leadingAnchor),
            view.trailingAnchor.constraint(equalTo: streamCollectionContainerView.trailingAnchor),
            view.bottomAnchor.constraint(equalTo: streamCollectionContainerView.bottomAnchor)
        ])
    }

    // MARK: - Private methods

    private func setupElements() {
        [configureButton, configurationFailedButton, loadingButton, playButton, pauseButton]
            .forEach { $0.withBorderColor(.black) }

        timeLabel.text = "00:00"
        timeContainerView.layer.cornerRadius = 10
        setReplay(state: .loading)
    }

    @IBAction private func configureReplayButtonTapped(_ sender: UIButton) {
        delegate?.streamViewDidTapConfigureReplayButton(self)
    }

    @IBAction private func startReplayButtonTapped(_ sender: UIButton) {
        delegate?.streamViewDidTapStartReplayButton(self)
    }

    @IBAction private func pauseReplayButtonTapped(_ sender: UIButton) {
        delegate?.streamViewDidTapPauseReplayButton(self)
    }

    @IBAction private func replayFailedButtonTapped(_ sender: UIButton) {
        delegate?.streamViewDidTapReplayFailedButton(self)
    }

    private func setControlVisibility(replayState state: ReplayState) {
        let showsConfiguration = state == .playing || state == .paused || state == .failure || state == .ended
        configureButton.isVisible = showsConfiguration
        configurationFailedButton.isVisible = state == .failure
        loadingButton.isVisible = state == .loading

        playButton.isVisible = state == .paused
        pauseButton.isVisible = state == .playing
    }

    private func setControlInteraction(replayState state: ReplayState) {
        let isConfigurationButtonEnabled = state == .playing || state == .paused || state == .ended
        configureButton.isEnabled = isConfigurationButtonEnabled
        configureButton.alpha = isConfigurationButtonEnabled ? 1 : 0.5
    }
}

private extension UIButton {
    @discardableResult
    func withBorderColor(_ color: UIColor) -> Self {
        layer.cornerRadius = 10
        layer.borderWidth = 1
        layer.borderColor = color.withAlphaComponent(0.25).cgColor
        return self
    }

    var isVisible: Bool {
        get { !isHidden }
        set { isHidden = newValue == false }
    }
}
