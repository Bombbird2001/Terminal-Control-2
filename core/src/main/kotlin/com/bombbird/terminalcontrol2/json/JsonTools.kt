package com.bombbird.terminalcontrol2.json

import com.bombbird.terminalcontrol2.components.*
import com.bombbird.terminalcontrol2.navigation.Route
import com.squareup.moshi.Moshi
import com.squareup.moshi.adapters.PolymorphicJsonAdapterFactory

/**
 * Gets a Moshi instance that contains adapters for all components that can be serialized
 * @return a [Moshi] that contains all adapters
 */
fun getMoshiWithAllAdapters(): Moshi {
    return Moshi.Builder().add(TakeoffRollAdapter).add(LandingRollAdapter).add(VisualArmedAdapter)
        .add(VisualCapturedAdapter).add(LocalizerArmedAdapter).add(LocalizerCapturedAdapter).add(GlideSlopeArmedAdapter)
        .add(GlideSlopeCapturedAdapter).add(StepDownApproachAdapter).add(CirclingApproachAdapter)
        .add(AircraftAdapter).add(AirportAdapter).add(RunwayAdapter).add(ApproachAdapter).add(RunwayInfoAdapter)
        .add(RandomMetarDistributionAdapter).add(Vector2Adapter).add(ControllableAdapter).add(PendingClearanceAdapter)
        .add(ClearanceActAdapter).add(RunwayChildrenAdapter).add(ApproachChildrenAdapter)
        .add(VisualApproachAdapter).add(DependentOppositeRunwayAdapter).add(DependentParallelRunwayAdapter)
        .add(CrossingRunwayAdapter).add(DepartureDependencyAdapter).add(RandomAirlineDataAdapter).add(AirportNextDepartureAdapter)
        .add(WakeTrailAdapter).add(RouteAdapter).add(WaypointAdapter).add(ArrivalRouteZoneAdapter)
        .add(DepartureRouteZoneAdapter).add(RouteZoneAdapter).add(WakeZoneAdapter).add(TrailInfoAdapter)
        .add(getPolymorphicComponentAdapter()).add(getPolymorphicLegAdapter()).build()
}

/**
 * Interface to mark components that should not be overwritten by the same component type in a newly instantiated
 * entity during loading of newest world data
 */
interface DoNotOverwriteSavedJSON

/** Interface for implementing JSON serialization for subclasses of Component */
interface BaseComponentJSONInterface {
    enum class ComponentType {
        TAKEOFF_ROLL, TAKEOFF_CLIMB, LANDING_ROLL, COMMAND_TARGET, COMMAND_VECTOR, COMMAND_INIT_CLIMB, COMMAND_DIRECT,
        COMMAND_HOLD, COMMAND_EXPEDITE, COMMAND_CDA, COMMAND_TARGET_VERT_SPD, LAST_RESTRICTIONS, VISUAL_ARMED, VISUAL_CAPTURED,
        LOCALIZER_ARMED, LOCALIZER_CAPTURED, GLIDE_SLOPE_ARMED, GLIDE_SLOPE_CAPTURED, STEP_DOWN_APPROACH, CIRCLING_APPROACH,
        APPROACH_INFO, LOCALIZER, LINE_UP_DIST, GLIDE_SLOPE, STEP_DOWN, CIRCLING, MINIMUMS, VISUAL, AIRPORT_INFO, RUNWAY_INFO,
        METAR_INFO, RANDOM_METAR_INFO, WAYPOINT_INFO, AIRCRAFT_INFO, ARRIVAL_AIRPORT, DEPARTURE_AIRPORT, CONTROLLABLE, FLIGHT_TYPE,
        WAITING_TAKEOFF, CONTACT_FROM_TOWER, CONTACT_TO_TOWER, CONTACT_FROM_CENTRE, CONTACT_TO_CENTRE, ACCELERATE_TO_ABOVE_250KTS,
        DECELERATE_TO_240KTS, APP_DECELERATE_TO_190KTS, DECELERATE_TO_APP_SPD, PENDING_CLEARANCES, CLEARANCE_ACT, LATEST_CLEARANCE_CHANGED,
        CLEARANCE_ACT_CHANGED, INITIAL_ARRIVAL_SPAWN, RECENT_GO_AROUND, PENDING_CRUISE_ALTITUDE, DIVERGENT_DEPARTURE_ALLOWED,
        G_POLYGON, POSITION, CUSTOM_POSITION, DIRECTION, SPEED, ALTITUDE, ACCELERATION, INDICATED_AIR_SPEED, GROUND_TRACK,
        AFFECTED_BY_WIND, ON_GROUND, RUNWAY_CHILDREN,  APPROACH_CHILDREN, VISUAL_APPROACH, DEPENDENT_OPPOSITE_RUNWAY, DEPENDENT_PARALLEL_RUNWAY,
        CROSSING_RUNWAY, DEPARTURE_DEPENDENCY, ARRIVAL_ROUTE_ZONE, DEPARTURE_ROUTE_ZONE, ACTIVE_LANDING, ACTIVE_TAKEOFF,
        RANDOM_AIRLINE_DATA, ACTIVE_RUNWAY_CONFIG, ARRIVAL_CLOSED, TIME_SINCE_LAST_DEPARTURE, DEPARTURE_INFO, AIRPORT_NEXT_DEPARTURE,
        RUNWAY_PREVIOUS_ARRIVAL, RUNWAY_PREVIOUS_DEPARTURE, RUNWAY_OCCUPIED, WAKE_TRAIL, WAKE_INFO, INITIAL_CLIENT_DATATAG_POSITION,
        TRAIL_INFO, TTS_VOICE, EMERGENCY_PENDING, RUNNING_CHECKLISTS, REQUIRES_FUEL_DUMP, IMMOBILIZE_ON_LANDING, RUNWAY_CLOSED,
        ON_GO_AROUND_ROUTE
    }

    val componentType: ComponentType
}

/** Gets a [PolymorphicJsonAdapterFactory] for serializable Component subclasses */
private fun getPolymorphicComponentAdapter(): PolymorphicJsonAdapterFactory<BaseComponentJSONInterface> {
    // The below code has been written with the help of Python (I would go crazy if I had to manually write everything)
    return PolymorphicJsonAdapterFactory.of(BaseComponentJSONInterface::class.java, "componentType")
        .withSubtype(TakeoffRoll::class.java, BaseComponentJSONInterface.ComponentType.TAKEOFF_ROLL.name)
        .withSubtype(TakeoffClimb::class.java, BaseComponentJSONInterface.ComponentType.TAKEOFF_CLIMB.name)
        .withSubtype(LandingRoll::class.java, BaseComponentJSONInterface.ComponentType.LANDING_ROLL.name)
        .withSubtype(CommandTarget::class.java, BaseComponentJSONInterface.ComponentType.COMMAND_TARGET.name)
        .withSubtype(CommandVector::class.java, BaseComponentJSONInterface.ComponentType.COMMAND_VECTOR.name)
        .withSubtype(CommandInitClimb::class.java, BaseComponentJSONInterface.ComponentType.COMMAND_INIT_CLIMB.name)
        .withSubtype(CommandDirect::class.java, BaseComponentJSONInterface.ComponentType.COMMAND_DIRECT.name)
        .withSubtype(CommandHold::class.java, BaseComponentJSONInterface.ComponentType.COMMAND_HOLD.name)
        .withSubtype(CommandExpedite::class.java, BaseComponentJSONInterface.ComponentType.COMMAND_EXPEDITE.name)
        .withSubtype(CommandCDA::class.java, BaseComponentJSONInterface.ComponentType.COMMAND_CDA.name)
        .withSubtype(CommandTargetVertSpd::class.java, BaseComponentJSONInterface.ComponentType.COMMAND_TARGET_VERT_SPD.name)
        .withSubtype(LastRestrictions::class.java, BaseComponentJSONInterface.ComponentType.LAST_RESTRICTIONS.name)
        .withSubtype(VisualArmed::class.java, BaseComponentJSONInterface.ComponentType.VISUAL_ARMED.name)
        .withSubtype(VisualCaptured::class.java, BaseComponentJSONInterface.ComponentType.VISUAL_CAPTURED.name)
        .withSubtype(LocalizerArmed::class.java, BaseComponentJSONInterface.ComponentType.LOCALIZER_ARMED.name)
        .withSubtype(LocalizerCaptured::class.java, BaseComponentJSONInterface.ComponentType.LOCALIZER_CAPTURED.name)
        .withSubtype(GlideSlopeArmed::class.java, BaseComponentJSONInterface.ComponentType.GLIDE_SLOPE_ARMED.name)
        .withSubtype(GlideSlopeCaptured::class.java, BaseComponentJSONInterface.ComponentType.GLIDE_SLOPE_CAPTURED.name)
        .withSubtype(StepDownApproach::class.java, BaseComponentJSONInterface.ComponentType.STEP_DOWN_APPROACH.name)
        .withSubtype(CirclingApproach::class.java, BaseComponentJSONInterface.ComponentType.CIRCLING_APPROACH.name)
        .withSubtype(ApproachInfo::class.java, BaseComponentJSONInterface.ComponentType.APPROACH_INFO.name)
        .withSubtype(Localizer::class.java, BaseComponentJSONInterface.ComponentType.LOCALIZER.name)
        .withSubtype(LineUpDist::class.java, BaseComponentJSONInterface.ComponentType.LINE_UP_DIST.name)
        .withSubtype(GlideSlope::class.java, BaseComponentJSONInterface.ComponentType.GLIDE_SLOPE.name)
        .withSubtype(StepDown::class.java, BaseComponentJSONInterface.ComponentType.STEP_DOWN.name)
        .withSubtype(Circling::class.java, BaseComponentJSONInterface.ComponentType.CIRCLING.name)
        .withSubtype(Minimums::class.java, BaseComponentJSONInterface.ComponentType.MINIMUMS.name)
        .withSubtype(Visual::class.java, BaseComponentJSONInterface.ComponentType.VISUAL.name)
        .withSubtype(AirportInfo::class.java, BaseComponentJSONInterface.ComponentType.AIRPORT_INFO.name)
        .withSubtype(RunwayInfo::class.java, BaseComponentJSONInterface.ComponentType.RUNWAY_INFO.name)
        .withSubtype(MetarInfo::class.java, BaseComponentJSONInterface.ComponentType.METAR_INFO.name)
        .withSubtype(RandomMetarInfo::class.java, BaseComponentJSONInterface.ComponentType.RANDOM_METAR_INFO.name)
        .withSubtype(WaypointInfo::class.java, BaseComponentJSONInterface.ComponentType.WAYPOINT_INFO.name)
        .withSubtype(AircraftInfo::class.java, BaseComponentJSONInterface.ComponentType.AIRCRAFT_INFO.name)
        .withSubtype(ArrivalAirport::class.java, BaseComponentJSONInterface.ComponentType.ARRIVAL_AIRPORT.name)
        .withSubtype(DepartureAirport::class.java, BaseComponentJSONInterface.ComponentType.DEPARTURE_AIRPORT.name)
        .withSubtype(Controllable::class.java, BaseComponentJSONInterface.ComponentType.CONTROLLABLE.name)
        .withSubtype(FlightType::class.java, BaseComponentJSONInterface.ComponentType.FLIGHT_TYPE.name)
        .withSubtype(WaitingTakeoff::class.java, BaseComponentJSONInterface.ComponentType.WAITING_TAKEOFF.name)
        .withSubtype(ContactFromTower::class.java, BaseComponentJSONInterface.ComponentType.CONTACT_FROM_TOWER.name)
        .withSubtype(ContactToTower::class.java, BaseComponentJSONInterface.ComponentType.CONTACT_TO_TOWER.name)
        .withSubtype(ContactFromCentre::class.java, BaseComponentJSONInterface.ComponentType.CONTACT_FROM_CENTRE.name)
        .withSubtype(ContactToCentre::class.java, BaseComponentJSONInterface.ComponentType.CONTACT_TO_CENTRE.name)
        .withSubtype(AccelerateToAbove250kts::class.java, BaseComponentJSONInterface.ComponentType.ACCELERATE_TO_ABOVE_250KTS.name)
        .withSubtype(DecelerateTo240kts::class.java, BaseComponentJSONInterface.ComponentType.DECELERATE_TO_240KTS.name)
        .withSubtype(AppDecelerateTo190kts::class.java, BaseComponentJSONInterface.ComponentType.APP_DECELERATE_TO_190KTS.name)
        .withSubtype(DecelerateToAppSpd::class.java, BaseComponentJSONInterface.ComponentType.DECELERATE_TO_APP_SPD.name)
        .withSubtype(PendingClearances::class.java, BaseComponentJSONInterface.ComponentType.PENDING_CLEARANCES.name)
        .withSubtype(ClearanceAct::class.java, BaseComponentJSONInterface.ComponentType.CLEARANCE_ACT.name)
        .withSubtype(LatestClearanceChanged::class.java, BaseComponentJSONInterface.ComponentType.LATEST_CLEARANCE_CHANGED.name)
        .withSubtype(ClearanceActChanged::class.java, BaseComponentJSONInterface.ComponentType.CLEARANCE_ACT_CHANGED.name)
        .withSubtype(InitialArrivalSpawn::class.java, BaseComponentJSONInterface.ComponentType.INITIAL_ARRIVAL_SPAWN.name)
        .withSubtype(RecentGoAround::class.java, BaseComponentJSONInterface.ComponentType.RECENT_GO_AROUND.name)
        .withSubtype(PendingCruiseAltitude::class.java, BaseComponentJSONInterface.ComponentType.PENDING_CRUISE_ALTITUDE.name)
        .withSubtype(DivergentDepartureAllowed::class.java, BaseComponentJSONInterface.ComponentType.DIVERGENT_DEPARTURE_ALLOWED.name)
        .withSubtype(GPolygon::class.java, BaseComponentJSONInterface.ComponentType.G_POLYGON.name)
        .withSubtype(Position::class.java, BaseComponentJSONInterface.ComponentType.POSITION.name)
        .withSubtype(CustomPosition::class.java, BaseComponentJSONInterface.ComponentType.CUSTOM_POSITION.name)
        .withSubtype(Direction::class.java, BaseComponentJSONInterface.ComponentType.DIRECTION.name)
        .withSubtype(Speed::class.java, BaseComponentJSONInterface.ComponentType.SPEED.name)
        .withSubtype(Altitude::class.java, BaseComponentJSONInterface.ComponentType.ALTITUDE.name)
        .withSubtype(Acceleration::class.java, BaseComponentJSONInterface.ComponentType.ACCELERATION.name)
        .withSubtype(IndicatedAirSpeed::class.java, BaseComponentJSONInterface.ComponentType.INDICATED_AIR_SPEED.name)
        .withSubtype(GroundTrack::class.java, BaseComponentJSONInterface.ComponentType.GROUND_TRACK.name)
        .withSubtype(AffectedByWind::class.java, BaseComponentJSONInterface.ComponentType.AFFECTED_BY_WIND.name)
        .withSubtype(OnGround::class.java, BaseComponentJSONInterface.ComponentType.ON_GROUND.name)
        .withSubtype(RunwayChildren::class.java, BaseComponentJSONInterface.ComponentType.RUNWAY_CHILDREN.name)
        .withSubtype(ApproachChildren::class.java, BaseComponentJSONInterface.ComponentType.APPROACH_CHILDREN.name)
        .withSubtype(VisualApproach::class.java, BaseComponentJSONInterface.ComponentType.VISUAL_APPROACH.name)
        .withSubtype(DependentOppositeRunway::class.java, BaseComponentJSONInterface.ComponentType.DEPENDENT_OPPOSITE_RUNWAY.name)
        .withSubtype(DependentParallelRunway::class.java, BaseComponentJSONInterface.ComponentType.DEPENDENT_PARALLEL_RUNWAY.name)
        .withSubtype(CrossingRunway::class.java, BaseComponentJSONInterface.ComponentType.CROSSING_RUNWAY.name)
        .withSubtype(DepartureDependency::class.java, BaseComponentJSONInterface.ComponentType.DEPARTURE_DEPENDENCY.name)
        .withSubtype(ArrivalRouteZone::class.java, BaseComponentJSONInterface.ComponentType.ARRIVAL_ROUTE_ZONE.name)
        .withSubtype(DepartureRouteZone::class.java, BaseComponentJSONInterface.ComponentType.DEPARTURE_ROUTE_ZONE.name)
        .withSubtype(ActiveLanding::class.java, BaseComponentJSONInterface.ComponentType.ACTIVE_LANDING.name)
        .withSubtype(ActiveTakeoff::class.java, BaseComponentJSONInterface.ComponentType.ACTIVE_TAKEOFF.name)
        .withSubtype(RandomAirlineData::class.java, BaseComponentJSONInterface.ComponentType.RANDOM_AIRLINE_DATA.name)
        .withSubtype(ActiveRunwayConfig::class.java, BaseComponentJSONInterface.ComponentType.ACTIVE_RUNWAY_CONFIG.name)
        .withSubtype(ArrivalClosed::class.java, BaseComponentJSONInterface.ComponentType.ARRIVAL_CLOSED.name)
        .withSubtype(TimeSinceLastDeparture::class.java, BaseComponentJSONInterface.ComponentType.TIME_SINCE_LAST_DEPARTURE.name)
        .withSubtype(DepartureInfo::class.java, BaseComponentJSONInterface.ComponentType.DEPARTURE_INFO.name)
        .withSubtype(AirportNextDeparture::class.java, BaseComponentJSONInterface.ComponentType.AIRPORT_NEXT_DEPARTURE.name)
        .withSubtype(RunwayPreviousArrival::class.java, BaseComponentJSONInterface.ComponentType.RUNWAY_PREVIOUS_ARRIVAL.name)
        .withSubtype(RunwayPreviousDeparture::class.java, BaseComponentJSONInterface.ComponentType.RUNWAY_PREVIOUS_DEPARTURE.name)
        .withSubtype(RunwayOccupied::class.java, BaseComponentJSONInterface.ComponentType.RUNWAY_OCCUPIED.name)
        .withSubtype(WakeTrail::class.java, BaseComponentJSONInterface.ComponentType.WAKE_TRAIL.name)
        .withSubtype(WakeInfo::class.java, BaseComponentJSONInterface.ComponentType.WAKE_INFO.name)
        .withSubtype(InitialClientDatatagPosition::class.java, BaseComponentJSONInterface.ComponentType.INITIAL_CLIENT_DATATAG_POSITION.name)
        .withSubtype(TrailInfo::class.java, BaseComponentJSONInterface.ComponentType.TRAIL_INFO.name)
        .withSubtype(TTSVoice::class.java, BaseComponentJSONInterface.ComponentType.TTS_VOICE.name)
        .withSubtype(EmergencyPending::class.java, BaseComponentJSONInterface.ComponentType.EMERGENCY_PENDING.name)
        .withSubtype(RunningChecklists::class.java, BaseComponentJSONInterface.ComponentType.RUNNING_CHECKLISTS.name)
        .withSubtype(RequiresFuelDump::class.java, BaseComponentJSONInterface.ComponentType.REQUIRES_FUEL_DUMP.name)
        .withSubtype(ImmobilizeOnLanding::class.java, BaseComponentJSONInterface.ComponentType.IMMOBILIZE_ON_LANDING.name)
        .withSubtype(RunwayClosed::class.java, BaseComponentJSONInterface.ComponentType.RUNWAY_CLOSED.name)
        .withSubtype(OnGoAroundRoute::class.java, BaseComponentJSONInterface.ComponentType.ON_GO_AROUND_ROUTE.name)
}

/** Interface for implementing JSON serialization for subclasses of Leg */
interface BaseLegJSONInterface {
    enum class LegType {
        WAYPOINT_LEG,
        VECTOR_LEG,
        INIT_CLIMB_LEG,
        DISCONTINUITY_LEG,
        HOLD_LEG
    }

    val legType: LegType
}

/** Gets a [PolymorphicJsonAdapterFactory] for serializable [Route.Leg] subclasses */
private fun getPolymorphicLegAdapter(): PolymorphicJsonAdapterFactory<BaseLegJSONInterface> {
    return PolymorphicJsonAdapterFactory.of(BaseLegJSONInterface::class.java, "legType")
        .withSubtype(Route.WaypointLeg::class.java, BaseLegJSONInterface.LegType.WAYPOINT_LEG.name)
        .withSubtype(Route.VectorLeg::class.java, BaseLegJSONInterface.LegType.VECTOR_LEG.name)
        .withSubtype(Route.InitClimbLeg::class.java, BaseLegJSONInterface.LegType.INIT_CLIMB_LEG.name)
        .withSubtype(Route.DiscontinuityLeg::class.java, BaseLegJSONInterface.LegType.DISCONTINUITY_LEG.name)
        .withSubtype(Route.HoldLeg::class.java, BaseLegJSONInterface.LegType.HOLD_LEG.name)
}
