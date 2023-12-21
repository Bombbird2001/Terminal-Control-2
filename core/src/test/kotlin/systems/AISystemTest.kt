package systems

import com.badlogic.ashley.core.Engine
import com.badlogic.ashley.core.Entity
import com.badlogic.gdx.math.Vector2
import com.bombbird.terminalcontrol2.components.*
import com.bombbird.terminalcontrol2.entities.Airport
import com.bombbird.terminalcontrol2.global.GAME
import com.bombbird.terminalcontrol2.global.GAME_SERVER_THREAD_NAME
import com.bombbird.terminalcontrol2.global.getEngine
import com.bombbird.terminalcontrol2.navigation.Approach
import com.bombbird.terminalcontrol2.navigation.Route
import com.bombbird.terminalcontrol2.systems.AISystem
import com.bombbird.terminalcontrol2.systems.FamilyWithListener
import com.bombbird.terminalcontrol2.utilities.calculateMaxAcceleration
import com.bombbird.terminalcontrol2.utilities.calculateTASFromIAS
import com.bombbird.terminalcontrol2.utilities.ktToPxps
import com.bombbird.terminalcontrol2.utilities.nmToPx
import io.kotest.core.spec.style.FunSpec
import io.kotest.matchers.booleans.shouldBeFalse
import io.kotest.matchers.booleans.shouldBeTrue
import io.kotest.matchers.floats.shouldBeBetween
import io.kotest.matchers.floats.shouldBeLessThan
import io.kotest.matchers.nulls.shouldBeNull
import io.kotest.matchers.nulls.shouldNotBeNull
import io.kotest.matchers.shouldBe
import ktx.ashley.*
import ktx.collections.set
import testInitialiseGameAndServer
import kotlin.math.min

object AISystemTest: FunSpec() {
    private val engine: Engine
    private val entity = Entity()

    init {
        testInitialiseGameAndServer()
        val aiSystem = AISystem()
        FamilyWithListener.addAllServerFamilyEntityListeners()
        engine = getEngine(false)
        engine.addSystem(aiSystem)
        engine.addEntity(entity)

        beforeEach {
            Thread.currentThread().name = GAME_SERVER_THREAD_NAME
            if (!engine.entities.contains(entity)) engine.addEntity(entity)
        }

        afterEach {
            entity.removeAll()
        }

        test("Takeoff constant acceleration") {
            initTakeoffAccEntity()
            val alt = entity[Altitude.mapper]?.altitudeFt.shouldNotBeNull()
            val takeoffAcc = entity[TakeoffRoll.mapper]?.targetAccMps2.shouldNotBeNull()
            val acPerf = entity[AircraftInfo.mapper]?.aircraftPerf.shouldNotBeNull()
            entity[AircraftInfo.mapper]?.maxAcc = calculateMaxAcceleration(acPerf, alt, 0f, 0f,
                onApproach = false, takingOff = true, takeoffGoAround = false)
            val acMaxAcc = entity[AircraftInfo.mapper]?.maxAcc.shouldNotBeNull()
            runUpdate()
            entity[Acceleration.mapper]?.dSpeedMps2 shouldBe min(takeoffAcc, acMaxAcc)
        }

        test("Takeoff transition to takeoff climb") {
            initTakeoffAccEntity()
            val alt = entity[Altitude.mapper]?.altitudeFt.shouldNotBeNull()
            val vr = entity[AircraftInfo.mapper]?.aircraftPerf?.vR.shouldNotBeNull()
            entity[Speed.mapper]?.speedKts = calculateTASFromIAS(alt, vr.toFloat()) + 1
            val takeoffRollRwy = entity[TakeoffRoll.mapper]?.rwy.shouldNotBeNull()
            runUpdate()
            entity[TakeoffClimb.mapper]?.accelAltFt.shouldNotBeNull().shouldBeBetween(alt + 1200f, alt + 1800f, 1f)
            takeoffRollRwy.hasNot(RunwayOccupied.mapper).shouldBeTrue()
            entity.has(AccelerateToAbove250kts.mapper).shouldBeTrue()
            entity.has(DivergentDepartureAllowed.mapper).shouldBeTrue()
            entity.hasNot(TakeoffRoll.mapper).shouldBeTrue()
        }

        test("Takeoff climb to acceleration phase - trip IAS more than 250 knots, no route speed restriction") {
            initTakeoffClimbEntity()
            entity[ClearanceAct.mapper]?.actingClearance?.clearanceState?.route.shouldNotBeNull().add(Route.WaypointLeg())
            runUpdate()
            entity[CommandTarget.mapper]?.targetIasKt.shouldNotBeNull().shouldBe(250)
            entity[ClearanceAct.mapper]?.actingClearance?.clearanceState?.clearedIas?.shouldNotBeNull().shouldBe(250)
            entity.hasNot(TakeoffClimb.mapper).shouldBeTrue()
        }

        test("Takeoff climb to acceleration phase - trip IAS less than 250 knots, no route speed restriction") {
            initTakeoffClimbEntity()
            entity[AircraftInfo.mapper]?.aircraftPerf?.tripIas = 200
            entity[ClearanceAct.mapper]?.actingClearance?.clearanceState?.route.shouldNotBeNull().add(Route.WaypointLeg())
            runUpdate()
            entity[CommandTarget.mapper]?.targetIasKt.shouldNotBeNull().shouldBe(200)
            entity[ClearanceAct.mapper]?.actingClearance?.clearanceState?.clearedIas?.shouldNotBeNull().shouldBe(200)
            entity.hasNot(TakeoffClimb.mapper).shouldBeTrue()
        }

        test("Takeoff climb to acceleration phase - trip IAS more than 250 knots, lower route speed restriction") {
            initTakeoffClimbEntity()
            entity[ClearanceAct.mapper]?.actingClearance?.clearanceState?.route.shouldNotBeNull().add(
                Route.WaypointLeg(0, null, null, 230, true, true, true)
            )
            runUpdate()
            entity[CommandTarget.mapper]?.targetIasKt.shouldNotBeNull().shouldBe(230)
            entity[ClearanceAct.mapper]?.actingClearance?.clearanceState?.clearedIas?.shouldNotBeNull().shouldBe(230)
            entity.hasNot(TakeoffClimb.mapper).shouldBeTrue()
        }

        test("Takeoff climb to acceleration phase - trip IAS more than 250 knots, higher route speed restriction") {
            initTakeoffClimbEntity()
            entity[ClearanceAct.mapper]?.actingClearance?.clearanceState?.route.shouldNotBeNull().add(
                Route.WaypointLeg(0, null, null, 270, true, true, true)
            )
            runUpdate()
            entity[CommandTarget.mapper]?.targetIasKt.shouldNotBeNull().shouldBe(250)
            entity[ClearanceAct.mapper]?.actingClearance?.clearanceState?.clearedIas?.shouldNotBeNull().shouldBe(250)
            entity.hasNot(TakeoffClimb.mapper).shouldBeTrue()
        }

        test("Takeoff climb to acceleration phase - trip IAS less than 250 knots, lower route speed restriction") {
            initTakeoffClimbEntity()
            entity[ClearanceAct.mapper]?.actingClearance?.clearanceState?.route.shouldNotBeNull().add(
                Route.WaypointLeg(0, null, null, 190, true, true, true)
            )
            entity[AircraftInfo.mapper]?.aircraftPerf?.tripIas = 200
            entity[ClearanceAct.mapper]?.actingClearance?.clearanceState?.route.shouldNotBeNull().add(Route.WaypointLeg())
            runUpdate()
            entity[CommandTarget.mapper]?.targetIasKt.shouldNotBeNull().shouldBe(190)
            entity[ClearanceAct.mapper]?.actingClearance?.clearanceState?.clearedIas?.shouldNotBeNull().shouldBe(190)
            entity.hasNot(TakeoffClimb.mapper).shouldBeTrue()
        }

        test("Takeoff climb to acceleration phase - trip IAS less than 250 knots, higher route speed restriction") {
            initTakeoffClimbEntity()
            entity[ClearanceAct.mapper]?.actingClearance?.clearanceState?.route.shouldNotBeNull().add(
                Route.WaypointLeg(0, null, null, 230, true, true, true)
            )
            entity[AircraftInfo.mapper]?.aircraftPerf?.tripIas = 200
            entity[ClearanceAct.mapper]?.actingClearance?.clearanceState?.route.shouldNotBeNull().add(Route.WaypointLeg())
            runUpdate()
            entity[CommandTarget.mapper]?.targetIasKt.shouldNotBeNull().shouldBe(200)
            entity[ClearanceAct.mapper]?.actingClearance?.clearanceState?.clearedIas?.shouldNotBeNull().shouldBe(200)
            entity.hasNot(TakeoffClimb.mapper).shouldBeTrue()
        }

        test("Landing acceleration > 60 knots") {
            initLandingAccEntity()
            entity[GroundTrack.mapper]?.trackVectorPxps = Vector2(Vector2.Y).scl(ktToPxps(61))
            runUpdate()
            entity[Acceleration.mapper]?.dSpeedMps2.shouldNotBeNull() shouldBe -1.5f
            entity.has(LandingRoll.mapper).shouldBeTrue()
        }

        test("Landing acceleration 35 knots < x <= 60 knots") {
            initLandingAccEntity()
            entity[GroundTrack.mapper]?.trackVectorPxps = Vector2(Vector2.Y).scl(ktToPxps(59))
            runUpdate()
            entity[Acceleration.mapper]?.dSpeedMps2.shouldNotBeNull() shouldBe -1f
            entity.has(LandingRoll.mapper).shouldBeTrue()
        }

        test("Landing acceleration < 35 knots, not immobilized") {
            val gs = GAME.gameServer.shouldNotBeNull()
            val arpt = Airport()
            gs.airports[0] = arpt
            initLandingAccEntity()
            entity[GroundTrack.mapper]?.trackVectorPxps = Vector2(Vector2.Y).scl(ktToPxps(34.5f))
            val landingRwy = entity[LandingRoll.mapper]?.rwy.shouldNotBeNull().add(RunwayNextArrival(entity))
            gs.trafficValue = 12f
            gs.score = 10
            arpt.entity[DepartureInfo.mapper]?.backlog = 5
            try {
                runUpdate()
            } catch (e: MissingEntitySystemException) {
                // Ignore
            }
            landingRwy.hasNot(RunwayNextArrival.mapper).shouldBeTrue()
            gs.trafficValue.shouldNotBeNull() shouldBe 12.2f
            gs.score shouldBe 11
            arpt.entity[DepartureInfo.mapper]?.backlog.shouldNotBeNull() shouldBe 6
            entity.isScheduledForRemoval.shouldBeTrue()
        }

        test("Landing acceleration < 35 knots, immobilized") {
            val gs = GAME.gameServer.shouldNotBeNull()
            val arpt = Airport()
            gs.airports[0] = arpt
            initLandingAccEntity()
            entity[GroundTrack.mapper]?.trackVectorPxps = Vector2(Vector2.Y).scl(ktToPxps(0.5f))
            entity += ImmobilizeOnLanding(90f)
            val landingRwy = entity[LandingRoll.mapper]?.rwy.shouldNotBeNull().add(RunwayNextArrival(entity))
            gs.trafficValue = 12f
            gs.score = 10
            arpt.entity[DepartureInfo.mapper]?.backlog = 5
            runUpdate()
            entity[Acceleration.mapper]?.dSpeedMps2.shouldNotBeNull() shouldBe 0f
            entity[GroundTrack.mapper]?.trackVectorPxps.shouldNotBeNull().len() shouldBeLessThan 0.01f
            landingRwy.has(RunwayNextArrival.mapper).shouldBeTrue()
            gs.trafficValue.shouldNotBeNull() shouldBe 12f
            gs.score shouldBe 10
            arpt.entity[DepartureInfo.mapper]?.backlog.shouldNotBeNull() shouldBe 5
            entity.isScheduledForRemoval.shouldBeFalse()
        }

        test("Above 10000 feet, accelerate to trip IAS above 250 knots") {
            initAbove250Entity()
            entity[Altitude.mapper]?.altitudeFt = 10001f
            val targetIas = entity[AircraftInfo.mapper]?.aircraftPerf?.tripIas.shouldNotBeNull()
            runUpdate()
            entity[CommandTarget.mapper]?.targetIasKt.shouldNotBeNull() shouldBe targetIas
            entity[ClearanceAct.mapper]?.actingClearance?.clearanceState?.clearedIas.shouldNotBeNull() shouldBe targetIas
            entity.hasNot(AccelerateToAbove250kts.mapper).shouldBeTrue()
        }

        test("Above 10000 feet, accelerate to waypoint max speed restriction with trip IAS above 250 knots") {
            initAbove250Entity()
            entity[Altitude.mapper]?.altitudeFt = 10001f
            entity[ClearanceAct.mapper]?.actingClearance?.clearanceState?.route.shouldNotBeNull().add(
                Route.WaypointLeg(0, null, null, 230,
                    legActive = true, altRestrActive = true, spdRestrActive = true)
            )
            runUpdate()
            entity[CommandTarget.mapper]?.targetIasKt.shouldNotBeNull() shouldBe 230
            entity[ClearanceAct.mapper]?.actingClearance?.clearanceState?.clearedIas.shouldNotBeNull() shouldBe 230
            entity.hasNot(AccelerateToAbove250kts.mapper).shouldBeTrue()
        }

        test("Above 10000 feet, accelerate to trip IAS below 250 knots") {
            initAbove250Entity()
            entity[Altitude.mapper]?.altitudeFt = 10001f
            entity[AircraftInfo.mapper]?.aircraftPerf?.tripIas = 200
            runUpdate()
            entity[CommandTarget.mapper]?.targetIasKt.shouldNotBeNull() shouldBe 200
            entity[ClearanceAct.mapper]?.actingClearance?.clearanceState?.clearedIas.shouldNotBeNull() shouldBe 200
            entity.hasNot(AccelerateToAbove250kts.mapper).shouldBeTrue()
        }

        test("Above 10000 feet, accelerate to waypoint max speed restriction with trip IAS below 250 knots") {
            initAbove250Entity()
            entity[Altitude.mapper]?.altitudeFt = 10001f
            entity[ClearanceAct.mapper]?.actingClearance?.clearanceState?.route.shouldNotBeNull().add(
                Route.WaypointLeg(0, null, null, 190,
                    legActive = true, altRestrActive = true, spdRestrActive = true)
            )
            runUpdate()
            entity[CommandTarget.mapper]?.targetIasKt.shouldNotBeNull() shouldBe 190
            entity[ClearanceAct.mapper]?.actingClearance?.clearanceState?.clearedIas.shouldNotBeNull() shouldBe 190
            entity.hasNot(AccelerateToAbove250kts.mapper).shouldBeTrue()
        }

        test("Below 11000 feet going to below 10000 feet, decelerate to 240 knots from trip IAS above 240 knots") {
            initBelow240Entity()
            val tripIas = entity[AircraftInfo.mapper]?.aircraftPerf?.tripIas.shouldNotBeNull()
            entity[Altitude.mapper]?.altitudeFt = 10999f
            entity[CommandTarget.mapper]?.targetIasKt = tripIas
            entity[CommandTarget.mapper]?.targetAltFt = 9000
            runUpdate()
            entity[CommandTarget.mapper]?.targetIasKt.shouldNotBeNull() shouldBe 240
            entity[ClearanceAct.mapper]?.actingClearance?.clearanceState?.clearedIas.shouldNotBeNull() shouldBe 240
            entity.hasNot(DecelerateTo240kts.mapper).shouldBeTrue()
        }

        test("Below 11000 feet going to 10000 feet, decelerate to 240 knots from trip IAS above 240 knots") {
            initBelow240Entity()
            val tripIas = entity[AircraftInfo.mapper]?.aircraftPerf?.tripIas.shouldNotBeNull()
            entity[Altitude.mapper]?.altitudeFt = 10999f
            entity[CommandTarget.mapper]?.targetIasKt = tripIas
            entity[CommandTarget.mapper]?.targetAltFt = 10000
            runUpdate()
            entity[CommandTarget.mapper]?.targetIasKt.shouldNotBeNull() shouldBe 240
            entity[ClearanceAct.mapper]?.actingClearance?.clearanceState?.clearedIas.shouldNotBeNull() shouldBe 240
            entity.hasNot(DecelerateTo240kts.mapper).shouldBeTrue()
        }

        test("Below 11000 feet going to above 10000 feet, maintain trip IAS above 240 knots") {
            initBelow240Entity()
            val tripIas = entity[AircraftInfo.mapper]?.aircraftPerf?.tripIas.shouldNotBeNull()
            entity[Altitude.mapper]?.altitudeFt = 10999f
            entity[CommandTarget.mapper]?.targetIasKt = tripIas
            entity[CommandTarget.mapper]?.targetAltFt = 10500
            entity[ClearanceAct.mapper]?.actingClearance?.clearanceState.shouldNotBeNull().clearedIas = tripIas
            runUpdate()
            entity[CommandTarget.mapper]?.targetIasKt.shouldNotBeNull() shouldBe tripIas
            entity[ClearanceAct.mapper]?.actingClearance?.clearanceState?.clearedIas.shouldNotBeNull() shouldBe tripIas
            entity.has(DecelerateTo240kts.mapper).shouldBeTrue()
        }

        test("Below 11000 feet going to below 10000 feet, maintain trip IAS below 240 knots") {
            initBelow240Entity()
            val tripIas: Short = 201
            entity[AircraftInfo.mapper]?.aircraftPerf?.tripIas = tripIas
            entity[Altitude.mapper]?.altitudeFt = 10999f
            entity[CommandTarget.mapper]?.targetIasKt = tripIas
            entity[CommandTarget.mapper]?.targetAltFt = 9000
            entity[ClearanceAct.mapper]?.actingClearance?.clearanceState.shouldNotBeNull().clearedIas = tripIas
            runUpdate()
            entity[CommandTarget.mapper]?.targetIasKt.shouldNotBeNull() shouldBe tripIas
            entity[ClearanceAct.mapper]?.actingClearance?.clearanceState?.clearedIas.shouldNotBeNull() shouldBe tripIas
            entity.hasNot(DecelerateTo240kts.mapper).shouldBeTrue()
        }

        test("Less than 16nm from runway threshold, decelerate to 190 knots from above 190 knots") {
            initBelow190Entity()
            entity[Position.mapper]?.x = nmToPx(15.9f)
            entity[CommandTarget.mapper]?.targetIasKt = 200
            entity[ClearanceAct.mapper]?.actingClearance?.clearanceState?.clearedIas = 200
            runUpdate()
            entity[CommandTarget.mapper]?.targetIasKt.shouldNotBeNull() shouldBe 190
            entity[ClearanceAct.mapper]?.actingClearance?.clearanceState?.clearedIas.shouldNotBeNull() shouldBe 190
            entity.hasNot(AppDecelerateTo190kts.mapper).shouldBeTrue()
        }

        test("Less than 16nm from runway threshold, maintain below 190 knots") {
            initBelow190Entity()
            entity[Position.mapper]?.x = nmToPx(15.9f)
            entity[CommandTarget.mapper]?.targetIasKt = 180
            entity[ClearanceAct.mapper]?.actingClearance?.clearanceState?.clearedIas = 180
            runUpdate()
            entity[CommandTarget.mapper]?.targetIasKt.shouldNotBeNull() shouldBe 180
            entity[ClearanceAct.mapper]?.actingClearance?.clearanceState?.clearedIas.shouldNotBeNull() shouldBe 180
            entity.hasNot(AppDecelerateTo190kts.mapper).shouldBeTrue()
        }

        test("More than 16nm from runway threshold, maintain above 190 knots") {
            initBelow190Entity()
            entity[Position.mapper]?.x = nmToPx(16.1f)
            entity[CommandTarget.mapper]?.targetIasKt = 220
            entity[ClearanceAct.mapper]?.actingClearance?.clearanceState?.clearedIas = 220
            runUpdate()
            entity[CommandTarget.mapper]?.targetIasKt.shouldNotBeNull() shouldBe 220
            entity[ClearanceAct.mapper]?.actingClearance?.clearanceState?.clearedIas.shouldNotBeNull() shouldBe 220
            entity.has(AppDecelerateTo190kts.mapper).shouldBeTrue()
        }

        test("Less than 6.4nm from runway threshold, decelerate to minimum approach speed") {
            initMinAppSpdEntity()
            val appSpd = entity[AircraftInfo.mapper]?.aircraftPerf?.appSpd.shouldNotBeNull()
            entity[Position.mapper]?.x = nmToPx(6.3f)
            entity[CommandTarget.mapper]?.targetIasKt = 190
            entity[ClearanceAct.mapper]?.actingClearance?.clearanceState?.clearedIas = 190
            runUpdate()
            entity[CommandTarget.mapper]?.targetIasKt.shouldNotBeNull() shouldBe appSpd
            entity[ClearanceAct.mapper]?.actingClearance?.clearanceState?.clearedIas.shouldNotBeNull() shouldBe appSpd
            entity.hasNot(DecelerateToAppSpd.mapper).shouldBeTrue()
        }

        test("Less than 6.4nm from runway threshold, maintain minimum approach speed") {
            initMinAppSpdEntity()
            val appSpd = entity[AircraftInfo.mapper]?.aircraftPerf?.appSpd.shouldNotBeNull()
            entity[Position.mapper]?.x = nmToPx(6.3f)
            entity[CommandTarget.mapper]?.targetIasKt = appSpd
            entity[ClearanceAct.mapper]?.actingClearance?.clearanceState?.clearedIas = appSpd
            runUpdate()
            entity[CommandTarget.mapper]?.targetIasKt.shouldNotBeNull() shouldBe appSpd
            entity[ClearanceAct.mapper]?.actingClearance?.clearanceState?.clearedIas.shouldNotBeNull() shouldBe appSpd
            entity.hasNot(DecelerateToAppSpd.mapper).shouldBeTrue()
        }

        test("More than 6.4nm from runway threshold, maintain current speed") {
            initMinAppSpdEntity()
            entity[Position.mapper]?.x = nmToPx(6.5f)
            entity[CommandTarget.mapper]?.targetIasKt = 190
            entity[ClearanceAct.mapper]?.actingClearance?.clearanceState?.clearedIas = 190
            runUpdate()
            entity[CommandTarget.mapper]?.targetIasKt.shouldNotBeNull() shouldBe 190
            entity[ClearanceAct.mapper]?.actingClearance?.clearanceState?.clearedIas.shouldNotBeNull() shouldBe 190
            entity.has(DecelerateToAppSpd.mapper).shouldBeTrue()
        }

        test("Less than 6.4nm from runway threshold on visual approach, maintain current speed") {
            initMinAppSpdEntity()
            val locApp = entity[LocalizerCaptured.mapper]?.locApp.shouldNotBeNull()
            entity.remove<LocalizerCaptured>()
            entity += CirclingApproach(locApp)
            entity[Position.mapper]?.x = nmToPx(6.3f)
            entity[CommandTarget.mapper]?.targetIasKt = 190
            entity[ClearanceAct.mapper]?.actingClearance?.clearanceState?.clearedIas = 190
            runUpdate()
            entity[CommandTarget.mapper]?.targetIasKt.shouldNotBeNull() shouldBe 190
            entity[ClearanceAct.mapper]?.actingClearance?.clearanceState?.clearedIas.shouldNotBeNull() shouldBe 190
            entity.has(DecelerateToAppSpd.mapper).shouldBeTrue()
        }

        test("Initial arrival spawn with no legs") {
            initArrivalSpawn()
            entity[CommandTarget.mapper]?.targetHdgDeg = 270f
            entity[ClearanceAct.mapper]?.actingClearance?.clearanceState?.clearedAlt = 10000
            runUpdate()
            entity[CommandTarget.mapper].shouldNotBeNull().targetHdgDeg shouldBe 270f
            entity[CommandTarget.mapper].shouldNotBeNull().targetAltFt shouldBe 10000
            entity[CommandTarget.mapper].shouldNotBeNull().turnDir = CommandTarget.TURN_DEFAULT
            entity[LastRestrictions.mapper].shouldNotBeNull().maxAltFt.shouldBeNull()
            entity[LastRestrictions.mapper].shouldNotBeNull().minAltFt.shouldBeNull()
            entity[LastRestrictions.mapper].shouldNotBeNull().maxSpdKt.shouldBeNull()
            entity.hasNot(InitialArrivalSpawn.mapper).shouldBeTrue()
        }

        test("Initial arrival spawn with vector leg") {
            initArrivalSpawn()
            entity[ClearanceAct.mapper].shouldNotBeNull().actingClearance.clearanceState.route.add(Route.VectorLeg(120, CommandTarget.TURN_LEFT))
            entity[ClearanceAct.mapper]?.actingClearance?.clearanceState?.clearedAlt = 10000
            runUpdate()
            entity[CommandVector.mapper].shouldNotBeNull().heading shouldBe 120
            entity[CommandVector.mapper].shouldNotBeNull().turnDir shouldBe CommandTarget.TURN_LEFT
            entity[LastRestrictions.mapper].shouldNotBeNull().maxAltFt.shouldBeNull()
            entity[LastRestrictions.mapper].shouldNotBeNull().minAltFt.shouldBeNull()
            entity[LastRestrictions.mapper].shouldNotBeNull().maxSpdKt.shouldBeNull()
            entity.hasNot(InitialArrivalSpawn.mapper).shouldBeTrue()
        }

        test("Initial arrival spawn with waypoint without restrictions") {
            initArrivalSpawn()
            entity[ClearanceAct.mapper].shouldNotBeNull().actingClearance.clearanceState.route.add(
                Route.WaypointLeg(0, null, null, null,
                    legActive = true, altRestrActive = true, spdRestrActive = true))
            entity[ClearanceAct.mapper]?.actingClearance?.clearanceState?.clearedAlt = 10000
            runUpdate()
            val cmdDir = entity[CommandDirect.mapper].shouldNotBeNull()
            cmdDir.wptId shouldBe 0
            cmdDir.maxAltFt shouldBe null
            cmdDir.minAltFt shouldBe null
            cmdDir.maxSpdKt shouldBe null
            cmdDir.flyOver shouldBe false
            cmdDir.turnDir shouldBe CommandTarget.TURN_DEFAULT
            entity[LastRestrictions.mapper].shouldNotBeNull().maxAltFt.shouldBeNull()
            entity[LastRestrictions.mapper].shouldNotBeNull().minAltFt.shouldBeNull()
            entity[LastRestrictions.mapper].shouldNotBeNull().maxSpdKt.shouldBeNull()
            entity.hasNot(InitialArrivalSpawn.mapper).shouldBeTrue()
        }
    }

    private fun runUpdate() {
        engine.update(1f / 60)
    }

    private fun initTakeoffAccEntity() {
        val takeoffRwy = Entity().add(RunwayOccupied())
        entity += Acceleration()
        entity += Altitude(1000f)
        entity += AircraftInfo()
        entity += TakeoffRoll(rwy = takeoffRwy)
        entity += Speed()
        entity += IndicatedAirSpeed()
        entity += AffectedByWind()
        entity += Direction(Vector2.Y)
    }

    private fun initTakeoffClimbEntity() {
        entity += Altitude(1501f)
        entity += CommandTarget()
        entity += AircraftInfo()
        entity += TakeoffClimb()
        entity += ClearanceAct()
    }

    private fun initLandingAccEntity() {
        val landingRwy = Entity().add(RunwayOccupied()).add(RunwayNextArrival(entity))
        entity += AircraftInfo()
        entity += Acceleration()
        entity += LandingRoll(landingRwy)
        entity += GroundTrack()
        entity += ArrivalAirport(0)
    }

    private fun initAbove250Entity() {
        entity += AccelerateToAbove250kts()
        entity += Altitude()
        entity += CommandTarget()
        entity += ClearanceAct()
        entity += AircraftInfo()
        entity += FlightType(FlightType.DEPARTURE)
    }

    private fun initBelow240Entity() {
        entity += AircraftInfo()
        entity += DecelerateTo240kts()
        entity += Altitude()
        entity += CommandTarget()
        entity += ClearanceAct()
    }

    private fun initBelow190Entity() {
        val airport = Airport()
        GAME.gameServer.shouldNotBeNull().airports[0] = airport
        val rwy = Airport.Runway()
        airport.entity[RunwayChildren.mapper].shouldNotBeNull().rwyMap[0] = rwy
        val app = Approach()
        entity += Position()
        entity += AppDecelerateTo190kts()
        entity += CommandTarget()
        entity += ClearanceAct()
        entity += LocalizerCaptured(app.entity)
    }

    private fun initMinAppSpdEntity() {
        val airport = Airport()
        GAME.gameServer.shouldNotBeNull().airports[0] = airport
        val rwy = Airport.Runway()
        airport.entity[RunwayChildren.mapper].shouldNotBeNull().rwyMap[0] = rwy
        val app = Approach()
        entity += AircraftInfo()
        entity += Position()
        entity += DecelerateToAppSpd()
        entity += CommandTarget()
        entity += ClearanceAct()
        entity += LocalizerCaptured(app.entity)
    }

    private fun initArrivalSpawn() {
        entity += ClearanceAct()
        entity += InitialArrivalSpawn()
        entity += CommandTarget()
        entity += ArrivalAirport(0)
    }
}