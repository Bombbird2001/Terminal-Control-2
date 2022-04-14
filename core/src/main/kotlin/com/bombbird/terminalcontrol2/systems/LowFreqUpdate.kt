package com.bombbird.terminalcontrol2.systems

/** Interface for systems that require an additional low frequency update function */
interface LowFreqUpdate {
    fun lowFreqUpdate()
}