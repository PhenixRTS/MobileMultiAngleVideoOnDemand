//
//  Copyright 2021 Phenix Real Time Solutions, Inc. Confidential and Proprietary. All rights reserved.
//

import Foundation

public struct Act: CustomStringConvertible {
    public var title: String
    public var offset: TimeInterval

    public var description: String {
        "Act, title: \(title), offset: \(offset)"
    }

    public init?(time: String) {
        let result = time.split(separator: ":")

        guard let minutes = TimeInterval(result[0]) else {
            return nil
        }

        guard let seconds = TimeInterval(result[1]) else {
            return nil
        }

        let timeinterval: TimeInterval = minutes * 60 + seconds

        self.title = time
        self.offset = timeinterval
    }
}

public extension Act {
    // swiftlint:disable force_unwrapping
    static let zero = Act(time: "00:00")!
}
