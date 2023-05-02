import com.badlogic.gdx.math.Polygon
import com.bombbird.terminalcontrol2.components.CommandTarget
import com.bombbird.terminalcontrol2.components.Position
import com.bombbird.terminalcontrol2.entities.Waypoint
import com.bombbird.terminalcontrol2.global.GAME
import com.bombbird.terminalcontrol2.navigation.*
import io.kotest.core.spec.style.FunSpec
import io.kotest.matchers.booleans.shouldBeFalse
import io.kotest.matchers.booleans.shouldBeTrue
import io.kotest.matchers.floats.plusOrMinus
import io.kotest.matchers.maps.shouldContainKey
import io.kotest.matchers.maps.shouldNotHaveKey
import io.kotest.matchers.nulls.shouldBeNull
import io.kotest.matchers.nulls.shouldNotBeNull
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
    private val hold2 = Route.HoldLeg(1, null, 5000, 230, 240, 230,
        5, CommandTarget.TURN_RIGHT)
    private val vector1 = Route.VectorLeg(180)
    private val discontinuity = Route.DiscontinuityLeg()
    private val initClimb1 = Route.InitClimbLeg(50, 1500, Route.Leg.MISSED_APP)
    private val route1 = Route().apply {
        add(wpt1)
        add(vector1)
        add(hold1)
        add(discontinuity)
    }
    private val route2 = Route().apply {
        add(wpt2)
        add(wpt1)
        add(vector1)
        add(hold2)
        add(discontinuity)
    }
    private val route3 = Route().apply {
        add(hold1)
        add(wpt2)
        add(vector1)
        add(discontinuity)
    }
    private val route4 = Route().apply {
        add(wpt3)
        add(vector1)
    }

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
            checkRouteEqualityStrict(route1, route1).shouldBeTrue()
            checkRouteEqualityStrict(route1, route2).shouldBeFalse()
            checkRouteEqualityStrict(route1, route3).shouldBeFalse()
            checkRouteEqualityStrict(route2, route3).shouldBeFalse()
            checkRouteEqualityStrict(route1, route4).shouldBeFalse()
        }

        test("Leg changed check") {
            checkLegChanged(route1, wpt1).shouldBeFalse()
            checkLegChanged(route1, wpt2).shouldBeTrue()
            checkLegChanged(route2, hold2).shouldBeFalse()
            checkLegChanged(route2, Route.VectorLeg(190)).shouldBeTrue()
            checkLegChanged(route3, discontinuity).shouldBeFalse()
            checkLegChanged(route4, (wpt3.copyLeg() as Route.WaypointLeg).apply { legActive = true }).shouldBeFalse()
        }

        test("Restriction changed check") {
            checkRestrChanged(route1, wpt1) shouldBe Triple(false, false, false)
            checkRestrChanged(route1, wpt2) shouldBe Triple(true, true, true)
            checkRestrChanged(route2, wpt3) shouldBe Triple(true, true, true)
            checkRestrChanged(route3, (wpt2.copyLeg() as Route.WaypointLeg).apply { altRestrActive = false }) shouldBe Triple(true, false, false)
            checkRestrChanged(route4, (wpt3.copyLeg() as Route.WaypointLeg).apply { legActive = true }) shouldBe Triple(false, false, true)
            checkRestrChanged(route4, (wpt3.copyLeg() as Route.WaypointLeg).apply { spdRestrActive = true }) shouldBe Triple(false, true, false)
        }

        test("Create, remove custom waypoint") {
            testInitialiseGameAndServer()
            GAME.gameServer?.apply {
                waypoints[-3] = Waypoint(-3, "TESTWPT", 0, 0, false)
                createCustomHoldWaypoint(1f, 1f)
                waypoints shouldContainKey -2
                createCustomHoldWaypoint(2f, 2f)
                waypoints shouldContainKey -4
                removeCustomHoldWaypoint(-3)
                waypoints shouldNotHaveKey -3
                waypoints.clear()
            }.shouldNotBeNull()
        }
        
        test("Find first waypoint leg in sector") {
            testInitialiseGameAndServer()
            GAME.gameServer?.apply { 
                waypoints[0] = Waypoint(0, "TESTWPT1", 0, 0, false)
                waypoints[1] = Waypoint(1, "TESTWPT2", 1, 1, false)
                getFirstWaypointLegInSector(Polygon(floatArrayOf(-1f, -1f, -1f, 1f, 1f, 1f, 1f, -1f)), route1) shouldBe 0
                getFirstWaypointLegInSector(Polygon(floatArrayOf(-0.5f, -0.5f, -0.5f, 0.5f, 0.5f, 0.5f, 0.5f, -0.5f)), route2) shouldBe 1
                getFirstWaypointLegInSector(Polygon(floatArrayOf(-0.5f, -0.5f, -0.5f, 0.5f, 0.5f, 0.5f, 0.5f, -0.5f)), route3).shouldBeNull()
                getFirstWaypointLegInSector(Polygon(floatArrayOf(1f, 1f, 1f, -1f, -1f, 1f)), route4) shouldBe 0
                getFirstWaypointLegInSector(Polygon(floatArrayOf(1f, 1f, 1f, -1f, -1f, 1f)), Route().apply {
                    add(hold1)
                }).shouldBeNull()
                waypoints.clear()
            }.shouldNotBeNull()
        }

        test("Find next waypoint track and turn direction") {
            testInitialiseGameAndServer()
            GAME.gameServer?.apply {
                waypoints[0] = Waypoint(0, "TESTWPT1", 0, 0, false)
                waypoints[1] = Waypoint(1, "TESTWPT2", 1, 1, false)
                findNextWptLegTrackAndDirection(route1).shouldBeNull()
                findNextWptLegTrackAndDirection(route2) shouldBe Pair(225f, CommandTarget.TURN_DEFAULT)
                findNextWptLegTrackAndDirection(route3).shouldBeNull()
                findNextWptLegTrackAndDirection(route4).shouldBeNull()
                findNextWptLegTrackAndDirection(Route().apply {
                    add(wpt1)
                    add(Route.WaypointLeg(1, null, null, null, legActive = true,
                        altRestrActive = true, spdRestrActive = true, turnDir = CommandTarget.TURN_LEFT))
                }) shouldBe Pair(45f, CommandTarget.TURN_LEFT)
                findNextWptLegTrackAndDirection(Route().apply {
                    add(wpt2)
                    add(wpt3)
                }).shouldBeNull()
                waypoints.clear()
            }.shouldNotBeNull()
        }

        test("Find after waypoint heading leg") {
            getAfterWptHdgLeg(wpt1, route1) shouldBe vector1
            getAfterWptHdgLeg(wpt2, route2).shouldBeNull()
            getAfterWptHdgLeg(wpt2, route3) shouldBe vector1
            getAfterWptHdgLeg(wpt1, route4) shouldBe vector1
        }

        test("Find next after waypoint heading leg") {
            getNextAfterWptHdgLeg(route1) shouldBe wpt1
            getNextAfterWptHdgLeg(route2) shouldBe wpt1
            getNextAfterWptHdgLeg(route3) shouldBe wpt2
            getNextAfterWptHdgLeg(route4) shouldBe wpt3
            getNextAfterWptHdgLeg(Route().apply {
                add(wpt1)
                add(wpt2)
            }).shouldBeNull()
        }

        test("Find next hold leg") {
            getNextHoldLeg(route1).shouldBeNull()
            getNextHoldLeg(route2).shouldBeNull()
            getNextHoldLeg(route3) shouldBe hold1
            getNextHoldLeg(route4).shouldBeNull()
        }

        test("Find first leg with hold ID") {
            findFirstHoldLegWithID(0, route1).shouldBeNull()
            findFirstHoldLegWithID(1, route2).shouldBeNull()
            findFirstHoldLegWithID(0, route3) shouldBe hold1
            findFirstHoldLegWithID(1, route4).shouldBeNull()
            findFirstHoldLegWithID(0, Route().apply {
                add(Route.HoldLeg(-2, null, null, null, null, 360, 5, CommandTarget.TURN_RIGHT))
                add(wpt1)
                add(hold1)
            }) shouldBe hold1
            findFirstHoldLegWithID(-2, Route().apply {
                add(Route.HoldLeg(-2, null, null, null, null, 360, 5, CommandTarget.TURN_RIGHT))
                add(wpt1)
                add(hold1)
            }) shouldBe Route.HoldLeg(-2, null, null, null, null, 360, 5, CommandTarget.TURN_RIGHT)
            findFirstHoldLegWithID(-1, Route().apply {
                add(Route.HoldLeg(-1, null, null, null, null, 320, 5, CommandTarget.TURN_LEFT))
            }) shouldBe Route.HoldLeg(-1, null, null, null, null, 320, 5, CommandTarget.TURN_LEFT)
        }

        test("Find next waypoint with speed restriction") {
            val pair1 = getNextWaypointWithSpdRestr(route1, 10000f)
            pair1?.first shouldBe wpt1
            pair1?.second shouldBe 240
            val pair2 = getNextWaypointWithSpdRestr(route2, 10000f)
            pair2?.first shouldBe wpt1
            pair2?.second shouldBe 240
            val pair3 = getNextWaypointWithSpdRestr(route3, 10000f)
            pair3?.first shouldBe hold1
            pair3?.second shouldBe 230
            getNextWaypointWithSpdRestr(route4, 10000f).shouldBeNull()
        }

        test("Find next maximum speed restriction") {
            getNextMaxSpd(route1) shouldBe 240
            getNextMaxSpd(route2) shouldBe 240
            getNextMaxSpd(route3).shouldBeNull()
            getNextMaxSpd(route4).shouldBeNull()
            getNextMaxSpd(Route().apply {
                add(vector1)
                add(wpt2)
                add(wpt1)
            }) shouldBe 240
        }

        test("Find next minimum altitude restriction") {
            getNextMinAlt(route1) shouldBe 5000
            getNextMinAlt(route2) shouldBe 5000
            getNextMinAlt(route3).shouldBeNull()
            getNextMinAlt(route4).shouldBeNull()
            getNextMinAlt(Route().apply {
                add(Route.WaypointLeg(3, null, null, null, legActive = false, altRestrActive = true, spdRestrActive = false))
                add(hold1)
                add(Route.WaypointLeg(4, 10000, 2000, 230, legActive = true, altRestrActive = true, spdRestrActive = true))
                add(vector1)
            }) shouldBe 2000
        }

        test("Find next maximum altitude restriction") {
            getNextMaxAlt(route1).shouldBeNull()
            getNextMaxAlt(route2).shouldBeNull()
            getNextMaxAlt(route3).shouldBeNull()
            getNextMaxAlt(route4).shouldBeNull()
            getNextMaxAlt(Route().apply {
                add(wpt1)
                add(wpt2)
                add(Route.WaypointLeg(5, 9000, 2500, null, legActive = true, altRestrActive = true, spdRestrActive = true))
            }) shouldBe 9000
        }

        test("Find FAF altitude") {
            getFafAltitude(route1).shouldBeNull()
            getFafAltitude(route2).shouldBeNull()
            getFafAltitude(Route().apply {
                add(wpt1)
                add(wpt2)
                add(Route.WaypointLeg(6, null, 2000, 180, legActive = true, altRestrActive = true,
                    spdRestrActive = true, phase = Route.Leg.APP))
            }) shouldBe 2000
            getFafAltitude(Route().apply {
                add(wpt1)
                add(wpt2)
                add(Route.WaypointLeg(6, null, 2000, 180, legActive = true, altRestrActive = true,
                    spdRestrActive = true, phase = Route.Leg.APP))
                add(Route.WaypointLeg(7, null, 1400, 180, legActive = true, altRestrActive = true,
                    spdRestrActive = true, phase = Route.Leg.APP))
                add(Route.WaypointLeg(8, 2000, null, null, legActive = true, altRestrActive = true,
                    spdRestrActive = true, phase = Route.Leg.MISSED_APP))
            }) shouldBe 1400
        }

        test("Has only waypoint legs till missed approach") {
            hasOnlyWaypointLegsTillMissed(wpt1, route1).shouldBeFalse()
            hasOnlyWaypointLegsTillMissed(wpt2, route1).shouldBeTrue()
            hasOnlyWaypointLegsTillMissed(wpt1, route2).shouldBeFalse()
            hasOnlyWaypointLegsTillMissed(wpt1, Route().apply {
                add(wpt1)
                add(wpt2)
                add(Route.WaypointLeg(6, null, 2000, 180, legActive = true, altRestrActive = true,
                    spdRestrActive = true, phase = Route.Leg.APP))
                add(Route.WaypointLeg(8, 2000, null, null, legActive = true, altRestrActive = true,
                    spdRestrActive = true, phase = Route.Leg.MISSED_APP))
            }).shouldBeTrue()
            hasOnlyWaypointLegsTillMissed(wpt1, Route().apply {
                add(wpt1)
                add(vector1)
                add(Route.WaypointLeg(6, null, 2000, 180, legActive = true, altRestrActive = true,
                    spdRestrActive = true, phase = Route.Leg.APP))
                add(Route.WaypointLeg(8, 2000, null, null, legActive = true, altRestrActive = true,
                    spdRestrActive = true, phase = Route.Leg.MISSED_APP))
            }).shouldBeFalse()
        }

        test("Remove all legs till missed approach") {
            val tempAppLeg1 = Route.WaypointLeg(6, null, 2000, 180, legActive = true, altRestrActive = true,
                spdRestrActive = true, phase = Route.Leg.APP)
            val tempMissedLeg1 = Route.WaypointLeg(8, 2000, null, null, legActive = true, altRestrActive = true,
                spdRestrActive = true, phase = Route.Leg.MISSED_APP)
            val tempRoute1 = Route().apply {
                add(wpt1)
                add(wpt2)
                add(tempAppLeg1)
                add(tempMissedLeg1)
            }
            removeAllLegsTillMissed(tempRoute1)
            tempRoute1.contains(wpt1).shouldBeFalse()
            tempRoute1.contains(wpt2).shouldBeFalse()
            tempRoute1.contains(tempAppLeg1).shouldBeFalse()
            tempRoute1.contains(tempMissedLeg1).shouldBeTrue()

            val tempRoute2 = Route().apply {
                add(wpt1)
                add(vector1)
                add(tempAppLeg1)
                add(discontinuity)
                add(tempMissedLeg1)
            }
            removeAllLegsTillMissed(tempRoute2)
            tempRoute2.contains(wpt1).shouldBeFalse()
            tempRoute2.contains(vector1).shouldBeFalse()
            tempRoute2.contains(tempAppLeg1).shouldBeFalse()
            tempRoute2.contains(discontinuity).shouldBeFalse()
            tempRoute2.contains(tempMissedLeg1).shouldBeTrue()
        }

        test("Set all missed approach legs phase to normal") {
            val tempMissedLeg1 = Route.WaypointLeg(8, 2000, null, null, legActive = true, altRestrActive = true,
                spdRestrActive = true, phase = Route.Leg.MISSED_APP)
            val tempMissedLeg2 = Route.HoldLeg(9, null, 3000, 230, 240,
                270, 5, CommandTarget.TURN_RIGHT, Route.Leg.MISSED_APP)
            val tempRoute1 = Route().apply {
                add(tempMissedLeg1)
                add(tempMissedLeg2)
            }
            setAllMissedLegsToNormal(tempRoute1)
            tempRoute1[0].phase shouldBe Route.Leg.NORMAL
            tempRoute1[1].phase shouldBe Route.Leg.NORMAL
        }

        test("Find missed approach altitude") {
            val tempMissedLeg1 = Route.WaypointLeg(8, null, 2000, null, legActive = true, altRestrActive = true,
                spdRestrActive = true, phase = Route.Leg.MISSED_APP)
            val tempMissedLeg2 = Route.HoldLeg(8, null, 2000, 230, 240,
                270, 5, CommandTarget.TURN_RIGHT, Route.Leg.MISSED_APP)
            val tempRoute1 = Route().apply {
                add(tempMissedLeg1)
                add(tempMissedLeg2)
            }
            findMissedApproachAlt(tempRoute1) shouldBe 2000

            val tempRoute2 = Route().apply {
                add(wpt1)
                add(vector1)
                add(discontinuity)
                add(tempMissedLeg1)
            }
            findMissedApproachAlt(tempRoute2) shouldBe 2000

            val tempRoute3 = Route().apply {
                add(wpt1)
                add(vector1)
                add(discontinuity)
                add(initClimb1)
                add(Route.WaypointLeg(9, null, null, null, legActive = true, altRestrActive = true,
                    spdRestrActive = true, phase = Route.Leg.MISSED_APP))
            }
            findMissedApproachAlt(tempRoute3) shouldBe 1500

            findMissedApproachAlt(route1).shouldBeNull()
        }

        test("Calculate distance to go") {
            testInitialiseGameAndServer()
            GAME.gameServer?.apply {
                waypoints[0] = Waypoint(0, "TESTWPT1", 0, 0, false)
                waypoints[1] = Waypoint(1, "TESTWPT2", 1, 1, false)
                waypoints[2] = Waypoint(2, "TESTWPT3", 4, 5, false)
                val tempWpt = Route.WaypointLeg(2, null, null, null, legActive = true, altRestrActive = true, spdRestrActive = true)
                val tempWpt2 = Route.WaypointLeg(1, null, null, null, legActive = true, altRestrActive = true, spdRestrActive = true)
                calculateDistToGo(null, wpt1, wpt1, route1) shouldBe 0f
                calculateDistToGo(Position(-1f, -1f), wpt1, wpt1, route1) shouldBe 1.4142f.plusOrMinus(0.0001f)
                calculateDistToGo(null, wpt2, wpt1, route2) shouldBe 0f
                calculateDistToGo(Position(4f, 5f), wpt2, wpt1, route2) shouldBe 6.4031f.plusOrMinus(0.0001f)
                calculateDistToGo(Position(-1f, -1f), wpt2, wpt3, route4) shouldBe 0f
                calculateDistToGo(Position(-1f, -1f), wpt3, wpt3, route4) shouldBe 0f
                calculateDistToGo(null, tempWpt2, tempWpt, Route().apply {
                    add(wpt1)
                    add(tempWpt2)
                    add(tempWpt)
                }) shouldBe 5f
            }.shouldNotBeNull()
        }
    }
}