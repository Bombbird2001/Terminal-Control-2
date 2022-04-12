package com.bombbird.terminalcontrol2.utilities

/** Game specific math tools for use */
object MathTools {
    /** Unit conversions */
    private const val NM_TO_PX = 25f
    private const val NM_TO_FT = 6076.12f
    private const val NM_TO_M = 1852f

    fun nmToPx(nm: Int): Float {
        return nm * NM_TO_PX
    }

    fun nmToPx(nm: Float): Float {
        return nm * NM_TO_PX
    }

    fun pxToNm(px: Int): Float {
        return px / NM_TO_PX
    }

    fun ftToPx(ft: Int): Float {
        return nmToPx(ft / NM_TO_FT)
    }

    fun ftToPx(ft: Float): Float {
        return nmToPx(ft / NM_TO_FT)
    }

    fun pxToFt(px: Int): Float {
        return pxToNm(px) * NM_TO_FT
    }

    fun mToPx(m: Int): Float {
        return nmToPx(m / NM_TO_M)
    }

    fun mToPx(m: Float): Float {
        return nmToPx(m / NM_TO_M)
    }

    fun pxToM(px: Int): Float {
        return pxToNm(px) * NM_TO_M
    }

    /** Converts between in-game world degrees and degree used by the rendering systems
     *
     * World heading: 360 is up, 90 is right, 180 is down and 270 is left
     *
     * Render degrees: 90 is up, 0 is right, -90 is down and -180 is left
     * */
    fun convertWorldAndRenderDeg(origDeg: Float): Float {
        return 90 - origDeg
    }
}