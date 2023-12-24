import com.bombbird.terminalcontrol2.utilities.*
import io.kotest.core.spec.style.FunSpec
import io.kotest.matchers.floats.plusOrMinus
import io.kotest.matchers.shouldBe

/** Kotest FunSpec class for testing physics tools functions */
object PhysicsToolsTest: FunSpec() {
    init {
        test("Temperature calculations") {
            // Delta set at 1st decimal place
            calculateTempAtAlt(0f) shouldBe 288.15f.plusOrMinus(0.01f)
            calculateTempAtAlt(5000f) shouldBe 278.244f.plusOrMinus(0.01f)
            calculateTempAtAlt(10000f) shouldBe 268.338f.plusOrMinus(0.01f)
            calculateTempAtAlt(15000f) shouldBe 258.432f.plusOrMinus(0.01f)
            calculateTempAtAlt(20000f) shouldBe 248.526f.plusOrMinus(0.01f)
            calculateTempAtAlt(25000f) shouldBe 238.62f.plusOrMinus(0.01f)
            calculateTempAtAlt(30000f) shouldBe 228.714f.plusOrMinus(0.01f)
            calculateTempAtAlt(35000f) shouldBe 218.808f.plusOrMinus(0.01f)
            calculateTempAtAlt(36090f) shouldBe 216.65f.plusOrMinus(0.01f)
            calculateTempAtAlt(40000f) shouldBe 216.65f.plusOrMinus(0.01f)
            calculateTempAtAlt(45000f) shouldBe 216.65f.plusOrMinus(0.01f)
        }

        test("Pressure calculations") {
            // Delta set at ones
            calculatePressureAtAlt(0f) shouldBe 101325f.plusOrMinus(1f)
            calculatePressureAtAlt(5000f) shouldBe 84307f.plusOrMinus(1f)
            calculatePressureAtAlt(10000f) shouldBe 69682f.plusOrMinus(1f)
            calculatePressureAtAlt(15000f) shouldBe 57182f.plusOrMinus(1f)
            calculatePressureAtAlt(20000f) shouldBe 46563f.plusOrMinus(1f)
            calculatePressureAtAlt(25000f) shouldBe 37601f.plusOrMinus(1f)
            calculatePressureAtAlt(30000f) shouldBe 30090f.plusOrMinus(1f)
            calculatePressureAtAlt(35000f) shouldBe 23842f.plusOrMinus(1f)
            calculatePressureAtAlt(36090f) shouldBe 22631f.plusOrMinus(1f)
            calculatePressureAtAlt(40000f) shouldBe 18754f.plusOrMinus(1f)
            calculatePressureAtAlt(45000f) shouldBe 14748f.plusOrMinus(1f)
        }

        test("Density calculations") {
            // Delta set at 4th decimal place
            calculateAirDensity(101325f, 288.15f) shouldBe 1.225f.plusOrMinus(0.0001f)
            calculateAirDensity(84307f, 278.244f) shouldBe 1.05555f.plusOrMinus(0.0001f)
            calculateAirDensity(69682f, 268.338f) shouldBe 0.90464f.plusOrMinus(0.0001f)
            calculateAirDensity(57182f, 258.432f) shouldBe 0.77082f.plusOrMinus(0.0001f)
            calculateAirDensity(46563f, 248.526f) shouldBe 0.65269f.plusOrMinus(0.0001f)
            calculateAirDensity(37601f, 238.62f) shouldBe 0.54895f.plusOrMinus(0.0001f)
            calculateAirDensity(30090f, 228.714f) shouldBe 0.45831f.plusOrMinus(0.0001f)
            calculateAirDensity(23842f, 218.808f) shouldBe 0.3796f.plusOrMinus(0.0001f)
            calculateAirDensity(22631f, 216.65f) shouldBe 0.3639f.plusOrMinus(0.0001f)
            calculateAirDensity(18754f, 216.65f) shouldBe 0.30156f.plusOrMinus(0.0001f)
            calculateAirDensity(14748f, 216.65f) shouldBe 0.23714f.plusOrMinus(0.0001f)
        }

        test("Thrust calculations") {
            // Delta for the following 5 tests are set at a higher value as expected values are less precise
            calculateMaxJetThrust(17000, 101325f, 288.15f) shouldBe 17000f.plusOrMinus(100f)
            calculateMaxJetThrust(17000, 97698.71f, 286.111f) shouldBe 16490f.plusOrMinus(100f)
            calculateMaxJetThrust(17000, 69706f, 268.333f) shouldBe 12070f.plusOrMinus(100f)
            calculateMaxJetThrust(17000, 46608.56f, 248.889f) shouldBe 8330f.plusOrMinus(100f)
            calculateMaxJetThrust(17000, 30130.09f, 229.444f) shouldBe 5610f.plusOrMinus(100f)

            // Delta set at 5th significant figure
            calculateMaxPropThrust(7562000, 26.40509f, 50f, 101325f, 288.15f) shouldBe 146993.52f.plusOrMinus(1f)
            calculateMaxPropThrust(7562000, 26.40509f, 240f, 101325f, 288.15f) shouldBe 30623.65f.plusOrMinus(1f)
            calculateMaxPropThrust(7562000, 26.40509f, 50f, 46563f, 248.526f) shouldBe 146993.52f.plusOrMinus(1f)
            calculateMaxPropThrust(7562000, 26.40509f, 240f, 46563f, 248.526f) shouldBe 30623.65f.plusOrMinus(1f)
        }

        test("Drag calculations") {
            // Delta set at 5th significant figure
            calculateParasiticDrag(1.28f, 1.225f, 50f) shouldBe 518.719f.plusOrMinus(0.01f)
            calculateParasiticDrag(1.28f, 1.225f, 240f) shouldBe 11951.374f.plusOrMinus(1f)
            calculateParasiticDrag(6.552f, 1.225f, 80f) shouldBe 6797.322f.plusOrMinus(0.1f)
            calculateParasiticDrag(6.552f, 0.36518f, 400f) shouldBe 50658.08f.plusOrMinus(1f)
            calculateInducedDrag(351533, 340, 300000f, 37000f, 488f, false) shouldBe 179097.4f.plusOrMinus(10f)
            calculateInducedDrag(240000, 340, 1000000f, 1000f, 178f, true) shouldBe 236386.53f.plusOrMinus(10f)
        }

        test("Speed calculations") {
            // Delta set at 5th significant figure
            calculateIASFromTAS(0f, 80f) shouldBe 79.999f.plusOrMinus(0.001f)
            calculateIASFromTAS(0f, 300f) shouldBe 299.996f.plusOrMinus(0.01f)
            calculateIASFromTAS(30000f, 200f) shouldBe 123.56f.plusOrMinus(0.01f)
            calculateIASFromTAS(30000f, -200f) shouldBe (-123.56f).plusOrMinus(0.01f)
            calculateIASFromTAS(30000f, 480f) shouldBe 309.948f.plusOrMinus(0.01f)
            calculateTASFromIAS(0f, 80f) shouldBe 80f.plusOrMinus(0.001f)
            calculateTASFromIAS(0f, 300f) shouldBe 300f.plusOrMinus(0.01f)
            calculateTASFromIAS(30000f, 200f) shouldBe 318.93f.plusOrMinus(0.01f)
            calculateTASFromIAS(30000f, -200f) shouldBe (-318.93f).plusOrMinus(0.01f)
            calculateTASFromIAS(30000f, 480f) shouldBe 705.01f.plusOrMinus(0.01f)

            calculateSpeedOfSoundAtAlt(0f) shouldBe 661.48f.plusOrMinus(0.01f)
            calculateSpeedOfSoundAtAlt(5000f) shouldBe 650.01f.plusOrMinus(0.01f)
            calculateSpeedOfSoundAtAlt(10000f) shouldBe 638.33f.plusOrMinus(0.01f)
            calculateSpeedOfSoundAtAlt(15000f) shouldBe 626.44f.plusOrMinus(0.01f)
            calculateSpeedOfSoundAtAlt(20000f) shouldBe 614.31f.plusOrMinus(0.01f)
            calculateSpeedOfSoundAtAlt(25000f) shouldBe 601.95f.plusOrMinus(0.01f)
            calculateSpeedOfSoundAtAlt(30000f) shouldBe 589.32f.plusOrMinus(0.01f)
            calculateSpeedOfSoundAtAlt(35000f) shouldBe 576.42f.plusOrMinus(0.01f)
            calculateSpeedOfSoundAtAlt(40000f) shouldBe 573.57f.plusOrMinus(0.01f)
            calculateSpeedOfSoundAtAlt(45000f) shouldBe 573.57f.plusOrMinus(0.01f)

            calculateIASFromMach(0f, 1f) shouldBe 661.47f.plusOrMinus(0.01f)
            calculateIASFromMach(0f, 0.834f) shouldBe 551.67f.plusOrMinus(0.01f)
            calculateIASFromMach(10000f, 1f) shouldBe 566.29f.plusOrMinus(0.01f)
            calculateIASFromMach(10000f, 0.834f) shouldBe 468.37f.plusOrMinus(0.01f)
            calculateIASFromMach(35000f, 1f) shouldBe 350.02f.plusOrMinus(0.01f)
            calculateIASFromMach(35000f, 0.834f) shouldBe 284.81f.plusOrMinus(0.01f)
        }

        test("Acceleration calculations") {
            // Delta set at 5th significant figure
            calculateAcceleration(10000f, 10000f, 10000) shouldBe 0f.plusOrMinus(0.0001f)
            calculateAcceleration(17000f, 10000f, 10000) shouldBe 0.7f.plusOrMinus(0.00001f)
            calculateAcceleration(294600f, 4048.4f, 73550) shouldBe 3.9504f.plusOrMinus(0.0001f)
            calculateAcceleration(260000f, 1866.9f, 67880) shouldBe 3.8028f.plusOrMinus(0.0001f)

            calculateAccelerationDueToVertSpd(-2000f, 250f) shouldBe 0.77471f.plusOrMinus(0.00001f)
            calculateAccelerationDueToVertSpd(-1000f, 260f) shouldBe 0.37245f.plusOrMinus(0.00001f)

            calculateRequiredAcceleration(0, 100, 3000f) shouldBe 0.44109f.plusOrMinus(0.00001f)
            calculateRequiredAcceleration(0, 160, 3000f) shouldBe 1.1292f.plusOrMinus(0.0001f)
            calculateRequiredAcceleration(80, 180, 1700f) shouldBe 2.0238f.plusOrMinus(0.0001f)
        }

        test("Vertical speed calculations") {
            // Delta set at 5th significant figure
            calculateVerticalSpd(286000f, 140f, 0f, 73550) shouldBe 5621.7f.plusOrMinus(0.1f)
            calculateVerticalSpd(286000f, 140f, 1.5f, 73550) shouldBe 3453.1f.plusOrMinus(0.1f)
            calculateVerticalSpd(252000f, 140f, 0f, 67880) shouldBe 5367.1f.plusOrMinus(0.1f)
            calculateVerticalSpd(252000f, 140f, 1.5f, 67880) shouldBe 3198.5f.plusOrMinus(0.1f)
        }

        test("Altitude calculations") {
            // Delta set at ones
            calculateAltAtPressure(101325f) shouldBe 0f.plusOrMinus(1f)
            calculateAltAtPressure(84307f) shouldBe 5000f.plusOrMinus(1f)
            calculateAltAtPressure(69682f) shouldBe 10000f.plusOrMinus(1f)
            calculateAltAtPressure(57182f) shouldBe 15000f.plusOrMinus(1f)
            calculateAltAtPressure(46563f) shouldBe 20000f.plusOrMinus(1f)
            calculateAltAtPressure(37601f) shouldBe 25000f.plusOrMinus(1f)
            calculateAltAtPressure(30090f) shouldBe 30000f.plusOrMinus(1f)
            calculateAltAtPressure(23842f) shouldBe 35000f.plusOrMinus(1f)
            calculateAltAtPressure(22631f) shouldBe 36090f.plusOrMinus(1f)
            calculateAltAtPressure(18754f) shouldBe 40000f.plusOrMinus(1f)
            calculateAltAtPressure(14748f) shouldBe 45000f.plusOrMinus(1f)

            // Delta set at 2x 5th significant figure
            calculateCrossoverAltitude(250, 0.7f) shouldBe 32260f.plusOrMinus(2f)
            calculateCrossoverAltitude(320, 0.834f) shouldBe 29730f.plusOrMinus(2f)
            calculateCrossoverAltitude(313, 0.83f) shouldBe 30509f.plusOrMinus(2f)
            calculateCrossoverAltitude(309, 0.81f) shouldBe 29861f.plusOrMinus(2f)

            // 0 delta for max altitude calculations
            val aircraftPerfData = AircraftTypeData.AircraftPerfData().apply {
                massKg = 260000
                tripMach = 0.85f
                vR = 170
            }
            calculateMaxAlt(aircraftPerfData) shouldBe 36000

            aircraftPerfData.apply {
                massKg = 310000
                tripMach = 0.843f
                vR = 170
            }
            calculateMaxAlt(aircraftPerfData) shouldBe 34000

            calculateMaxAlt(AircraftTypeData.AircraftPerfData(AircraftTypeData.AircraftPerfData.WAKE_LIGHT,
                AircraftTypeData.AircraftPerfData.RECAT_E, null, 4102000, 24.26f,
                0.4575f, 6.1f, 200, 0.465f, 25000, 116, 113, 13450, 23000).apply {
                massKg = 13450
                tripMach = 0.43f
                tripIas = 190
            }) shouldBe 25000

            calculateMaxAlt(AircraftTypeData.AircraftPerfData(AircraftTypeData.AircraftPerfData.WAKE_LIGHT,
                AircraftTypeData.AircraftPerfData.RECAT_E, null, 4102000, 24.26f,
                0.4575f, 6.1f, 200, 0.465f, 25000, 116, 113, 13450, 23000).apply {
                massKg = 23000
                tripMach = 0.43f
                tripIas = 190
            }) shouldBe 19000
        }

        test("Gradient calculations") {
            calculateDescentGradient(112973f, 190000) shouldBe 0.060632f.plusOrMinus(0.000001f)
        }
    }
}