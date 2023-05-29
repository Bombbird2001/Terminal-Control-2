package com.bombbird.terminalcontrol2.screens.settings

import com.bombbird.terminalcontrol2.screens.BasicUIScreen

/** Abstract base class for the sub-setting screens (game specific settings); should not be instantiated directly */
abstract class BaseGameSettings: BasicUIScreen() {
    /**
     * Abstract function that should be implemented to take the relevant variables from the game context and sets the
     * settings screen elements from them
     */
    abstract fun setToCurrentGameSettings()

    /**
     * Abstract function that should be implemented to take the relevant selections from the settings screen elements
     * and sets the relevant game variables from them
     */
    abstract fun updateCurrentGameSettings()
}