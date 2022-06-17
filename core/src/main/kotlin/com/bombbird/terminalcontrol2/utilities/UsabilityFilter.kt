package com.bombbird.terminalcontrol2.utilities

/** Helper interface that contains data and functions to determine whether a SID, STAR or approach is valid for use
 * under the given conditions */
interface UsabilityFilter {
    companion object {
        const val DAY_AND_NIGHT: Byte = 0
        const val DAY_ONLY: Byte = 1
        const val NIGHT_ONLY: Byte = 2
    }

    val timeRestriction: Byte
}