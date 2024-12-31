package com.bombbird.terminalcontrol2.utilities

import com.badlogic.ashley.core.Entity
import com.badlogic.gdx.math.MathUtils
import com.bombbird.terminalcontrol2.components.*
import com.bombbird.terminalcontrol2.global.MAG_HDG_DEV
import com.bombbird.terminalcontrol2.navigation.Route
import com.squareup.moshi.JsonClass
import ktx.ashley.get
import ktx.ashley.plusAssign
import java.util.Optional
import kotlin.math.abs

/** Function to initialise aircraft requests for an aircraft [entity] */
fun initialiseAircraftRequests(entity: Entity) {
    val requests = entity.addAndReturn(AircraftRequestChildren())
    if (HighSpeedClimbRequest.shouldCreateRequest(0.1f, entity)) {
        val highSpeedRequest = HighSpeedClimbRequest()
        highSpeedRequest.initialise(entity)
        requests.requests.add(highSpeedRequest)
    }
    if (DirectRequest.shouldCreateRequest(0.1f, entity)) {
        val directRequest = DirectRequest()
        directRequest.initialise(entity)
        requests.requests.add(directRequest)
    }
    if (FurtherClimbRequest.shouldCreateRequest(1f, entity)) {
        val furtherClimbRequest = FurtherClimbRequest()
        furtherClimbRequest.initialise(entity)
        requests.requests.add(furtherClimbRequest)
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
 *
 * [disableForEmergency] denotes whether the request should be disabled if the aircraft
 * declares an emergency
 */
sealed class AircraftRequest(val minRecurrentTimeS: Int = -1, val activationTimeS: Int = 0, val disableForEmergency: Boolean = true) {
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

    /** Function to be called when the request is activated, which allows actions to be performed on the [entity] */
    abstract fun onActivation(entity: Entity)

    /**
     * Function to update the request status and check if it should be activated given the [entity] state, and updates
     * internal states accordingly with [deltaS] seconds since last update
     *
     * @return Optional parameters if the request should be activated, empty if not
     */
    fun updateShouldSendRequest(deltaS: Int, entity: Entity): Optional<Array<String>> {
        if (isDone) return Optional.empty()

        val wasActive = isActive
        isActive = false
        nextRequestTimer += deltaS
        if (nextRequestTimer < minRecurrentTimeS) return Optional.empty()
        if (disableForEmergency && entity[EmergencyPending.mapper]?.active == true) return Optional.empty()

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
        if (!wasActive) onActivation(entity)

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

    override fun onActivation(entity: Entity) {}
}

/** Aircraft request for high speed climb (>250 knots below 10000 feet) */
@JsonClass(generateAdapter = true)
class HighSpeedClimbRequest: AircraftRequest() {
    override val requestType = RequestType.HIGH_SPEED_CLIMB

    companion object: AircraftRequestCreationCheck {
        private const val MAX_ACTIVATION_ALTITUDE = 8000f

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
        activationAltFt = MathUtils.random((entity[Altitude.mapper]?.altitudeFt ?: 0f) + 4000, MAX_ACTIVATION_ALTITUDE)
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

    override fun onActivation(entity: Entity) {
        entity += HighSpeedRequested()
        val spds = getMinMaxOptimalIAS(entity)
        entity[ClearanceAct.mapper]?.actingClearance?.clearanceState?.let {
            it.optimalIas = spds.third
            it.maxIas = spds.second
            it.minIas = spds.first
        }
        entity += LatestClearanceChanged()
    }
}

/** Aircraft request for direct to waypoint */
@JsonClass(generateAdapter = true)
class DirectRequest: AircraftRequest(activationTimeS = 5) {
    override val requestType = RequestType.DIRECT

    companion object: AircraftRequestCreationCheck {
        override fun shouldCreateRequest(probability: Float, entity: Entity): Boolean {
            // Only departures should be considered for directs
            val flightType = entity[FlightType.mapper] ?: return false
            if (flightType.type != FlightType.DEPARTURE) return false

            return MathUtils.randomBoolean(probability)
        }
    }

    var minimumActivationAltFt = 0f

    override fun initialise(entity: Entity): AircraftRequest {
        minimumActivationAltFt = (entity[Altitude.mapper]?.altitudeFt ?: 0f) + MathUtils.random(4000f, 7000f)
        return this
    }

    override fun shouldActivateWithParameters(entity: Entity): Optional<Array<String>> {
        // Check altitude higher than minimum activation
        val altitude = entity[Altitude.mapper]?.altitudeFt ?: return Optional.empty()
        if (altitude < minimumActivationAltFt) return Optional.empty()

        // Request direct to first waypoint that is not the current waypoint, is at least 20nm away, and is
        // at least 20 degrees off the current target heading
        val pos = entity[Position.mapper] ?: return Optional.empty()
        val route = entity[ClearanceAct.mapper]?.actingClearance?.clearanceState?.route ?: return Optional.empty()
        for (i in 1 until route.size) {
            val waypoint = getServerWaypointMap()?.get((route[i] as? Route.WaypointLeg)?.wptId)?.entity ?: continue
            val wptPos = waypoint[Position.mapper] ?: continue
            if (calculateDistanceBetweenPoints(pos.x, pos.y, wptPos.x, wptPos.y) < nmToPx(20)) continue
            val reqTrack = getRequiredTrack(pos.x, pos.y, wptPos.x, wptPos.y)
            val currentTargetTrack = (entity[CommandTarget.mapper]?.targetHdgDeg ?: continue) - MAG_HDG_DEV
            if (abs(findDeltaHeading(currentTargetTrack, reqTrack, CommandTarget.TURN_DEFAULT)) < 20) continue

            return Optional.of(arrayOf(waypoint[WaypointInfo.mapper]?.wptName ?: "waypoint"))
        }

        return Optional.empty()
    }

    override fun onActivation(entity: Entity) {}
}

/** Aircraft request for further climb after reaching cleared altitude */
@JsonClass(generateAdapter = true)
class FurtherClimbRequest: AircraftRequest(minRecurrentTimeS = 120, activationTimeS = 120) {
    override val requestType = RequestType.FURTHER_CLIMB

    companion object: AircraftRequestCreationCheck {
        override fun shouldCreateRequest(probability: Float, entity: Entity): Boolean {
            // Only departures should be considered for further climb
            val flightType = entity[FlightType.mapper] ?: return false
            if (flightType.type != FlightType.DEPARTURE) return false

            return MathUtils.randomBoolean(probability)
        }
    }

    override fun initialise(entity: Entity): AircraftRequest {
        return this
    }

    override fun shouldActivateWithParameters(entity: Entity): Optional<Array<String>> {
        // Check if within 25ft of cleared altitude (not target since there may be SID restrictions)
        val currentAlt = entity[Altitude.mapper]?.altitudeFt ?: return Optional.empty()
        val clearedAlt = entity[ClearanceAct.mapper]?.actingClearance?.clearanceState?.clearedAlt ?: return Optional.empty()
        if (!withinRange(currentAlt, clearedAlt - 25f, clearedAlt + 25f)) return Optional.empty()

        return Optional.of(arrayOf())
    }

    override fun onActivation(entity: Entity) {}
}
