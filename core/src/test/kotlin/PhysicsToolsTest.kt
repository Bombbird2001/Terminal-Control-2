import com.bombbird.terminalcontrol2.utilities.AircraftTypeData
import com.bombbird.terminalcontrol2.utilities.PhysicsTools
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.DisplayName
import org.junit.jupiter.api.Test

object PhysicsToolsTest {
    @Test
    @DisplayName("Temperature calculations")
    fun checkTempCalculations() {
        // Delta set at 1st decimal place
        assertEquals(288.15f, PhysicsTools.calculateTempAtAlt(0f), 0.01f)
        assertEquals(278.244f, PhysicsTools.calculateTempAtAlt(5000f), 0.01f)
        assertEquals(268.338f, PhysicsTools.calculateTempAtAlt(10000f), 0.01f)
        assertEquals(258.432f, PhysicsTools.calculateTempAtAlt(15000f), 0.01f)
        assertEquals(248.526f, PhysicsTools.calculateTempAtAlt(20000f), 0.01f)
        assertEquals(238.62f, PhysicsTools.calculateTempAtAlt(25000f), 0.01f)
        assertEquals(228.714f, PhysicsTools.calculateTempAtAlt(30000f), 0.01f)
        assertEquals(218.808f, PhysicsTools.calculateTempAtAlt(35000f), 0.01f)
        assertEquals(216.65f, PhysicsTools.calculateTempAtAlt(36090f), 0.01f)
        assertEquals(216.65f, PhysicsTools.calculateTempAtAlt(40000f), 0.01f)
        assertEquals(216.65f, PhysicsTools.calculateTempAtAlt(45000f), 0.01f)
    }

    @Test
    @DisplayName("Pressure calculations")
    fun checkPressureCalculations() {
        // Delta set at ones
        assertEquals(101325f, PhysicsTools.calculatePressureAtAlt(0f), 1f)
        assertEquals(84307f, PhysicsTools.calculatePressureAtAlt(5000f), 1f)
        assertEquals(69682f, PhysicsTools.calculatePressureAtAlt(10000f), 1f)
        assertEquals(57182f, PhysicsTools.calculatePressureAtAlt(15000f), 1f)
        assertEquals(46563f, PhysicsTools.calculatePressureAtAlt(20000f), 1f)
        assertEquals(37601f, PhysicsTools.calculatePressureAtAlt(25000f), 1f)
        assertEquals(30090f, PhysicsTools.calculatePressureAtAlt(30000f), 1f)
        assertEquals(23842f, PhysicsTools.calculatePressureAtAlt(35000f), 1f)
        assertEquals(22631f, PhysicsTools.calculatePressureAtAlt(36090f), 1f)
        assertEquals(18754f, PhysicsTools.calculatePressureAtAlt(40000f), 1f)
        assertEquals(14748f, PhysicsTools.calculatePressureAtAlt(45000f), 1f)
    }

    @Test
    @DisplayName("Density calculations")
    fun checkDensityCalculations() {
        // Delta set at 4th decimal place
        assertEquals(1.225f, PhysicsTools.calculateAirDensity(101325f, 288.15f), 0.0001f)
        assertEquals(1.05555f, PhysicsTools.calculateAirDensity(84307f, 278.244f), 0.0001f)
        assertEquals(0.90464f, PhysicsTools.calculateAirDensity(69682f, 268.338f), 0.0001f)
        assertEquals(0.77082f, PhysicsTools.calculateAirDensity(57182f, 258.432f), 0.0001f)
        assertEquals(0.65269f, PhysicsTools.calculateAirDensity(46563f, 248.526f), 0.0001f)
        assertEquals(0.54895f, PhysicsTools.calculateAirDensity(37601f, 238.62f), 0.0001f)
        assertEquals(0.45831f, PhysicsTools.calculateAirDensity(30090f, 228.714f), 0.0001f)
        assertEquals(0.3796f, PhysicsTools.calculateAirDensity(23842f, 218.808f), 0.0001f)
        assertEquals(0.3639f, PhysicsTools.calculateAirDensity(22631f, 216.65f), 0.0001f)
        assertEquals(0.30156f, PhysicsTools.calculateAirDensity(18754f, 216.65f), 0.0001f)
        assertEquals(0.23714f, PhysicsTools.calculateAirDensity(14748f, 216.65f), 0.0001f)
    }

    @Test
    @DisplayName("Thrust calculations")
    fun checkThrustCalculations() {
        // Delta for the following 5 tests are set at a higher value as expected values are not exact
        assertEquals(17000f, PhysicsTools.calculateMaxJetThrust(17000, 101325f, 288.15f), 100f)
        assertEquals(16490f, PhysicsTools.calculateMaxJetThrust(17000, 97698.71f, 286.111f), 100f)
        assertEquals(12070f, PhysicsTools.calculateMaxJetThrust(17000, 69706f, 268.333f), 100f)
        assertEquals(8330f, PhysicsTools.calculateMaxJetThrust(17000, 46608.56f, 248.889f), 100f)
        assertEquals(5610f, PhysicsTools.calculateMaxJetThrust(17000, 30130.09f, 229.444f), 100f)

        // Delta set at 5th significant figure
        assertEquals(77261.88f, PhysicsTools.calculateMaxPropThrust(7562000, 26.40509f, 50f, 101325f, 288.15f), 1f)
        assertEquals(30162.31f, PhysicsTools.calculateMaxPropThrust(7562000, 26.40509f, 240f, 101325f, 288.15f), 1f)
        assertEquals(62192.316f, PhysicsTools.calculateMaxPropThrust(7562000, 26.40509f, 50f, 46563f, 248.526f), 1f)
        assertEquals(29779.682f, PhysicsTools.calculateMaxPropThrust(7562000, 26.40509f, 240f, 46563f, 248.526f), 1f)
    }

    @Test
    @DisplayName("Drag calculations")
    fun checkDragCalculations() {
        // Delta set at 5th significant figure
        assertEquals(518.719f, PhysicsTools.calculateDrag(1.28f, 1.225f, 50f), 0.01f)
        assertEquals(11951.374f, PhysicsTools.calculateDrag(1.28f, 1.225f, 240f), 1f)
        assertEquals(6797.322f, PhysicsTools.calculateDrag(6.552f, 1.225f, 80f), 0.1f)
        assertEquals(50658.08f, PhysicsTools.calculateDrag(6.552f, 0.36518f, 400f), 1f)
    }

    @Test
    @DisplayName("Speed calculations")
    fun checkSpeedCalculations() {
        // Delta set at 5th significant figure
        assertEquals(79.999f, PhysicsTools.calculateIASFromTAS(0f, 80f), 0.001f)
        assertEquals(299.996f, PhysicsTools.calculateIASFromTAS(0f, 300f), 0.01f)
        assertEquals(123.56f, PhysicsTools.calculateIASFromTAS(30000f, 200f), 0.01f)
        assertEquals(309.948f, PhysicsTools.calculateIASFromTAS(30000f, 480f), 0.01f)
        assertEquals(41.156f, PhysicsTools.calculateTASFromIAS(0f, 80f), 0.001f)
        assertEquals(154.335f, PhysicsTools.calculateTASFromIAS(0f, 300f), 0.01f)
        assertEquals(164.07f, PhysicsTools.calculateTASFromIAS(30000f, 200f), 0.01f)
        assertEquals(362.691f, PhysicsTools.calculateTASFromIAS(30000f, 480f), 0.01f)
    }

    @Test
    @DisplayName("Acceleration calculations")
    fun checkAccelerationCalculations() {
        assertEquals(0f, PhysicsTools.calculateAcceleration(10000f, 10000f, 10000), 0.0001f)
        assertEquals(0.7f, PhysicsTools.calculateAcceleration(17000f, 10000f, 10000), 0.00001f)
        assertEquals(3.9504f, PhysicsTools.calculateAcceleration(294600f, 4048.4f, 73550), 0.0001f)
        assertEquals(3.8028f, PhysicsTools.calculateAcceleration(260000f, 1866.9f, 67880), 0.0001f)
        assertEquals(3.9261f, PhysicsTools.calculateMaxAcceleration(AircraftTypeData.AircraftPerfData(), 0f, 80f, false), 0.0001f)
        assertEquals(-0.67396f, PhysicsTools.calculateMinAcceleration(AircraftTypeData.AircraftPerfData(), 0f, 140f, true), 0.00001f)
        assertEquals(0.44109f, PhysicsTools.calculateRequiredAcceleration(0, 100, 3000f), 0.00001f)
        assertEquals(1.1292f, PhysicsTools.calculateRequiredAcceleration(0, 160, 3000f), 0.0001f)
        assertEquals(2.0238f, PhysicsTools.calculateRequiredAcceleration(80, 180, 1700f), 0.0001f)
    }
}