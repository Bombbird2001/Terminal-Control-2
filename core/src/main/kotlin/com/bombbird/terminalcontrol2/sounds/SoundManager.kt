package com.bombbird.terminalcontrol2.sounds

import com.badlogic.gdx.audio.Sound
import com.badlogic.gdx.utils.Disposable
import com.badlogic.gdx.utils.Timer
import com.bombbird.terminalcontrol2.global.*

class SoundManager: Disposable {
    private var conflictPlaying = false
    private var runwayChangePlaying = false
    private var initialContactPlaying = false
    private var alertPlaying = false
    private val timer = Timer()

    private val alertAudio: Sound = GAME.assetStorage["Audio/alert.wav"]
    private val conflictAudio: Sound = GAME.assetStorage["Audio/conflict.wav"]
    private val initialContactAudio: Sound = GAME.assetStorage["Audio/initial_contact.wav"]
    private val runwayChangeAudio: Sound = GAME.assetStorage["Audio/rwy_change.wav"]

    /** Loops the warning audio effect  */
    fun playWarning() {
        if (!conflictPlaying && COMMUNICATIONS_SOUND > COMMS_OFF) {
            conflictAudio.play(0.8f)
            conflictPlaying = true
            timer.scheduleTask(object : Timer.Task() {
                override fun run() {
                    conflictPlaying = false
                }
            }, 2.07f)
        }
    }

    /** Plays a runway change sound effect if not playing  */
    fun playRunwayChange() {
        if (!runwayChangePlaying && canPlayAlerts()) {
            runwayChangeAudio.play(0.8f)
            runwayChangePlaying = true
            timer.scheduleTask(object : Timer.Task() {
                override fun run() {
                    runwayChangePlaying = false
                }
            }, 2.0f)
        }
    }

    /** Plays initial contact sound effect if not playing  */
    fun playInitialContact() {
        if (!initialContactPlaying && canPlaySoundEffect()) {
            if (alertPlaying) {
                // If alert playing, wait till alert has finished then play this again
                timer.scheduleTask(object : Timer.Task() {
                    override fun run() {
                        playInitialContact()
                    }
                }, 0.45f)
            } else {
                initialContactAudio.play(0.8f)
                initialContactPlaying = true
                timer.scheduleTask(object : Timer.Task() {
                    override fun run() {
                        initialContactPlaying = false
                    }
                }, 0.23f)
            }
        }
    }

    /** Plays alert sound effect if not playing  */
    fun playAlert() {
        if (!alertPlaying && canPlayAlerts()) {
            alertAudio.play(0.8f)
            alertPlaying = true
            timer.scheduleTask(object : Timer.Task() {
                override fun run() {
                    alertPlaying = false
                }
            }, 0.45f)
        }
    }

    /** Returns true if player settings allow for sound effects to be played */
    private fun canPlaySoundEffect(): Boolean {
        // We do not play sound effects if pilot voices are enabled
        return COMMUNICATIONS_SOUND == COMMS_SOUND_EFFECTS
    }

    private fun canPlayAlerts(): Boolean {
        return ALERT_SOUND_ON
    }

    /** Pauses playing all sounds  */
    fun pause() {
        conflictAudio.pause()
        runwayChangeAudio.pause()
        initialContactAudio.pause()
        alertAudio.pause()
        timer.stop()
    }

    /** Resumes playing all sounds  */
    fun resume() {
        conflictAudio.resume()
        runwayChangeAudio.resume()
        initialContactAudio.resume()
        alertAudio.resume()
        timer.start()
    }

    /** Stops all sounds */
    fun stop() {
        conflictAudio.stop()
        runwayChangeAudio.stop()
        initialContactAudio.stop()
        alertAudio.stop()
    }

    /** Stops, disposes all sounds  */
    override fun dispose() {
        stop()
        conflictAudio.dispose()
        runwayChangeAudio.dispose()
        initialContactAudio.dispose()
        alertAudio.dispose()
    }
}