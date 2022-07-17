import com.bombbird.terminalcontrol2.components.CommandTarget
import com.bombbird.terminalcontrol2.navigation.*
import io.kotest.core.spec.style.FunSpec
import io.kotest.matchers.booleans.shouldBeFalse
import io.kotest.matchers.booleans.shouldBeTrue
import io.kotest.matchers.shouldBe

/** Kotest FunSpec class for testing route tools functions */
object RouteToolsTest: FunSpec() {
    private val wpt1 = Route.WaypointLeg(0, null, 5000, 240,
        legActive = true, altRestrActive = true, spdRestrActive = true)
    private val wpt2 = Route.WaypointLeg(1, 10000, 3000, null,
        legActive = false, altRestrActive = true, spdRestrActive = true)
    private val wpt3 = Route.WaypointLeg(0, null, 5000, 240,
        legActive = false, altRestrActive = false, spdRestrActive = false)
    private val hold1 = Route.HoldLeg(0, null, 4000, 230, 240, 50,
        5, CommandTarget.TURN_LEFT)
    private val vector1 = Route.VectorLeg(180)
    private val discontinuity = Route.DiscontinuityLeg()

    init {
        test("Leg equality check") {
            compareLegEquality(wpt1, Route.WaypointLeg(1, 10000, 3000, null,
                legActive = false, altRestrActive = true, spdRestrActive = true)).shouldBeFalse()
            compareLegEquality(wpt1, Route.WaypointLeg(0, null, 5000, 240,
                legActive = false, altRestrActive = false, spdRestrActive = false)).shouldBeTrue()
            compareLegEquality(wpt1, hold1).shouldBeFalse()
            compareLegEquality(wpt1, vector1).shouldBeFalse()
            compareLegEquality(wpt1, discontinuity).shouldBeFalse()

            compareLegEquality(hold1, hold1).shouldBeTrue()
            compareLegEquality(hold1, Route.HoldLeg(0, null, null, 230, 240,
                50, 5, CommandTarget.TURN_LEFT)).shouldBeFalse()
            compareLegEquality(hold1, Route.HoldLeg(1, null, 4000, 230, 240,
                50, 5, CommandTarget.TURN_LEFT)).shouldBeFalse()
            compareLegEquality(hold1, vector1).shouldBeFalse()
            compareLegEquality(hold1, discontinuity).shouldBeFalse()

            compareLegEquality(vector1, vector1).shouldBeTrue()
            compareLegEquality(vector1, Route.VectorLeg(300)).shouldBeFalse()
            compareLegEquality(vector1, discontinuity).shouldBeFalse()

            compareLegEquality(discontinuity, discontinuity).shouldBeTrue()
        }

        test("Route equality strict check") {
            val route1 = Route().apply {
                add(wpt1)
                add(vector1)
                add(hold1)
                add(discontinuity)
            }
            val route2 = Route().apply {
                add(wpt2)
                add(wpt1)
                add(vector1)
                add(hold1)
                add(discontinuity)
            }
            val route3 = Route().apply {
                add(wpt2)
                add(vector1)
                add(hold1)
                add(discontinuity)
            }
            val route4 = Route().apply {
                add(wpt3)
                add(vector1)
                add(hold1)
                add(discontinuity)
            }

            checkRouteEqualityStrict(route1, route1).shouldBeTrue()
            checkRouteEqualityStrict(route1, route2).shouldBeFalse()
            checkRouteEqualityStrict(route1, route3).shouldBeFalse()
            checkRouteEqualityStrict(route2, route3).shouldBeFalse()
            checkRouteEqualityStrict(route1, route4).shouldBeFalse()
        }

        test("Leg changed check") {
            val route1 = Route().apply {
                add(wpt1)
                add(vector1)
                add(hold1)
                add(discontinuity)
            }
            val route2 = Route().apply {
                add(wpt2)
                add(wpt1)
                add(vector1)
                add(hold1)
                add(discontinuity)
            }
            val route3 = Route().apply {
                add(wpt2)
                add(vector1)
                add(hold1)
                add(discontinuity)
            }
            val route4 = Route().apply {
                add(wpt3)
                add(vector1)
                add(hold1)
                add(discontinuity)
            }

            checkLegChanged(route1, wpt1).shouldBeFalse()
            checkLegChanged(route1, wpt2).shouldBeTrue()
            checkLegChanged(route2, hold1).shouldBeFalse()
            checkLegChanged(route2, Route.VectorLeg(190)).shouldBeTrue()
            checkLegChanged(route3, discontinuity).shouldBeFalse()
            checkLegChanged(route4, (wpt3.copyLeg() as Route.WaypointLeg).apply { legActive = true }).shouldBeFalse()
        }

        test("Restriction changed check") {
            val route1 = Route().apply {
                add(wpt1)
                add(vector1)
                add(hold1)
                add(discontinuity)
            }
            val route2 = Route().apply {
                add(wpt2)
                add(wpt1)
                add(vector1)
                add(hold1)
                add(discontinuity)
            }
            val route3 = Route().apply {
                add(wpt2)
                add(vector1)
                add(hold1)
                add(discontinuity)
            }
            val route4 = Route().apply {
                add(wpt3)
                add(vector1)
                add(hold1)
                add(discontinuity)
            }

            checkRestrChanged(route1, wpt1) shouldBe Triple(false, false, false)
            checkRestrChanged(route1, wpt2) shouldBe Triple(true, true, true)
            checkRestrChanged(route2, wpt3) shouldBe Triple(true, true, true)
            checkRestrChanged(route3, (wpt2.copyLeg() as Route.WaypointLeg).apply { altRestrActive = false }) shouldBe Triple(true, false, false)
            checkRestrChanged(route4, (wpt3.copyLeg() as Route.WaypointLeg).apply { legActive = true }) shouldBe Triple(false, false, true)
            checkRestrChanged(route4, (wpt3.copyLeg() as Route.WaypointLeg).apply { spdRestrActive = true }) shouldBe Triple(false, true, false)
        }
    }
}