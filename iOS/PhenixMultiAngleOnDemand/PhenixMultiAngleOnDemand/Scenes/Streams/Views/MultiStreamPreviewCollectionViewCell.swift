//
//  Copyright 2021 Phenix Real Time Solutions, Inc. Confidential and Proprietary. All rights reserved.
//

import os.log
import PhenixCore
import UIKit

class MultiStreamPreviewCollectionViewCell: UICollectionViewCell {
    typealias Stream = PhenixCore.Stream

    enum State {
        case offline, loading, playing, ended
    }

    private lazy var activityIndicatorView: UIActivityIndicatorView = {
        let view = UIActivityIndicatorView()
        view.translatesAutoresizingMaskIntoConstraints = false
        view.startAnimating()
        view.color = .systemRed
        return view
    }()

    private lazy var offlineLabel: UILabel = {
        let label = UILabel()
        label.translatesAutoresizingMaskIntoConstraints = false
        label.text = "OFFLINE"
        label.textColor = .white
        label.backgroundColor = UIColor.gray.withAlphaComponent(0.50)
        label.font = .boldSystemFont(ofSize: 12)
        label.layer.cornerRadius = 5
        return label
    }()

    private lazy var endLabel: UILabel = {
        let label = UILabel()
        label.translatesAutoresizingMaskIntoConstraints = false
        label.text = "STREAM ENDED"
        label.textColor = .white
        label.backgroundColor = UIColor.gray.withAlphaComponent(0.50)
        label.font = .boldSystemFont(ofSize: 12)
        label.layer.cornerRadius = 5
        return label
    }()

    var state: State = .offline {
        didSet {
            switch state {
            case .offline:
                remove(endLabel)
                remove(activityIndicatorView)
                add(offlineLabel)
            case .loading:
                remove(endLabel)
                remove(offlineLabel)
                add(activityIndicatorView)
                activityIndicatorView.startAnimating()
            case .playing:
                remove(endLabel)
                remove(offlineLabel)
                remove(activityIndicatorView)
            case .ended:
                remove(offlineLabel)
                remove(activityIndicatorView)
                add(endLabel)
            }
            contentView.layoutIfNeeded()
        }
    }

    override init(frame: CGRect) {
        super.init(frame: frame)
        setup()
    }

    required init?(coder: NSCoder) {
        super.init(coder: coder)
        setup()
    }

    override func prepareForReuse() {
        super.prepareForReuse()
        // When the cell will be prepared for the reuse, we need to remove all the preview layers,
        // so that there would not be a situation that other (previous) streams would flicker
        // before showing the correct video stream on the reused cell.
        contentView.layer.sublayers?
            .filter { $0.name == VideoLayer.previewLayerName }
            .forEach { layer in
                os_log(.debug, log: .ui, "Remove video layer: %{PRIVATE}s", layer.description)
                layer.removeFromSuperlayer()
            }
    }

    func process(state: Stream.State) {
        switch state {
        case .playing:
            self.state = .playing

        case .loading,
             .readyToPlay:
            self.state = .loading

        case .ended:
            self.state = .ended

        case .failure:
            self.state = .offline
        }
    }
}

// MARK: - Private methods
private extension MultiStreamPreviewCollectionViewCell {
    func setup() {
        contentView.layer.cornerRadius = 5
    }

    func add(_ view: UIView) {
        contentView.addSubview(view)
        NSLayoutConstraint.activate([
            view.centerXAnchor.constraint(equalTo: contentView.centerXAnchor),
            view.centerYAnchor.constraint(equalTo: contentView.centerYAnchor)
        ])
    }

    func remove(_ view: UIView) {
        view.removeFromSuperview()
    }
}

// MARK: - StreamObserver
extension MultiStreamPreviewCollectionViewCell: StreamObserver {
    func stream(_ stream: Stream, didChangePlaybackHeadDate date: Date, startDate: Date) {
        // Do nothing
    }

    func stream(_ stream: Stream, didChange state: Stream.State) {
        DispatchQueue.main.async { [weak self] in
            self?.process(state: state)
        }
    }
}
