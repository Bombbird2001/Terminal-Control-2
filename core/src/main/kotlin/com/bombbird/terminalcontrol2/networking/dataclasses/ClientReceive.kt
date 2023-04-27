package com.bombbird.terminalcontrol2.networking.dataclasses

import com.bombbird.terminalcontrol2.screens.RadarScreen

interface ClientReceive {
    /**
     * Handles this data received on client
     * @param rs the [RadarScreen] object
     */
    fun handleClientReceive(rs: RadarScreen)
}