import com.bombbird.terminalcontrol2.utilities.MathTools
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.DisplayName
import org.junit.jupiter.api.Test

object MathToolsTest {
    @Test
    @DisplayName("Unit conversions")
    fun checkUnitConversion() {
        // Delta set at 5th significant figure
        assertEquals(0.0005399568f, MathTools.mToNm(1f), 0.00000001f)
        assertEquals(3.280840f, MathTools.mToFt(1f), 0.0001f)
        assertEquals(0.3048f, MathTools.ftToM(1f), 0.00001f)
        assertEquals(1.943844f, MathTools.mpsToKt(1f), 0.0001f)
        assertEquals(0.514444f, MathTools.ktToMps(1f), 0.00001f)
        assertEquals(196.8504f, MathTools.mpsToFpm(1f), 0.01f)
    }

    @Test
    @DisplayName("Unit conversions involving pixels")
    fun checkPixelConversions() {
        assertEquals(MathTools.NM_TO_PX, MathTools.nmToPx(1))
        assertEquals(MathTools.NM_TO_PX, MathTools.nmToPx(1.0f))
        assertEquals(1 / MathTools.NM_TO_PX, MathTools.pxToNm(1))
        assertEquals(1 / MathTools.NM_TO_PX, MathTools.pxToNm(1.0f))
        assertEquals(MathTools.NM_TO_PX / 6076.12f, MathTools.ftToPx(1))
        assertEquals(MathTools.NM_TO_PX / 6076.12f, MathTools.ftToPx(1.0f))
        assertEquals(6076.12f / MathTools.NM_TO_PX, MathTools.pxToFt(1))
        assertEquals(6076.12f / MathTools.NM_TO_PX, MathTools.pxToFt(1.0f))
        assertEquals(MathTools.NM_TO_PX / 1852, MathTools.mToPx(1))
        assertEquals(MathTools.NM_TO_PX / 1852, MathTools.mToPx(1.0f))
        assertEquals(1852 / MathTools.NM_TO_PX, MathTools.pxToM(1))
        assertEquals(1852 / MathTools.NM_TO_PX, MathTools.pxToM(1))
        assertEquals(MathTools.NM_TO_PX / 3600, MathTools.ktToPxps(1))
        assertEquals(MathTools.NM_TO_PX / 3600, MathTools.ktToPxps(1.0f))
    }
}