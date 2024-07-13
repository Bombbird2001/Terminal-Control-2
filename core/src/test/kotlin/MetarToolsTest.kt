import com.bombbird.terminalcontrol2.components.MetarInfo
import com.bombbird.terminalcontrol2.entities.Airport
import com.bombbird.terminalcontrol2.global.GAME
import com.bombbird.terminalcontrol2.utilities.getInterpolatedWindVector
import io.kotest.core.spec.style.FunSpec
import io.kotest.matchers.floats.plusOrMinus
import io.kotest.matchers.shouldBe
import ktx.ashley.get

object MetarToolsTest: FunSpec() {
    init {
        testInitialiseGameAndServer()

        test("Test wind interpolation - Single airport different position") {
            initialiseSingleAirport()
            val windVector = getInterpolatedWindVector(0f, 0f)
            windVector.x shouldBe 0.1f.plusOrMinus(0.00001f)
            windVector.y shouldBe 0.1f.plusOrMinus(0.00001f)
        }

        test("Test wind interpolation - Single airport same position") {
            initialiseSingleAirport()
            val windVector = getInterpolatedWindVector(20f, 40f)
            windVector.x shouldBe 0.1f.plusOrMinus(0.00001f)
            windVector.y shouldBe 0.1f.plusOrMinus(0.00001f)
        }

        test("Test wind interpolation - Multiple airport") {
            initialiseMultipleAirports()
            val windVector1 = getInterpolatedWindVector(0f, 0f)
            windVector1.x shouldBe 0.063966f.plusOrMinus(0.000001f)
            windVector1.y shouldBe (-0.0089028f).plusOrMinus(0.0000001f)

            val windVector2 = getInterpolatedWindVector(-100f, -100f)
            windVector2.x shouldBe 0.042663f.plusOrMinus(0.000001f)
            windVector2.y shouldBe (-0.011878f).plusOrMinus(0.000001f)
        }

        test("Test wind interpolation - Multiple airport same position") {
            initialiseMultipleAirports()

            val windVector1 = getInterpolatedWindVector(250f, 400f)
            windVector1.x shouldBe 0.1f.plusOrMinus(0.00001f)
            windVector1.y shouldBe 0.1f.plusOrMinus(0.00001f)

            val windVector2 = getInterpolatedWindVector(-300f, -500f)
            windVector2.x shouldBe (-0.1f).plusOrMinus(0.00001f)
            windVector2.y shouldBe 0.1f.plusOrMinus(0.00001f)

            val windVector3 = getInterpolatedWindVector(150f, -300f)
            windVector3.x shouldBe 0.1f.plusOrMinus(0.00001f)
            windVector3.y shouldBe (-0.1f).plusOrMinus(0.00001f)
        }

        test("Test wind interpolation - Multiple airport within 1px") {
            initialiseMultipleAirports()

            val windVector1 = getInterpolatedWindVector(250.5f, 400.5f)
            windVector1.x shouldBe 0.1f.plusOrMinus(0.00001f)
            windVector1.y shouldBe 0.1f.plusOrMinus(0.00001f)

            val windVector2 = getInterpolatedWindVector(-299.5f, -500.5f)
            windVector2.x shouldBe (-0.1f).plusOrMinus(0.00001f)
            windVector2.y shouldBe 0.1f.plusOrMinus(0.00001f)

            val windVector3 = getInterpolatedWindVector(150.5f, -300f)
            windVector3.x shouldBe 0.1f.plusOrMinus(0.00001f)
            windVector3.y shouldBe (-0.1f).plusOrMinus(0.00001f)
        }
    }

    private fun initialiseSingleAirport() {
        GAME.gameServer?.airports?.clear()

        val airport1 = Airport(0, "TCTP", "Haoyuan", 1, 10, 250f, 400f,
            20, "TCTP", onClient = false)
        airport1.entity[MetarInfo.mapper]?.windVectorPx?.set(0.1f, 0.1f)
        GAME.gameServer?.airports?.put(0, airport1)
    }

    private fun initialiseMultipleAirports() {
        GAME.gameServer?.airports?.clear()

        val airport1 = Airport(0, "TCTP", "Haoyuan", 1, 10, 250f, 400f,
            20, "TCTP", onClient = false)
        airport1.entity[MetarInfo.mapper]?.windVectorPx?.set(0.1f, 0.1f)
        GAME.gameServer?.airports?.put(0, airport1)

        val airport2 = Airport(1, "TCWS", "Changli", 1, 10, -300f, -500f,
            13, "TCWS", onClient = false)
        airport2.entity[MetarInfo.mapper]?.windVectorPx?.set(-0.1f, 0.1f)
        GAME.gameServer?.airports?.put(1, airport2)

        val airport3 = Airport(2, "TCTT", "Naheda", 1, 10, 150f, -300f,
            8, "TCTT", onClient = false)
        airport3.entity[MetarInfo.mapper]?.windVectorPx?.set(0.1f, -0.1f)
        GAME.gameServer?.airports?.put(2, airport3)
    }
}