package com.bombbird.terminalcontrol2.utilities

import com.bombbird.terminalcontrol2.global.Constants

/** Game specific math tools for use */
object MathTools {
    fun nmToPx(nm: Int): Float {
        return nm * Constants.NM_TO_PX
    }

    fun nmToPx(nm: Float): Float {
        return nm * Constants.NM_TO_PX
    }

    fun pxToNm(px: Int): Float {
        return px / Constants.NM_TO_PX
    }
}