import com.badlogic.ashley.core.Entity
import com.badlogic.gdx.math.CumulativeDistribution
import com.badlogic.gdx.math.MathUtils
import com.badlogic.gdx.math.Vector2
import com.badlogic.gdx.utils.Queue
import com.badlogic.gdx.utils.Queue.QueueIterator
import com.bombbird.terminalcontrol2.components.*
import com.bombbird.terminalcontrol2.entities.Aircraft
import com.bombbird.terminalcontrol2.entities.Airport
import com.bombbird.terminalcontrol2.entities.WakeZone
import com.bombbird.terminalcontrol2.entities.Waypoint
import com.bombbird.terminalcontrol2.global.GAME
import com.bombbird.terminalcontrol2.json.getMoshiWithAllAdapters
import com.bombbird.terminalcontrol2.json.runDelayedEntityRetrieval
import com.bombbird.terminalcontrol2.navigation.*
import com.bombbird.terminalcontrol2.utilities.AircraftTypeData
import com.bombbird.terminalcontrol2.utilities.UsabilityFilter
import com.bombbird.terminalcontrol2.utilities.checkClearanceEquality
import com.squareup.moshi.adapter
import io.kotest.core.spec.style.FunSpec
import io.kotest.matchers.Matcher
import io.kotest.matchers.MatcherResult
import io.kotest.matchers.booleans.shouldBeTrue
import io.kotest.matchers.floats.plusOrMinus
import io.kotest.matchers.nulls.shouldNotBeNull
import io.kotest.matchers.should
import io.kotest.matchers.shouldBe
import ktx.ashley.get
import ktx.ashley.plusAssign
import ktx.collections.GdxArray
import ktx.collections.GdxArrayMap
import ktx.collections.set
import java.util.*

/** Kotest FunSpec class for testing JSON serialization for components and entities */
@OptIn(ExperimentalStdlibApi::class)
object JsonTest: FunSpec() {
    private var arpt: Airport? = null

    private var rwy1: Airport.Runway? = null
    private var rwy2: Airport.Runway? = null

    private var app1: Approach? = null
    private var app2: Approach? = null
    private var app3: Approach? = null

    private var route1: Route? = null

    private var aircraft1: Aircraft? = null
    private var aircraft2: Aircraft? = null

    private val testMoshi = getMoshiWithAllAdapters()

    init {
        beforeTest {
            testInitialiseGameAndServer()
            arpt = Airport(0, "TEST", "Test Airport", 1, 0, 1f, 1f, 20, "XXXX", false)
            arpt?.addRunway(0, "05L", -1f, 1f, 45f, 4000, 200, 0,
                20, "Test Tower", "118.6", RunwayLabel.LEFT)
            arpt?.setRunwayMapping("05L", 0)
            arpt?.addRunway(1, "05R", 1f, -1f, 45f, 3800, 0, 0,
                25, "Test Tower", "118.6", RunwayLabel.RIGHT)
            arpt?.setRunwayMapping("05R", 1)
            rwy1 = arpt?.getRunway("05L")
            rwy2 = arpt?.getRunway("05R")
            rwy1?.entity?.plusAssign(DependentOppositeRunway().apply { rwy1?.entity?.let { depOppRwys.add(it) }})
            rwy2?.entity?.plusAssign(DependentOppositeRunway())
            rwy1?.entity?.plusAssign(DependentParallelRunway().apply { rwy2?.entity?.let { depParRwys.add(it) }})
            rwy2?.entity?.plusAssign(DependentParallelRunway().apply { rwy1?.entity?.let { depParRwys.add(it) }})
            rwy1?.entity?.plusAssign(CrossingRunway().apply { rwy2?.entity?.let { crossRwys.add(it) }})
            rwy2?.entity?.plusAssign(CrossingRunway().apply { rwy1?.entity?.let { crossRwys.add(it) }})
            arpt?.assignOppositeRunways()
            GAME.gameServer?.apply {
                airports.clear()
                arpt?.let { airports[0] = it }
                waypoints.clear()
                waypoints[0] = Waypoint(0, "TEST0", 0, 0, false)
                waypoints[1] = Waypoint(1, "TEST1", 1, 1, false)
                waypoints[2] = Waypoint(2, "TEST2", 2, 2, false)
                waypoints[3] = Waypoint(3, "TEST3", 3, 3, false)
                waypoints[4] = Waypoint(4, "TEST4", 4, 4, false)
                waypoints[5] = Waypoint(5, "TEST5", 5, 5, false)
                waypoints[6] = Waypoint(6, "TEST6", 6, 6, false)
                waypoints[7] = Waypoint(7, "TEST7", 7, 7, false)
                waypoints[8] = Waypoint(8, "TEST8", 8, 8, false)
                waypoints[9] = Waypoint(9, "TEST9", 9, 9, false)
                waypoints[10] = Waypoint(10, "TEST10", 10, 10, false)

                aircraft.clear()
                AircraftTypeData.addAircraftPerf("B77W", AircraftTypeData.AircraftPerfData())
                AircraftTypeData.addAircraftPerf("A359", AircraftTypeData.AircraftPerfData())
                aircraft["SHIBA1"] = Aircraft("SHIBA1", -200f, -100f, 19806.4f, "B77W", FlightType.ARRIVAL, false).apply { aircraft1 = this }
                aircraft["SHIBA2"] = Aircraft("SHIBA2", 50f, 50f, 3000f, "A359", FlightType.DEPARTURE, false).apply { aircraft2 = this }
            }

            app1 = Approach("ILS05L", 0, 0, 9f, 11f, 220, 800, false, UsabilityFilter.DAY_AND_NIGHT).apply {
                addLocalizer(45f, 20)
                addGlideslope(3f, -2f, 4000)
                routeLegs.setToRoute(Route().apply {
                    add(Route.WaypointLeg(3, null, 2000, 230, legActive = true, altRestrActive = true, spdRestrActive = true, phase = Route.Leg.APP))
                    add(Route.WaypointLeg(4, 12000, 5000, null, legActive = true, altRestrActive = false, spdRestrActive = true, phase = Route.Leg.APP))
                })
                routeZones.addAll(getZonesForArrivalRoute(routeLegs))
                missedLegs.setToRoute(Route().apply {
                    add(Route.InitClimbLeg(45, 1000, phase = Route.Leg.MISSED_APP))
                    add(Route.WaypointLeg(5, null, 3000, null, legActive = true, altRestrActive = true, spdRestrActive = true, phase = Route.Leg.MISSED_APP))
                    add(Route.HoldLeg(5, null, 4000, 230, 240, 45, 5, CommandTarget.TURN_RIGHT, phase = Route.Leg.MISSED_APP))
                })
                missedRouteZones.addAll(getZonesForDepartureRoute(missedLegs))
                transitions["TESTT"] = Route().apply {
                    add(Route.WaypointLeg(6, null, 2000, 230, legActive = true, altRestrActive = true, spdRestrActive = true, phase = Route.Leg.APP_TRANS))
                    add(Route.WaypointLeg(7, 12000, 5000, null, legActive = true, altRestrActive = false, spdRestrActive = true, phase = Route.Leg.APP_TRANS))
                }
                transitionRouteZones["TESTT"] = getZonesForArrivalRoute(transitions["TESTT"])
            }.shouldNotBeNull()
            app2 = Approach("LDA05R", 0, 1, 7f, 11f, 625, 2700, false, UsabilityFilter.DAY_AND_NIGHT).apply {
                addLocalizer(30f, 20)
                addStepDown(arrayOf(StepDown.Step(4f, 1200), StepDown.Step(8f, 2000), StepDown.Step(15f, 4000)))
                addLineUpDist(1f)
                routeLegs.setToRoute(Route().apply {
                    add(Route.WaypointLeg(8, null, 2000, 230, legActive = true, altRestrActive = true, spdRestrActive = true, phase = Route.Leg.APP))
                    add(Route.WaypointLeg(9, 12000, 5000, null, legActive = true, altRestrActive = false, spdRestrActive = true, phase = Route.Leg.APP))
                })
                routeZones.addAll(getZonesForArrivalRoute(routeLegs))
                missedLegs.setToRoute(Route().apply {
                    add(Route.InitClimbLeg(45, 1000, phase = Route.Leg.MISSED_APP))
                    add(Route.WaypointLeg(10, null, 3000, null, legActive = true, altRestrActive = true, spdRestrActive = true, phase = Route.Leg.MISSED_APP))
                    add(Route.HoldLeg(10, null, 4000, 230, 240, 45, 5, CommandTarget.TURN_RIGHT, phase = Route.Leg.MISSED_APP))
                })
                missedRouteZones.addAll(getZonesForDepartureRoute(missedLegs))
            }.shouldNotBeNull()
            app3 = Approach("CIRCLE05L", 0, 0, -1f, 1f, 1220, 4000, false, UsabilityFilter.DAY_AND_NIGHT).apply {
                addLocalizer(225f, 20)
                addGlideslope(3f, -2f, 4000)
                addCircling(1000, 1400, CommandTarget.TURN_RIGHT)
            }.shouldNotBeNull()
            arpt?.entity?.get(ApproachChildren.mapper)?.apply {
                app1?.let { approachMap["ILS05L"] = it }.shouldNotBeNull()
                app2?.let { approachMap["LDA05R"] = it }.shouldNotBeNull()
                app3?.let { approachMap["CIRCLE05L"] = it }.shouldNotBeNull()
            }.shouldNotBeNull()

            route1 = Route().apply {
                add(Route.InitClimbLeg(45, 1000))
                add(Route.WaypointLeg(0, null, 2000, 230, legActive = true, altRestrActive = true, spdRestrActive = true))
                add(Route.WaypointLeg(1, 12000, 5000, null, legActive = true, altRestrActive = false, spdRestrActive = true, phase = Route.Leg.APP))
                add(Route.VectorLeg(250))
                add(Route.HoldLeg(2, null, 4000, 230, 240, 45, 5, CommandTarget.TURN_RIGHT))
                add(Route.DiscontinuityLeg())
            }
        }

        test("TakeoffRoll serialization") {
            val takeoffRollAdapter = testMoshi.adapter<TakeoffRoll>()
            val takeoffRoll1 = TakeoffRoll(2f, rwy1?.entity ?: Entity())
            val takeoffRoll2 = TakeoffRoll(2.5f, rwy2?.entity ?: Entity())
            val takeoffRoll1FromJson = takeoffRollAdapter.fromJson(takeoffRollAdapter.toJson(takeoffRoll1))
            val takeoffRoll2FromJson = takeoffRollAdapter.fromJson(takeoffRollAdapter.toJson(takeoffRoll2))
            runDelayedEntityRetrieval()
            takeoffRoll1FromJson shouldBe takeoffRoll1
            takeoffRoll2FromJson shouldBe takeoffRoll2
        }

        test("LandingRoll serialization") {
            val landingRollAdapter = testMoshi.adapter<LandingRoll>()
            val landingRoll1 = LandingRoll(rwy1?.entity ?: Entity())
            val landingRoll2 = LandingRoll(rwy2?.entity ?: Entity())
            val landingRoll1FromJson = landingRollAdapter.fromJson(landingRollAdapter.toJson(landingRoll1))
            val landingRoll2FromJson = landingRollAdapter.fromJson(landingRollAdapter.toJson(landingRoll2))
            runDelayedEntityRetrieval()
            landingRoll1FromJson shouldBe landingRoll1
            landingRoll2FromJson shouldBe landingRoll2
        }

        test("CommandTarget serialization") {
            val cmdTargetAdapter = testMoshi.adapter<CommandTarget>()
            val cmdTarget1 = CommandTarget(45f, CommandTarget.TURN_DEFAULT, 3000, 240)
            val cmdTarget2 = CommandTarget(180f, CommandTarget.TURN_LEFT, 5000, 220)
            val cmdTarget3 = CommandTarget(360f, CommandTarget.TURN_RIGHT, 10000, 260)
            cmdTargetAdapter.fromJson(cmdTargetAdapter.toJson(cmdTarget1)) shouldBe cmdTarget1
            cmdTargetAdapter.fromJson(cmdTargetAdapter.toJson(cmdTarget2)) shouldBe cmdTarget2
            cmdTargetAdapter.fromJson(cmdTargetAdapter.toJson(cmdTarget3)) shouldBe cmdTarget3
        }

        test("CommandVector serialization") {
            val cmdVectorAdapter = testMoshi.adapter<CommandVector>()
            val cmdVector1 = CommandVector(45, CommandTarget.TURN_DEFAULT)
            val cmdVector2 = CommandVector(180, CommandTarget.TURN_LEFT)
            val cmdVector3 = CommandVector(360, CommandTarget.TURN_RIGHT)
            cmdVectorAdapter.fromJson(cmdVectorAdapter.toJson(cmdVector1)) shouldBe cmdVector1
            cmdVectorAdapter.fromJson(cmdVectorAdapter.toJson(cmdVector2)) shouldBe cmdVector2
            cmdVectorAdapter.fromJson(cmdVectorAdapter.toJson(cmdVector3)) shouldBe cmdVector3
        }

        test("CommandInitClimb serialization") {
            val cmdInitClimbAdapter = testMoshi.adapter<CommandInitClimb>()
            val cmdInitClimb1 = CommandInitClimb(45, 600)
            val cmdInitClimb2 = CommandInitClimb(180, 1000)
            val cmdInitClimb3 = CommandInitClimb(360, 4000)
            cmdInitClimbAdapter.fromJson(cmdInitClimbAdapter.toJson(cmdInitClimb1)) shouldBe cmdInitClimb1
            cmdInitClimbAdapter.fromJson(cmdInitClimbAdapter.toJson(cmdInitClimb2)) shouldBe cmdInitClimb2
            cmdInitClimbAdapter.fromJson(cmdInitClimbAdapter.toJson(cmdInitClimb3)) shouldBe cmdInitClimb3
        }

        test("CommandDirect serialization") {
            val cmdDirectAdapter = testMoshi.adapter<CommandDirect>()
            val cmdDirect1 = CommandDirect(0, null, 2000, null, false, CommandTarget.TURN_DEFAULT)
            val cmdDirect2 = CommandDirect(1, 5000, null, 230, false, CommandTarget.TURN_LEFT)
            val cmdDirect3 = CommandDirect(3, null, null, null, true, CommandTarget.TURN_RIGHT)
            cmdDirectAdapter.fromJson(cmdDirectAdapter.toJson(cmdDirect1)) shouldBe cmdDirect1
            cmdDirectAdapter.fromJson(cmdDirectAdapter.toJson(cmdDirect2)) shouldBe cmdDirect2
            cmdDirectAdapter.fromJson(cmdDirectAdapter.toJson(cmdDirect3)) shouldBe cmdDirect3
        }

        test("CommandHold serialization") {
            val cmdHoldAdapter = testMoshi.adapter<CommandHold>()
            val cmdHold1 = CommandHold(0, null, 5000, 230, 45, 5, CommandTarget.TURN_RIGHT,
                0, entryDone = false, oppositeTravelled = false, flyOutbound = false)
            val cmdHold2 = CommandHold(1, 16000, 4000, 240, 180, 4, CommandTarget.TURN_LEFT,
                1, entryDone = true, oppositeTravelled = false, flyOutbound = true)
            val cmdHold3 = CommandHold(3, null, null, 230, 360, 6, CommandTarget.TURN_LEFT,
                2, entryDone = true, oppositeTravelled = true, flyOutbound = false)
            cmdHoldAdapter.fromJson(cmdHoldAdapter.toJson(cmdHold1)) shouldBe cmdHold1
            cmdHoldAdapter.fromJson(cmdHoldAdapter.toJson(cmdHold2)) shouldBe cmdHold2
            cmdHoldAdapter.fromJson(cmdHoldAdapter.toJson(cmdHold3)) shouldBe cmdHold3
        }

        test("LastRestrictions serialization") {
            val lastRestrAdapter = testMoshi.adapter<LastRestrictions>()
            val lastRestr1 = LastRestrictions(null, null, null)
            val lastRestr2 = LastRestrictions(5000, null, 230)
            val lastRestr3 = LastRestrictions(null, 20000, 280)
            lastRestrAdapter.fromJson(lastRestrAdapter.toJson(lastRestr1)) shouldBe lastRestr1
            lastRestrAdapter.fromJson(lastRestrAdapter.toJson(lastRestr2)) shouldBe lastRestr2
            lastRestrAdapter.fromJson(lastRestrAdapter.toJson(lastRestr3)) shouldBe lastRestr3
        }

        test("VisualCaptured serialization") {
            val visCapAdapter = testMoshi.adapter<VisualCaptured>()
            val visCap1 = VisualCaptured(rwy1?.entity?.get(VisualApproach.mapper)?.visual ?: Entity(), app1?.entity ?: Entity())
            val visCap2 = VisualCaptured(rwy2?.entity?.get(VisualApproach.mapper)?.visual ?: Entity(), app2?.entity ?: Entity())
            val visCap1FromJson = visCapAdapter.fromJson(visCapAdapter.toJson(visCap1))
            val visCap2FromJson = visCapAdapter.fromJson(visCapAdapter.toJson(visCap2))
            runDelayedEntityRetrieval()
            visCap1FromJson shouldBe visCap1
            visCap2FromJson shouldBe visCap2
        }

        test("LocalizerArmed serialization") {
            val locArmedAdapter = testMoshi.adapter<LocalizerArmed>()
            val locArmed1 = LocalizerArmed(app1?.entity ?: Entity())
            val locArmed2 = LocalizerArmed(app2?.entity ?: Entity())
            val locArmed1FromJson = locArmedAdapter.fromJson(locArmedAdapter.toJson(locArmed1))
            val locArmed2FromJson = locArmedAdapter.fromJson(locArmedAdapter.toJson(locArmed2))
            runDelayedEntityRetrieval()
            locArmed1FromJson shouldBe locArmed1
            locArmed2FromJson shouldBe locArmed2
        }

        test("LocalizerCaptured serialization") {
            val locCapAdapter = testMoshi.adapter<LocalizerCaptured>()
            val locCap1 = LocalizerCaptured(app1?.entity ?: Entity())
            val locCap2 = LocalizerCaptured(app2?.entity ?: Entity())
            val locCap1FromJson = locCapAdapter.fromJson(locCapAdapter.toJson(locCap1))
            val locCap2FromJson = locCapAdapter.fromJson(locCapAdapter.toJson(locCap2))
            runDelayedEntityRetrieval()
            locCap1FromJson shouldBe locCap1
            locCap2FromJson shouldBe locCap2
        }

        test("GlideSlopeArmed serialization") {
            val gsArmedAdapter = testMoshi.adapter<GlideSlopeArmed>()
            val gsArmed1 = GlideSlopeArmed(app1?.entity ?: Entity())
            val gsArmed2 = GlideSlopeArmed(app2?.entity ?: Entity())
            val gsArmed1FromJson = gsArmedAdapter.fromJson(gsArmedAdapter.toJson(gsArmed1))
            val gsArmed2FromJson = gsArmedAdapter.fromJson(gsArmedAdapter.toJson(gsArmed2))
            runDelayedEntityRetrieval()
            gsArmed1FromJson shouldBe gsArmed1
            gsArmed2FromJson shouldBe gsArmed2
        }

        test("GlideSlopeCaptured serialization") {
            val gsCapAdapter = testMoshi.adapter<GlideSlopeCaptured>()
            val gsCap1 = GlideSlopeCaptured(app1?.entity ?: Entity())
            val gsCap2 = GlideSlopeCaptured(app2?.entity ?: Entity())
            val gsCap1FromJson = gsCapAdapter.fromJson(gsCapAdapter.toJson(gsCap1))
            val gsCap2FromJson = gsCapAdapter.fromJson(gsCapAdapter.toJson(gsCap2))
            runDelayedEntityRetrieval()
            gsCap1FromJson shouldBe gsCap1
            gsCap2FromJson shouldBe gsCap2
        }

        test("StepDownApproach serialization"){
            val stepDownAdapter = testMoshi.adapter<StepDownApproach>()
            val stepDown = StepDownApproach(app2?.entity ?: Entity())
            val stepDownFromJson = stepDownAdapter.fromJson(stepDownAdapter.toJson(stepDown))
            runDelayedEntityRetrieval()
            stepDownFromJson shouldBe stepDown
        }

        test("CirclingApproach serialization") {
            val circlingAdapter = testMoshi.adapter<CirclingApproach>()
            val circling1 = CirclingApproach(app3?.entity ?: Entity(), 1200, 0)
            val circling2 = CirclingApproach(app3?.entity ?: Entity(), 1100, 1, 34.5f)
            val circling3 = CirclingApproach(app3?.entity ?: Entity(), 1150, 2, 0f)
            val circling4 = CirclingApproach(app3?.entity ?: Entity(), 1250, 3, 0f, 21f)
            val circling1FromJson = circlingAdapter.fromJson(circlingAdapter.toJson(circling1))
            val circling2FromJson = circlingAdapter.fromJson(circlingAdapter.toJson(circling2))
            val circling3FromJson = circlingAdapter.fromJson(circlingAdapter.toJson(circling3))
            val circling4FromJson = circlingAdapter.fromJson(circlingAdapter.toJson(circling4))
            runDelayedEntityRetrieval()
            circling1FromJson shouldBe circling1
            circling2FromJson shouldBe circling2
            circling3FromJson shouldBe circling3
            circling4FromJson shouldBe circling4
        }

        test("ApproachInfo serialization") {
            val appInfoAdapter = testMoshi.adapter<ApproachInfo>()
            val appInfo1 = ApproachInfo("ILS05L", 0, 0)
            val appInfo2 = ApproachInfo("LDA05R", 0, 1)
            val appInfo3 = ApproachInfo("CIRCLE05L", 0, 0)
            appInfoAdapter.fromJson(appInfoAdapter.toJson(appInfo1)) shouldBe appInfo1
            appInfoAdapter.fromJson(appInfoAdapter.toJson(appInfo2)) shouldBe appInfo2
            appInfoAdapter.fromJson(appInfoAdapter.toJson(appInfo3)) shouldBe appInfo3
        }

        test("Localizer serialization") {
            val locAdapter = testMoshi.adapter<Localizer>()
            val loc1 = Localizer(20)
            val loc2 = Localizer(25)
            val loc3 = Localizer(15)
            locAdapter.fromJson(locAdapter.toJson(loc1)) shouldBe loc1
            locAdapter.fromJson(locAdapter.toJson(loc2)) shouldBe loc2
            locAdapter.fromJson(locAdapter.toJson(loc3)) shouldBe loc3
        }

        test("LineUpDist serialization") {
            val lineUpDistAdapter = testMoshi.adapter<LineUpDist>()
            val lineUpDist1 = LineUpDist(0.3f)
            val lineUpDist2 = LineUpDist(0.65f)
            val lineUpDist3 = LineUpDist(1.35f)
            lineUpDistAdapter.fromJson(lineUpDistAdapter.toJson(lineUpDist1)) shouldBe lineUpDist1
            lineUpDistAdapter.fromJson(lineUpDistAdapter.toJson(lineUpDist2)) shouldBe lineUpDist2
            lineUpDistAdapter.fromJson(lineUpDistAdapter.toJson(lineUpDist3)) shouldBe lineUpDist3
        }

        test("GlideSlope serialization") {
            val gsAdapter = testMoshi.adapter<GlideSlope>()
            val gs1 = GlideSlope(3f, -1.4f, 4000)
            val gs2 = GlideSlope(3f, -2f, 5000)
            val gs3 = GlideSlope(4.5f, -1f, 6000)
            gsAdapter.fromJson(gsAdapter.toJson(gs1)) shouldBe gs1
            gsAdapter.fromJson(gsAdapter.toJson(gs2)) shouldBe gs2
            gsAdapter.fromJson(gsAdapter.toJson(gs3)) shouldBe gs3
        }

        test("StepDown serialization") {
            val stepDownAdapter = testMoshi.adapter<StepDown>()
            val stepDown1 = StepDown(arrayOf(StepDown.Step(4f, 1200), StepDown.Step(8f, 2000), StepDown.Step(15f, 4000)))
            val stepDown2 = StepDown(arrayOf(StepDown.Step(5f, 1200), StepDown.Step(8.4f, 2000), StepDown.Step(14f, 4000)))
            val stepDown3 = StepDown(arrayOf(StepDown.Step(3.3f, 800), StepDown.Step(7f, 2100), StepDown.Step(10f, 3000)))
            stepDownAdapter.fromJson(stepDownAdapter.toJson(stepDown1)) shouldBe stepDown1
            stepDownAdapter.fromJson(stepDownAdapter.toJson(stepDown2)) shouldBe stepDown2
            stepDownAdapter.fromJson(stepDownAdapter.toJson(stepDown3)) shouldBe stepDown3
        }

        test("Circling serialization") {
            val circlingAdapter = testMoshi.adapter<Circling>()
            val circling1 = Circling(1000, 1400, CommandTarget.TURN_LEFT)
            val circling2 = Circling(1200, 1400, CommandTarget.TURN_RIGHT)
            val circling3 = Circling(900, 1300, CommandTarget.TURN_LEFT)
            circlingAdapter.fromJson(circlingAdapter.toJson(circling1)) shouldBe circling1
            circlingAdapter.fromJson(circlingAdapter.toJson(circling2)) shouldBe circling2
            circlingAdapter.fromJson(circlingAdapter.toJson(circling3)) shouldBe circling3
        }

        test("Minimums serialization") {
            val minsAdapter = testMoshi.adapter<Minimums>()
            val mins1 = Minimums(220, 800)
            val mins2 = Minimums(620, 2700)
            val mins3 = Minimums(1220, 4000)
            minsAdapter.fromJson(minsAdapter.toJson(mins1)) shouldBe mins1
            minsAdapter.fromJson(minsAdapter.toJson(mins2)) shouldBe mins2
            minsAdapter.fromJson(minsAdapter.toJson(mins3)) shouldBe mins3
        }

        test("AirportInfo serialization") {
            val arptInfoAdapter = testMoshi.adapter<AirportInfo>()
            val arptInfo1 = AirportInfo(0, "TESA", "Test 1", 1)
            val arptInfo2 = AirportInfo(1, "TESB", "Test 2", 3)
            arptInfoAdapter.fromJson(arptInfoAdapter.toJson(arptInfo1)) shouldBe arptInfo1
            arptInfoAdapter.fromJson(arptInfoAdapter.toJson(arptInfo2)) shouldBe arptInfo2
        }

        test("RunwayInfo serialization") {
            val rwyInfoAdapter = testMoshi.adapter<RunwayInfo>()
            val rwyInfo1 = rwy1?.entity?.get(RunwayInfo.mapper).shouldNotBeNull()
            val rwyInfo2 = rwy2?.entity?.get(RunwayInfo.mapper).shouldNotBeNull()
            val rwyInfo1FromJson = rwyInfoAdapter.fromJson(rwyInfoAdapter.toJson(rwyInfo1))
            val rwyInfo2FromJson = rwyInfoAdapter.fromJson(rwyInfoAdapter.toJson(rwyInfo2))
            runDelayedEntityRetrieval()
            rwyInfo1FromJson shouldBe rwyInfo1
            rwyInfo2FromJson shouldBe rwyInfo2
        }

        test("RandomMetarInfo serialization") {
            val randomMetarAdapter = testMoshi.adapter<RandomMetarInfo>()
            val cumDist1a = CumulativeDistribution<Short>().apply { for (i in 0..36) add(i.toShort(), i + 1f) }
            val cumDist1b = CumulativeDistribution<Short>().apply { for (i in 0..50) add(i.toShort(), 51f - i) }
            val cumDist1c = CumulativeDistribution<Short>().apply { for (i in 0 until 20) add(i.toShort(), i + 1f) }
            val cumDist1d = CumulativeDistribution<Short>().apply { for (i in 0 until 15) add(i.toShort(), i + 1f) }
            val randomMetar = RandomMetarInfo(cumDist1a, cumDist1b, cumDist1c, cumDist1d)
            val randomMetarFromJson = randomMetarAdapter.fromJson(randomMetarAdapter.toJson(randomMetar)).shouldNotBeNull()
            randomMetarFromJson.windDirDist should matchCumDist(cumDist1a)
            randomMetarFromJson.windSpdDist should matchCumDist(cumDist1b)
            randomMetarFromJson.visibilityDist should matchCumDist(cumDist1c)
            randomMetarFromJson.ceilingDist should matchCumDist(cumDist1d)
        }

        test("WaypointInfo serialization") {
            val wptAdapter = testMoshi.adapter<WaypointInfo>()
            val wpt1 = WaypointInfo(0, "SHIBA")
            val wpt2 = WaypointInfo(1, "SHIBE")
            val wpt3 = WaypointInfo(2, "DREKO")
            wptAdapter.fromJson(wptAdapter.toJson(wpt1)) shouldBe wpt1
            wptAdapter.fromJson(wptAdapter.toJson(wpt2)) shouldBe wpt2
            wptAdapter.fromJson(wptAdapter.toJson(wpt3)) shouldBe wpt3
        }

        test("AircraftInfo serialization") {
            val acAdapter = testMoshi.adapter<AircraftInfo>()
            val ac1 = AircraftInfo("SHIBA1", "B77W")
            val ac2 = AircraftInfo("SHIBA2", "B78X")
            val ac3 = AircraftInfo("DREKO1", "A359")
            acAdapter.fromJson(acAdapter.toJson(ac1)) shouldBe ac1
            acAdapter.fromJson(acAdapter.toJson(ac2)) shouldBe ac2
            acAdapter.fromJson(acAdapter.toJson(ac3)) shouldBe ac3
        }

        test("ArrivalAirport serialization") {
            val arrivalArptAdapter = testMoshi.adapter<ArrivalAirport>()
            val arrivalArpt1 = ArrivalAirport(0)
            val arrivalArpt2 = ArrivalAirport(1)
            arrivalArptAdapter.fromJson(arrivalArptAdapter.toJson(arrivalArpt1)) shouldBe arrivalArpt1
            arrivalArptAdapter.fromJson(arrivalArptAdapter.toJson(arrivalArpt2)) shouldBe arrivalArpt2
        }

        test("DepartureAirport serialization") {
            val depArptAdapter = testMoshi.adapter<DepartureAirport>()
            val depArpt1 = DepartureAirport(0, 0)
            val depArpt2 = DepartureAirport(1, 0)
            val depArpt3 = DepartureAirport(1, 1)
            depArptAdapter.fromJson(depArptAdapter.toJson(depArpt1)) shouldBe depArpt1
            depArptAdapter.fromJson(depArptAdapter.toJson(depArpt2)) shouldBe depArpt2
            depArptAdapter.fromJson(depArptAdapter.toJson(depArpt3)) shouldBe depArpt3
        }

        test("Controllable serialization") {
            val controllableAdapter = testMoshi.adapter<Controllable>()
            val controllable1 = Controllable(0, null)
            val controllable2 = Controllable(2, UUID.randomUUID())
            val controllable3 = Controllable(SectorInfo.TOWER, null)
            val controllable4 = Controllable(SectorInfo.CENTRE, null)
            controllableAdapter.fromJson(controllableAdapter.toJson(controllable1)) shouldBe controllable1
            controllableAdapter.fromJson(controllableAdapter.toJson(controllable2)) shouldBe controllable1
            controllableAdapter.fromJson(controllableAdapter.toJson(controllable3)) shouldBe controllable3
            controllableAdapter.fromJson(controllableAdapter.toJson(controllable4)) shouldBe controllable4
        }

        test("FlightType serialization") {
            val flightTypeAdapter = testMoshi.adapter<FlightType>()
            val flightType1 = FlightType(FlightType.ARRIVAL)
            val flightType2 = FlightType(FlightType.DEPARTURE)
            val flightType3 = FlightType(FlightType.EN_ROUTE)
            flightTypeAdapter.fromJson(flightTypeAdapter.toJson(flightType1)) shouldBe flightType1
            flightTypeAdapter.fromJson(flightTypeAdapter.toJson(flightType2)) shouldBe flightType2
            flightTypeAdapter.fromJson(flightTypeAdapter.toJson(flightType3)) shouldBe flightType3
        }

        test("ContactFromTower serialization") {
            val contactFromTowerAdapter = testMoshi.adapter<ContactFromTower>()
            val cft1 = ContactFromTower(600)
            val cft2 = ContactFromTower(1200)
            val cft3 = ContactFromTower(1270)
            contactFromTowerAdapter.fromJson(contactFromTowerAdapter.toJson(cft1)) shouldBe cft1
            contactFromTowerAdapter.fromJson(contactFromTowerAdapter.toJson(cft2)) shouldBe cft2
            contactFromTowerAdapter.fromJson(contactFromTowerAdapter.toJson(cft3)) shouldBe cft3
        }

        test("ContactToTower serialization") {
            val contactToTowerAdapter = testMoshi.adapter<ContactToTower>()
            val ctt1 = ContactToTower(1170)
            val ctt2 = ContactToTower(1200)
            val ctt3 = ContactToTower(1270)
            contactToTowerAdapter.fromJson(contactToTowerAdapter.toJson(ctt1)) shouldBe ctt1
            contactToTowerAdapter.fromJson(contactToTowerAdapter.toJson(ctt2)) shouldBe ctt2
            contactToTowerAdapter.fromJson(contactToTowerAdapter.toJson(ctt3)) shouldBe ctt3
        }

        test("ContactFromCentre serialization") {
            val contactFromCentreAdapter = testMoshi.adapter<ContactFromCentre>()
            val cfc1 = ContactFromCentre(20500)
            val cfc2 = ContactFromCentre(22700)
            val cfc3 = ContactFromCentre(18200)
            contactFromCentreAdapter.fromJson(contactFromCentreAdapter.toJson(cfc1)) shouldBe cfc1
            contactFromCentreAdapter.fromJson(contactFromCentreAdapter.toJson(cfc2)) shouldBe cfc2
            contactFromCentreAdapter.fromJson(contactFromCentreAdapter.toJson(cfc3)) shouldBe cfc3
        }

        test("ContactToCentre serialization") {
            val contactToCentreAdapter = testMoshi.adapter<ContactToCentre>()
            val ctc1 = ContactToCentre(20500)
            val ctc2 = ContactToCentre(22700)
            val ctc3 = ContactToCentre(18200)
            contactToCentreAdapter.fromJson(contactToCentreAdapter.toJson(ctc1)) shouldBe ctc1
            contactToCentreAdapter.fromJson(contactToCentreAdapter.toJson(ctc2)) shouldBe ctc2
            contactToCentreAdapter.fromJson(contactToCentreAdapter.toJson(ctc3)) shouldBe ctc3
        }

        test("PendingClearances serialization") {
            val pendingClearancesAdapter = testMoshi.adapter<PendingClearances>()
            val pending1 = PendingClearances(Queue<ClearanceState.PendingClearanceState>().apply {
                addLast(ClearanceState.PendingClearanceState(0.4f, ClearanceState()))
                addLast(ClearanceState.PendingClearanceState(1.9f, ClearanceState(vectorHdg = 200, clearedAlt = 5000, clearedIas = 220)))
            })
            val pending2 = PendingClearances()
            pendingClearancesAdapter.fromJson(pendingClearancesAdapter.toJson(pending1)) shouldBe matchPendingClearances(pending1)
            pendingClearancesAdapter.fromJson(pendingClearancesAdapter.toJson(pending2)) shouldBe matchPendingClearances(pending2)
        }

        test("ClearanceAct serialization") {
            val clearanceActAdapter = testMoshi.adapter<ClearanceAct>()
            val act1 = ClearanceAct(ClearanceState().ActingClearance())
            val act2 = ClearanceAct(ClearanceState(vectorHdg = 200, clearedAlt = 5000, clearedIas = 220, route = route1.shouldNotBeNull()).ActingClearance())
            val act1FromJson = clearanceActAdapter.fromJson(clearanceActAdapter.toJson(act1))?.actingClearance?.clearanceState.shouldNotBeNull()
            checkClearanceEquality(act1FromJson, act1.actingClearance.clearanceState, true).shouldBeTrue()
            val act2FromJson = clearanceActAdapter.fromJson(clearanceActAdapter.toJson(act2))?.actingClearance?.clearanceState.shouldNotBeNull()
            checkClearanceEquality(act2FromJson, act2.actingClearance.clearanceState, true).shouldBeTrue()
        }

        test("RecentGoAround serialization") {
            val recentGaAdapter = testMoshi.adapter<RecentGoAround>()
            val recentGa1 = RecentGoAround(20f)
            val recentGa2 = RecentGoAround(40.1f)
            val recentGa3 = RecentGoAround(14.2f)
            recentGaAdapter.fromJson(recentGaAdapter.toJson(recentGa1)) shouldBe recentGa1
            recentGaAdapter.fromJson(recentGaAdapter.toJson(recentGa2)) shouldBe recentGa2
            recentGaAdapter.fromJson(recentGaAdapter.toJson(recentGa3)) shouldBe recentGa3
        }

        test("GPolygon serialization") {
            val gPolygonAdapter = testMoshi.adapter<GPolygon>()
            val gPolygon1 = GPolygon(floatArrayOf(12.3f, 43.1f, 5.6f, 3.5f, 8.2f, 90.3f))
            val gPolygon2 = GPolygon(floatArrayOf(0f, 111.1f, 222.2f, 333.3f, 444.4f, 555.5f))
            val gPolygon3 = GPolygon(floatArrayOf(-90.3f, -45.1f, -34f, -56.1f, 23.1f, -90.6f))
            gPolygonAdapter.fromJson(gPolygonAdapter.toJson(gPolygon1))?.vertices?.contentEquals(gPolygon1.vertices).shouldNotBeNull().shouldBeTrue()
            gPolygonAdapter.fromJson(gPolygonAdapter.toJson(gPolygon2))?.vertices?.contentEquals(gPolygon2.vertices).shouldNotBeNull().shouldBeTrue()
            gPolygonAdapter.fromJson(gPolygonAdapter.toJson(gPolygon3))?.vertices?.contentEquals(gPolygon3.vertices).shouldNotBeNull().shouldBeTrue()
        }

        test("Position serialization") {
            val positionAdapter = testMoshi.adapter<Position>()
            val pos1 = Position()
            val pos2 = Position(45.3f, 871.4f)
            val pos3 = Position(-45.2f, -435.2f)
            positionAdapter.fromJson(positionAdapter.toJson(pos1)) shouldBe pos1
            positionAdapter.fromJson(positionAdapter.toJson(pos2)) shouldBe pos2
            positionAdapter.fromJson(positionAdapter.toJson(pos3)) shouldBe pos3
        }

        test("CustomPosition serialization") {
            val customPositionAdapter = testMoshi.adapter<CustomPosition>()
            val customPos1 = CustomPosition()
            val customPos2 = CustomPosition(45.3f, 871.4f)
            val customPos3 = CustomPosition(-45.2f, -435.2f)
            customPositionAdapter.fromJson(customPositionAdapter.toJson(customPos1)) shouldBe customPos1
            customPositionAdapter.fromJson(customPositionAdapter.toJson(customPos2)) shouldBe customPos2
            customPositionAdapter.fromJson(customPositionAdapter.toJson(customPos3)) shouldBe customPos3
        }

        test("Direction serialization") {
            val directionAdapter = testMoshi.adapter<Direction>()
            val dir1 = Direction()
            val dir2 = Direction(Vector2(Vector2.Y).rotate90(1))
            val dir3 = Direction(Vector2(Vector2.Y).rotateDeg(31.4f))
            val dir4 = Direction(Vector2(Vector2.Y).rotateDeg(-45.6f))
            directionAdapter.fromJson(directionAdapter.toJson(dir1)) shouldBe dir1
            directionAdapter.fromJson(directionAdapter.toJson(dir2)) shouldBe dir2
            directionAdapter.fromJson(directionAdapter.toJson(dir3)) shouldBe dir3
            directionAdapter.fromJson(directionAdapter.toJson(dir4)) shouldBe dir4
        }

        test("Speed serialization") {
            val speedAdapter = testMoshi.adapter<Speed>()
            val speed1 = Speed()
            val speed2 = Speed(200f, 1500f, 3f)
            val speed3 = Speed(80f, 0f, 0f)
            val speed4 = Speed(490f, 1200f, 0.02f)
            speedAdapter.fromJson(speedAdapter.toJson(speed1)) shouldBe speed1
            speedAdapter.fromJson(speedAdapter.toJson(speed2)) shouldBe speed2
            speedAdapter.fromJson(speedAdapter.toJson(speed3)) shouldBe speed3
            speedAdapter.fromJson(speedAdapter.toJson(speed4)) shouldBe speed4
        }

        test("Altitude serialization") {
            val altAdapter = testMoshi.adapter<Altitude>()
            val alt1 = Altitude()
            val alt2 = Altitude(54f)
            val alt3 = Altitude(5089.3f)
            val alt4 = Altitude(32189.3f)
            altAdapter.fromJson(altAdapter.toJson(alt1)) shouldBe alt1
            altAdapter.fromJson(altAdapter.toJson(alt2)) shouldBe alt2
            altAdapter.fromJson(altAdapter.toJson(alt3)) shouldBe alt3
            altAdapter.fromJson(altAdapter.toJson(alt4)) shouldBe alt4
        }

        test("Acceleration serialization") {
            val accelAdapter = testMoshi.adapter<Acceleration>()
            val acc1 = Acceleration()
            val acc2 = Acceleration(2f, 0f, 0f)
            val acc3 = Acceleration(0.05f, 0.9f, 0f)
            val acc4 = Acceleration(0.1f, 0f, 0.13f)
            accelAdapter.fromJson(accelAdapter.toJson(acc1)) shouldBe acc1
            accelAdapter.fromJson(accelAdapter.toJson(acc2)) shouldBe acc2
            accelAdapter.fromJson(accelAdapter.toJson(acc3)) shouldBe acc3
            accelAdapter.fromJson(accelAdapter.toJson(acc4)) shouldBe acc4
        }

        test("IndicatedAirSpeed serialization") {
            val iasAdapter = testMoshi.adapter<IndicatedAirSpeed>()
            val ias1 = IndicatedAirSpeed()
            val ias2 = IndicatedAirSpeed(200f)
            val ias3 = IndicatedAirSpeed(80.4f)
            val ias4 = IndicatedAirSpeed(271.3f)
            iasAdapter.fromJson(iasAdapter.toJson(ias1)) shouldBe ias1
            iasAdapter.fromJson(iasAdapter.toJson(ias2)) shouldBe ias2
            iasAdapter.fromJson(iasAdapter.toJson(ias3)) shouldBe ias3
            iasAdapter.fromJson(iasAdapter.toJson(ias4)) shouldBe ias4
        }

        test("GroundTrack serialization") {
            val groundTrackAdapter = testMoshi.adapter<GroundTrack>()
            val groundTrack1 = GroundTrack()
            val groundTrack2 = GroundTrack(Vector2(Vector2.Y).rotate90(1).scl(1.38f))
            val groundTrack3 = GroundTrack(Vector2(Vector2.Y).rotateDeg(31.4f).scl(0.56f))
            val groundTrack4 = GroundTrack(Vector2(Vector2.Y).rotateDeg(-45.6f).scl(3.40f))
            groundTrackAdapter.fromJson(groundTrackAdapter.toJson(groundTrack1)) shouldBe groundTrack1
            groundTrackAdapter.fromJson(groundTrackAdapter.toJson(groundTrack2)) shouldBe groundTrack2
            groundTrackAdapter.fromJson(groundTrackAdapter.toJson(groundTrack3)) shouldBe groundTrack3
            groundTrackAdapter.fromJson(groundTrackAdapter.toJson(groundTrack4)) shouldBe groundTrack4
        }

        test("AffectedByWind serialization") {
            val affectedByWindAdapter = testMoshi.adapter<AffectedByWind>()
            val affectedByWind1 = AffectedByWind()
            val affectedByWind2 = AffectedByWind(Vector2(Vector2.Y).rotate90(1).scl(0.0694f))
            val affectedByWind3 = AffectedByWind(Vector2(Vector2.Y).rotateDeg(31.4f).scl(0.222f))
            val affectedByWind4 = AffectedByWind(Vector2(Vector2.Y).rotateDeg(-45.6f).scl(0.493f))
            affectedByWindAdapter.fromJson(affectedByWindAdapter.toJson(affectedByWind1)) shouldBe affectedByWind1
            affectedByWindAdapter.fromJson(affectedByWindAdapter.toJson(affectedByWind2)) shouldBe affectedByWind2
            affectedByWindAdapter.fromJson(affectedByWindAdapter.toJson(affectedByWind3)) shouldBe affectedByWind3
            affectedByWindAdapter.fromJson(affectedByWindAdapter.toJson(affectedByWind4)) shouldBe affectedByWind4
        }

        test("RunwayChildren serialization") {
            val rwyChildrenAdapter = testMoshi.adapter<RunwayChildren>()
            val rwyChildren = arpt?.entity?.get(RunwayChildren.mapper).shouldNotBeNull()
            rwyChildrenAdapter.fromJson(rwyChildrenAdapter.toJson(rwyChildren))?.apply {
                rwyMap should matchMap(rwyChildren.rwyMap)
                updatedRwyMapping should matchMap(GdxArrayMap())
            }.shouldNotBeNull()
        }

        test("ApproachChildren serialization") {
            val appChildrenAdapter = testMoshi.adapter<ApproachChildren>()
            val appChildren = arpt?.entity?.get(ApproachChildren.mapper).shouldNotBeNull()
            appChildrenAdapter.fromJson(appChildrenAdapter.toJson(appChildren))?.apply {
                approachMap should matchMap(appChildren.approachMap)
            }.shouldNotBeNull()
        }

        test("VisualApproach serialization") {
            val visAppAdapter = testMoshi.adapter<VisualApproach>()
            val visApp1 = rwy1?.entity?.get(VisualApproach.mapper).shouldNotBeNull()
            val visApp2 = rwy2?.entity?.get(VisualApproach.mapper).shouldNotBeNull()
            val visApp1FromJson = visAppAdapter.fromJson(visAppAdapter.toJson(visApp1)).shouldNotBeNull()
            val visApp2FromJson = visAppAdapter.fromJson(visAppAdapter.toJson(visApp2)).shouldNotBeNull()
            visApp1.visual should matchEntityExactly(visApp1FromJson.visual)
            visApp2.visual should matchEntityExactly(visApp2FromJson.visual)
        }

        test("DependentOppositeRunway serialization") {
            val depOppRwyAdapter = testMoshi.adapter<DependentOppositeRunway>()
            val depOppRwy1 = rwy1?.entity?.get(DependentOppositeRunway.mapper).shouldNotBeNull()
            val depOppRwy2 = rwy2?.entity?.get(DependentOppositeRunway.mapper).shouldNotBeNull()
            val depOppRwy1FromJson = depOppRwyAdapter.fromJson(depOppRwyAdapter.toJson(depOppRwy1)).shouldNotBeNull()
            val depOppRwy2FromJson = depOppRwyAdapter.fromJson(depOppRwyAdapter.toJson(depOppRwy2)).shouldNotBeNull()
            runDelayedEntityRetrieval()
            for ((index, rwy) in depOppRwy1FromJson.depOppRwys.withIndex()) {
                rwy should matchRunway(depOppRwy1.depOppRwys[index])
            }
            for ((index, rwy) in depOppRwy2FromJson.depOppRwys.withIndex()) {
                rwy should matchRunway(depOppRwy2.depOppRwys[index])
            }
        }

        test("DependentParallelRunway serialization") {
            val depParRwyAdapter = testMoshi.adapter<DependentParallelRunway>()
            val depParRwy1 = rwy1?.entity?.get(DependentParallelRunway.mapper).shouldNotBeNull()
            val depParRwy2 = rwy2?.entity?.get(DependentParallelRunway.mapper).shouldNotBeNull()
            val depParRwy1FromJson = depParRwyAdapter.fromJson(depParRwyAdapter.toJson(depParRwy1)).shouldNotBeNull()
            val depParRwy2FromJson = depParRwyAdapter.fromJson(depParRwyAdapter.toJson(depParRwy2)).shouldNotBeNull()
            runDelayedEntityRetrieval()
            for ((index, rwy) in depParRwy1FromJson.depParRwys.withIndex()) {
                rwy should matchRunway(depParRwy1.depParRwys[index])
            }
            for ((index, rwy) in depParRwy2FromJson.depParRwys.withIndex()) {
                rwy should matchRunway(depParRwy2.depParRwys[index])
            }
        }

        test("CrossingRunway serialization") {
            val crossingRwyAdapter = testMoshi.adapter<CrossingRunway>()
            val crossingRwy1 = rwy1?.entity?.get(CrossingRunway.mapper).shouldNotBeNull()
            val crossingRwy2 = rwy2?.entity?.get(CrossingRunway.mapper).shouldNotBeNull()
            val crossingRwy1FromJson = crossingRwyAdapter.fromJson(crossingRwyAdapter.toJson(crossingRwy1)).shouldNotBeNull()
            val crossingRwy2FromJson = crossingRwyAdapter.fromJson(crossingRwyAdapter.toJson(crossingRwy2)).shouldNotBeNull()
            runDelayedEntityRetrieval()
            for ((index, rwy) in crossingRwy1FromJson.crossRwys.withIndex()) {
                rwy should matchRunway(crossingRwy1.crossRwys[index])
            }
            for ((index, rwy) in crossingRwy2FromJson.crossRwys.withIndex()) {
                rwy should matchRunway(crossingRwy2.crossRwys[index])
            }
        }

        test("ArrivalRouteZone serialization") {
            val arrivalRouteZoneAdapter = testMoshi.adapter<ArrivalRouteZone>()
            val arrivalRouteZone1 = ArrivalRouteZone().apply {
                starZone.addAll(getZonesForArrivalRoute(route1.shouldNotBeNull()))
                appZone.addAll(app1.shouldNotBeNull().transitionRouteZones["TESTT"])
                appZone.addAll(app1.shouldNotBeNull().routeZones)
                appZone.addAll(app1.shouldNotBeNull().missedRouteZones)
            }
            val arrivalRouteZone2 = ArrivalRouteZone().apply {
                appZone.addAll(app2.shouldNotBeNull().routeZones)
                appZone.addAll(app2.shouldNotBeNull().missedRouteZones)
            }
            arrivalRouteZoneAdapter.fromJson(arrivalRouteZoneAdapter.toJson(arrivalRouteZone1)).shouldNotBeNull() should matchArrivalZone(arrivalRouteZone1)
            arrivalRouteZoneAdapter.fromJson(arrivalRouteZoneAdapter.toJson(arrivalRouteZone2)).shouldNotBeNull() should matchArrivalZone(arrivalRouteZone2)
        }

        test("DepartureRouteZone serialization") {
            val departureRouteZoneAdapter = testMoshi.adapter<DepartureRouteZone>()
            val departureRouteZone = DepartureRouteZone().apply {
                sidZone.addAll(getZonesForDepartureRoute(route1.shouldNotBeNull()))
            }
            departureRouteZoneAdapter.fromJson(departureRouteZoneAdapter.toJson(departureRouteZone)).shouldNotBeNull() should matchDepartureZone(departureRouteZone)
        }

        test("RandomAirlineData serialization") {
            val randomAirlineAdapter = testMoshi.adapter<RandomAirlineData>()
            val cumDist = CumulativeDistribution<Triple<String, Boolean, GdxArray<String>>>().apply {
                for (i in 0 until 25) {
                    var randomString = ""
                    for (j in 0 until MathUtils.random(8)) randomString += "A"
                    for (j in 0 until MathUtils.random(8)) randomString += "B"
                    for (j in 0 until MathUtils.random(8)) randomString += "C"
                    val possibleTypes = arrayOf("A321", "A333", "A359", "A388", "B739", "B77W", "B78X", "B748")
                    val typeArray = GdxArray<String>()
                    for (j in 0 until MathUtils.random(5) + 1) typeArray.add(possibleTypes[MathUtils.random(7)])
                    add(Triple(randomString, MathUtils.randomBoolean(0.1f), typeArray))
                }
            }
            val randomAirlineData = RandomAirlineData(cumDist)
            randomAirlineAdapter.fromJson(randomAirlineAdapter.toJson(randomAirlineData)).shouldNotBeNull().airlineDistribution should matchCumDist(cumDist)
        }

        test("ActiveRunwayConfig serialization") {
            val activeRwyConfigAdapter = testMoshi.adapter<ActiveRunwayConfig>()
            val activeRwyConfig1 = ActiveRunwayConfig(0)
            val activeRwyConfig2 = ActiveRunwayConfig(1)
            val activeRwyConfig3 = ActiveRunwayConfig(3)
            activeRwyConfigAdapter.fromJson(activeRwyConfigAdapter.toJson(activeRwyConfig1)) shouldBe activeRwyConfig1
            activeRwyConfigAdapter.fromJson(activeRwyConfigAdapter.toJson(activeRwyConfig2)) shouldBe activeRwyConfig2
            activeRwyConfigAdapter.fromJson(activeRwyConfigAdapter.toJson(activeRwyConfig3)) shouldBe activeRwyConfig3
        }

        test("DepartureInfo serialization") {
            val depInfoAdapter = testMoshi.adapter<DepartureInfo>()
            val depInfo1 = DepartureInfo(false, 5)
            val depInfo2 = DepartureInfo(false, -23)
            val depInfo3 = DepartureInfo(true, -5)
            depInfoAdapter.fromJson(depInfoAdapter.toJson(depInfo1)) shouldBe depInfo1
            depInfoAdapter.fromJson(depInfoAdapter.toJson(depInfo2)) shouldBe depInfo2
            depInfoAdapter.fromJson(depInfoAdapter.toJson(depInfo3)) shouldBe depInfo3
        }

        test("AirportNextDeparture serialization") {
            val arptNextDepAdapter = testMoshi.adapter<AirportNextDeparture>()
            val arptNextDep1 = AirportNextDeparture(aircraft1?.entity ?: Entity())
            val arptNextDep2 = AirportNextDeparture(aircraft2?.entity ?: Entity())
            val arptNextDep1Json = arptNextDepAdapter.fromJson(arptNextDepAdapter.toJson(arptNextDep1))
            val arptNextDep2Json = arptNextDepAdapter.fromJson(arptNextDepAdapter.toJson(arptNextDep2))
            runDelayedEntityRetrieval()
            arptNextDep1Json shouldBe arptNextDep1
            arptNextDep2Json shouldBe arptNextDep2
        }

        test("RunwayPreviousArrival serialization") {
            val rwyPrevArrAdapter = testMoshi.adapter<RunwayPreviousArrival>()
            val rwyPrevArr1 = RunwayPreviousArrival(65f, 'H', 'B')
            val rwyPrevArr2 = RunwayPreviousArrival(151f, 'J', 'A')
            val rwyPrevArr3 = RunwayPreviousArrival(83f, 'M', 'D')
            rwyPrevArrAdapter.fromJson(rwyPrevArrAdapter.toJson(rwyPrevArr1)) shouldBe rwyPrevArr1
            rwyPrevArrAdapter.fromJson(rwyPrevArrAdapter.toJson(rwyPrevArr2)) shouldBe rwyPrevArr2
            rwyPrevArrAdapter.fromJson(rwyPrevArrAdapter.toJson(rwyPrevArr3)) shouldBe rwyPrevArr3
        }

        test("RunwayPreviousDeparture serialization") {
            val rwyPrevDepAdapter = testMoshi.adapter<RunwayPreviousDeparture>()
            val rwyPrevDep1 = RunwayPreviousDeparture(33f, 'H', 'B')
            val rwyPrevDep2 = RunwayPreviousDeparture(193f, 'J', 'A')
            val rwyPrevDep3 = RunwayPreviousDeparture(67f, 'M', 'D')
            rwyPrevDepAdapter.fromJson(rwyPrevDepAdapter.toJson(rwyPrevDep1)) shouldBe rwyPrevDep1
            rwyPrevDepAdapter.fromJson(rwyPrevDepAdapter.toJson(rwyPrevDep2)) shouldBe rwyPrevDep2
            rwyPrevDepAdapter.fromJson(rwyPrevDepAdapter.toJson(rwyPrevDep3)) shouldBe rwyPrevDep3
        }

        test("WakeTrail serialization") {
            val wakeTrailAdapter = testMoshi.adapter<WakeTrail>()
            val wakeTrail1 = WakeTrail(Queue<Pair<Position, WakeZone?>>().apply {
                addLast(Pair(Position(1f, 1f), WakeZone(1f, 2f, 1f, 0f, 7000f, "SHIBA1", 'H', 'B')))
                addLast(Pair(Position(10f, 10f), WakeZone(10f, 11f, 10f, 9f, 6500f, "SHIBA1", 'H', 'B')))
                addLast(Pair(Position(20f, 20f), null))
            }, 1.65f)
            val wakeTrail2 = WakeTrail(distNmCounter = 0.2f)
            wakeTrailAdapter.fromJson(wakeTrailAdapter.toJson(wakeTrail1)).shouldNotBeNull() should matchWakeTrail(wakeTrail1)
            wakeTrailAdapter.fromJson(wakeTrailAdapter.toJson(wakeTrail2)).shouldNotBeNull() should matchWakeTrail(wakeTrail2)
        }

        test("WakeInfo serialization") {
            val wakeInfoAdapter = testMoshi.adapter<WakeInfo>()
            val wakeInfo1 = WakeInfo("SHIBA1", 'H', 'B', 0.5f)
            val wakeInfo2 = WakeInfo("SHIBA2", 'M', 'C', 3.5f)
            wakeInfoAdapter.fromJson(wakeInfoAdapter.toJson(wakeInfo1)) shouldBe wakeInfo1
            wakeInfoAdapter.fromJson(wakeInfoAdapter.toJson(wakeInfo2)) shouldBe wakeInfo2
        }

        test("InitialClientDatatagPosition serialization") {
            val datatagPosAdapter = testMoshi.adapter<InitialClientDatatagPosition>()
            val pos1 = InitialClientDatatagPosition(123.45f, 246.89f, false)
            val pos2 = InitialClientDatatagPosition(-98.76f, -126.42f, true)
            datatagPosAdapter.fromJson(datatagPosAdapter.toJson(pos1)) shouldBe pos1
            datatagPosAdapter.fromJson(datatagPosAdapter.toJson(pos2)) shouldBe pos2
        }
    }

    /**
     * Matcher for checking contents of a cumulative distribution are equal
     * @param otherDist the other distribution to check for equality with; the existing distribution to check is accessed
     * from [Matcher]
     */
    private fun <T> matchCumDist(otherDist: CumulativeDistribution<T>) = Matcher<CumulativeDistribution<T>> {
        var discrepancyFound = true
        if (otherDist.size() == it.size()) {
            discrepancyFound = false
            for (i in 0 until otherDist.size())
                if (otherDist.getValue(i) != it.getValue(i) || otherDist.getInterval(i) != it.getInterval(i)) {
                    discrepancyFound = true
                    break
                }
        }
        return@Matcher MatcherResult(!discrepancyFound, {
            if (otherDist.size() != it.size()) "Cumulative distributions did not match in size: ${otherDist.size()} != ${it.size()}"
            else "Cumulative distribution values and/or intervals did not match exactly (including order)"
        }, { "Cumulative distributions matched, but should not have" })
    }

    /**
     * Matcher for checking contents of a pending clearance component are equal
     * @param otherPending the other pending clearance component to check for equality with; the existing pending clearance
     * to check is accessed from [Matcher]
     */
    private fun matchPendingClearances(otherPending: PendingClearances) = Matcher<PendingClearances> {
        val size1 = otherPending.clearanceQueue.size
        val size2 = it.clearanceQueue.size
        if (size1 != size2) return@Matcher MatcherResult(false, {
            "Pending clearance queue size did not match: $size1 != $size2"
        }, { "Pending clearance should not have matched" })
        for (i in 0 until size1) {
            val item1 = otherPending.clearanceQueue[i]
            val item2 = it.clearanceQueue[i]
            if (item1.timeLeft != item2.timeLeft) return@Matcher MatcherResult(false, {
                "Pending clearance time remaining did not match at index $i: ${item1.timeLeft} != ${item2.timeLeft}"
            }, { "Pending clearance should not have matched" })
            if (!checkClearanceEquality(item1.clearanceState, item2.clearanceState, true)) return@Matcher MatcherResult(false, {
                "Pending clearance state did not match at index $i"
            }, { "Pending clearance should not have matched" })
        }
        return@Matcher MatcherResult(true, { "Pending clearance state matched" }, { "Pending clearance should not have matched" })
    }

    /**
     * Matcher for checking contents of a [GdxArrayMap] are equal
     * @param otherMap the other GdxArrayMap to check for equality with; the existing map to check is accessed from [Matcher]
     */
    private fun <T, U> matchMap(otherMap: GdxArrayMap<T, U>) = Matcher<GdxArrayMap<T, U>> {
        val size1 = otherMap.size
        val size2 = it.size
        if (size1 != size2) return@Matcher MatcherResult(false, {
            "Map size did not match: $size1 != $size2"
        }, { "Map should not have matched" })
        for (entry in it) {
            val thisObj = entry.value
            val otherObj = otherMap[entry.key]
            when {
                thisObj is Airport.Runway && otherObj is Airport.Runway -> thisObj.entity should matchRunway(otherObj.entity)
                thisObj is Approach && otherObj is Approach -> thisObj should matchApproach(otherObj)
                otherObj != thisObj -> return@Matcher MatcherResult(false, {
                    "Map entry did not match at key ${entry.key}: ${otherMap[entry.key]} != ${entry.value}"
                }, { "Map should not have matched" })
            }
        }
        return@Matcher MatcherResult(true, { "Map matched" }, { "Map should not have matched" })
    }

    /**
     * Matcher for checking contents of a [GdxArray] are equal
     * @param otherArray the other GdxArray to check for equality with; the existing array to check is accessed from [Matcher]
     */
    private fun <T> matchGdxArray(otherArray: GdxArray<T>) = Matcher<GdxArray<T>> {
        val size1 = otherArray.size
        val size2 = it.size
        if (size1 != size2) return@Matcher MatcherResult(false, {
            "Array size did not match: $size1 != $size2"
        }, { "Array should not have matched" })
        for ((index, entry) in it.withIndex()) {
            if (otherArray[index] != entry) return@Matcher MatcherResult(false, {
                "Array did not contain element $entry"
            }, { "Array should not have matched" })
        }
        return@Matcher MatcherResult(true, { "Array matched" }, { "Array should not have matched" })
    }

    /**
     * Matcher for checking contents of a [Airport.Runway] are equal; it will only check the runway info component
     * @param otherRunway the other Runway to check for equality with; the existing runway to check is accessed from
     * [Matcher]
     */
    private fun matchRunway(otherRunway: Entity) = Matcher<Entity> {
        val rwyInfo1 = otherRunway[RunwayInfo.mapper].shouldNotBeNull()
        val rwyInfo2 = it[RunwayInfo.mapper].shouldNotBeNull()
        if (rwyInfo1.rwyId != rwyInfo2.rwyId ||
            rwyInfo1.rwyName != rwyInfo2.rwyName || rwyInfo1.lengthM != rwyInfo2.lengthM ||
            rwyInfo1.displacedThresholdM != rwyInfo2.displacedThresholdM ||
            rwyInfo1.intersectionTakeoffM != rwyInfo2.intersectionTakeoffM ||
            rwyInfo1.tower != rwyInfo2.tower || rwyInfo1.freq != rwyInfo2.freq) return@Matcher MatcherResult(false, {
            "RunwayInfo of both runways do not match"
        }, { "Runway should not have matched" })
        return@Matcher MatcherResult(true, { "Runway matched" }, { "Runway should not have matched" })
    }

    /**
     * Matcher for checking contents of a [Approach] are equal
     * @param otherApp the other Approach to check for equality with; the existing approach to check is accessed from
     * [Matcher]
     */
    private fun matchApproach(otherApp: Approach) = Matcher<Approach> {
        val size1 = otherApp.entity.components.size()
        val size2 = it.entity.components.size()
        if (size1 == 0 || size2 == 0) return@Matcher MatcherResult(false, { "Approach entity is empty" }, { "Approach should not have matched" })
        it.entity should matchEntityExactly(otherApp.entity)
        return@Matcher MatcherResult(true, { "Approach matched" }, { "Approach should not have matched" })
    }

    /**
     * Matcher for checking components of an [Entity] are equal; components have to match exactly
     * @param otherEntity the other Entity to check for equality with; the existing entity to check is accessed from
     * [Matcher]
     */
    private fun matchEntityExactly(otherEntity: Entity) = Matcher<Entity> {
        val size1 = otherEntity.components.size()
        val size2 = it.components.size()
        if (size1 != size2) return@Matcher MatcherResult(false, {
            "Entity do not contain the same number of components: $size1 != $size2"
        }, { "Entity should not have matched" })
        for (comp in otherEntity.components) {
            if (!it.components.contains(comp, false)) {
                return@Matcher MatcherResult(false, {
                    "Missing component ${comp::class.simpleName} in entity"
                }, { "Entity should not have matched" })
            }
        }
        return@Matcher MatcherResult(true, { "Entity matched" }, { "Entity should not have matched" })
    }

    /**
     * Matcher for checking contents of a [ArrivalRouteZone] are equal
     * @param otherArrZone the other arrival zone to check for equality with; the existing arrival zone to check is accessed
     * from [Matcher]
     */
    private fun matchArrivalZone(otherArrZone: ArrivalRouteZone) = Matcher<ArrivalRouteZone> {
        val arr1 = it.starZone
        val arr2 = otherArrZone.starZone
        val app1 = it.appZone
        val app2 = otherArrZone.appZone
        if (arr1.size != arr2.size) return@Matcher MatcherResult(false, {
            "STAR zone size did not match: ${arr1.size} != ${arr2.size}"
        }, { "Arrival route zone should not have matched" })
        if (app1.size != app2.size) return@Matcher MatcherResult(false, {
            "Approach zone size did not match: ${arr1.size} != ${arr2.size}"
        }, { "Arrival route zone should not have matched" })
        for ((index, starZone) in arr1.withIndex()) {
            starZone.entity[GPolygon.mapper].shouldNotBeNull().vertices.contentEquals(arr2[index].entity[GPolygon.mapper].shouldNotBeNull().vertices).shouldBeTrue()
            starZone.entity[Altitude.mapper] shouldBe arr2[index].entity[Altitude.mapper]
        }
        for ((index, appZone) in app1.withIndex()) {
            appZone.entity[GPolygon.mapper].shouldNotBeNull().vertices.contentEquals(app2[index].entity[GPolygon.mapper].shouldNotBeNull().vertices).shouldBeTrue()
            appZone.entity[Altitude.mapper] shouldBe app2[index].entity[Altitude.mapper]
        }
        return@Matcher MatcherResult(true, {
            "Arrival route zone matched"
        }, { "Arrival route zone should not have matched" })
    }

    /**
     * Matcher for checking contents of a [DepartureRouteZone] are equal
     * @param otherDepZone the other departure zone to check for equality with; the existing departure zone to check is
     * accessed from [Matcher]
     */
    private fun matchDepartureZone(otherDepZone: DepartureRouteZone) = Matcher<DepartureRouteZone> {
        val dep1 = it.sidZone
        val dep2 = otherDepZone.sidZone
        if (dep1.size != dep2.size) return@Matcher MatcherResult(false, {
            "SID zone size did not match: ${dep1.size} != ${dep2.size}"
        }, { "Departure route zone should not have matched" })
        for ((index, depZone) in dep1.withIndex()) {
            depZone.entity[GPolygon.mapper].shouldNotBeNull().vertices.contentEquals(dep2[index].entity[GPolygon.mapper].shouldNotBeNull().vertices).shouldBeTrue()
            depZone.entity[Altitude.mapper] shouldBe dep2[index].entity[Altitude.mapper]
        }
        return@Matcher MatcherResult(true, {
            "Departure route zone matched"
        }, { "Departure route zone should not have matched" })
    }

    /**
     * Matcher for checking contents of a [WakeTrail] are equal
     * @param otherWakeTrail the other wake trail to check for equality with; the existing wake trail to check is accessed
     * from [Matcher]
     */
    private fun matchWakeTrail(otherWakeTrail: WakeTrail) = Matcher<WakeTrail> {
        it.distNmCounter shouldBe otherWakeTrail.distNmCounter.plusOrMinus(0.001f)
        val wake1 = it.wakeZones
        val wake2 = otherWakeTrail.wakeZones
        if (wake1.size != wake2.size) return@Matcher MatcherResult(false, {
            "Wake queue size did not match: ${wake1.size} != ${wake2.size}"
        }, { "Wake trail should not have matched" })
        val queueIterator2 = QueueIterator(wake2)
        for (trail in wake1) {
            val secondTrail = queueIterator2.next()
            trail.first shouldBe secondTrail.first
            trail.second should matchWakeZone(secondTrail.second)
        }
        return@Matcher MatcherResult(true, {
            "Wake trail matched"
        }, { "Wake trail should not have matched" })
    }

    /**
     * Matcher for checking contents of a [WakeZone] are equal; if both are null the check passes
     * @param otherWakeZone the other wake zone to check for equality with; the existing wake zone to check is accessed
     * from [Matcher]
     */
    private fun matchWakeZone(otherWakeZone: WakeZone?) = Matcher<WakeZone?> {
        if (it == null && otherWakeZone == null) return@Matcher MatcherResult(true, {
            "Both null wake zones matched"
        }, { "Wake zone should not have matched" })
        if (it == null || otherWakeZone == null) return@Matcher MatcherResult(false, {
            "One wake zone is null"
        }, { "Wake zone should not have matched" })
        val wake1 = it.entity
        val wake2 = otherWakeZone.entity
        wake1[GPolygon.mapper].shouldNotBeNull().vertices.contentEquals(wake2[GPolygon.mapper].shouldNotBeNull().vertices).shouldBeTrue()
        wake1[Altitude.mapper].shouldNotBeNull() shouldBe wake2[Altitude.mapper].shouldNotBeNull()
        wake1[WakeInfo.mapper].shouldNotBeNull() shouldBe wake2[WakeInfo.mapper].shouldNotBeNull()
        return@Matcher MatcherResult(true, {
            "Wake zone matched"
        }, { "Wake zone should not have matched" })
    }
}