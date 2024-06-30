package com.bombbird.terminalcontrol2.utilities

import com.badlogic.gdx.Gdx
import com.bombbird.terminalcontrol2.files.getAvailableSaveGames
import com.bombbird.terminalcontrol2.global.PREFS_FILE_NAME
import com.bombbird.terminalcontrol2.integrations.AchievementHandler

class AchievementManager(private val achievementHandler: AchievementHandler) {
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
    private val knockKnockAchievement = "CgkIkdmytrkNEAIQJA"
    private val iAmGodAchievement = "CgkIkdmytrkNEAIQDg"
    private var iAmGodCounter = 0
    private val vectorVictorAchievement = "CgkIkdmytrkNEAIQCg"
    private var vectorVictorMinuteCounter = 0

    /** Show achievements UI */
    fun showAchievements() {
        achievementHandler.showAchievements()
    }

    /**
     * Sets initial arrival achievements for the player, should be called only once when the game is first launched
     * after achievements are added
     */
    fun setInitialArrivalAchievements() {
        val prefs = Gdx.app.getPreferences(PREFS_FILE_NAME)
        if (prefs.getBoolean("initialArrivalAchievementsSet", false)) return

        val arrivalSum = getAvailableSaveGames().values().toArray().sumOf { it.landed }
        if (arrivalSum > 0) achievementHandler.unlockAchievement(firstTouchdownAchievement)
        arrivalAchievements.forEach {
            achievementHandler.setAchievementSteps(it, arrivalSum)
        }

        prefs.putBoolean("initialArrivalAchievementsSet", true)
        prefs.flush()
    }

    /** Increments achievement arrival count at [airportIcao] */
    fun incrementArrival(airportIcao: String) {
        achievementHandler.unlockAchievement(firstTouchdownAchievement)
        arrivalAchievements.forEach {
            achievementHandler.incrementAchievementSteps(it, 1)
        }

        airportArrivalAchievements[airportIcao]?.let {
            achievementHandler.incrementAchievementSteps(it, 1)
        }
    }

    /** Increments multiplayer achievement arrival count */
    fun incrementMultiplayerArrival() {
        multiplayerArrivalAchievements.forEach {
            achievementHandler.incrementAchievementSteps(it, 1)
        }
    }

    fun unlockKnockKnock() {
        achievementHandler.unlockAchievement(knockKnockAchievement)
    }

    fun resetGodCounter() {
        iAmGodCounter = 0
    }

    /** Increments the "I Am God" achievement counter by [seconds], and unlocks it once 30 minutes is up */
    fun incrementGodCounter(seconds: Int) {
        iAmGodCounter += seconds
        if (iAmGodCounter >= 1800) {
            achievementHandler.unlockAchievement(iAmGodAchievement)
            iAmGodCounter = Int.MIN_VALUE
        }
    }

    /**
     * Increments the "Vector Victor" achievement counter by [seconds], and increments the achievement step every
     * minute
     */
    fun incrementVectorVictorCounter(seconds: Int) {
        vectorVictorMinuteCounter += seconds
        if (vectorVictorMinuteCounter >= 60) {
            achievementHandler.incrementAchievementSteps(vectorVictorAchievement, vectorVictorMinuteCounter / 60)
            vectorVictorMinuteCounter %= 60
        }
    }
}