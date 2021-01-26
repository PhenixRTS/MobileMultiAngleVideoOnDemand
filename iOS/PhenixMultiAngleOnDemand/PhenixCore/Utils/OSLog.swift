//
//  Copyright 2021 Phenix Real Time Solutions, Inc. Confidential and Proprietary. All rights reserved.
//

import Foundation
import os.log

// swiftlint:disable identifier_name force_unwrapping
extension OSLog {
    private static var subsystem = Bundle.main.bundleIdentifier!

    /// Logs the main Phenix manager
    static let phenixManager = OSLog(subsystem: subsystem, category: "Phenix.Core.PhenixManager")
    static let stream = OSLog(subsystem: subsystem, category: "Phenix.Core.Stream")
    static let timeShift = OSLog(subsystem: subsystem, category: "Phenix.Core.TimeShiftWorker")
    static let actController = OSLog(subsystem: subsystem, category: "Phenix.Core.ActController")
    static let mediaController = OSLog(subsystem: subsystem, category: "Phenix.Core.MediaController")
}
