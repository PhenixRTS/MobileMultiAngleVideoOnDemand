//
//  Copyright 2021 Phenix Real Time Solutions, Inc. Confidential and Proprietary. All rights reserved.
//

import os.log
import PhenixCore
import UIKit

/// Have all instructions how to initiate application dependencies and the main coordinator
class Launcher {
    private let deeplink: PhenixDeeplinkModel?
    private weak var window: UIWindow?

    init(window: UIWindow, deeplink: PhenixDeeplinkModel? = nil) {
        self.window = window
        self.deeplink = deeplink
    }

    /// Starts all necessary application processes
    /// - Parameter completion: Provides coordinator instance, which must be contained.
    func start(completion: @escaping (MainCoordinator) -> Void) {
        os_log(.debug, log: .launcher, "Launcher started")
        defer { os_log(.debug, log: .launcher, "Launcher finished") }

        // Create launch view controller, which will hide all the async. loading.
        let vc = LaunchViewController.instantiate()

        // Create navigation controller
        let nc = UINavigationController(rootViewController: vc)
        nc.isNavigationBarHidden = true
        nc.navigationBar.isTranslucent = false

        window?.rootViewController = nc
        window?.makeKeyAndVisible()

        let unrecoverableErrorCompletion: (String?) -> Void = { description in
            DispatchQueue.main.async {
                AppDelegate.terminate(
                    afterDisplayingAlertWithTitle: "Something went wrong!",
                    message: "Application entered in unrecoverable state and will be terminated (\(description ?? "N/A"))."
                )
            }
        }

        DispatchQueue.global(qos: .userInitiated).async {
            // Keep a strong reference so that the Launcher would not be deallocated too quickly.

            // Configure necessary object instances
            os_log(.debug, log: .launcher, "Configure Phenix instance")

            let backend = self.deeplink?.backend ?? PhenixConfiguration.backend
            let pcast = self.deeplink?.uri ?? PhenixConfiguration.pcast

            let manager = PhenixManager(backend: backend, pcast: pcast)
            manager.start(unrecoverableErrorCompletion: unrecoverableErrorCompletion)

            // Create dependencies
            os_log(.debug, log: .launcher, "Create Dependency container")
            let container = DependencyContainer(phenixManager: manager)

            os_log(.debug, log: .launcher, "Start main coordinator")
            os_log(.debug, log: .launcher, "Deeplink model: %{private}s", String(describing: self.deeplink))
            let streamIDs = self.deeplink?.streamIDs ?? PhenixConfiguration.streamIDs
            let acts = self.deeplink?.acts ?? PhenixConfiguration.acts
            let coordinator = MainCoordinator(navigationController: nc, dependencyContainer: container, streamIDs: streamIDs, acts: acts)

            DispatchQueue.main.async {
                coordinator.start()
                completion(coordinator)
            }
        }
    }
}
