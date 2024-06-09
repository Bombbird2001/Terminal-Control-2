package com.bombbird.terminalcontrol2.utilities

import com.badlogic.gdx.Gdx
import com.bombbird.terminalcontrol2.files.getAvailableSaveGames
import com.bombbird.terminalcontrol2.global.PREFS_FILE_NAME
import com.bombbird.terminalcontrol2.integrations.PlayServicesInterface

class AchievementManager(private val playServicesInterface: PlayServicesInterface) {
    private val firstTouchdownAchievement = "CgkIkdmytrkNEAIQBg"
    private val arrivalAchievements = arrayOf(
        "CgkIkdmytrkNEAIQBw",
        "CgkIkdmytrkNEAIQCA",
        "CgkIkdmytrkNEAIQCQ",
        "CgkIkdmytrkNEAIQCw",
        "CgkIkdmytrkNEAIQDA",
        "CgkIkdmytrkNEAIQDQ"
    )
    private val multiplayerArrivalAchievements = arrayOf(
        "CgkIkdmytrkNEAIQIg",
        "CgkIkdmytrkNEAIQIw"
    )
    private val airportArrivalAchievements = mutableMapOf(
        Pair("TCTP", "CgkIkdmytrkNEAIQAA"),
        Pair("TCSS", "CgkIkdmytrkNEAIQAQ"),
        Pair("TCWS", "CgkIkdmytrkNEAIQAg"),
        Pair("TCSL", "CgkIkdmytrkNEAIQAw"),
        Pair("TCDD", "CgkIkdmytrkNEAIQFg"),
        Pair("TCTT", "CgkIkdmytrkNEAIQBA"),
        Pair("TCAA", "CgkIkdmytrkNEAIQBQ"),
        Pair("TCBB", "CgkIkdmytrkNEAIQDw"),
        Pair("TCOO", "CgkIkdmytrkNEAIQEA"),
        Pair("TCBE", "CgkIkdmytrkNEAIQEQ"),
        Pair("TCHH", "CgkIkdmytrkNEAIQEg"),
        Pair("TCMC", "CgkIkdmytrkNEAIQEw"),
        Pair("TCBD", "CgkIkdmytrkNEAIQFA"),
        Pair("TCBS", "CgkIkdmytrkNEAIQFQ"),
        Pair("TCMD", "CgkIkdmytrkNEAIQFw"),
        Pair("TCPG", "CgkIkdmytrkNEAIQGA"),
        Pair("TCPO", "CgkIkdmytrkNEAIQGQ"),
        Pair("TCPB", "CgkIkdmytrkNEAIQGg"),
        Pair("TCSF", "CgkIkdmytrkNEAIQHQ"),
        Pair("TCOA", "CgkIkdmytrkNEAIQHA"),
        Pair("TCSJ", "CgkIkdmytrkNEAIQGw")
    )

    /**
     * Sets initial arrival achievements for the player, should be called only once when the game is first launched
     * after achievements are added
     */
    fun setInitialArrivalAchievements() {
        val prefs = Gdx.app.getPreferences(PREFS_FILE_NAME)
        if (prefs.getBoolean("initialArrivalAchievementsSet", false)) return

        val arrivalSum = getAvailableSaveGames().values().toArray().sumOf { it.landed }
        if (arrivalSum > 0) playServicesInterface.unlockAchievement(firstTouchdownAchievement)
        arrivalAchievements.forEach {
            playServicesInterface.setAchievementSteps(it, arrivalSum)
        }

        prefs.putBoolean("initialArrivalAchievementsSet", true)
    }

    /** Increments achievement arrival count at [airportIcao] */
    fun incrementArrival(airportIcao: String) {
        playServicesInterface.unlockAchievement(firstTouchdownAchievement)
        arrivalAchievements.forEach {
            playServicesInterface.incrementAchievementSteps(it, 1)
        }

        airportArrivalAchievements[airportIcao]?.let {
            playServicesInterface.incrementAchievementSteps(it, 1)
        }
    }

    /** Increments multiplayer achievement arrival count */
    fun incrementMultiplayerArrival() {
        multiplayerArrivalAchievements.forEach {
            playServicesInterface.incrementAchievementSteps(it, 1)
        }
    }
}