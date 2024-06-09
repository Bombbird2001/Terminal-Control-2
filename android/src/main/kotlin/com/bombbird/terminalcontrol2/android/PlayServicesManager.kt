package com.bombbird.terminalcontrol2.android

import com.bombbird.terminalcontrol2.integrations.PlayServicesInterface
import com.esotericsoftware.minlog.Log
import com.google.android.gms.games.PlayGames
import com.google.android.gms.games.PlayGamesSdk


class PlayServicesManager(private val activity: AndroidLauncher): PlayServicesInterface {
    private val gamesSignInClient = PlayGames.getGamesSignInClient(activity)
    private val achievementsClient = PlayGames.getAchievementsClient(activity)

    fun initialize() {
        PlayGamesSdk.initialize(activity)
        gamesSignInClient.isAuthenticated.addOnCompleteListener {

        }
    }

    private fun requiresSignIn(allowRetry: Boolean = true, onSuccess: () -> Unit) {
        gamesSignInClient.isAuthenticated.addOnSuccessListener {
            if (it.isAuthenticated) {
                onSuccess()
            } else {
                if (allowRetry) gamesSignInClient.signIn().addOnSuccessListener {
                    requiresSignIn(false, onSuccess)
                }
            }
        }
    }

    override fun showAchievements() {
        requiresSignIn {
            achievementsClient.achievementsIntent.addOnSuccessListener { intent ->
                activity.startActivityForResult(intent, AndroidLauncher.PLAY_SHOW_ACHIEVEMENTS)
            }
        }
    }

    override fun unlockAchievement(id: String) {
        requiresSignIn {
            Log.info("PlayServicesManager", "Unlocking achievement $id")
            achievementsClient.unlock(id)
        }
    }

    override fun setAchievementSteps(id: String, steps: Int) {
        requiresSignIn {
            Log.info("PlayServicesManager", "Setting achievement $id to $steps")
            achievementsClient.setSteps(id, steps)
        }
    }

    override fun incrementAchievementSteps(id: String, steps: Int) {
        requiresSignIn {
            Log.info("PlayServicesManager", "Incrementing achievement $id by $steps")
            achievementsClient.increment(id, steps)
        }
    }

    override fun driveSaveGame() {
        // save = true
        // startDriveSignIn()
        // driveManager?.saveGame()
    }

    override fun driveLoadGame() {
        // save = false
        // startDriveSignIn()
        // driveManager?.loadGame()
    }
}