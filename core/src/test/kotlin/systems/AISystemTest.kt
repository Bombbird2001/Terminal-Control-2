package systems

import com.badlogic.ashley.core.Engine
import com.badlogic.ashley.core.Entity
import com.badlogic.gdx.math.Vector2
import com.bombbird.terminalcontrol2.components.*
import com.bombbird.terminalcontrol2.entities.Airport
import com.bombbird.terminalcontrol2.global.GAME
import com.bombbird.terminalcontrol2.global.GAME_SERVER_THREAD_NAME
import com.bombbird.terminalcontrol2.global.getEngine
import com.bombbird.terminalcontrol2.navigation.Route
import com.bombbird.terminalcontrol2.systems.AISystem
import com.bombbird.terminalcontrol2.systems.FamilyWithListener
import com.bombbird.terminalcontrol2.utilities.calculateMaxAcceleration
import com.bombbird.terminalcontrol2.utilities.calculateTASFromIAS
import com.bombbird.terminalcontrol2.utilities.ktToPxps
import io.kotest.core.spec.style.FunSpec
import io.kotest.matchers.booleans.shouldBeFalse
import io.kotest.matchers.booleans.shouldBeTrue
import io.kotest.matchers.floats.shouldBeBetween
import io.kotest.matchers.floats.shouldBeLessThan
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
                onApproach = false, takingOff = true, takeoffClimb = false)
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
}