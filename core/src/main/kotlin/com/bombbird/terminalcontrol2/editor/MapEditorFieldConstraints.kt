package com.bombbird.terminalcontrol2.editor

import com.bombbird.terminalcontrol2.utilities.modulateHeading
import kotlin.math.roundToInt

/** Shared parse/clamp rules for map editor property fields (pane + [com.bombbird.terminalcontrol2.editor.validation.AirportMapValidator]). */
object MapEditorFieldConstraints {

    const val MIN_ELEVATION_FT: Short = -1500

    val RUNWAY_LENGTH_M_RANGE: IntRange = 500..20000

    const val MIN_ALT_SECTOR_FT_MIN: Int = -1500
    const val MIN_ALT_SECTOR_FT_MAX: Int = 99900

    /** Parse integer runway length and clamp to [RUNWAY_LENGTH_M_RANGE]. */
    fun parseRunwayLengthM(text: String): Short? {
        val v = text.trim().toIntOrNull() ?: return null
        return v.coerceIn(RUNWAY_LENGTH_M_RANGE).toShort()
    }

    /** Parse a non-negative runway distance field and clamp to `0..maxM` (inclusive). */
    fun parseRunwayDistanceM(text: String, maxM: Short): Short? {
        val v = text.trim().toIntOrNull() ?: return null
        return v.coerceIn(0, maxM.toInt()).toShort()
    }

    /**
     * True heading in **(0, 360]**; same rules as [modulateHeading].
     * Examples: 0 → 360, 360 → 360, 370 → 10, -10 → 350.
     */
    fun normalizeRunwayTrueHeadingDeg(deg: Float): Float = modulateHeading(deg)

    /** Stored heading valid for [com.bombbird.terminalcontrol2.editor.validation.AirportMapValidator] (no normalization). */
    fun isValidStoredRunwayTrueHeadingDeg(deg: Float): Boolean =
        deg > 0f && deg <= 360f

    fun coerceElevationFt(value: Short): Short =
        value.coerceAtLeast(MIN_ELEVATION_FT)

    /** Nearest multiple of 100 (half-up). */
    fun snapToNearest100(value: Int): Int =
        (value / 100.0).roundToInt() * 100

    /** Clamp to sector altitude range, then snap to nearest 100 ft. */
    fun clampMinAltSectorFt(value: Int): Int {
        val c = value.coerceIn(MIN_ALT_SECTOR_FT_MIN, MIN_ALT_SECTOR_FT_MAX)
        return snapToNearest100(c)
    }

    fun isValidMinAltSectorFt(value: Int): Boolean =
        value in MIN_ALT_SECTOR_FT_MIN..MIN_ALT_SECTOR_FT_MAX && value % 100 == 0
}
