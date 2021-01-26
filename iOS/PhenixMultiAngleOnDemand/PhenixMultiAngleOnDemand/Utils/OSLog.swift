//
//  Copyright 2021 Phenix Real Time Solutions, Inc. Confidential and Proprietary. All rights reserved.
//

import Foundation
import os.log

// swiftlint:disable identifier_name force_unwrapping
extension OSLog {
    private static var subsystem = Bundle.main.bundleIdentifier!

    // MARK: - Application components
    static let coordinator = OSLog(subsystem: subsystem, category: "Phenix.App.Coordinator")
    static let launcher = OSLog(subsystem: subsystem, category: "Phenix.App.Launcher")
    static let ui = OSLog(subsystem: subsystem, category: "Phenix.App.UserInterface")
}
