import com.bombbird.terminalcontrol2.components.CommandTarget
import com.bombbird.terminalcontrol2.navigation.Route
import io.kotest.core.spec.style.FunSpec
import io.kotest.matchers.shouldBe

/** Kotest FunSpec class for testing route functions */
object RouteTest: FunSpec() {
    private lateinit var route1: Route
    private lateinit var route2: Route

    init {
        beforeTest {
            route1 = Route().apply {
                add(Route.WaypointLeg(0, null, null, null, legActive = true, altRestrActive = true, spdRestrActive = true))
                add(Route.WaypointLeg(1, null, null, null, legActive = true, altRestrActive = true, spdRestrActive = true))
                add(Route.WaypointLeg(2, null, null, null, legActive = true, altRestrActive = true, spdRestrActive = true))
                add(Route.HoldLeg(2, null, null, null, null, 360, 5, CommandTarget.TURN_LEFT))
            }
            route2 = Route().apply {
                add(Route.WaypointLeg(6, null, null, null, legActive = true, altRestrActive = true, spdRestrActive = true))
                add(Route.WaypointLeg(3, null, null, null, legActive = true, altRestrActive = true, spdRestrActive = true, phase = Route.Leg.APP_TRANS))
                add(Route.WaypointLeg(4, null, null, null, legActive = true, altRestrActive = true, spdRestrActive = true, phase = Route.Leg.APP))
                add(Route.WaypointLeg(5, null, null, null, legActive = true, altRestrActive = true, spdRestrActive = true, phase = Route.Leg.APP))
            }
        }

        test("Route extend") {
            route1.extendRoute(route2)
            (route2[0] as Route.WaypointLeg).legActive = false
            route1[0] shouldBe Route.WaypointLeg(0, null, null, null, legActive = true, altRestrActive = true, spdRestrActive = true)
            route1[1] shouldBe Route.WaypointLeg(1, null, null, null, legActive = true, altRestrActive = true, spdRestrActive = true)
            route1[2] shouldBe Route.WaypointLeg(2, null, null, null, legActive = true, altRestrActive = true, spdRestrActive = true)
            route1[3] shouldBe Route.HoldLeg(2, null, null, null, null, 360, 5, CommandTarget.TURN_LEFT)
            route1[4] shouldBe Route.WaypointLeg(6, null, null, null, legActive = false, altRestrActive = true, spdRestrActive = true)
            route1[5] shouldBe Route.WaypointLeg(3, null, null, null, legActive = true, altRestrActive = true, spdRestrActive = true, phase = Route.Leg.APP_TRANS)
            route1[6] shouldBe Route.WaypointLeg(4, null, null, null, legActive = true, altRestrActive = true, spdRestrActive = true, phase = Route.Leg.APP)
            route1[7] shouldBe Route.WaypointLeg(5, null, null, null, legActive = true, altRestrActive = true, spdRestrActive = true, phase = Route.Leg.APP)
        }

        test("Route extend copy") {
            route1.extendRouteCopy(route2)
            (route2[0] as Route.WaypointLeg).legActive = false
            route1[0] shouldBe Route.WaypointLeg(0, null, null, null, legActive = true, altRestrActive = true, spdRestrActive = true)
            route1[1] shouldBe Route.WaypointLeg(1, null, null, null, legActive = true, altRestrActive = true, spdRestrActive = true)
            route1[2] shouldBe Route.WaypointLeg(2, null, null, null, legActive = true, altRestrActive = true, spdRestrActive = true)
            route1[3] shouldBe Route.HoldLeg(2, null, null, null, null, 360, 5, CommandTarget.TURN_LEFT)
            route1[4] shouldBe Route.WaypointLeg(6, null, null, null, legActive = true, altRestrActive = true, spdRestrActive = true)
            route1[5] shouldBe Route.WaypointLeg(3, null, null, null, legActive = true, altRestrActive = true, spdRestrActive = true, phase = Route.Leg.APP_TRANS)
            route1[6] shouldBe Route.WaypointLeg(4, null, null, null, legActive = true, altRestrActive = true, spdRestrActive = true, phase = Route.Leg.APP)
            route1[7] shouldBe Route.WaypointLeg(5, null, null, null, legActive = true, altRestrActive = true, spdRestrActive = true, phase = Route.Leg.APP)
        }

        test("Route set") {
            route1.setToRoute(route2)
            (route2[0] as Route.WaypointLeg).legActive = false
            route1[0] shouldBe Route.WaypointLeg(6, null, null, null, legActive = false, altRestrActive = true, spdRestrActive = true)
            route1[1] shouldBe Route.WaypointLeg(3, null, null, null, legActive = true, altRestrActive = true, spdRestrActive = true, phase = Route.Leg.APP_TRANS)
            route1[2] shouldBe Route.WaypointLeg(4, null, null, null, legActive = true, altRestrActive = true, spdRestrActive = true, phase = Route.Leg.APP)
            route1[3] shouldBe Route.WaypointLeg(5, null, null, null, legActive = true, altRestrActive = true, spdRestrActive = true, phase = Route.Leg.APP)
        }

        test("Route set copy") {
            route1.setToRouteCopy(route2)
            (route2[0] as Route.WaypointLeg).legActive = false
            route1[0] shouldBe Route.WaypointLeg(6, null, null, null, legActive = true, altRestrActive = true, spdRestrActive = true)
            route1[1] shouldBe Route.WaypointLeg(3, null, null, null, legActive = true, altRestrActive = true, spdRestrActive = true, phase = Route.Leg.APP_TRANS)
            route1[2] shouldBe Route.WaypointLeg(4, null, null, null, legActive = true, altRestrActive = true, spdRestrActive = true, phase = Route.Leg.APP)
            route1[3] shouldBe Route.WaypointLeg(5, null, null, null, legActive = true, altRestrActive = true, spdRestrActive = true, phase = Route.Leg.APP)
        }
    }
}