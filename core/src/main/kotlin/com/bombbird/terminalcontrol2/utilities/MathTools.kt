package com.bombbird.terminalcontrol2.utilities

/** Game specific math tools for use */
object MathTools {
    /** Convenience [Int] to [Byte] getter */
    inline val Int.byte
        get() = this.toByte()

    /** Unit conversions */
    const val NM_TO_PX = 25f
    private const val NM_TO_FT = 6076.12f
    private const val NM_TO_M = 1852f

    /** Convert from nautical miles to pixels */
    fun nmToPx(nm: Int): Float {
        return nmToPx(nm.toFloat())
    }

    fun nmToPx(nm: Float): Float {
        return nm * NM_TO_PX
    }

    /** Convert from pixels to nautical miles */
    fun pxToNm(px: Int): Float {
        return pxToNm(px.toFloat())
    }

    fun pxToNm(px: Float): Float {
        return px / NM_TO_PX
    }

    /** Convert from feet to pixels */
    fun ftToPx(ft: Int): Float {
        return ftToPx(ft.toFloat())
    }

    fun ftToPx(ft: Float): Float {
        return nmToPx(ft / NM_TO_FT)
    }

    /** Convert from pixels to feet */
    fun pxToFt(px: Int): Float {
        return pxToFt(px.toFloat())
    }

    fun pxToFt(px: Float): Float {
        return pxToNm(px) * NM_TO_FT
    }

    /** Convert from metres to pixels */
    fun mToPx(m: Int): Float {
        return mToPx(m.toFloat())
    }

    fun mToPx(m: Float): Float {
        return nmToPx(m / NM_TO_M)
    }

    /** Convert from pixels to metres */
    fun pxToM(px: Int): Float {
        return pxToNm(px) * NM_TO_M
    }

    /** Convert from metres to nautical miles */
    fun mToNm(m: Float): Float {
        return pxToNm(mToPx(m))
    }

    /** Convert from metres to feet */
    fun mToFt(m: Float): Float {
        return pxToFt(mToPx(m))
    }

    /** Convert from feet to metres */
    fun ftToM(ft: Float): Float {
        return ft / mToFt(1.0f)
    }

    /** Convert from knots to pixels per second */
    fun ktToPxps(kt: Int): Float {
        return ktToPxps(kt.toFloat())
    }

    fun ktToPxps(kt: Float): Float {
        return nmToPx(kt / 3600)
    }

    /** Convert from metres per second to knots */
    fun mpsToKt(mps: Float): Float {
        return mToNm(mps * 3600)
    }

    /** Convert from knots to metres per second */
    fun ktToMps(kt: Float): Float {
        return kt / mpsToKt(1f)
    }

    /** Convert from metres per second to feet per minute */
    fun mpsToFpm(ms: Float): Float {
        return mToFt(ms * 60)
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