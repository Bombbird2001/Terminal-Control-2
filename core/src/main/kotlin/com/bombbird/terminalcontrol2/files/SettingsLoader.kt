package com.bombbird.terminalcontrol2.files

import com.bombbird.terminalcontrol2.global.*
import com.squareup.moshi.FromJson
import com.squareup.moshi.JsonClass
import com.squareup.moshi.Moshi
import com.squareup.moshi.ToJson
import com.squareup.moshi.adapter
import java.util.UUID

private const val settingsPath = "Player/settings.json"
private const val uuidPath = "Player/uuid.json"

/** Data class for storing player's global settings, which will be used for serializing to and from JSON */
@JsonClass(generateAdapter = true)
data class PlayerSettingsJSON(
    // Display settings
    val trajectoryLineDurationS: Int,
    val radarRefreshIntervalS: Float,
    val trailDurationS: Int,
    val showUncontrolledAircraftTrail: Byte,
    val rangeRingIntervalNm: Int,
    val showMvaAltitude: Boolean,
    val realisticIlsDisplay: Boolean,
    val colourfulStyle: Boolean,
    val showDistToGo: Byte,

    // Datatag settings
    val datatagStyleId: Byte,
    val datatagBackground: Byte,
    val datatagBorder: Byte,
    val datatagRowSpacingPx: Byte,

    // Sound settings
    val communicationsSounds: Byte,
    val alertSoundOn: Boolean,

    // Advanced trajectory settings
    val advTrajectoryDurationS: Int,
    val apwDurationS: Int,
    val stcaDurationS: Int
)

/** Loads the player's existing global settings and sets the global variables to them if available */
@OptIn(ExperimentalStdlibApi::class)
fun loadPlayerSettings() {
    val settingsHandle = getExtDir(settingsPath) ?: return
    if (settingsHandle.exists()) {
        // File exists, read from it
        val jsonString = settingsHandle.readString()
        val moshi = Moshi.Builder().build()
        val settingsAdapter = moshi.adapter<PlayerSettingsJSON>()
        settingsAdapter.fromJson(jsonString)?.let { loadPlayerSettingsFromJson(it) }
    } else {
        // File does not exist, create new file with the default settings
        savePlayerSettings()
    }
}

/** Saves the player's existing global settings to the user app data directory */
@OptIn(ExperimentalStdlibApi::class)
fun savePlayerSettings() {
    val settingsHandle = getExtDir(settingsPath) ?: return
    val moshi = Moshi.Builder().build()
    val settingsAdapter = moshi.adapter<PlayerSettingsJSON>().indent("  ")
    val jsonString = settingsAdapter.toJson(getJsonFromPlayerSettings())
    settingsHandle.writeString(jsonString, false)
}

/**
 * Loads player settings from the input [PlayerSettingsJSON] object and sets the global variables to them
 * @param playerSettingsJSON the JSON object containing player settings data
 */
private fun loadPlayerSettingsFromJson(playerSettingsJSON: PlayerSettingsJSON) {
    playerSettingsJSON.apply {
        // Display settings
        TRAJECTORY_DURATION_S = trajectoryLineDurationS
        RADAR_REFRESH_INTERVAL_S = radarRefreshIntervalS
        TRAIL_DURATION_S = trailDurationS
        SHOW_UNCONTROLLED_AIRCRAFT_TRAIL = showUncontrolledAircraftTrail
        RANGE_RING_INTERVAL_NM = rangeRingIntervalNm
        SHOW_MVA_ALTITUDE = showMvaAltitude
        REALISTIC_ILS_DISPLAY = realisticIlsDisplay
        COLOURFUL_STYLE = colourfulStyle
        SHOW_DIST_TO_GO = showDistToGo

        // Datatag settings
        DATATAG_STYLE_ID = datatagStyleId
        DATATAG_BACKGROUND = datatagBackground
        DATATAG_BORDER = datatagBorder
        DATATAG_ROW_SPACING_PX = datatagRowSpacingPx

        // Sound settings
        COMMUNICATIONS_SOUND = communicationsSounds
        ALERT_SOUND_ON = alertSoundOn

        // Advanced trajectory settings
        ADV_TRAJECTORY_DURATION_S = advTrajectoryDurationS
        APW_DURATION_S = apwDurationS
        STCA_DURATION_S = stcaDurationS
    }
}

/**
 * Gets player settings from the global variables and returns a [PlayerSettingsJSON] object with them
 * @return a JSON object containing the current player global settings
 */
private fun getJsonFromPlayerSettings(): PlayerSettingsJSON {
    return PlayerSettingsJSON(TRAJECTORY_DURATION_S, RADAR_REFRESH_INTERVAL_S, TRAIL_DURATION_S, SHOW_UNCONTROLLED_AIRCRAFT_TRAIL,
        RANGE_RING_INTERVAL_NM, SHOW_MVA_ALTITUDE, REALISTIC_ILS_DISPLAY, COLOURFUL_STYLE, SHOW_DIST_TO_GO,
        DATATAG_STYLE_ID, DATATAG_BACKGROUND, DATATAG_BORDER, DATATAG_ROW_SPACING_PX, COMMUNICATIONS_SOUND, ALERT_SOUND_ON,
        ADV_TRAJECTORY_DURATION_S, APW_DURATION_S, STCA_DURATION_S)
}

/** Adapter class for serializing UUID to and from JSON */
private class UUIDAdapter {
    @ToJson
    fun toJson(uuid: UUID): String {
        return uuid.toString()
    }

    @FromJson
    fun fromJson(json: String): UUID {
        return UUID.fromString(json)
    }
}

/** Loads the player's existing UUID and sets the global variables to it if available */
@OptIn(ExperimentalStdlibApi::class)
fun loadPlayerUUID() {
    val uuidHandle = getExtDir(uuidPath) ?: return
    uuid = if (uuidHandle.exists()) {
        // File exists, read from it
        val jsonString = uuidHandle.readString()
        val moshi = Moshi.Builder().add(UUIDAdapter()).build()
        val uuidAdapter = moshi.adapter<UUID>()
        uuidAdapter.fromJson(jsonString) ?: generateRandomUUIDAndSave()
    } else {
        // File does not exist, create new file with randomly generated UUID
        generateRandomUUIDAndSave()
    }
}

/**
 * Generates a random UUID, sets the global UUID variable to it, and saves it to the player's app data directory
 * @return the newly generated [UUID]
 * */
@OptIn(ExperimentalStdlibApi::class)
private fun generateRandomUUIDAndSave(): UUID {
    val newUUID = UUID.randomUUID()
    val moshi = Moshi.Builder().add(UUIDAdapter()).build()
    val uuidAdapter = moshi.adapter<UUID>()
    val jsonString = uuidAdapter.toJson(newUUID)
    getExtDir(uuidPath)?.writeString(jsonString, false)
    return newUUID
}
