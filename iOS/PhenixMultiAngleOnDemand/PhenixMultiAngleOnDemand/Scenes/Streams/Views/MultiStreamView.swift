//
//  Copyright 2021 Phenix Real Time Solutions, Inc. Confidential and Proprietary. All rights reserved.
//

import UIKit

protocol MultiStreamViewDelegate: AnyObject {
    func actConfigurationButtonTapped()
    func restartActConfiguration()
}

class MultiStreamView: UIView {
    typealias State = MultiStreamViewController.Stream.State

    private lazy var calendar: Calendar = .current
    private lazy var formatter: DateComponentsFormatter = {
        let formatter = DateComponentsFormatter()
        formatter.allowedUnits = [.second, .minute]
        formatter.unitsStyle = .positional
        formatter.zeroFormattingBehavior = .pad
        return formatter
    }()

    weak var delegate: MultiStreamViewDelegate?

    var previewLayer: CALayer {
        primaryPreview.layer
    }

    @IBOutlet private var primaryPreview: UIView!
    @IBOutlet private var secondaryPreviewCollectionView: UICollectionView!
    @IBOutlet private var selectActButton: UIButton!
    @IBOutlet private var selectActFailedButton: UIButton!
    @IBOutlet private var loadingButton: UIButton!
    @IBOutlet private var timerLabel: UILabel!
    @IBOutlet private var timerContainerView: UIView!

    @IBAction
    private func selectActButtonTapped(_ sender: UIButton) {
        delegate?.actConfigurationButtonTapped()
    }

    @IBAction
    private func selectActFailedButtonTapped(_ sender: UIButton) {
        delegate?.restartActConfiguration()
    }

    func configureUIElements() {
        let buttons: [UIButton] = [selectActButton, selectActFailedButton, loadingButton]

        buttons.forEach { button in
            button.layer.cornerRadius = 10
            button.layer.borderWidth = 1
            button.layer.borderColor = UIColor.black.withAlphaComponent(0.25).cgColor
        }

        timerLabel.text = "00:00"
        timerContainerView.layer.cornerRadius = 10

        setAct(state: .loading)
    }

    func configurePreviewCollectionView(with manager: MultiStreamPreviewCollectionViewManager) {
        secondaryPreviewCollectionView.delegate = manager
        secondaryPreviewCollectionView.dataSource = manager
        secondaryPreviewCollectionView.delaysContentTouches = false
        secondaryPreviewCollectionView.allowsSelection = true
        secondaryPreviewCollectionView.allowsMultipleSelection = false
        secondaryPreviewCollectionView.collectionViewLayout = UICollectionViewFlowLayout()
    }

    func invalidateLayout() {
        secondaryPreviewCollectionView.collectionViewLayout.invalidateLayout()
    }

    func setActButtonTitle(_ title: String) {
        selectActButton.setTitle(title, for: .normal)
    }

    func setDate(beginning: Date, current: Date) {
        let components = calendar.dateComponents([.minute, .second], from: beginning, to: current)
        timerLabel.text = formatter.string(from: components)
    }

    func setAct(state: State) {
        setControlVisibility(forActState: state)
        setControlInteraction(forActState: state)
    }
}

// MARK: - Private methods
private extension MultiStreamView {
    func setControlVisibility(forActState state: State) {
        let showSelectAct = state == .playing || state == .failure || state == .ended
        selectActButton.isHidden = showSelectAct == false
        selectActFailedButton.isHidden = state != .failure
        loadingButton.isHidden = state != .loading && state != .readyToPlay
    }

    func setControlInteraction(forActState state: State) {
        let isSelectActEnabled = state == .readyToPlay || state == .playing || state == .ended
        selectActButton.isEnabled = isSelectActEnabled
        selectActButton.alpha = isSelectActEnabled ? 1 : 0.5
    }
}
