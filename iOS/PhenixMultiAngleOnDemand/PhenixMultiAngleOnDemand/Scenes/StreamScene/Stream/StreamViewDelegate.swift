//
//  Copyright 2022 Phenix Real Time Solutions, Inc. Confidential and Proprietary. All rights reserved.
//

import Foundation

protocol StreamViewDelegate: AnyObject {
    func streamViewDidTapStartReplayButton(_ view: StreamView)
    func streamViewDidTapPauseReplayButton(_ view: StreamView)
    func streamViewDidTapConfigureReplayButton(_ view: StreamView)
    func streamViewDidTapReplayFailedButton(_ view: StreamView)
}
