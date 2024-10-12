package com.bombbird.terminalcontrol2.components

/** Initialises all components and assigns indices for their mappers to prevent race conditions */
fun loadAllComponents() {
    ACCSectorInfo.initialise()
    ACCTempAltitude.initialise()
    AccelerateToAbove250kts.initialise()
    Acceleration.initialise()
    ActiveLanding.initialise()
    ActiveRunwayConfig.initialise()
    ActiveTakeoff.initialise()
    AffectedByWind.initialise()
    AircraftInfo.initialise()
    AircraftRequestChildren.initialise()
    AirportArrivalStats.initialise()
    AirportInfo.initialise()
    AirportNextDeparture.initialise()
    Altitude.initialise()
    AppDecelerateTo190kts.initialise()
    ApproachChildren.initialise()
    ApproachInfo.initialise()
    ApproachList.initialise()
    ApproachNOZChildren.initialise()
    ApproachNOZGroup.initialise()
    ApproachWakeSequence.initialise()
    ApproachWakeSequencePosition.initialise()
    ArrivalAirport.initialise()
    ArrivalClosed.initialise()
    ArrivalRouteZone.initialise()
    CanBeHandedOver.initialise()
    Circling.initialise()
    CirclingApproach.initialise()
    ClearanceAct.initialise()
    ClearanceActChanged.initialise()
    CommandCDA.initialise()
    CommandDirect.initialise()
    CommandExpedite.initialise()
    CommandHold.initialise()
    CommandInitClimb.initialise()
    CommandTarget.initialise()
    CommandTargetVertSpd.initialise()
    CommandVector.initialise()
    ConflictAble.initialise()
    ConstantZoomSize.initialise()
    ContactFromCentre.initialise()
    ContactFromTower.initialise()
    ContactNotification.initialise()
    ContactToCentre.initialise()
    ContactToTower.initialise()
    Controllable.initialise()
    CrossingRunway.initialise()
    CustomApproachSeparation.initialise()
    CustomApproachSeparationChildren.initialise()
    CustomPosition.initialise()
    Datatag.initialise()
    DecelerateTo240kts.initialise()
    DecelerateToAppSpd.initialise()
    DepartureAirport.initialise()
    DepartureDependency.initialise()
    DepartureInfo.initialise()
    DepartureNOZ.initialise()
    DepartureRouteZone.initialise()
    DependentOppositeRunway.initialise()
    DependentParallelRunway.initialise()
    DeprecatedEntity.initialise()
    Direction.initialise()
    DivergentDepartureAllowed.initialise()
    DoNotRenderLabel.initialise()
    DoNotRenderShape.initialise()
    EmergencyPending.initialise()
    FlightType.initialise()
    GCircle.initialise()
    GLine.initialise()
    GLineArray.initialise()
    GPolygon.initialise()
    GRect.initialise()
    GenericLabel.initialise()
    GenericLabels.initialise()
    GlideSlope.initialise()
    GlideSlopeArmed.initialise()
    GlideSlopeCaptured.initialise()
    GlideSlopeCircle.initialise()
    GroundTrack.initialise()
    ImmobilizeOnLanding.initialise()
    IndicatedAirSpeed.initialise()
    InitialArrivalSpawn.initialise()
    InitialClientDatatagPosition.initialise()
    LandingRoll.initialise()
    LastRestrictions.initialise()
    LatestClearanceChanged.initialise()
    LineUpDist.initialise()
    Localizer.initialise()
    LocalizerArmed.initialise()
    LocalizerCaptured.initialise()
    MaxAdvancedDepartures.initialise()
    MetarInfo.initialise()
    MinAltSectorInfo.initialise()
    Minimums.initialise()
    NeedsToInformOfGoAround.initialise()
    OnGoAroundRoute.initialise()
    OnGround.initialise()
    OppositeRunway.initialise()
    ParallelWakeAffects.initialise()
    PendingClearances.initialise()
    PendingCruiseAltitude.initialise()
    PendingRunwayConfig.initialise()
    Position.initialise()
    PublishedHoldInfo.initialise()
    RSSprite.initialise()
    RadarData.initialise()
    RandomAirlineData.initialise()
    RandomMetarInfo.initialise()
    ReadyForApproachClient.initialise()
    RealLifeMetarIcao.initialise()
    RecentGoAround.initialise()
    RequiresFuelDump.initialise()
    RenderLast.initialise()
    RouteSegment.initialise()
    RunningChecklists.initialise()
    RunwayChildren.initialise()
    RunwayClosed.initialise()
    RunwayConfigurationChildren.initialise()
    RunwayConfigurationList.initialise()
    RunwayInfo.initialise()
    RunwayLabel.initialise()
    RunwayNextArrival.initialise()
    RunwayOccupied.initialise()
    RunwayPreviousArrival.initialise()
    RunwayPreviousDeparture.initialise()
    RunwayWindComponents.initialise()
    SIDChildren.initialise()
    SRColor.initialise()
    SRConstantZoomSize.initialise()
    STARChildren.initialise()
    SectorInfo.initialise()
    Speed.initialise()
    StepDown.initialise()
    StepDownApproach.initialise()
    TakeoffClimb.initialise()
    TakeoffRoll.initialise()
    TimeSinceLastDeparture.initialise()
    TrailInfo.initialise()
    TrajectoryPointInfo.initialise()
    TTSVoice.initialise()
    Visual.initialise()
    VisualAfterFaf.initialise()
    VisualApproach.initialise()
    VisualArmed.initialise()
    VisualCaptured.initialise()
    WaitingTakeoff.initialise()
    WakeInfo.initialise()
    WakeInhibit.initialise()
    WakeTolerance.initialise()
    WakeTrail.initialise()
    WaypointInfo.initialise()
    WindshearGoAround.initialise()
}