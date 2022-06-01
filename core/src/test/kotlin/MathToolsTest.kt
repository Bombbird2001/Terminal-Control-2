import com.bombbird.terminalcontrol2.components.CommandTarget
import com.bombbird.terminalcontrol2.utilities.*
import org.junit.jupiter.api.Assertions.*
import org.junit.jupiter.api.DisplayName
import org.junit.jupiter.api.Test

object MathToolsTest {
    @Test
    @DisplayName("Unit conversions")
    fun checkUnitConversion() {
        // Delta set at 5th significant figure
        assertEquals(0.0005399568f, mToNm(1f), 0.00000001f)
        assertEquals(3.280840f, mToFt(1f), 0.0001f)
        assertEquals(0.3048f, ftToM(1f), 0.00001f)
        assertEquals(1.943844f, mpsToKt(1f), 0.0001f)
        assertEquals(0.514444f, ktToMps(1f), 0.00001f)
        assertEquals(101.269f, ktToFpm(1f), 0.01f)
        assertEquals(196.8504f, mpsToFpm(1f), 0.01f)
        assertEquals(10.16f, fpmToMps(2000f), 0.001f)
    }

    @Test
    @DisplayName("Unit conversions involving pixels")
    fun checkPixelConversions() {
        assertEquals(NM_TO_PX, nmToPx(1))
        assertEquals(NM_TO_PX, nmToPx(1.0f))
        assertEquals(1 / NM_TO_PX, pxToNm(1))
        assertEquals(1 / NM_TO_PX, pxToNm(1.0f))
        assertEquals(NM_TO_PX / 6076.12f, ftToPx(1))
        assertEquals(NM_TO_PX / 6076.12f, ftToPx(1.0f))
        assertEquals(6076.12f / NM_TO_PX, pxToFt(1))
        assertEquals(6076.12f / NM_TO_PX, pxToFt(1.0f))
        assertEquals(NM_TO_PX / 1852, mToPx(1))
        assertEquals(NM_TO_PX / 1852, mToPx(1.0f))
        assertEquals(1852 / NM_TO_PX, pxToM(1))
        assertEquals(1852 / NM_TO_PX, pxToM(1))
        assertEquals(NM_TO_PX / 3600, ktToPxps(1))
        assertEquals(NM_TO_PX / 3600, ktToPxps(1.0f))
        assertEquals(3600 / NM_TO_PX, pxpsToKt(1.0f))
    }

    @Test
    @DisplayName("Heading calculations")
    fun checkHeadingCalculations() {
        assertEquals(50f, findDeltaHeading(360f, 50f, CommandTarget.TURN_DEFAULT))
        assertEquals(50f, findDeltaHeading(360f, 50f, CommandTarget.TURN_RIGHT))
        assertEquals(-310f, findDeltaHeading(360f, 50f, CommandTarget.TURN_LEFT))
        assertEquals(120f, findDeltaHeading(-30f, 450f, CommandTarget.TURN_DEFAULT))
        assertEquals(120f, findDeltaHeading(-30f, 450f, CommandTarget.TURN_RIGHT))
        assertEquals(-240f, findDeltaHeading(-30f, 450f, CommandTarget.TURN_LEFT))
    }

    @Test
    @DisplayName("Arc calculations")
    fun checkArcCalculations() {
        assertTrue(checkInArc(0f, 0f, 45f, 10f, 35f, 2f, 2f))
        assertTrue(checkInArc(100f, -100f, 45f, 300f, 35f, 300f, 100f))
        assertTrue(checkInArc(0f, 0f, 45f, 10f, 35f, 1f, 2f))
        assertTrue(checkInArc(100f, -100f, 45f, 300f, 35f, 200f, 100f))
        assertFalse(checkInArc(0f, 0f, 45f, 10f, 35f, 0f, 2f))
        assertFalse(checkInArc(100f, -100f, 45f, 300f, 35f, 100f, 100f))
        assertFalse(checkInArc(0f, 0f, 45f, 1f, 35f, 2f, 2f))
        assertFalse(checkInArc(100f, -100f, 45f, 100f, 35f, 300f, 100f))
    }
}