//
//  Copyright 2021 Phenix Real Time Solutions, Inc. Confidential and Proprietary. All rights reserved.
//

import Foundation

public struct DeeplinkModel: DeeplinkModelProvider {
    var streamIDs: [String]?
    var acts: [String]?
    var uri: URL?
    var backend: URL?

    public init?(components: URLComponents) {
        if let string = components.queryItems?.first(where: { $0.name == "streamIDs" })?.value {
            self.streamIDs = string
                .split(separator: ",")
                .map(String.init)
        }

        if let string = components.queryItems?.first(where: { $0.name == "acts" })?.value {
            self.acts = string
                .split(separator: ",")
                .map(String.init)
        }

        if let string = components.queryItems?.first(where: { $0.name == "uri" })?.value {
            self.uri = URL(string: string)
        }

        if let string = components.queryItems?.first(where: { $0.name == "backend" })?.value {
            self.backend = URL(string: string)
        }
    }
}
