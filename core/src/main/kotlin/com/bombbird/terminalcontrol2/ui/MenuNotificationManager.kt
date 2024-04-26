package com.bombbird.terminalcontrol2.ui

import com.badlogic.gdx.Gdx
import com.badlogic.gdx.scenes.scene2d.Stage
import com.badlogic.gdx.utils.Align
import com.bombbird.terminalcontrol2.global.PREFS_FILE_NAME
import ktx.collections.GdxArray

class MenuNotificationManager(private val stage: Stage) {
    private class MenuNotification(val prefKey: String, val title: String, val message: String, val height: Int, val width: Int)

    /**
     * Notification array, added in order of highest to lowest priority (i.e. if a notification is shown, all
     * notifications added after will not be shown even if it has not been shown ever before to the user
     */
    private val notifications = GdxArray<MenuNotification>().apply {
        add(
            MenuNotification("beta-welcome-msg-shown", "Welcome to the beta!", "Thank you for" +
                    " joining the beta! This is an early version of the game, so there are likely to be bugs and" +
                    " issues. Please report them by emailing bombbirddev@gmail.com, or by clicking the \"Report Bug\"" +
                    " button in the Pause screen or info menu. Please include as much information as possible," +
                    " including the build version (in info menu), expected behaviour and steps to reproduce the bug." +
                    " Screenshots and video recordings are very helpful too.\n\nCurrently, there are 9 default airports" +
                    " for testing, and more will be added as testing goes on. We hope you will enjoy the new" +
                    " multiplayer functionality, have fun!", 800, 1800)
        )
        add(
            MenuNotification("tcsf-added", "New airport available!", "TCSF has been added to the" +
                    " game. A few bugs have also been fixed. As always, please report bugs encountered by clicking" +
                    " the \"Report Bug\" button in the Pause screen in-game, or from the info menu. Enjoy the new" +
                    " challenging map!", 600, 1400)
        )
    }

    /** Shows the message that has not been shown before with the highest priority */
    fun showMessages() {
        val prefs = Gdx.app.getPreferences(PREFS_FILE_NAME)

        for (i in 0 until notifications.size) {
            val notif = notifications[i]
            if (!prefs.getBoolean(notif.prefKey, false)) {
                // Show welcome message
                CustomDialog(notif.title, notif.message, "", "Ok!", height = notif.height,
                    width = notif.width, fontAlign = Align.left).show(stage)
                break
            }
        }

        // All messages are marked as shown
        for (i in 0 until notifications.size) {
            prefs.putBoolean(notifications[i].prefKey, true)
        }
        prefs.flush()
    }
}