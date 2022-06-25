package com.bombbird.terminalcontrol2.screens.settings

import com.bombbird.terminalcontrol2.screens.BasicUIScreen

/** Abstract base class for the sub-setting screens; should not be instantiated directly */
abstract class BaseSettings: BasicUIScreen() {
    companion object {
        const val OFF = "Off"
        const val ON = "On"
        const val SECONDS_SUFFIX = " sec"
    }

    /**
     * Abstract function that should be implemented to take the relevant variables from the global context and sets the
     * settings screen elements from them
     * */
    abstract fun setToCurrentClientSettings()

    /**
     * Abstract function that should be implemented to take the relevant selections from the settings screen elements
     * and sets the global variables from them
     * */
    abstract fun updateClientSettings()
}