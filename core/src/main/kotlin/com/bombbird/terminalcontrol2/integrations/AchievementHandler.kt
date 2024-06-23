package com.bombbird.terminalcontrol2.integrations

interface AchievementHandler {
    fun showAchievements()

    fun unlockAchievement(id: String)

    fun setAchievementSteps(id: String, steps: Int)

    fun incrementAchievementSteps(id: String, steps: Int)

    fun driveSaveGame()

    fun driveLoadGame()
}

/** Stub Play Services interface object for testing, not implemented or not required */
object StubAchievementHandler: AchievementHandler {
    override fun showAchievements() {}

    override fun unlockAchievement(id: String) {}

    override fun setAchievementSteps(id: String, steps: Int) {}

    override fun incrementAchievementSteps(id: String, steps: Int) {}

    override fun driveSaveGame() {}

    override fun driveLoadGame() {}
}