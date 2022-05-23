package com.bombbird.terminalcontrol2.systems

/** Interface for systems that require an additional low frequency update function */
interface LowFreqUpdate {
    /** Time between low frequency updates */
    val updateTimeS: Float

    /** Timer to keep track of when to perform an update */
    var timer: Float

    /** The function to be run for each low frequency update */
    fun lowFreqUpdate()

    /**
     * The function to be run every update loop, which will check whether it is time to run the low frequency update
     * */
    fun checkLowFreqUpdate(deltaTime: Float) {
        timer -= deltaTime
        if (timer < 0f) {
            lowFreqUpdate()
            timer += updateTimeS
        }
    }
}