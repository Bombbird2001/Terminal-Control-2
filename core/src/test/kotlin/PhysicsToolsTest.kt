import com.bombbird.terminalcontrol2.utilities.*
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.DisplayName
import org.junit.jupiter.api.Test

object PhysicsToolsTest {
    @Test
    @DisplayName("Temperature calculations")
    fun checkTempCalculations() {
        // Delta set at 1st decimal place
        assertEquals(288.15f, calculateTempAtAlt(0f), 0.01f)
        assertEquals(278.244f, calculateTempAtAlt(5000f), 0.01f)
        assertEquals(268.338f, calculateTempAtAlt(10000f), 0.01f)
        assertEquals(258.432f, calculateTempAtAlt(15000f), 0.01f)
        assertEquals(248.526f, calculateTempAtAlt(20000f), 0.01f)
        assertEquals(238.62f, calculateTempAtAlt(25000f), 0.01f)
        assertEquals(228.714f, calculateTempAtAlt(30000f), 0.01f)
        assertEquals(218.808f, calculateTempAtAlt(35000f), 0.01f)
        assertEquals(216.65f, calculateTempAtAlt(36090f), 0.01f)
        assertEquals(216.65f, calculateTempAtAlt(40000f), 0.01f)
        assertEquals(216.65f, calculateTempAtAlt(45000f), 0.01f)
    }

    @Test
    @DisplayName("Pressure calculations")
    fun checkPressureCalculations() {
        // Delta set at ones
        assertEquals(101325f, calculatePressureAtAlt(0f), 1f)
        assertEquals(84307f, calculatePressureAtAlt(5000f), 1f)
        assertEquals(69682f, calculatePressureAtAlt(10000f), 1f)
        assertEquals(57182f, calculatePressureAtAlt(15000f), 1f)
        assertEquals(46563f, calculatePressureAtAlt(20000f), 1f)
        assertEquals(37601f, calculatePressureAtAlt(25000f), 1f)
        assertEquals(30090f, calculatePressureAtAlt(30000f), 1f)
        assertEquals(23842f, calculatePressureAtAlt(35000f), 1f)
        assertEquals(22631f, calculatePressureAtAlt(36090f), 1f)
        assertEquals(18754f, calculatePressureAtAlt(40000f), 1f)
        assertEquals(14748f, calculatePressureAtAlt(45000f), 1f)
    }

    @Test
    @DisplayName("Density calculations")
    fun checkDensityCalculations() {
        // Delta set at 4th decimal place
        assertEquals(1.225f, calculateAirDensity(101325f, 288.15f), 0.0001f)
        assertEquals(1.05555f, calculateAirDensity(84307f, 278.244f), 0.0001f)
        assertEquals(0.90464f, calculateAirDensity(69682f, 268.338f), 0.0001f)
        assertEquals(0.77082f, calculateAirDensity(57182f, 258.432f), 0.0001f)
        assertEquals(0.65269f, calculateAirDensity(46563f, 248.526f), 0.0001f)
        assertEquals(0.54895f, calculateAirDensity(37601f, 238.62f), 0.0001f)
        assertEquals(0.45831f, calculateAirDensity(30090f, 228.714f), 0.0001f)
        assertEquals(0.3796f, calculateAirDensity(23842f, 218.808f), 0.0001f)
        assertEquals(0.3639f, calculateAirDensity(22631f, 216.65f), 0.0001f)
        assertEquals(0.30156f, calculateAirDensity(18754f, 216.65f), 0.0001f)
        assertEquals(0.23714f, calculateAirDensity(14748f, 216.65f), 0.0001f)
    }

    @Test
    @DisplayName("Thrust calculations")
    fun checkThrustCalculations() {
        // Delta for the following 5 tests are set at a higher value as expected values are not exact
        assertEquals(17000f, calculateMaxJetThrust(17000, 101325f, 288.15f), 100f)
        assertEquals(16490f, calculateMaxJetThrust(17000, 97698.71f, 286.111f), 100f)
        assertEquals(12070f, calculateMaxJetThrust(17000, 69706f, 268.333f), 100f)
        assertEquals(8330f, calculateMaxJetThrust(17000, 46608.56f, 248.889f), 100f)
        assertEquals(5610f, calculateMaxJetThrust(17000, 30130.09f, 229.444f), 100f)

        // Delta set at 5th significant figure
        assertEquals(77261.88f, calculateMaxPropThrust(7562000, 26.40509f, 50f, 101325f, 288.15f), 1f)
        assertEquals(30162.31f, calculateMaxPropThrust(7562000, 26.40509f, 240f, 101325f, 288.15f), 1f)
        assertEquals(62192.316f, calculateMaxPropThrust(7562000, 26.40509f, 50f, 46563f, 248.526f), 1f)
        assertEquals(29779.682f, calculateMaxPropThrust(7562000, 26.40509f, 240f, 46563f, 248.526f), 1f)
    }

    @Test
    @DisplayName("Drag calculations")
    fun checkDragCalculations() {
        // Delta set at 5th significant figure
        assertEquals(518.719f, calculateParasiticDrag(1.28f, 1.225f, 50f), 0.01f)
        assertEquals(11951.374f, calculateParasiticDrag(1.28f, 1.225f, 240f), 1f)
        assertEquals(6797.322f, calculateParasiticDrag(6.552f, 1.225f, 80f), 0.1f)
        assertEquals(50658.08f, calculateParasiticDrag(6.552f, 0.36518f, 400f), 1f)
        assertEquals(162054f, calculateInducedDrag(351533, 160, 340, 300000f, 37000f, 488f, false), 10f)
        assertEquals(274022f, calculateInducedDrag(240000, 175, 340, 1000000f, 1000f, 178f, true), 10f)
    }

    @Test
    @DisplayName("Speed calculations")
    fun checkSpeedCalculations() {
        // Delta set at 5th significant figure
        assertEquals(79.999f, calculateIASFromTAS(0f, 80f), 0.001f)
        assertEquals(299.996f, calculateIASFromTAS(0f, 300f), 0.01f)
        assertEquals(123.56f, calculateIASFromTAS(30000f, 200f), 0.01f)
        assertEquals(-123.56f, calculateIASFromTAS(30000f, -200f), 0.01f)
        assertEquals(309.948f, calculateIASFromTAS(30000f, 480f), 0.01f)
        assertEquals(80f, calculateTASFromIAS(0f, 80f), 0.001f)
        assertEquals(300f, calculateTASFromIAS(0f, 300f), 0.01f)
        assertEquals(318.93f, calculateTASFromIAS(30000f, 200f), 0.01f)
        assertEquals(-318.93f, calculateTASFromIAS(30000f, -200f), 0.01f)
        assertEquals(705.01f, calculateTASFromIAS(30000f, 480f), 0.01f)
        assertEquals(661.48f, calculateSpeedOfSoundAtAlt(0f), 0.01f)
        assertEquals(650.01f, calculateSpeedOfSoundAtAlt(5000f), 0.01f)
        assertEquals(638.33f, calculateSpeedOfSoundAtAlt(10000f), 0.01f)
        assertEquals(626.44f, calculateSpeedOfSoundAtAlt(15000f), 0.01f)
        assertEquals(614.31f, calculateSpeedOfSoundAtAlt(20000f), 0.01f)
        assertEquals(601.95f, calculateSpeedOfSoundAtAlt(25000f), 0.01f)
        assertEquals(589.32f, calculateSpeedOfSoundAtAlt(30000f), 0.01f)
        assertEquals(576.42f, calculateSpeedOfSoundAtAlt(35000f), 0.01f)
        assertEquals(573.57f, calculateSpeedOfSoundAtAlt(40000f), 0.01f)
        assertEquals(573.57f, calculateSpeedOfSoundAtAlt(45000f), 0.01f)
        assertEquals(661.47f, calculateIASFromMach(0f, 1f), 0.01f)
        assertEquals(551.67f, calculateIASFromMach(0f, 0.834f), 0.01f)
        assertEquals(566.29f, calculateIASFromMach(10000f, 1f), 0.01f)
        assertEquals(468.37f, calculateIASFromMach(10000f, 0.834f), 0.01f)
        assertEquals(350.02f, calculateIASFromMach(35000f, 1f), 0.01f)
        assertEquals(284.81f, calculateIASFromMach(35000f, 0.834f), 0.01f)
    }

    @Test
    @DisplayName("Acceleration calculations")
    fun checkAccelerationCalculations() {
        assertEquals(0f, calculateAcceleration(10000f, 10000f, 10000), 0.0001f)
        assertEquals(0.7f, calculateAcceleration(17000f, 10000f, 10000), 0.00001f)
        assertEquals(3.9504f, calculateAcceleration(294600f, 4048.4f, 73550), 0.0001f)
        assertEquals(3.8028f, calculateAcceleration(260000f, 1866.9f, 67880), 0.0001f)
        assertEquals(0.77471f, calculateAccelerationDueToVertSpd(-2000f, 250f), 0.0001f)
        assertEquals(0.37245f, calculateAccelerationDueToVertSpd(-1000f, 260f), 0.0001f)
        assertEquals(0.44109f, calculateRequiredAcceleration(0, 100, 3000f), 0.00001f)
        assertEquals(1.1292f, calculateRequiredAcceleration(0, 160, 3000f), 0.0001f)
        assertEquals(2.0238f, calculateRequiredAcceleration(80, 180, 1700f), 0.0001f)
    }

    @Test
    @DisplayName("Vertical speed calculations")
    fun checkVerticalSpeedCalculations() {
        // Delta set at 5th significant figure
        assertEquals(5621.7f, calculateVerticalSpd(286000f, 140f, 0f, 73550), 0.1f)
        assertEquals(3453.1f, calculateVerticalSpd(286000f, 140f, 1.5f, 73550), 0.1f)
        assertEquals(5367.1f, calculateVerticalSpd(252000f, 140f, 0f, 67880), 0.1f)
        assertEquals(3198.5f, calculateVerticalSpd(252000f, 140f, 1.5f, 67880), 0.1f)
    }

    @Test
    @DisplayName("Altitude calculations")
    fun checkAltitudeCalculations() {
        // Delta set at ones
        assertEquals(0f, calculateAltAtPressure(101325f), 1f)
        assertEquals(5000f, calculateAltAtPressure(84307f), 1f)
        assertEquals(10000f, calculateAltAtPressure(69682f), 1f)
        assertEquals(15000f, calculateAltAtPressure(57182f), 1f)
        assertEquals(20000f, calculateAltAtPressure(46563f), 1f)
        assertEquals(25000f, calculateAltAtPressure(37601f), 1f)
        assertEquals(30000f, calculateAltAtPressure(30090f), 1f)
        assertEquals(35000f, calculateAltAtPressure(23842f), 1f)
        assertEquals(36090f, calculateAltAtPressure(22631f), 1f)
        assertEquals(40000f, calculateAltAtPressure(18754f), 1f)
        assertEquals(45000f, calculateAltAtPressure(14748f), 1f)

        // Delta set at 2x 5th significant figure
        assertEquals(32260f, calculateCrossoverAltitude(250, 0.7f), 2f)
        assertEquals(29730f, calculateCrossoverAltitude(320, 0.834f), 2f)
        assertEquals(30509f, calculateCrossoverAltitude(313, 0.83f), 2f)
        assertEquals(29861f, calculateCrossoverAltitude(309, 0.81f), 2f)

        // 0 delta for max altitude calculations
        val aircraftPerfData = AircraftTypeData.AircraftPerfData().apply {
            massKg = 260000
            tripMach = 0.85f
            vR = 170
        }
        assertEquals(37000, calculateMaxAlt(aircraftPerfData))
        aircraftPerfData.apply {
            massKg = 310000
            tripMach = 0.843f
            vR = 170
        }
        assertEquals(34000, calculateMaxAlt(aircraftPerfData))
    }

    @Test
    @DisplayName("Gradient calculations")
    fun checkGradientCalculations() {
        // Delta set at 5th significant figure
        assertEquals(0.060632f, calculateDescentGradient(112973f, 190000), 0.000001f)
    }
}