//
//  Copyright 2021 Phenix Real Time Solutions, Inc. Confidential and Proprietary. All rights reserved.
//

import PhenixCore
import UIKit

class MultiStreamPreviewCollectionViewManager: NSObject {
    typealias Stream = PhenixCore.Stream

    private let application: UIApplication = .shared
    /// Contains references to the shown secondary previews
    ///
    /// There should only be one secondary preview layer visible at the time,
    /// so all of the other secondary preview layers can be removed from its superview this collection.
    private var secondaryPreviewLayers: Set<CALayer> = []
    /// Selected item indicator KVO for the layer frame updates
    private var selectionLayerObservation: NSKeyValueObservation?
    /// Selected item indicator
    private lazy var selectionLayer: CALayer = {
        let layer = CALayer()
        layer.borderColor = UIColor.red.cgColor
        layer.borderWidth = 3
        layer.cornerRadius = 5
        return layer
    }()

    var streams: [Stream] = []
    var isStreamSelected: ((Stream) -> Bool)?
    var itemSelectionHandler: ((IndexPath) -> Void)?

    deinit {
        selectionLayerObservation = nil
    }
}

// MARK: - Private methods
private extension MultiStreamPreviewCollectionViewManager {
    func markCellSelected(_ cell: UICollectionViewCell) {
        selectionLayer.frame = cell.layer.bounds
        cell.layer.addSublayer(selectionLayer)

        selectionLayerObservation?.invalidate()
        selectionLayerObservation = cell.layer.observe(\.bounds, options: [.new]) { [weak self] _, change in
            self?.selectionLayer.frame = change.newValue ?? .zero
        }
    }

    func configureSelectedCell(_ cell: MultiStreamPreviewCollectionViewCell, stream: Stream) {
        // If stream is selected, then it means that this stream's primary preview layer is displayed in the hero view.
        // So we need to add the secondary preview layer to this cell.

        // Remove all previously added secondary preview layers (there should be only one secondary layer visible).
        // We need to be sure that any other secondary preview layers are not visible to not waste the performance.
        secondaryPreviewLayers.forEach { $0.removeFromSuperlayer() }
        secondaryPreviewLayers.removeAll()

        stream.addSecondaryLayer(to: cell.contentView.layer)
        stream.media?.setAudio(enabled: true)

        // Save the secondary preview layer inside the preview collection.
        secondaryPreviewLayers.insert(stream.secondaryPreviewLayer)

        markCellSelected(cell)
    }

    func configureUnselectedCell(_ cell: MultiStreamPreviewCollectionViewCell, stream: Stream) {
        stream.addPrimaryLayer(to: cell.contentView.layer)
        stream.media?.setAudio(enabled: false)
    }
}

// MARK: - UICollectionViewDataSource
extension MultiStreamPreviewCollectionViewManager: UICollectionViewDataSource {
    func collectionView(_ collectionView: UICollectionView, numberOfItemsInSection section: Int) -> Int {
        streams.count
    }

    func collectionView(_ collectionView: UICollectionView, cellForItemAt indexPath: IndexPath) -> UICollectionViewCell {
        let cell = collectionView.dequeueReusableCell(withReuseIdentifier: "Cell", for: indexPath) as! MultiStreamPreviewCollectionViewCell

        let stream = streams[indexPath.item]

        if isStreamSelected?(stream) == true {
            configureSelectedCell(cell, stream: stream)

            // Special case when the collection view cells are first initialized,
            // the collection view does not know that this cell is selected.
            // So we need to select it programmatically to get the
            // `collectionView(_:didSelectItemAt:)` and `collectionView(_:didDeselectItemAt:)`
            // delegate methods working correctly.
            if collectionView.indexPathsForSelectedItems?.isEmpty != false {
                collectionView.selectItem(at: indexPath, animated: false, scrollPosition: .top)
            }
        } else {
            configureUnselectedCell(cell, stream: stream)
        }

        cell.process(state: stream.state)
        stream.addStreamObserver(cell)

        return cell
    }
}

// MARK: - UICollectionViewDelegateFlowLayout
extension MultiStreamPreviewCollectionViewManager: UICollectionViewDelegateFlowLayout {
    func collectionView(_ collectionView: UICollectionView, layout collectionViewLayout: UICollectionViewLayout, sizeForItemAt indexPath: IndexPath) -> CGSize {
        if application.statusBarOrientation.isPortrait {
            let width: CGFloat = (collectionView.bounds.size.width - 15) / 2
            return CGSize(width: width, height: 100)
        } else {
            let width: CGFloat = collectionView.bounds.size.width - 10
            return CGSize(width: width, height: 100)
        }
    }

    func collectionView(_ collectionView: UICollectionView, layout collectionViewLayout: UICollectionViewLayout, minimumLineSpacingForSectionAt section: Int) -> CGFloat { 5 }

    func collectionView(_ collectionView: UICollectionView, layout collectionViewLayout: UICollectionViewLayout, minimumInteritemSpacingForSectionAt section: Int) -> CGFloat { 5 }

    func collectionView(_ collectionView: UICollectionView, layout collectionViewLayout: UICollectionViewLayout, insetForSectionAt section: Int) -> UIEdgeInsets {
        if application.statusBarOrientation.isPortrait {
            return UIEdgeInsets(top: 5, left: 5, bottom: 0, right: 5)
        } else {
            return UIEdgeInsets(top: 0, left: 5, bottom: 0, right: 5)
        }
    }

    func collectionView(_ collectionView: UICollectionView, didSelectItemAt indexPath: IndexPath) {
        itemSelectionHandler?(indexPath)

        guard let cell = collectionView.cellForItem(at: indexPath) as? MultiStreamPreviewCollectionViewCell else { return }
        let stream = streams[indexPath.row]

        configureSelectedCell(cell, stream: stream)
    }

    func collectionView(_ collectionView: UICollectionView, didDeselectItemAt indexPath: IndexPath) {
        guard let cell = collectionView.cellForItem(at: indexPath) as? MultiStreamPreviewCollectionViewCell else { return }
        let stream = streams[indexPath.row]

        configureUnselectedCell(cell, stream: stream)
    }
}
