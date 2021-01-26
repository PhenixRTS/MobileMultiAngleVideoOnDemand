//
//  Copyright 2021 Phenix Real Time Solutions, Inc. Confidential and Proprietary. All rights reserved.
//

import Foundation
import os.log
import PhenixSdk

enum PhenixOptionBuilder {
    static func createPCastExpressOptions(backend: URL, pcast: URL?, unrecoverableErrorCallback: @escaping (_ description: String?) -> Void) -> PhenixPCastExpressOptions {
        var builder: PhenixPCastExpressOptionsBuilder = PhenixPCastExpressFactory.createPCastExpressOptionsBuilder()
            .withBackendUri(backend.absoluteString)
            .withUnrecoverableErrorCallback { _, description in
                os_log(.error, log: .phenixManager, "Unrecoverable Error: %{PRIVATE}s", String(describing: description))
                unrecoverableErrorCallback(description)
            }

        if let pcast = pcast {
            builder = builder.withPCastUri(pcast.absoluteString)
        }

        return builder.buildPCastExpressOptions()
    }

    static func createSubscriberOptions(streamID: String) -> PhenixSubscribeOptions {
        PhenixPCastExpressFactory.createSubscribeOptionsBuilder()
            .withCapabilities(["on-demand"])
            .withStreamId(streamID)
            .buildSubscribeOptions()
    }
}
