//
//  Copyright 2022 Phenix Real Time Solutions, Inc. Confidential and Proprietary. All rights reserved.
//

import Foundation

struct Act: RawRepresentable, CustomStringConvertible {
    var rawValue: String
    var offset: TimeInterval

    var description: String {
        "Act(rawValue: \(rawValue), offset: \(offset))"
    }

    init?(rawValue: String) {
        let result = rawValue.split(separator: ":")

        guard result.count == 2 else {
            return nil
        }

        guard let minutes = TimeInterval(result[0]) else {
            return nil
        }

        guard let seconds = TimeInterval(result[1]) else {
            return nil
        }

        let offset = minutes * 60 + seconds

        self.rawValue = rawValue
        self.offset = offset
    }
}

extension Act {
    // swiftlint:disable:next force_unwrapping
    static let zero = Act(rawValue: "00:00")!
}
