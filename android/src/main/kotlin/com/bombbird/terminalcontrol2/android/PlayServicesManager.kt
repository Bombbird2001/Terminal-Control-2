package com.bombbird.terminalcontrol2.android

import android.content.Context
import android.graphics.Bitmap
import androidx.datastore.core.DataStore
import androidx.datastore.preferences.core.Preferences
import androidx.datastore.preferences.core.booleanPreferencesKey
import androidx.datastore.preferences.core.edit
import androidx.datastore.preferences.preferencesDataStore
import com.bombbird.terminalcontrol2.integrations.AchievementHandler
import com.bombbird.terminalcontrol2.integrations.CloudSaveHandler
import com.bombbird.terminalcontrol2.utilities.FileLog
import com.google.android.gms.games.PlayGames
import com.google.android.gms.games.PlayGamesSdk
import com.google.android.gms.games.SnapshotsClient
import com.google.android.gms.games.snapshot.SnapshotMetadataChange
import com.google.android.gms.tasks.OnSuccessListener
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.launch
import androidx.core.graphics.createBitmap
import com.badlogic.gdx.graphics.Pixmap


private val PREFS_PLAY_GAMES_SIGN_IN_FAILED = booleanPreferencesKey("play_games_sign_in_failed")
private val Context.dataStore: DataStore<Preferences> by preferencesDataStore(name = "main_store")

class PlayServicesManager(private val activity: AndroidLauncher): AchievementHandler, CloudSaveHandler {
    private val gamesSignInClient = PlayGames.getGamesSignInClient(activity)
    private val achievementsClient = PlayGames.getAchievementsClient(activity)
    private val snapshotsClient = PlayGames.getSnapshotsClient(activity)

    fun initialize() {
        PlayGamesSdk.initialize(activity)
        gamesSignInClient.isAuthenticated.addOnCompleteListener {
            CoroutineScope(Dispatchers.IO).launch {
                setSignInFailed(!it.isSuccessful || !it.result.isAuthenticated)
            }
        }
    }

    private suspend fun isPlayGamesSignInFailed(): Boolean {
        return activity.dataStore.data.map { prefs ->
            prefs[PREFS_PLAY_GAMES_SIGN_IN_FAILED] ?: false
        }.first()
    }

    private suspend fun setSignInFailed(signInFailed: Boolean) {
        activity.dataStore.edit { prefs ->
            prefs[PREFS_PLAY_GAMES_SIGN_IN_FAILED] = signInFailed
        }
    }

    private fun requiresSignIn(alwaysTryLogin: Boolean = false, onSuccess: () -> Unit) {
        gamesSignInClient.isAuthenticated.addOnSuccessListener {
            CoroutineScope(Dispatchers.IO).launch {
                if (it.isAuthenticated) {
                    if (isPlayGamesSignInFailed()) setSignInFailed(false)
                    onSuccess()
                } else {
                    // If already failed login before, don't try again unless alwaysTryLogin is true
                    if (!alwaysTryLogin && isPlayGamesSignInFailed()) return@launch
                    doManualSignIn(onSuccess)
                }
            }
        }
    }

    private fun doManualSignIn(onSuccess: () -> Unit) {
        gamesSignInClient.signIn().addOnSuccessListener { signInResult ->
            if (signInResult.isAuthenticated) {
                CoroutineScope(Dispatchers.IO).launch {
                    if (isPlayGamesSignInFailed()) setSignInFailed(false)
                }
                onSuccess()
            } else {
                CoroutineScope(Dispatchers.IO).launch {

                    setSignInFailed(true)
                }
            }
        }
    }

    override fun showAchievements() {
        requiresSignIn(alwaysTryLogin = true) {
            achievementsClient.achievementsIntent.addOnSuccessListener { intent ->
                activity.startActivityForResult(intent, AndroidLauncher.PLAY_SHOW_ACHIEVEMENTS)
            }
        }
    }

    override fun unlockAchievement(id: String) {
        requiresSignIn {
            FileLog.info("PlayServicesManager", "Unlocking achievement $id")
            achievementsClient.unlock(id)
        }
    }

    override fun setAchievementSteps(id: String, steps: Int) {
        requiresSignIn {
            FileLog.info("PlayServicesManager", "Setting achievement $id to $steps")
            achievementsClient.setSteps(id, steps)
        }
    }

    override fun incrementAchievementSteps(id: String, steps: Int) {
        requiresSignIn {
            FileLog.info("PlayServicesManager", "Incrementing achievement $id by $steps")
            achievementsClient.increment(id, steps)
        }
    }

    override fun showSavedGames() {
        val intentTask = snapshotsClient.getSelectSnapshotIntent(
            "See Saved Games", true, true, SnapshotsClient.DISPLAY_LIMIT_NONE
        )

        intentTask.addOnSuccessListener(OnSuccessListener { intent ->
            activity.startActivityForResult(intent, AndroidLauncher.SHOW_SAVED_GAMES)
        })
    }

    override fun saveGame(uniqueId: String, saveData: String, description: String, pixmap: Pixmap) {
        val snapshot = snapshotsClient.open(uniqueId, true)
        snapshot.addOnSuccessListener {
            it?.data?.let { snapshot ->
                snapshot.snapshotContents.writeBytes(saveData.encodeToByteArray())
                val bitmap = createBitmap(pixmap.width, pixmap.height, Bitmap.Config.ARGB_8888)
                bitmap.copyPixelsFromBuffer(pixmap.pixels)
                val metadataChange = SnapshotMetadataChange.Builder()
                    .setDescription(description)
                    .setCoverImage(bitmap)
                    .build()
                snapshotsClient.commitAndClose(snapshot, metadataChange)
            } ?: run {
                FileLog.error("PlayServicesManager", "Failed to open snapshot $uniqueId")
                return@addOnSuccessListener
            }
        }
    }
}