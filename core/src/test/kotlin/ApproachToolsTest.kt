import com.bombbird.terminalcontrol2.TerminalControl2
import com.bombbird.terminalcontrol2.components.RunwayChildren
import com.bombbird.terminalcontrol2.components.RunwayLabel
import com.bombbird.terminalcontrol2.components.VisualApproach
import com.bombbird.terminalcontrol2.entities.Airport
import com.bombbird.terminalcontrol2.global.GAME
import com.bombbird.terminalcontrol2.global.MAG_HDG_DEV
import com.bombbird.terminalcontrol2.global.isGameInitialised
import com.bombbird.terminalcontrol2.navigation.Approach
import com.bombbird.terminalcontrol2.navigation.getAppAltAtPos
import com.bombbird.terminalcontrol2.networking.GameServer
import com.bombbird.terminalcontrol2.utilities.UsabilityFilter
import com.bombbird.terminalcontrol2.utilities.nmToPx
import com.bombbird.terminalcontrol2.utilities.pxToFt
import io.kotest.core.spec.style.FunSpec
import io.kotest.matchers.floats.plusOrMinus
import io.kotest.matchers.shouldBe
import io.kotest.matchers.shouldNotBe
import ktx.ashley.get
import ktx.collections.set

object ApproachToolsTest: FunSpec() {
    private var approach: Approach? = null

    init {
        beforeTest {
            if (!isGameInitialised) GAME = TerminalControl2()
            if (GAME.gameServer == null) GAME.gameServer = GameServer()
            MAG_HDG_DEV = 0f
            approach = Approach(
                "TESTAPP1", 0, 0, 10f, 10f, 200, 500,
                false, UsabilityFilter.DAY_AND_NIGHT
            )
            GAME.gameServer?.apply {
                airports.clear()
                airports[0] = Airport(0, "TEST", "Test Airport", 1, 0f, 0f, 0, false).also {
                    it.addRunway(0, "Test Runway", 10f, 10f, 270f, 3500,
                        0, 0, 0, "", "", RunwayLabel.BEFORE)
                }
            }
        }

        test("Approach altitude at position calculations") {
            approach?.addLocalizer(180, 25)
            approach?.addGlideslope(3f, -1.9f, 4000)
            approach?.apply { getAppAltAtPos(entity, 10f, 10f + nmToPx(10), 130f) shouldBe pxToFt(nmToPx(0.4245f)).plusOrMinus(0.1f) } shouldNotBe null

            approach?.addLocalizer(270, 25)
            approach?.addGlideslope(4.5f, -1.2f, 6000)
            approach?.apply {
                getAppAltAtPos(entity, 10f + nmToPx(8), 10f + nmToPx(6), 100f) shouldBe pxToFt(nmToPx(0.69258f)).plusOrMinus(0.1f)
                getAppAltAtPos(entity, 10f + nmToPx(26), 10f, 200f) shouldBe null
            } shouldNotBe null

            GAME.gameServer?.airports?.get(0)?.entity?.get(RunwayChildren.mapper)?.rwyMap?.get(0)?.entity?.get(VisualApproach.mapper)?.apply {
                getAppAltAtPos(visual, 10f + nmToPx(5), 10f, 130f) shouldBe pxToFt(nmToPx(0.24736f)).plusOrMinus(0.1f)
                getAppAltAtPos(visual, 10f + nmToPx(3), 10f + nmToPx(4), 100f) shouldBe pxToFt(nmToPx(0.25005f)).plusOrMinus(0.1f)
                getAppAltAtPos(visual, 10f + nmToPx(11), 10f, 180f) shouldBe null
            } shouldNotBe null
        }
    }
}