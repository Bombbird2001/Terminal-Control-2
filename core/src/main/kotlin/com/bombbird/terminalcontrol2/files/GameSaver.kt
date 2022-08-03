package com.bombbird.terminalcontrol2.files

import com.bombbird.terminalcontrol2.global.GAME

fun componentListTest() {
    GAME.gameServer?.apply {
        aircraft.values().forEach {
            it.entity.components
        }
    }
}