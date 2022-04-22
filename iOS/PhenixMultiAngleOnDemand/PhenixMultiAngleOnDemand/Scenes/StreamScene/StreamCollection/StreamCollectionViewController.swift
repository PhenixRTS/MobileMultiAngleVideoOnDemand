//
//  Copyright 2022 Phenix Real Time Solutions, Inc. Confidential and Proprietary. All rights reserved.
//

import Combine
import UIKit

class StreamCollectionViewController: UIViewController {
    // swiftlint:disable:next force_cast
    private var contentView: StreamCollectionView { view as! StreamCollectionView }
    private var streamsCancellable: AnyCancellable?

    var viewModel: ViewModel!
    var dataSource: DataSource!

    override func loadView() {
        let view = StreamCollectionView()
        view.delegate = self
        view.backgroundColor = .systemBackground

        self.view = view
    }

    override func viewDidLoad() {
        super.viewDidLoad()

        assert(viewModel != nil, "ViewModel should exist!")
        assert(dataSource != nil, "DataSource should exist!")

        dataSource.setCollectionView(contentView.collectionView)
        viewModel.subscribeForStreamListEvents()
        subscribeForStreamUpdates()
    }

    override func viewWillTransition(to size: CGSize, with coordinator: UIViewControllerTransitionCoordinator) {
        super.viewWillTransition(to: size, with: coordinator)
        viewModel.refreshBandwidthLimitation()
    }

    private func subscribeForStreamUpdates() {
        streamsCancellable = viewModel.streamsPublisher.sink { [weak self] streams in
            self?.dataSource.updateData(streams)
        }
    }
}

extension StreamCollectionViewController: StreamCollectionViewDelegate {
    func streamCollectionView(_ view: StreamCollectionView, didSelectItemAt indexPath: IndexPath) {
        if let stream = dataSource.data(for: indexPath) {
            viewModel.select(stream)
        }
    }
}
