package com.bombbird.terminalcontrol2.utilities

import com.badlogic.ashley.core.Entity
import com.badlogic.gdx.math.MathUtils
import com.bombbird.terminalcontrol2.components.*
import com.squareup.moshi.JsonClass
import ktx.ashley.get
import java.util.Optional

/** Function to initialise aircraft requests for an aircraft [entity] */
fun initialiseAircraftRequests(entity: Entity) {
    val requests = entity.addAndReturn(AircraftRequestChildren())
    if (HighSpeedClimbRequest.shouldCreateRequest(1f, entity)) {
        val highSpeedRequest = HighSpeedClimbRequest()
        highSpeedRequest.initialise(entity)
        requests.requests.add(highSpeedRequest)
    }
}

/** Interface for companion objects to inherit from for checking if a request should be created for an aircraft */
interface AircraftRequestCreationCheck {
    fun shouldCreateRequest(probability: Float, entity: Entity): Boolean
}

/**
 * Component for tagging aircraft requests
 *
 * [minRecurrentTimeS] denotes the minimum number of seconds between subsequent
 * activations of the same request; -1 denotes the request should not be repeated,
 * and should be removed from the aircraft entity after activation
 *
 * [activationTimeS] denotes the time the condition for the request must be fulfilled
 * consecutively before the request is activated
 */
sealed class AircraftRequest(val minRecurrentTimeS: Int = -1, val activationTimeS: Int = 0) {
    enum class RequestType {
        NONE,
        HIGH_SPEED_CLIMB,
        DIRECT,
        FURTHER_CLIMB,
        WEATHER_AVOIDANCE,
        CANCEL_APPROACH_WEATHER,
        CANCEL_APPROACH_MECHANICAL
    }

    abstract val requestType: RequestType

    var nextRequestTimer = 0
    var activationTimer = 0
    var isActive = false

    // Flag to check if request is done and should be removed from the aircraft's request list
    var isDone = false

    /** Function to initialise the request with the [entity], and returns itself */
    abstract fun initialise(entity: Entity): AircraftRequest

    /**
     * Implementation should check if the request should be activated given the [entity] state, and return optional
     * parameters if it should
     *
     * @return [Optional.empty] if the request should not be activated, else an array of parameters (can be empty)
     */
    abstract fun shouldActivateWithParameters(entity: Entity): Optional<Array<String>>

    /**
     * Function to update the request status and check if it should be activated given the [entity] state, and updates
     * internal states accordingly with [deltaS] seconds since last update
     *
     * @return Optional parameters if the request should be activated, empty if not
     */
    fun updateShouldSendRequest(deltaS: Int, entity: Entity): Optional<Array<String>> {
        val wasActive = isActive
        isActive = false
        nextRequestTimer += deltaS
        if (nextRequestTimer < minRecurrentTimeS) return Optional.empty()

        val res = shouldActivateWithParameters(entity)
        if (!res.isPresent) {
            activationTimer = 0
            return Optional.empty()
        } else {
            activationTimer += deltaS
            if (activationTimer < activationTimeS) return Optional.empty()
        }

        isActive = true
        nextRequestTimer = 0
        if (minRecurrentTimeS == -1) isDone = true

        return if (wasActive) Optional.empty() else res
    }
}

/** Dummy request */
@JsonClass(generateAdapter = true)
class NoRequest: AircraftRequest() {
    override val requestType = RequestType.NONE

    override fun initialise(entity: Entity): AircraftRequest {
        return this
    }

    override fun shouldActivateWithParameters(entity: Entity): Optional<Array<String>> {
        return Optional.empty()
    }
}

/** Aircraft request for high speed climb (>250 knots below 10000 feet) */
@JsonClass(generateAdapter = true)
class HighSpeedClimbRequest: AircraftRequest() {
    override val requestType = RequestType.HIGH_SPEED_CLIMB

    companion object: AircraftRequestCreationCheck {
        private const val MAX_ACTIVATION_ALTITUDE = 9000f

        override fun shouldCreateRequest(probability: Float, entity: Entity): Boolean {
            // Only departures should be considered for high speed climb
            val flightType = entity[FlightType.mapper] ?: return false
            if (flightType.type != FlightType.DEPARTURE) return false

            // Check aircraft is actually able to climb at high speed
            val climbSpd = entity[AircraftInfo.mapper]?.aircraftPerf?.tripIas ?: return false
            if (climbSpd <= 250) return false

            return MathUtils.randomBoolean(probability)
        }
    }

    var activationAltFt = 0f

    override fun initialise(entity: Entity): AircraftRequest {
        activationAltFt = MathUtils.random((entity[Altitude.mapper]?.altitudeFt ?: 0f) + 4000, 9000f)
        return this
    }

    override fun shouldActivateWithParameters(entity: Entity): Optional<Array<String>> {
        // Check altitude within activation range
        val altitude = entity[Altitude.mapper]?.altitudeFt ?: return Optional.empty()
        if (!withinRange(altitude, activationAltFt, MAX_ACTIVATION_ALTITUDE)) return Optional.empty()

        // Check aircraft is not already cleared to climb at high speed
        val clearedIas = entity[ClearanceAct.mapper]?.actingClearance?.clearanceState?.clearedIas ?: return Optional.empty()
        if (clearedIas > 250) return Optional.empty()

        return Optional.of(arrayOf())
    }
}
