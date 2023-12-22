package com.bombbird.terminalcontrol2.files

import com.bombbird.terminalcontrol2.global.*
import com.bombbird.terminalcontrol2.ui.datatag.DatatagConfig
import com.squareup.moshi.FromJson
import com.squareup.moshi.JsonClass
import com.squareup.moshi.Moshi
import com.squareup.moshi.ToJson
import com.squareup.moshi.adapter
import ktx.assets.toInternalFile
import java.util.UUID

private const val SETTINGS_PATH = "Player/settings.json"
private const val UUID_PATH = "Player/uuid.json"
private const val BUILD_PATH = "BUILD"

@OptIn(ExperimentalStdlibApi::class)
val settingsAdapter = Moshi.Builder().build().adapter<PlayerSettingsJSON>()

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
    val showWptRestrictions: Boolean?,

    // Datatag settings
    val datatagStyleName: String?,
    val datatagBackground: Byte,
    val datatagBorder: Byte,
    val datatagRowSpacingPx: Byte,

    // Sound settings
    val communicationsSounds: Byte,
    val alertSoundOn: Boolean,
    var contactOtherPilotReadback: Boolean?,

    // Advanced trajectory settings
    val apwDurationS: Int,
    val stcaDurationS: Int
)

/** Loads the player's existing global settings and sets the global variables to them if available */
fun loadPlayerSettings() {
    val settingsHandle = getExtDir(SETTINGS_PATH) ?: return
    if (settingsHandle.exists()) {
        // File exists, read from it
        val jsonString = settingsHandle.readString()
        settingsAdapter.fromJson(jsonString)?.let { loadPlayerSettingsFromJson(it) }
    } else {
        // File does not exist, create new file with the default settings
        savePlayerSettings()
    }
}

/** Saves the player's existing global settings to the user app data directory */
@OptIn(ExperimentalStdlibApi::class)
fun savePlayerSettings() {
    val settingsHandle = getExtDir(SETTINGS_PATH) ?: return
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
        SHOW_WPT_RESTRICTIONS = showWptRestrictions ?: false

        // Datatag settings
        DATATAG_STYLE_NAME = datatagStyleName ?: DatatagConfig.DEFAULT
        DATATAG_BACKGROUND = datatagBackground
        DATATAG_BORDER = datatagBorder
        DATATAG_ROW_SPACING_PX = datatagRowSpacingPx

        // Sound settings
        COMMUNICATIONS_SOUND = communicationsSounds
        ALERT_SOUND_ON = alertSoundOn
        CONTACT_OTHER_PILOT_READBACK = contactOtherPilotReadback ?: false

        // Advanced trajectory settings
        APW_DURATION_S = apwDurationS
        STCA_DURATION_S = stcaDurationS
    }
}

/**
 * Gets player settings from the global variables and returns a [PlayerSettingsJSON] object with them
 * @return a JSON object containing the current player global settings
 */
fun getJsonFromPlayerSettings(): PlayerSettingsJSON {
    return PlayerSettingsJSON(TRAJECTORY_DURATION_S, RADAR_REFRESH_INTERVAL_S, TRAIL_DURATION_S, SHOW_UNCONTROLLED_AIRCRAFT_TRAIL,
        RANGE_RING_INTERVAL_NM, SHOW_MVA_ALTITUDE, REALISTIC_ILS_DISPLAY, COLOURFUL_STYLE, SHOW_DIST_TO_GO, SHOW_WPT_RESTRICTIONS,
        DATATAG_STYLE_NAME, DATATAG_BACKGROUND, DATATAG_BORDER, DATATAG_ROW_SPACING_PX, COMMUNICATIONS_SOUND, ALERT_SOUND_ON,
        CONTACT_OTHER_PILOT_READBACK, APW_DURATION_S, STCA_DURATION_S)
}

/** Adapter object for serializing UUID to and from JSON */
private object UUIDAdapter {
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
    val uuidHandle = getExtDir(UUID_PATH) ?: return
    myUuid = if (uuidHandle.exists()) {
        // File exists, read from it
        val jsonString = uuidHandle.readString()
        val moshi = Moshi.Builder().add(UUIDAdapter).build()
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
 */
@OptIn(ExperimentalStdlibApi::class)
private fun generateRandomUUIDAndSave(): UUID {
    val newUUID = UUID.randomUUID()
    val moshi = Moshi.Builder().add(UUIDAdapter).build()
    val uuidAdapter = moshi.adapter<UUID>()
    val jsonString = uuidAdapter.toJson(newUUID)
    getExtDir(UUID_PATH)?.writeString(jsonString, false)
    return newUUID
}

/** Loads the game version, build version */
fun loadBuildVersion() {
    val buildHandle = BUILD_PATH.toInternalFile()
    val buildInfo = buildHandle.readString().split(" ")
    GAME_VERSION = buildInfo[0]
    BUILD_VERSION = buildInfo[1].toInt()
}
