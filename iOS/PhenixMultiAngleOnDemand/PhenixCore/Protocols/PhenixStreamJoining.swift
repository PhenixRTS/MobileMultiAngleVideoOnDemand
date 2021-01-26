//
//  Copyright 2021 Phenix Real Time Solutions, Inc. Confidential and Proprietary. All rights reserved.
//

import Foundation
import os.log
import PhenixSdk

public protocol PhenixStreamJoining: AnyObject {
    func join(_ stream: Stream)
}

extension PhenixManager: PhenixStreamJoining {
    public func join(_ stream: Stream) {
        privateQueue.async { [weak self] in
            guard let self = self else { return }
            let options = PhenixOptionBuilder.createSubscriberOptions(streamID: stream.id)
            self.pcastExpress.subscribe(options) { status, subscriber, _ in
                stream.subscriberHandler(status: status, subscriber: subscriber)
            }
        }
    }
}
