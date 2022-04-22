//
//  Copyright 2022 Phenix Real Time Solutions, Inc. Confidential and Proprietary. All rights reserved.
//

import Combine
import PhenixCore
import UIKit

class StreamCollectionViewCell: UICollectionViewCell {
    private static let offlineBackgroundColor = UIColor(patternImage: UIImage(named: "OfflineNoise")!)

    private lazy var previewView: UIView = {
        let view = UIView()
        return view
    }()

    private lazy var offlineLabel: UILabel = {
        let label = UILabel()
        label.text = "OFFLINE"
        label.font = .boldSystemFont(ofSize: 12)
        label.textColor = .white
        label.backgroundColor = .gray.withAlphaComponent(0.8)
        label.layer.cornerRadius = 5
        return label
    }()

    private lazy var loadingLabel: UILabel = {
        let label = UILabel()
        label.text = "LOADING"
        label.font = .boldSystemFont(ofSize: 12)
        label.textColor = .white
        label.backgroundColor = .gray.withAlphaComponent(0.8)
        label.layer.cornerRadius = 5
        return label
    }()

    private lazy var endLabel: UILabel = {
        let label = UILabel()
        label.text = "STREAM ENDED"
        label.font = .boldSystemFont(ofSize: 12)
        label.textColor = .white
        label.backgroundColor = .gray.withAlphaComponent(0.8)
        label.layer.cornerRadius = 5
        return label
    }()

    private lazy var selectionLayer: CALayer = {
        let layer = CALayer()
        layer.borderColor = UIColor.red.cgColor
        layer.borderWidth = 3
        layer.cornerRadius = 5
        return layer
    }()

    private var viewModel: ViewModel?
    private var cancellable: AnyCancellable?

    override init(frame: CGRect) {
        super.init(frame: .zero)
        setup()
    }

    required init?(coder: NSCoder) {
        fatalError("init(coder:) has not been implemented")
    }

    func configure(viewModel: ViewModel) {
        self.viewModel = viewModel

        viewModel.getPreviewLayer = { [weak self] in
            self?.previewView.layer
        }
        viewModel.onStreamSelectionChange = { [weak self] isSelected in
            self?.streamSelectionDidChange(isSelected)
        }
        cancellable = viewModel.streamStatePublisher.sink { [weak self] state in
            self?.endLabel.isHidden = state != .ended
            self?.offlineLabel.isHidden = state != .offline
            self?.loadingLabel.isHidden = state != .loading
        }

        viewModel.subscribeForEvents()
    }

    // MARK: - Private methods

    private func setup() {
        contentView.backgroundColor = Self.offlineBackgroundColor
        setupElements()
    }

    private func setupElements() {
        endLabel.translatesAutoresizingMaskIntoConstraints = false
        previewView.translatesAutoresizingMaskIntoConstraints = false
        offlineLabel.translatesAutoresizingMaskIntoConstraints = false
        loadingLabel.translatesAutoresizingMaskIntoConstraints = false

        addSubview(previewView)
        addSubview(endLabel)
        addSubview(offlineLabel)
        addSubview(loadingLabel)

        NSLayoutConstraint.activate([
            previewView.topAnchor.constraint(equalTo: topAnchor),
            previewView.bottomAnchor.constraint(equalTo: bottomAnchor),
            previewView.leadingAnchor.constraint(equalTo: leadingAnchor),
            previewView.trailingAnchor.constraint(equalTo: trailingAnchor),

            offlineLabel.centerXAnchor.constraint(equalTo: centerXAnchor),
            offlineLabel.centerYAnchor.constraint(equalTo: centerYAnchor),

            endLabel.centerXAnchor.constraint(equalTo: centerXAnchor),
            endLabel.centerYAnchor.constraint(equalTo: centerYAnchor),

            loadingLabel.centerXAnchor.constraint(equalTo: centerXAnchor),
            loadingLabel.centerYAnchor.constraint(equalTo: centerYAnchor)
        ])
    }

    private func setSelected(_ isSelected: Bool) {
        if isSelected {
            selectionLayer.frame = layer.bounds
            layer.addSublayer(selectionLayer)
        } else {
            selectionLayer.removeFromSuperlayer()
        }
    }

    // MARK: - Completions

    private func streamSelectionDidChange(_ isSelected: Bool) {
        setSelected(isSelected)
    }
}
