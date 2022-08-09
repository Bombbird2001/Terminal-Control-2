import com.bombbird.terminalcontrol2.components.*
import com.bombbird.terminalcontrol2.global.MAG_HDG_DEV
import com.bombbird.terminalcontrol2.navigation.Approach
import com.bombbird.terminalcontrol2.utilities.UsabilityFilter
import io.kotest.core.spec.style.FunSpec
import io.kotest.matchers.collections.shouldContainExactly
import io.kotest.matchers.floats.plusOrMinus
import io.kotest.matchers.nulls.shouldNotBeNull
import io.kotest.matchers.shouldBe
import ktx.ashley.get

/** Kotest FunSpec class for testing approach functions */
object ApproachTest: FunSpec() {
    private var approach: Approach? = null

    init {
        beforeTest {
            testInitialiseGameAndServer()
            MAG_HDG_DEV = 0f
            approach = Approach("TESTAPP1", 0, 0, 0f, 0f, 200, 500,
                false, UsabilityFilter.DAY_AND_NIGHT)
        }

        test("Localizer addition") {
            approach?.addLocalizer(360, 25)
            approach?.entity?.get(Localizer.mapper)?.maxDistNm shouldBe 25
            approach?.entity?.get(Direction.mapper)?.trackUnitVector?.apply {
                x shouldBe 0f.plusOrMinus(0.0001f)
                y shouldBe (-1f).plusOrMinus(0.0001f)
            }.shouldNotBeNull()

            approach?.addLocalizer(90, 20)
            approach?.entity?.get(Localizer.mapper)?.maxDistNm shouldBe 20
            approach?.entity?.get(Direction.mapper)?.trackUnitVector?.apply {
                x shouldBe (-1f).plusOrMinus(0.0001f)
                y shouldBe 0f.plusOrMinus(0.0001f)
            }.shouldNotBeNull()

            MAG_HDG_DEV = 3f
            approach?.addLocalizer(180, 15)
            approach?.entity?.get(Localizer.mapper)?.maxDistNm shouldBe 15
            approach?.entity?.get(Direction.mapper)?.trackUnitVector?.apply {
                x shouldBe (-0.052336f).plusOrMinus(0.000001f)
                y shouldBe 0.99863f.plusOrMinus(0.00001f)
            }.shouldNotBeNull()

            approach?.addLocalizer(270, 10)
            approach?.entity?.get(Localizer.mapper)?.maxDistNm shouldBe 10
            approach?.entity?.get(Direction.mapper)?.trackUnitVector?.apply {
                x shouldBe 0.99863f.plusOrMinus(0.0001f)
                y shouldBe 0.052336f.plusOrMinus(0.0001f)
            }.shouldNotBeNull()
        }

        test("Glideslope addition") {
            approach?.addGlideslope(3f, -2f, 4000)
            approach?.entity?.get(GlideSlope.mapper)?.apply {
                glideAngle shouldBe 3f
                offsetNm shouldBe -2f
                maxInterceptAlt shouldBe 4000
            }.shouldNotBeNull()

            approach?.addGlideslope(4.5f, -1.2f, 6000)
            approach?.entity?.get(GlideSlope.mapper)?.apply {
                glideAngle shouldBe 4.5f
                offsetNm shouldBe -1.2f
                maxInterceptAlt shouldBe 6000
            }.shouldNotBeNull()
        }

        test("Step down addition") {
            approach?.addStepDown(arrayOf(StepDown.Step(3f, 900), StepDown.Step(5f, 1500), StepDown.Step(10f, 3000)))
            approach?.entity?.get(StepDown.mapper)?.altAtDist shouldContainExactly arrayOf(StepDown.Step(3f, 900), StepDown.Step(5f, 1500), StepDown.Step(10f, 3000))

            approach?.addStepDown(arrayOf(StepDown.Step(3.5f, 1000), StepDown.Step(8f, 2500), StepDown.Step(15f, 4700)))
            approach?.entity?.get(StepDown.mapper)?.altAtDist shouldContainExactly arrayOf(StepDown.Step(3.5f, 1000), StepDown.Step(8f, 2500), StepDown.Step(15f, 4700))
        }

        test("Line up distance addition") {
            approach?.addLineUpDist(1.25f)
            approach?.entity?.get(LineUpDist.mapper)?.lineUpDistNm shouldBe 1.25f

            approach?.addLineUpDist(1.9f)
            approach?.entity?.get(LineUpDist.mapper)?.lineUpDistNm shouldBe 1.9f
        }

        test("Circling addition") {
            approach?.addCircling(1000, 2000, CommandTarget.TURN_LEFT)
            approach?.entity?.get(Circling.mapper)?.apply {
                minBreakoutAlt shouldBe 1000
                maxBreakoutAlt shouldBe 2000
                breakoutDir shouldBe CommandTarget.TURN_LEFT
            }.shouldNotBeNull()

            approach?.addCircling(1300, 1900, CommandTarget.TURN_RIGHT)
            approach?.entity?.get(Circling.mapper)?.apply {
                minBreakoutAlt shouldBe 1300
                maxBreakoutAlt shouldBe 1900
                breakoutDir shouldBe CommandTarget.TURN_RIGHT
            }.shouldNotBeNull()
        }
    }
}