//
//  Copyright 2022 Phenix Real Time Solutions, Inc. Confidential and Proprietary. All rights reserved.
//

import Foundation
import PhenixDeeplink
import PhenixCore

class AppSession {
    enum ConfigurationError: Error {
        case missingMandatoryDeeplinkProperties
        case mismatch
        case mismatchAliasAndStreamTokenCount
    }

    private let rawActs: [String]
    private let rawStreamIDs: [String]

    let streamToken: String
    let authToken: String
    let configurations: [PhenixCore.Stream.Configuration]
    let acts: [Act]

    var selectedAct: Act = .zero

    init(deeplink: PhenixDeeplinkModel) throws {
        guard let authToken = deeplink.authToken,
              let streamToken = deeplink.streamToken,
              let rawActs = deeplink.acts,
              let streamIDs = deeplink.streamIDs else {
                  throw ConfigurationError.missingMandatoryDeeplinkProperties
              }

        self.rawActs = rawActs
        self.authToken = authToken
        self.streamToken = streamToken
        self.rawStreamIDs = streamIDs

        self.acts = rawActs.compactMap { Act(rawValue: $0) }
        self.configurations = streamIDs.map { id in
            PhenixCore.Stream.Configuration(id: id, streamToken: streamToken)
        }
    }

    func validate(_ deeplink: PhenixDeeplinkModel) throws {
        if let value = deeplink.authToken, value != authToken {
            throw ConfigurationError.mismatch
        }

        if let value = deeplink.streamToken, value != streamToken {
            throw ConfigurationError.mismatch
        }

        if let value = deeplink.streamIDs, value != rawStreamIDs {
            throw ConfigurationError.mismatch
        }

        if let value = deeplink.acts, value != rawActs {
            throw ConfigurationError.mismatch
        }
    }
}

extension AppSession: Equatable {
    static func == (lhs: AppSession, rhs: AppSession) -> Bool {
        lhs.authToken == rhs.authToken
        && lhs.streamToken == rhs.streamToken
        && lhs.rawActs == rhs.rawActs
        && lhs.rawStreamIDs == rhs.rawStreamIDs
    }
}
