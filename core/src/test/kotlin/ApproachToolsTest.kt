import com.bombbird.terminalcontrol2.components.RunwayChildren
import com.bombbird.terminalcontrol2.components.RunwayLabel
import com.bombbird.terminalcontrol2.components.StepDown
import com.bombbird.terminalcontrol2.components.VisualApproach
import com.bombbird.terminalcontrol2.entities.Airport
import com.bombbird.terminalcontrol2.global.*
import com.bombbird.terminalcontrol2.navigation.*
import com.bombbird.terminalcontrol2.utilities.UsabilityFilter
import com.bombbird.terminalcontrol2.utilities.nmToPx
import com.bombbird.terminalcontrol2.utilities.pxToFt
import io.kotest.core.spec.style.FunSpec
import io.kotest.matchers.booleans.shouldBeFalse
import io.kotest.matchers.booleans.shouldBeTrue
import io.kotest.matchers.floats.plusOrMinus
import io.kotest.matchers.nulls.shouldNotBeNull
import io.kotest.matchers.shouldBe
import ktx.ashley.get
import ktx.collections.set

/** Kotest FunSpec class for testing approach tools functions */
object ApproachToolsTest: FunSpec() {
    private lateinit var locGsApp1: Approach
    private lateinit var locGsApp2: Approach
    private lateinit var locStepApp: Approach
    private var visApp: VisualApproach? = null

    init {
        beforeTest {
            testInitialiseGameAndServer()
            MAG_HDG_DEV = 0f
            GAME.gameServer?.apply {
                airports.clear()
                airports[0] = Airport(0, "TEST", "Test Airport", 1, 0, 0f, 0f, 0, "XXXX", false).also {
                    it.addRunway(0, "Test Runway", 10f, 10f, 270f, 3500,
                        0, 0, 0, "", "", RunwayLabel.BEFORE)
                }
            }
            locGsApp1 = Approach(
                "TESTAPP1", 0, 0, 10f, 10f, 200, 500,
                false, UsabilityFilter.DAY_AND_NIGHT
            ).apply {
                addLocalizer(180, 25)
                addGlideslope(3f, -1.9f, 4000)
            }
            locGsApp2 = Approach(
                "TESTAPP2", 0, 0, 10f, 10f, 200, 500,
                false, UsabilityFilter.DAY_AND_NIGHT
            ).apply {
                addLocalizer(270, 25)
                addGlideslope(4.5f, -1.2f, 6000)
            }
            locStepApp = Approach(
                "TESTAPP3", 0, 0, 10f, 10f, 200, 500,
                false, UsabilityFilter.DAY_AND_NIGHT
            ).apply {
                addLocalizer(270, 25)
                addStepDown(arrayOf(StepDown.Step(3f, 900), StepDown.Step(5f, 1500), StepDown.Step(10f, 3000)))
                addLineUpDist(0.4f)
            }
            visApp = GAME.gameServer?.airports?.get(0)?.entity?.get(RunwayChildren.mapper)?.rwyMap?.get(0)?.entity?.get(VisualApproach.mapper)
        }

        test("Approach altitude at position calculations") {
            getAppAltAtPos(locGsApp1.entity, 10f, 10f + nmToPx(10), 130f) shouldBe pxToFt(nmToPx(0.4245f)).plusOrMinus(0.1f)

            getAppAltAtPos(locGsApp2.entity, 10f + nmToPx(8), 10f + nmToPx(6), 100f) shouldBe pxToFt(nmToPx(0.69258f)).plusOrMinus(0.1f)
            getAppAltAtPos(locGsApp2.entity, 10f + nmToPx(26), 10f, 200f) shouldBe null

            getAppAltAtPos(locStepApp.entity, 10f + nmToPx(12), 10f, 190f) shouldBe 3000f
            getAppAltAtPos(locStepApp.entity, 10f + nmToPx(7), 10f, 170f) shouldBe 1500f
            getAppAltAtPos(locStepApp.entity, 10f + nmToPx(3.5f), 10f, 130f) shouldBe 900f
            getAppAltAtPos(locStepApp.entity, 10f + nmToPx(2), 10f, 130f) shouldBe pxToFt(nmToPx(0.090135f)).plusOrMinus(0.01f)

            visApp?.apply {
                getAppAltAtPos(visual, 10f + nmToPx(5), 10f, 130f) shouldBe pxToFt(nmToPx(0.24736f)).plusOrMinus(0.1f)
                getAppAltAtPos(visual, 10f + nmToPx(3), 10f + nmToPx(4), 100f) shouldBe pxToFt(nmToPx(0.25005f)).plusOrMinus(0.1f)
                getAppAltAtPos(visual, 10f + nmToPx(11), 10f, 180f) shouldBe null
                getAppAltAtPos(visual, 11f, 10f, 100f) shouldBe -20f
            }.shouldNotBeNull()
        }

        test("Target position calculations") {
            getTargetPos(locGsApp1.entity, 10f, 10f + nmToPx(12))?.apply {
                x shouldBe 10f.plusOrMinus(0.001f)
                y shouldBe (10f + nmToPx(10.5f)).plusOrMinus(0.01f)
            }.shouldNotBeNull()

            getTargetPos(locGsApp1.entity, 10f + nmToPx(3), 10f + nmToPx(4))?.apply {
                x shouldBe 10f.plusOrMinus(0.001f)
                y shouldBe (10f + nmToPx(4.5f)).plusOrMinus(0.01f)
            }.shouldNotBeNull()

            getTargetPos(locGsApp2.entity, 10f + nmToPx(7), 10f)?.apply {
                x shouldBe (10f + nmToPx(6.5f))
                y shouldBe (10f.plusOrMinus(0.001f)).plusOrMinus(0.01f)
            }.shouldNotBeNull()

            getTargetPos(locStepApp.entity, 10f + nmToPx(30), 10f) shouldBe null

            visApp?.apply {
                getTargetPos(visual, 10f + nmToPx(4), 10f + nmToPx(0.5f))?.apply {
                    x shouldBe (10f + nmToPx(3.5311f)).plusOrMinus(0.02f)
                    y shouldBe 10f.plusOrMinus(0.001f)
                }.shouldNotBeNull()
            }.shouldNotBeNull()
        }

        test("Line up distance check") {
            checkLineUpDistReached(locStepApp.entity, 10f + nmToPx(1), 10f).shouldBeFalse()
            checkLineUpDistReached(locStepApp.entity, 10f + nmToPx(0.3f), 10f).shouldBeTrue()
        }

        test("Localizer arc check") {
            isInsideLocArc(locGsApp1.entity, 10f, 10f + nmToPx(9), LOC_INNER_ARC_ANGLE_DEG, LOC_INNER_ARC_DIST_NM).shouldBeTrue()
            isInsideLocArc(locGsApp1.entity, 10f + nmToPx(5), 10f + nmToPx(5), LOC_INNER_ARC_ANGLE_DEG, LOC_INNER_ARC_DIST_NM).shouldBeFalse()
            isInsideLocArc(locGsApp2.entity, 10f + nmToPx(12), 10f + nmToPx(4), LOC_OUTER_ARC_ANGLE_DEG, 25).shouldBeFalse()
            isInsideLocArc(locGsApp2.entity, 10f + nmToPx(12), 10f + nmToPx(1), LOC_OUTER_ARC_ANGLE_DEG, 25).shouldBeTrue()
            isInsideLocArc(locStepApp.entity, 10f + nmToPx(13), 10f, LOC_INNER_ARC_ANGLE_DEG, LOC_INNER_ARC_DIST_NM).shouldBeFalse()
            visApp?.apply { isInsideLocArc(visual, 10f + nmToPx(5), 10f, LOC_INNER_ARC_ANGLE_DEG, LOC_INNER_ARC_DIST_NM).shouldBeFalse() }.shouldNotBeNull()
        }
    }
}