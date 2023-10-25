package systems

import com.badlogic.ashley.core.Engine
import com.badlogic.ashley.core.Entity
import com.bombbird.terminalcontrol2.components.*
import com.bombbird.terminalcontrol2.global.getEngine
import com.bombbird.terminalcontrol2.systems.AISystem
import com.bombbird.terminalcontrol2.systems.FamilyWithListener
import com.bombbird.terminalcontrol2.utilities.calculateMaxAcceleration
import com.bombbird.terminalcontrol2.utilities.calculateTASFromIAS
import io.kotest.core.spec.style.FunSpec
import io.kotest.matchers.booleans.shouldBeTrue
import io.kotest.matchers.floats.shouldBeBetween
import io.kotest.matchers.nulls.shouldNotBeNull
import io.kotest.matchers.shouldBe
import ktx.ashley.get
import ktx.ashley.has
import ktx.ashley.hasNot
import ktx.ashley.plusAssign
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
            val takeoffRollRwy = entity[TakeoffRoll.mapper]?.rwy?.add(RunwayOccupied()).shouldNotBeNull()
            runUpdate()
            entity[TakeoffClimb.mapper]?.accelAltFt.shouldNotBeNull().shouldBeBetween(alt + 1200f, alt + 1800f, 1f)
            takeoffRollRwy.hasNot(RunwayOccupied.mapper).shouldBeTrue()
            entity.has(AccelerateToAbove250kts.mapper).shouldBeTrue()
            entity.has(DivergentDepartureAllowed.mapper).shouldBeTrue()
            entity.hasNot(TakeoffRoll.mapper).shouldBeTrue()
        }
    }

    private fun runUpdate() {
        engine.update(1f / 60)
    }

    private fun initTakeoffAccEntity() {
        entity += Acceleration()
        entity += Altitude(1000f)
        entity += AircraftInfo()
        entity += TakeoffRoll()
        entity += Speed()
        entity += IndicatedAirSpeed()
        entity += AffectedByWind()
    }
}