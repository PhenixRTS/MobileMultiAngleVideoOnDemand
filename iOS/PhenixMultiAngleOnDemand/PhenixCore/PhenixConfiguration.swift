//
//  Copyright 2021 Phenix Real Time Solutions, Inc. Confidential and Proprietary. All rights reserved.
//

import Foundation
import PhenixSdk

// swiftlint:disable force_unwrapping
public enum PhenixConfiguration {
    public static var backend = URL(string: "https://vdms-consumerexperience.phenixrts.com/pcast")!
    public static var pcast: URL?
    public static var streamIDs: [String] = [
        "us-southwest#PHX-AD-3.bW6xGJ57.20200901.PSazwzvT",
        "us-southwest#PHX-AD-3.d52ZDAtI.20200901.PSshZlR0",
        "us-northeast#US-ASHBURN-AD-1.kggKhyr8.20200901.PSaOPG77",
        "us-southwest#PHX-AD-3.CoNJprr3.20200901.PS2q9Y6I",
        "us-northeast#US-ASHBURN-AD-3.VV31mw4J.20200901.PSDD2zfx"
    ]
    public static var acts: [String] = ["0:06", "1:57", "3:45", "7:19", "9:05", "12:50", "14:37", "16:52"]
}
