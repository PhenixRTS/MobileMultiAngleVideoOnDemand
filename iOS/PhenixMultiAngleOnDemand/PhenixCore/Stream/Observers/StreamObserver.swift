//
//  Copyright 2021 Phenix Real Time Solutions, Inc. Confidential and Proprietary. All rights reserved.
//

import Foundation

public protocol StreamObserver: AnyObject {
    func stream(_ stream: Stream, didChange state: Stream.State)
    func stream(_ stream: Stream, didChangePlaybackHeadDate date: Date, startDate: Date)
}

// MARK: - Stream observation
public extension Stream {
    func addStreamObserver(_ observer: StreamObserver) {
        let id = ObjectIdentifier(observer)
        observations[id] = StreamObservation(observer: observer)
    }

    func removeAudioObserver(_ observer: StreamObserver) {
        let id = ObjectIdentifier(observer)
        observations.removeValue(forKey: id)
    }
}

internal extension Stream {
    struct StreamObservation {
        weak var observer: StreamObserver?
    }

    func streamStateDidChange(state: Stream.State) {
        for (id, observation) in observations {
            // If the observer is no longer in memory, we can clean up the observation for its ID
            guard let observer = observation.observer else {
                observations.removeValue(forKey: id)
                continue
            }

            observer.stream(self, didChange: state)
        }
    }

    func streamPlaybackHeadDidChange(startDate: Date, currentDate: Date) {
        for (id, observation) in observations {
            // If the observer is no longer in memory, we can clean up the observation for its ID
            guard let observer = observation.observer else {
                observations.removeValue(forKey: id)
                continue
            }

            observer.stream(self, didChangePlaybackHeadDate: currentDate, startDate: startDate)
        }
    }
}
