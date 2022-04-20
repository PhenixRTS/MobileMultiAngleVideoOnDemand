/*
 * Copyright 2022 Phenix Real Time Solutions, Inc. Confidential and Proprietary. All rights reserved.
 */

package com.phenixrts.suite.phenixcore.repositories.models

data class PhenixChannel(
    val alias: String,
    val isAudioEnabled: Boolean,
    val isVideoEnabled: Boolean,
    val isSelected: Boolean,
    val timeShiftHead: Long,
    val timeShiftState: PhenixTimeShiftState,
    val channelState: PhenixChannelState,
) {
    override fun equals(other: Any?): Boolean {
        if (this === other) return true
        if (javaClass != other?.javaClass) return false

        other as PhenixChannel

        if (alias != other.alias) return false
        if (isAudioEnabled != other.isAudioEnabled) return false
        if (isVideoEnabled != other.isVideoEnabled) return false
        if (isSelected != other.isSelected) return false
        if (timeShiftState != other.timeShiftState) return false

        return true
    }

    override fun hashCode(): Int {
        var result = alias.hashCode()
        result = 31 * result + isAudioEnabled.hashCode()
        result = 31 * result + isVideoEnabled.hashCode()
        result = 31 * result + isSelected.hashCode()
        result = 31 * result + timeShiftState.hashCode()
        return result
    }
}
