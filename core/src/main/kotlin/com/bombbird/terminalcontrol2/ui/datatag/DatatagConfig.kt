package com.bombbird.terminalcontrol2.ui.datatag

import com.bombbird.terminalcontrol2.files.DatatagLayoutJSON
import kotlin.math.min

/** Class for encapsulating datatag display layout behaviour */
class DatatagConfig(val name: String) {
    companion object {
        // Datatag value keys
        const val CALLSIGN = "Callsign"
        const val CALLSIGN_RECAT = "Callsign + Recat"
        const val CALLSIGN_WAKE = "Callsign + Wake"
        const val ICAO_TYPE = "Aircraft type"
        const val ICAO_TYPE_RECAT = "Aircraft type + Recat"
        const val ICAO_TYPE_WAKE = "Aircraft type + Wake"
        const val ALTITUDE = "Current altitude"
        const val ALTITUDE_TREND = "Altitude trend"
        const val CMD_ALTITUDE = "Target altitude"
        const val EXPEDITE = "Expedite"
        const val CLEARED_ALT = "Cleared altitude"
        const val HEADING = "Heading"
        const val LAT_CLEARED = "Cleared waypoint/heading"
        const val SIDSTARAPP_CLEARED = "Cleared SID/STAR/Approach"
        const val GROUND_SPEED = "Ground speed"
        const val CLEARED_IAS = "Cleared speed"
        const val AIRPORT = "Airport"

        const val DEFAULT = "Default"
        const val COMPACT = "Compact"
        val CAN_BE_HIDDEN = HashSet<String>(listOf(CLEARED_ALT, LAT_CLEARED, SIDSTARAPP_CLEARED, CLEARED_IAS))

        const val ARRANGEMENT_ROWS = 4
        const val ARRANGEMENT_COLS = 5
        const val MINI_ARRANGEMENT_ROWS = 3
        const val MINI_ARRANGEMENT_COLS = 2
    }

    val onlyShowWhenChanged: HashSet<String> = HashSet()
    val arrangement: Array<Array<String>> = Array(ARRANGEMENT_ROWS) {Array(ARRANGEMENT_COLS) {""}}
    val miniArrangementFirst = Array(MINI_ARRANGEMENT_ROWS) {Array(MINI_ARRANGEMENT_COLS) {""}}
    val miniArrangementSecond = Array(MINI_ARRANGEMENT_ROWS) {Array(MINI_ARRANGEMENT_COLS) {""}}
    private var arrangementEmpty = true
    private var miniArrangementFirstEmpty = true
    private var miniArrangementSecondEmpty = true

    init {
        when (name) {
            DEFAULT -> {
                arrangement[0][0] = CALLSIGN
                arrangement[0][1] = ICAO_TYPE_RECAT
                arrangement[1][0] = ALTITUDE
                arrangement[1][1] = ALTITUDE_TREND
                arrangement[1][2] = CMD_ALTITUDE
                arrangement[1][3] = EXPEDITE
                arrangement[1][4] = CLEARED_ALT
                arrangement[2][0] = HEADING
                arrangement[2][1] = LAT_CLEARED
                arrangement[2][2] = SIDSTARAPP_CLEARED
                arrangement[3][0] = GROUND_SPEED
                arrangement[3][1] = CLEARED_IAS
                miniArrangementFirst[0][0] = CALLSIGN_RECAT
                miniArrangementFirst[1][0] = ALTITUDE
                miniArrangementFirst[1][1] = HEADING
                miniArrangementFirst[2][0] = GROUND_SPEED
                miniArrangementFirstEmpty = false
            }
            COMPACT -> {
                arrangement[0][0] = CALLSIGN
                arrangement[0][1] = ICAO_TYPE_RECAT
                arrangement[1][0] = ALTITUDE
                arrangement[1][1] = ALTITUDE_TREND
                arrangement[1][2] = CMD_ALTITUDE
                arrangement[1][3] = EXPEDITE
                arrangement[1][4] = CLEARED_ALT
                arrangement[2][0] = LAT_CLEARED
                arrangement[2][1] = SIDSTARAPP_CLEARED
                arrangement[3][0] = GROUND_SPEED
                arrangement[3][1] = CLEARED_IAS
                miniArrangementFirst[0][0] = CALLSIGN_RECAT
                miniArrangementFirst[1][0] = ALTITUDE
                miniArrangementFirst[1][1] = GROUND_SPEED
                miniArrangementSecond[0][0] = CALLSIGN_RECAT
                miniArrangementSecond[1][0] = CLEARED_ALT
                miniArrangementSecond[1][1] = ICAO_TYPE
                onlyShowWhenChanged.add(LAT_CLEARED)
                miniArrangementFirstEmpty = false
                miniArrangementSecondEmpty = false
            }
            else -> {
                // Do nothing
            }
        }
    }

    constructor(name: String, layoutJSON: DatatagLayoutJSON) : this(name) {
        onlyShowWhenChanged.addAll(layoutJSON.showOnlyWhenChanged)

        for (i in 0 until min(arrangement.size, layoutJSON.mainArrangement.size)) {
            for (j in 0 until min(arrangement[i].size, layoutJSON.mainArrangement[i].size)) {
                arrangement[i][j] = layoutJSON.mainArrangement[i][j]
            }
        }
        for (i in 0 until min(miniArrangementFirst.size, layoutJSON.miniFirstArrangement.size)) {
            for (j in 0 until min(miniArrangementFirst[i].size, layoutJSON.miniFirstArrangement[i].size)) {
                miniArrangementFirst[i][j] = layoutJSON.miniFirstArrangement[i][j]
            }
        }
        for (i in 0 until min(miniArrangementSecond.size, layoutJSON.miniSecondArrangement.size)) {
            for (j in 0 until min(miniArrangementSecond[i].size, layoutJSON.miniSecondArrangement[i].size)) {
                miniArrangementSecond[i][j] = layoutJSON.miniSecondArrangement[i][j]
            }
        }

        updateEmptyFields()
    }

    constructor(name: String, showWhenChanged: HashSet<String>, mainArrangement: Array<Array<String>>, miniArrFirst: Array<Array<String>>, miniArrSecond: Array<Array<String>>): this(name) {
        onlyShowWhenChanged.addAll(showWhenChanged)

        for (i in 0 until min(arrangement.size, mainArrangement.size)) {
            for (j in 0 until min(arrangement[i].size, mainArrangement[i].size)) {
                arrangement[i][j] = mainArrangement[i][j]
            }
        }
        for (i in 0 until min(miniArrangementFirst.size, miniArrFirst.size)) {
            for (j in 0 until min(miniArrangementFirst[i].size, miniArrFirst[i].size)) {
                miniArrangementFirst[i][j] = miniArrFirst[i][j]
            }
        }
        for (i in 0 until min(miniArrangementSecond.size, miniArrSecond.size)) {
            for (j in 0 until min(miniArrangementSecond[i].size, miniArrangementSecond[i].size)) {
                miniArrangementSecond[i][j] = miniArrSecond[i][j]
            }
        }

        updateEmptyFields()
    }

    private fun updateEmptyFields() {
        arrangementEmpty = true
        for (line in arrangement) {
            for (field in line) {
                if (field.isNotEmpty()) {
                    arrangementEmpty = false
                    break
                }
            }
        }

        miniArrangementFirstEmpty = true
        for (line in miniArrangementFirst) {
            for (field in line) {
                if (field.isNotEmpty()) {
                    miniArrangementFirstEmpty = false
                    break
                }
            }
        }

        miniArrangementSecondEmpty = true
        for (line in miniArrangementSecond) {
            for (field in line) {
                if (field.isNotEmpty()) {
                    miniArrangementSecondEmpty = false
                    break
                }
            }
        }
    }

    fun showOnlyWhenChanged(field: String): Boolean {
        return onlyShowWhenChanged.contains(field)
    }

    fun generateTagText(fields: HashMap<String, String>, isMinimized: Boolean): String {
        val arrayToUse = if (isMinimized) {
            when {
                miniArrangementFirstEmpty -> miniArrangementSecond
                miniArrangementSecondEmpty -> miniArrangementFirst
                else -> if (System.currentTimeMillis() % 4000 >= 2500) miniArrangementSecond else miniArrangementFirst
            }
        } else arrangement
        val sb = StringBuilder()
        for (line in arrayToUse) {
            val sbLine = StringBuilder()
            for (field in line) {
                val text = fields[field] ?: ""
                if (text.isNotBlank() && sbLine.isNotEmpty()) sbLine.append(" ")
                if (text.isNotBlank()) sbLine.append(text)
            }
            if (sbLine.isNotBlank() && sb.isNotEmpty()) sb.append("\n")
            if (sbLine.isNotBlank()) sb.append(sbLine)
        }
        return sb.toString()
    }
}