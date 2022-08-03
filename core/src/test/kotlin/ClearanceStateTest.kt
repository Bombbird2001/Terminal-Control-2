import com.bombbird.terminalcontrol2.components.CommandTarget
import com.bombbird.terminalcontrol2.components.FlightType
import com.bombbird.terminalcontrol2.entities.Aircraft
import com.bombbird.terminalcontrol2.global.MAG_HDG_DEV
import com.bombbird.terminalcontrol2.navigation.ClearanceState
import com.bombbird.terminalcontrol2.navigation.Route
import com.bombbird.terminalcontrol2.utilities.AircraftTypeData
import io.kotest.core.spec.style.FunSpec
import io.kotest.matchers.shouldBe

/** Kotest FunSpec class for testing clearance state functions */
object ClearanceStateTest: FunSpec() {
    private lateinit var aircraft: Aircraft
    private lateinit var actingClearance1: ClearanceState.ActingClearance
    private lateinit var actingClearance2: ClearanceState.ActingClearance
    private lateinit var uiClearance1: ClearanceState

    init {
        beforeTest {
            initialiseGameAndServer()
            AircraftTypeData.addAircraftPerf("B77W", AircraftTypeData.AircraftPerfData())
            MAG_HDG_DEV = 0f
            aircraft = Aircraft("TESTSHIBA", 0f, 0f, 10000f, "B77W", FlightType.ARRIVAL, false)
            actingClearance1 = ClearanceState(route = Route().apply {
                add(Route.WaypointLeg(0, null, null, null, legActive = true, altRestrActive = true, spdRestrActive = true))
                add(Route.WaypointLeg(1, null, null, null, legActive = true, altRestrActive = true, spdRestrActive = true))
                add(Route.WaypointLeg(2, null, null, null, legActive = true, altRestrActive = true, spdRestrActive = true))
                add(Route.HoldLeg(2, null, null, null, null, 360, 5, CommandTarget.TURN_LEFT))
            }).ActingClearance()
            actingClearance2 = ClearanceState(route = Route().apply {
                add(Route.WaypointLeg(6, null, null, null, legActive = true, altRestrActive = true, spdRestrActive = true, phase = Route.Leg.APP))
                add(Route.WaypointLeg(3, null, null, null, legActive = true, altRestrActive = true, spdRestrActive = true, phase = Route.Leg.APP))
            }).ActingClearance()
            uiClearance1 = ClearanceState(route = Route().apply {
                add(Route.WaypointLeg(6, null, null, null, legActive = true, altRestrActive = true, spdRestrActive = true))
                add(Route.WaypointLeg(1, null, null, null, legActive = true, altRestrActive = true, spdRestrActive = true))
                add(Route.WaypointLeg(3, null, null, null, legActive = true, altRestrActive = true, spdRestrActive = true, phase = Route.Leg.APP_TRANS))
                add(Route.WaypointLeg(4, null, null, null, legActive = true, altRestrActive = true, spdRestrActive = true, phase = Route.Leg.APP))
                add(Route.WaypointLeg(5, null, null, null, legActive = true, altRestrActive = true, spdRestrActive = true, phase = Route.Leg.APP))
            })

        }

        test("Route conflict 1 test") {
            // Tests for hold clearance at waypoint that is already passed
            actingClearance1.updateClearanceAct(ClearanceState(route = Route().apply {
                add(Route.WaypointLeg(3, null, null, null, legActive = true, altRestrActive = true, spdRestrActive = true))
                add(Route.HoldLeg(3, null, null, null, null, 360, 5, CommandTarget.TURN_LEFT))
                add(Route.WaypointLeg(1, null, null, null, legActive = true, altRestrActive = true, spdRestrActive = true))
                add(Route.WaypointLeg(2, null, null, null, legActive = true, altRestrActive = true, spdRestrActive = true))
                add(Route.HoldLeg(2, null, null, null, null, 360, 5, CommandTarget.TURN_LEFT))
            }), aircraft.entity)
            actingClearance1.clearanceState.route[0] shouldBe Route.HoldLeg(3, null, null, null, null, 360, 5, CommandTarget.TURN_LEFT)
        }

        test("Route conflict 2 test") {
            // Tests for waypoint that is already passed
            actingClearance1.updateClearanceAct(ClearanceState(route = Route().apply {
                add(Route.WaypointLeg(3, null, null, null, legActive = true, altRestrActive = true, spdRestrActive = true))
                add(Route.WaypointLeg(0, null, null, null, legActive = true, altRestrActive = true, spdRestrActive = true))
                add(Route.WaypointLeg(1, null, null, null, legActive = true, altRestrActive = true, spdRestrActive = true))
                add(Route.WaypointLeg(2, null, null, null, legActive = true, altRestrActive = true, spdRestrActive = true))
                add(Route.HoldLeg(2, null, null, null, null, 360, 5, CommandTarget.TURN_LEFT))
            }), aircraft.entity)
            actingClearance1.clearanceState.route[0] shouldBe Route.WaypointLeg(0, null, null, null, legActive = true, altRestrActive = true, spdRestrActive = true)
        }

        test("Route conflict 3 test") {
            // Tests for new transition cleared after passing waypoint
            actingClearance1.updateClearanceAct(ClearanceState(route = Route().apply {
                add(Route.WaypointLeg(3, null, null, null, legActive = true, altRestrActive = true, spdRestrActive = true))
                add(Route.WaypointLeg(4, null, null, null, legActive = true, altRestrActive = true, spdRestrActive = true, phase = Route.Leg.APP_TRANS))
                add(Route.WaypointLeg(5, null, null, null, legActive = true, altRestrActive = true, spdRestrActive = true, phase = Route.Leg.APP))
                add(Route.WaypointLeg(6, null, null, null, legActive = true, altRestrActive = true, spdRestrActive = true, phase = Route.Leg.APP))
            }), aircraft.entity)
            actingClearance1.clearanceState.route[0] shouldBe Route.WaypointLeg(4, null, null, null, legActive = true, altRestrActive = true, spdRestrActive = true, phase = Route.Leg.APP_TRANS)
        }

        test("Route conflict 4 test") {
            // Tests for new approach/transition cleared while aircraft is direct to waypoint of previous approach which
            // is also present in the new approach
            actingClearance2.updateClearanceAct(ClearanceState(clearedApp = "TESTAPP", route = Route().apply {
                add(Route.WaypointLeg(4, null, null, null, legActive = true, altRestrActive = true, spdRestrActive = true, phase = Route.Leg.APP_TRANS))
                add(Route.WaypointLeg(5, null, null, null, legActive = true, altRestrActive = true, spdRestrActive = true, phase = Route.Leg.APP))
                add(Route.WaypointLeg(6, null, null, null, legActive = true, altRestrActive = true, spdRestrActive = true, phase = Route.Leg.APP))
                add(Route.WaypointLeg(3, null, null, null, legActive = true, altRestrActive = true, spdRestrActive = true, phase = Route.Leg.APP))
            }), aircraft.entity)
            actingClearance2.clearanceState.route[0] shouldBe Route.WaypointLeg(4, null, null, null, legActive = true, altRestrActive = true, spdRestrActive = true, phase = Route.Leg.APP_TRANS)
            actingClearance2.clearanceState.route[1] shouldBe Route.WaypointLeg(5, null, null, null, legActive = true, altRestrActive = true, spdRestrActive = true, phase = Route.Leg.APP)
            actingClearance2.clearanceState.route[2] shouldBe Route.WaypointLeg(6, null, null, null, legActive = true, altRestrActive = true, spdRestrActive = true, phase = Route.Leg.APP)
            actingClearance2.clearanceState.route[3] shouldBe Route.WaypointLeg(3, null, null, null, legActive = true, altRestrActive = true, spdRestrActive = true, phase = Route.Leg.APP)
        }

        test("Route conflict 5 test") {
            actingClearance2.clearanceState.route.insert(0, Route.WaypointLeg(0, null, null, null, legActive = true, altRestrActive = true, spdRestrActive = true, phase = Route.Leg.APP_TRANS))
            actingClearance2.updateClearanceAct(ClearanceState(clearedTrans = "TESTTRANS", route = Route().apply {
                add(Route.WaypointLeg(4, null, null, null, legActive = true, altRestrActive = true, spdRestrActive = true, phase = Route.Leg.APP_TRANS))
                add(Route.WaypointLeg(5, null, null, null, legActive = true, altRestrActive = true, spdRestrActive = true, phase = Route.Leg.APP))
                add(Route.WaypointLeg(6, null, null, null, legActive = true, altRestrActive = true, spdRestrActive = true, phase = Route.Leg.APP))
                add(Route.WaypointLeg(3, null, null, null, legActive = true, altRestrActive = true, spdRestrActive = true, phase = Route.Leg.APP))
            }), aircraft.entity)
            actingClearance2.clearanceState.route[0] shouldBe Route.WaypointLeg(4, null, null, null, legActive = true, altRestrActive = true, spdRestrActive = true, phase = Route.Leg.APP_TRANS)
            actingClearance2.clearanceState.route[1] shouldBe Route.WaypointLeg(5, null, null, null, legActive = true, altRestrActive = true, spdRestrActive = true, phase = Route.Leg.APP)
            actingClearance2.clearanceState.route[2] shouldBe Route.WaypointLeg(6, null, null, null, legActive = true, altRestrActive = true, spdRestrActive = true, phase = Route.Leg.APP)
            actingClearance2.clearanceState.route[3] shouldBe Route.WaypointLeg(3, null, null, null, legActive = true, altRestrActive = true, spdRestrActive = true, phase = Route.Leg.APP)
        }

        test("Route conflict 6 test") {
            actingClearance2.clearanceState.route.clear()
            actingClearance2.updateClearanceAct(ClearanceState(clearedApp = "TESTAPP", route = Route().apply {
                add(Route.WaypointLeg(0, null, null, null, legActive = true, altRestrActive = true, spdRestrActive = true, phase = Route.Leg.APP_TRANS))
                add(Route.WaypointLeg(1, null, null, null, legActive = true, altRestrActive = true, spdRestrActive = true, phase = Route.Leg.APP))
                add(Route.WaypointLeg(2, null, null, null, legActive = true, altRestrActive = true, spdRestrActive = true, phase = Route.Leg.APP))
                add(Route.WaypointLeg(4, null, null, null, legActive = true, altRestrActive = true, spdRestrActive = true, phase = Route.Leg.APP))
            }), aircraft.entity)
            actingClearance2.clearanceState.route[0] shouldBe Route.WaypointLeg(0, null, null, null, legActive = true, altRestrActive = true, spdRestrActive = true, phase = Route.Leg.APP_TRANS)
            actingClearance2.clearanceState.route[1] shouldBe Route.WaypointLeg(1, null, null, null, legActive = true, altRestrActive = true, spdRestrActive = true, phase = Route.Leg.APP)
            actingClearance2.clearanceState.route[2] shouldBe Route.WaypointLeg(2, null, null, null, legActive = true, altRestrActive = true, spdRestrActive = true, phase = Route.Leg.APP)
            actingClearance2.clearanceState.route[3] shouldBe Route.WaypointLeg(4, null, null, null, legActive = true, altRestrActive = true, spdRestrActive = true, phase = Route.Leg.APP)
        }

        test("UI clearance update 1 test") {
            uiClearance1.updateUIClearanceState(ClearanceState(route = Route().apply {
                add(Route.WaypointLeg(1, null, null, null, legActive = true, altRestrActive = true, spdRestrActive = true))
                add(Route.WaypointLeg(2, null, null, null, legActive = true, altRestrActive = true, spdRestrActive = true))
                add(Route.HoldLeg(2, null, null, null, null, 360, 5, CommandTarget.TURN_LEFT))
            }), ClearanceState(route = Route().apply {
                add(Route.WaypointLeg(0, null, null, null, legActive = true, altRestrActive = true, spdRestrActive = true))
                add(Route.WaypointLeg(1, null, null, null, legActive = true, altRestrActive = true, spdRestrActive = true))
                add(Route.WaypointLeg(2, null, null, null, legActive = true, altRestrActive = true, spdRestrActive = true))
                add(Route.HoldLeg(2, null, null, null, null, 360, 5, CommandTarget.TURN_LEFT))
            }))
            uiClearance1.route[0] shouldBe Route.WaypointLeg(1, null, null, null, legActive = true, altRestrActive = true, spdRestrActive = true)
            uiClearance1.route[1] shouldBe Route.WaypointLeg(3, null, null, null, legActive = true, altRestrActive = true, spdRestrActive = true, phase = Route.Leg.APP_TRANS)
            uiClearance1.route[2] shouldBe Route.WaypointLeg(4, null, null, null, legActive = true, altRestrActive = true, spdRestrActive = true, phase = Route.Leg.APP)
            uiClearance1.route[3] shouldBe Route.WaypointLeg(5, null, null, null, legActive = true, altRestrActive = true, spdRestrActive = true, phase = Route.Leg.APP)
        }

        test("UI clearance update 2 test") {
            uiClearance1.updateUIClearanceState(ClearanceState(routePrimaryName = "TESTROUTE", route = Route().apply {
                add(Route.WaypointLeg(1, null, null, null, legActive = true, altRestrActive = true, spdRestrActive = true))
                add(Route.WaypointLeg(2, null, null, null, legActive = true, altRestrActive = true, spdRestrActive = true))
                add(Route.HoldLeg(2, null, null, null, null, 360, 5, CommandTarget.TURN_LEFT))
            }), ClearanceState(routePrimaryName = "TESTROUTE", route = Route().apply {
                add(Route.WaypointLeg(0, null, null, null, legActive = true, altRestrActive = true, spdRestrActive = true))
                add(Route.WaypointLeg(1, null, null, null, legActive = true, altRestrActive = true, spdRestrActive = true))
                add(Route.WaypointLeg(2, null, null, null, legActive = true, altRestrActive = true, spdRestrActive = true))
                add(Route.HoldLeg(2, null, null, null, null, 360, 5, CommandTarget.TURN_LEFT))
            }))
            uiClearance1.route[0] shouldBe Route.WaypointLeg(6, null, null, null, legActive = true, altRestrActive = true, spdRestrActive = true)
            uiClearance1.route[1] shouldBe Route.WaypointLeg(1, null, null, null, legActive = true, altRestrActive = true, spdRestrActive = true)
            uiClearance1.route[2] shouldBe Route.WaypointLeg(3, null, null, null, legActive = true, altRestrActive = true, spdRestrActive = true, phase = Route.Leg.APP_TRANS)
            uiClearance1.route[3] shouldBe Route.WaypointLeg(4, null, null, null, legActive = true, altRestrActive = true, spdRestrActive = true, phase = Route.Leg.APP)
            uiClearance1.route[4] shouldBe Route.WaypointLeg(5, null, null, null, legActive = true, altRestrActive = true, spdRestrActive = true, phase = Route.Leg.APP)
        }
    }
}