import com.badlogic.ashley.core.Entity
import com.bombbird.terminalcontrol2.components.*
import com.bombbird.terminalcontrol2.entities.Aircraft
import com.bombbird.terminalcontrol2.navigation.ClearanceState
import com.bombbird.terminalcontrol2.navigation.Route
import com.bombbird.terminalcontrol2.networking.dataclasses.AircraftControlStateUpdateData
import com.bombbird.terminalcontrol2.utilities.*
import io.kotest.core.spec.style.FunSpec
import io.kotest.matchers.booleans.shouldBeFalse
import io.kotest.matchers.floats.plusOrMinus
import io.kotest.matchers.nulls.shouldBeNull
import io.kotest.matchers.nulls.shouldNotBeNull
import io.kotest.matchers.shouldBe
import ktx.ashley.get
import ktx.ashley.plusAssign
import ktx.ashley.remove
import kotlin.math.roundToInt

object ControlStateToolsTest: FunSpec() {
    init {
        beforeTest {
            testInitialiseGameAndServer()
        }

        test("Add new clearance to pending clearances - no existing clearances") {
            val entity = Entity()
            addNewClearanceToPendingClearances(entity, AircraftControlStateUpdateData(
                "SIA123", "CHA1D", Route().getSerialisedObject(),
                Route().getSerialisedObject(), 203, null,
                3000, false, 250, 180, 340,
                250, null, null
            ), 325)
            addNewClearanceToPendingClearances(entity, AircraftControlStateUpdateData(
                "SIA123", "CHA1D", Route().getSerialisedObject(),
                Route().getSerialisedObject(), 55, CommandTarget.TURN_LEFT,
                15000, false, 250, 190, 340,
                250, null, null
            ), 400)
            val pendingClearances = entity[PendingClearances.mapper].shouldNotBeNull()
            pendingClearances.clearanceQueue.size shouldBe 2
            val state = pendingClearances.clearanceQueue.first()
            state.clearanceState.routePrimaryName shouldBe "CHA1D"
            state.clearanceState.route.size shouldBe 0
            state.clearanceState.hiddenLegs.size shouldBe 0
            state.clearanceState.vectorHdg shouldBe 203
            state.clearanceState.vectorTurnDir.shouldBeNull()
            state.clearanceState.clearedAlt shouldBe 3000
            state.clearanceState.expedite.shouldBeFalse()
            state.clearanceState.clearedIas shouldBe 250
            state.clearanceState.minIas shouldBe 180
            state.clearanceState.maxIas shouldBe 340
            state.clearanceState.optimalIas shouldBe 250
            state.clearanceState.clearedApp.shouldBeNull()
            state.clearanceState.clearedTrans.shouldBeNull()
            state.timeLeft shouldBe 1.8375f

            val state2 = pendingClearances.clearanceQueue[1]
            state2.clearanceState.routePrimaryName shouldBe "CHA1D"
            state2.clearanceState.route.size shouldBe 0
            state2.clearanceState.hiddenLegs.size shouldBe 0
            state2.clearanceState.vectorHdg shouldBe 55
            state2.clearanceState.vectorTurnDir shouldBe CommandTarget.TURN_LEFT
            state2.clearanceState.clearedAlt shouldBe 15000
            state2.clearanceState.expedite.shouldBeFalse()
            state2.clearanceState.clearedIas shouldBe 250
            state2.clearanceState.minIas shouldBe 190
            state2.clearanceState.maxIas shouldBe 340
            state2.clearanceState.optimalIas shouldBe 250
            state2.clearanceState.clearedApp.shouldBeNull()
            state2.clearanceState.clearedTrans.shouldBeNull()
            state2.timeLeft shouldBe 0.01f
        }

        test("Add new clearance to pending clearances - existing pending clearance") {
            val entity = Entity()
            entity += PendingClearances().apply {
                clearanceQueue.addLast(ClearanceState.PendingClearanceState(1.2f,
                    ClearanceState("CHA1D", Route(),
                    Route(), 203, null,
                    3000, false, 250, 180, 340,
                    250, null, null
                )))
            }

            addNewClearanceToPendingClearances(entity, AircraftControlStateUpdateData(
                "SIA123", "CHA1D", Route().getSerialisedObject(),
                Route().getSerialisedObject(), 55, CommandTarget.TURN_LEFT,
                15000, false, 250, 190, 340,
                250, null, null
            ), 400)

            val pendingClearances = entity[PendingClearances.mapper].shouldNotBeNull()
            pendingClearances.clearanceQueue.size shouldBe 2
            val state2 = pendingClearances.clearanceQueue[1]
            state2.clearanceState.routePrimaryName shouldBe "CHA1D"
            state2.clearanceState.route.size shouldBe 0
            state2.clearanceState.hiddenLegs.size shouldBe 0
            state2.clearanceState.vectorHdg shouldBe 55
            state2.clearanceState.vectorTurnDir shouldBe CommandTarget.TURN_LEFT
            state2.clearanceState.clearedAlt shouldBe 15000
            state2.clearanceState.expedite.shouldBeFalse()
            state2.clearanceState.clearedIas shouldBe 250
            state2.clearanceState.minIas shouldBe 190
            state2.clearanceState.maxIas shouldBe 340
            state2.clearanceState.optimalIas shouldBe 250
            state2.clearanceState.clearedApp.shouldBeNull()
            state2.clearanceState.clearedTrans.shouldBeNull()
            state2.timeLeft shouldBe 0.6f.plusOrMinus(0.001f)
        }

        test("Get latest clearance state - active clearance, no pending clearances") {
            val entity = Entity()
            entity += ClearanceAct(ClearanceState(
                "CHA1D", Route(),
                Route(), 203, null,
                3000, false, 250, 180, 340,
                250, null, null
            ).ActingClearance())

            val clearance = getLatestClearanceState(entity).shouldNotBeNull()
            clearance.routePrimaryName shouldBe "CHA1D"
            clearance.route.size shouldBe 0
            clearance.hiddenLegs.size shouldBe 0
            clearance.vectorHdg shouldBe 203
            clearance.vectorTurnDir.shouldBeNull()
            clearance.clearedAlt shouldBe 3000
            clearance.expedite.shouldBeFalse()
            clearance.clearedIas shouldBe 250
            clearance.minIas shouldBe 180
            clearance.maxIas shouldBe 340
            clearance.optimalIas shouldBe 250
            clearance.clearedApp.shouldBeNull()
            clearance.clearedTrans.shouldBeNull()
        }

        test("Get latest clearance state - active clearance, pending clearance") {
            val entity = Entity()
            entity += ClearanceAct(ClearanceState(
                "CHA1D", Route(),
                Route(), 203, null,
                3000, false, 250, 180, 340,
                250, null, null
            ).ActingClearance())

            entity += PendingClearances().apply {
                clearanceQueue.addLast(ClearanceState.PendingClearanceState(1.2f,
                    ClearanceState("CHA1D", Route(),
                        Route(), 55, CommandTarget.TURN_LEFT,
                        15000, false, 250, 190, 340,
                        250, null, null
                    ))
                )
            }

            val clearance = getLatestClearanceState(entity).shouldNotBeNull()
            clearance.routePrimaryName shouldBe "CHA1D"
            clearance.route.size shouldBe 0
            clearance.hiddenLegs.size shouldBe 0
            clearance.vectorHdg shouldBe 55
            clearance.vectorTurnDir shouldBe CommandTarget.TURN_LEFT
            clearance.clearedAlt shouldBe 15000
            clearance.expedite.shouldBeFalse()
            clearance.clearedIas shouldBe 250
            clearance.minIas shouldBe 190
            clearance.maxIas shouldBe 340
            clearance.optimalIas shouldBe 250
            clearance.clearedApp.shouldBeNull()
            clearance.clearedTrans.shouldBeNull()
        }

        test("Get min max optimal IAS - taking off/takeoff climb") {
            val aircraft = Aircraft("SIA773", 0f, 0f, 0f, "B77W", FlightType.DEPARTURE, false).entity
            val acPerf = aircraft[AircraftInfo.mapper]?.aircraftPerf.shouldNotBeNull()
            aircraft += ClearanceAct(ClearanceState(
                "CHA1D", Route(),
                Route(), 203, null,
                3000, false, 250, acPerf.climbOutSpeed, acPerf.maxIas,
                acPerf.climbOutSpeed, null, null
            ).ActingClearance())
            aircraft += TakeoffRoll()
            getMinMaxOptimalIAS(aircraft) shouldBe Triple<Short, Short, Short>(acPerf.climbOutSpeed, 250, acPerf.climbOutSpeed)

            aircraft.remove<TakeoffRoll>()
            aircraft += TakeoffClimb()
            aircraft += Altitude(1232f)
            getMinMaxOptimalIAS(aircraft) shouldBe Triple<Short, Short, Short>(acPerf.climbOutSpeed, 250, acPerf.climbOutSpeed)
        }

        test("Get min max optimal IAS - climbing below 10000 feet") {
            val aircraft = Aircraft("SIA773", 0f, 0f, 0f, "B77W", FlightType.DEPARTURE, false).entity
            val acPerf = aircraft[AircraftInfo.mapper]?.aircraftPerf.shouldNotBeNull()
            aircraft += ClearanceAct(ClearanceState(
                "CHA1D", Route(),
                Route(), 55, CommandTarget.TURN_LEFT,
                15000, false, 250, acPerf.climbOutSpeed, acPerf.maxIas,
                250, null, null
            ).ActingClearance())
            aircraft += Altitude(7000f)
            aircraft += AccelerateToAbove250kts()

            getMinMaxOptimalIAS(aircraft) shouldBe Triple<Short, Short, Short>(acPerf.climbOutSpeed, 250, 250)
        }

        test("Get min max optimal IAS - above 10000 feet below crossover altitude") {
            val aircraft = Aircraft("SIA773", 0f, 0f, 0f, "B77W", FlightType.DEPARTURE, false).entity
            val acPerf = aircraft[AircraftInfo.mapper]?.aircraftPerf.shouldNotBeNull()
            aircraft += ClearanceAct(ClearanceState(
                "CHA1D", Route(),
                Route(), 300, null,
                15000, false, acPerf.tripIas, 190, acPerf.maxIas,
                acPerf.tripIas, null, null
            ).ActingClearance())
            aircraft += Altitude(12100f)

            getMinMaxOptimalIAS(aircraft) shouldBe Triple(
                ((2100f / (acPerf.maxAlt - 10000)) * (acPerf.climbOutSpeed * 2f / 9) + acPerf.climbOutSpeed * 10f / 9).roundToInt().toShort(),
                acPerf.maxIas, acPerf.tripIas)
        }

        test("Get min max optimal IAS - above crossover altitude") {
            val aircraft = Aircraft("SIA773", 0f, 0f, 0f, "B77W", FlightType.DEPARTURE, false).entity
            val acPerf = aircraft[AircraftInfo.mapper]?.aircraftPerf.shouldNotBeNull()
            val crossOverAlt = calculateCrossoverAltitude(acPerf.tripIas, acPerf.tripMach)
            val currIas = calculateIASFromMach(crossOverAlt + 1000, acPerf.tripMach).roundToInt().toShort()
            val currMaxIas = calculateIASFromMach(crossOverAlt + 1000, acPerf.maxMach).roundToInt().toShort()
            aircraft += ClearanceAct(ClearanceState(
                "CHA1D", Route(),
                Route(), 300, null,
                32000, false, currIas, 190, currMaxIas,
                currIas, null, null
            ).ActingClearance())
            aircraft += Altitude(crossOverAlt + 1000)

            getMinMaxOptimalIAS(aircraft) shouldBe Triple(
                (((crossOverAlt - 9000f) / (acPerf.maxAlt - 10000)) * (acPerf.climbOutSpeed * 2f / 9) + acPerf.climbOutSpeed * 10f / 9).roundToInt().toShort(),
                currMaxIas, currIas)
        }

        test("Cruise altitude based on heading") {
            getCruiseAltForHeading(0f, 39000) shouldBe 39000
            getCruiseAltForHeading(90f, 38000) shouldBe 37000
            getCruiseAltForHeading(120f, 41000) shouldBe 41000
            getCruiseAltForHeading(125f, 42000) shouldBe 41000
            getCruiseAltForHeading(155f, 43000) shouldBe 41000
            getCruiseAltForHeading(160f, 60000) shouldBe 57000
            getCruiseAltForHeading(180f, 32000) shouldBe 32000
            getCruiseAltForHeading(210f, 35000) shouldBe 34000
            getCruiseAltForHeading(270f, 42000) shouldBe 40000
            getCruiseAltForHeading(300f, 43000) shouldBe 43000
            getCruiseAltForHeading(330f, 60000) shouldBe 59000
        }
    }
}