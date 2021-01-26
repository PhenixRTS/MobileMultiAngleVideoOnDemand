//
//  Copyright 2021 Phenix Real Time Solutions, Inc. Confidential and Proprietary. All rights reserved.
//

import os.log
import PhenixCore
import UIKit

class MainCoordinator: Coordinator {
    let navigationController: UINavigationController
    private(set) var childCoordinators = [Coordinator]()
    private let dependencyContainer: DependencyContainer

    private var phenixManager: PhenixManager { dependencyContainer.phenixManager }

    let streamIDs: [String]
    let acts: [String]

    var phenixBackend: URL { phenixManager.backend }
    var phenixPcast: URL? { phenixManager.pcast }

    init(navigationController: UINavigationController, dependencyContainer: DependencyContainer, streamIDs: [String], acts: [String]) {
        self.navigationController = navigationController
        self.dependencyContainer = dependencyContainer
        self.streamIDs = streamIDs
        self.acts = acts
    }

    func start() {
        os_log(.debug, log: .coordinator, "Main coordinator started")

        var streams: [PhenixCore.Stream] = []

        let vc = MultiStreamViewController.instantiate()

        for streamID in streamIDs {
            let stream = PhenixCore.Stream(id: streamID)
            streams.append(stream)
        }

        vc.phenixManager = phenixManager
        vc.streams = streams
        vc.acts = acts.compactMap(Act.init)

        UIView.transition(with: self.navigationController.view) {
            self.navigationController.setViewControllers([vc], animated: false)
        }
    }
}

// MARK: - Helpers
fileprivate extension UIView {
    class func transition(with view: UIView, duration: TimeInterval = 0.25, options: UIView.AnimationOptions = [.transitionCrossDissolve], animations: (() -> Void)?) {
        UIView.transition(with: view, duration: duration, options: options, animations: animations, completion: nil)
    }
}
