package com.bombbird.terminalcontrol2.editor.model

/**
 * Editor-side data model for `.arpt` airport map files.
 *
 * Coordinates are stored in nautical miles (nm) to match the file format. Rendering code should convert nm→px using
 * existing helpers.
 */
data class AirportMapDefinition(
    val globals: WorldGlobals = WorldGlobals(),
    val waypoints: MutableList<WaypointDefinition> = mutableListOf(),
    /** Key = number of players (1..n) */
    val sectorsByPlayerCount: MutableMap<Byte, MutableList<SectorDefinition>> = mutableMapOf(),
    val accSectors: MutableList<AccSectorDefinition> = mutableListOf(),
    val minAltSectors: MutableList<MinAltSectorDefinition> = mutableListOf(),
    val shorelinePolylines: MutableList<PolylineDefinition> = mutableListOf(),
    val publishedHolds: MutableList<HoldDefinition> = mutableListOf(),
    val airports: MutableList<AirportDefinition> = mutableListOf(),
)

data class WorldGlobals(
    var maxPlayers: Int = 1,
    var minAltFt: Int = 2000,
    var maxAltFt: Int = 20000,
    /** May be empty in file (header with no values). */
    val intermediateAltitudesFt: MutableList<Int> = mutableListOf(),
    var transitionAltitudeFt: Int = 18000,
    /** Stored as FL (e.g. 140 for FL140) in file, but most logic treats this as hundreds of feet. */
    var transitionLevelFl: Int = 180,
    var minSepNm: Float = 3f,
    /** True heading = magnetic heading - magHdgDevDeg */
    var magHdgDevDeg: Float = 0f,
    var maxArrivals: Int = 20,
)

data class NmPoint(val xNm: Float, val yNm: Float)

data class WaypointDefinition(
    val id: Short,
    var name: String,
    var positionNm: NmPoint,
)

data class SectorDefinition(
    var frequency: String,
    /** Callsigns are stored with spaces in file; some older content may use '-' which loader converts. */
    var arrivalCallsign: String,
    var departureCallsign: String,
    /** Polygon vertices as (x,y) in nm. First and last point may be equal (closed polygon). */
    val verticesNm: MutableList<NmPoint>,
)

data class AccSectorDefinition(
    var frequency: String,
    var callsign: String,
    val verticesNm: MutableList<NmPoint>,
)

sealed class MinAltSectorDefinition {
    abstract var restrictionType: MinAltRestrictionType
    abstract var minAltitudeFt: Int?
}

enum class MinAltRestrictionType { MVA, RESTR }

data class MinAltPolygonSectorDefinition(
    override var restrictionType: MinAltRestrictionType,
    /** Null represents UNL */
    override var minAltitudeFt: Int?,
    val verticesNm: MutableList<NmPoint>,
    var labelPositionNm: NmPoint? = null,
) : MinAltSectorDefinition()

data class MinAltCircleSectorDefinition(
    override var restrictionType: MinAltRestrictionType,
    override var minAltitudeFt: Int?,
    var centerNm: NmPoint,
    var radiusNm: Float,
    /** Absolute label position in nm; null = use circle center. */
    var labelPositionNm: NmPoint? = null,
) : MinAltSectorDefinition()

data class PolylineDefinition(
    val pointsNm: MutableList<NmPoint>,
)

data class HoldDefinition(
    var waypointName: String,
    var inboundHeadingDeg: Short,
    var legDistanceNm: Byte,
    var maxSpeedLowerKt: Short,
    var maxSpeedHigherKt: Short,
    var turnDirection: TurnDirection,
    /** Optional token like A5000 or B6000 in file; keep raw for lossless export. */
    var altitudeRestrictionToken: String? = null,
)

enum class TurnDirection { LEFT, RIGHT }

data class AirportDefinition(
    var id: Byte,
    var icao: String,
    var name: String,
    var ratio: Byte,
    var maxAdvanceDepartures: Int,
    var positionNm: NmPoint,
    var elevationFt: Short,
    var realLifeWeatherIcao: String,
    val metar: RandomMetarDefinition = RandomMetarDefinition(),
    val runways: MutableList<RunwayDefinition> = mutableListOf(),
    val dependentOppositeRunways: MutableList<Pair<String, String>> = mutableListOf(),
    val crossingRunways: MutableList<Pair<String, String>> = mutableListOf(),
    val departureDependencies: MutableList<DepartureDependencyDefinition> = mutableListOf(),
    val departureNoz: MutableList<DepartureNozDefinition> = mutableListOf(),
    val runwayConfigs: MutableList<RunwayConfigDefinition> = mutableListOf(),
    val sids: MutableList<SidDefinition> = mutableListOf(),
    val stars: MutableList<StarDefinition> = mutableListOf(),
    val approaches: MutableList<ApproachDefinition> = mutableListOf(),
    val approachNozGroups: MutableList<ApproachNozGroupDefinition> = mutableListOf(),
    val customApproachSeparations: MutableList<CustomApproachSeparationDefinition> = mutableListOf(),
    /** Raw traffic lines inside TRAFFIC block to preserve distribution detail. */
    val trafficLines: MutableList<String> = mutableListOf(),
    /** If true, this airport block contains `DEPRECATED` marker. */
    var deprecated: Boolean = false,
)

data class RandomMetarDefinition(
    val windDirCumulative: MutableList<Float> = mutableListOf(),
    val windSpdCumulative: MutableList<Float> = mutableListOf(),
    val visibilityCumulative: MutableList<Float> = mutableListOf(),
    val ceilingCumulative: MutableList<Float> = mutableListOf(),
    var windshearLogCoefficients: Pair<Float, Float>? = null,
)

enum class RunwayLabelPlacement { LABEL_LEFT, LABEL_RIGHT, LABEL_BEFORE }

data class RunwayDefinition(
    var id: Byte,
    var name: String,
    var thresholdNm: NmPoint,
    var trueHeadingDeg: Float,
    var lengthM: Short,
    var displacedThresholdM: Short,
    var intersectionTakeoffLengthM: Short,
    var thresholdElevationFt: Short,
    var labelPlacement: RunwayLabelPlacement,
    var towerCallsign: String,
    var towerFrequency: String,
    var deprecated: Boolean = false,
)

data class DepartureDependencyDefinition(
    var departureRunwayName: String,
    /** Raw tokens after the runway name; rules are currently serialized as `RWY-TYPE[-SEP...]`. */
    val rules: MutableList<String> = mutableListOf(),
)

data class DepartureNozDefinition(
    var runwayName: String,
    var positionNm: NmPoint,
    var headingDeg: Short,
    var widthNm: Float,
    var lengthNm: Float,
)

enum class TimeSlot { DAY_ONLY, NIGHT_ONLY, DAY_NIGHT }

data class NoTransgressionZoneDefinition(
    var positionNm: NmPoint,
    var headingDeg: Float,
    var widthNm: Float,
    var lengthNm: Float,
)

data class RunwayConfigDefinition(
    var id: Byte,
    var timeSlot: TimeSlot,
    var name: String = "",
    val departureRunways: MutableList<String> = mutableListOf(),
    val arrivalRunways: MutableList<String> = mutableListOf(),
    val ntz: MutableList<NoTransgressionZoneDefinition> = mutableListOf(),
    val dependentParallelDeparturePairs: MutableList<Pair<String, String>> = mutableListOf(),
    var deprecated: Boolean = false,
)

/** Token stream for procedure routes, preserving leg markers like INITCLIMB / WYPT / HDNG / HOLD and their tokens. */
typealias RouteTokens = List<String>

data class SidDefinition(
    var name: String,
    var timeSlot: TimeSlot,
    var pronunciation: String,
    val runwaySegments: MutableMap<String, SidStarRunwaySegmentDefinition> = mutableMapOf(),
    var routeTokens: RouteTokens = emptyList(),
    var outboundTokens: RouteTokens = emptyList(),
    val allowedRunwayConfigIds: MutableList<Byte> = mutableListOf(),
    var deprecated: Boolean = false,
)

data class StarDefinition(
    var name: String,
    var timeSlot: TimeSlot,
    var pronunciation: String,
    /** STAR `RWY` lines mark availability; token list is typically empty but kept for symmetry. */
    val runwayAvailability: MutableList<String> = mutableListOf(),
    var routeTokens: RouteTokens = emptyList(),
    var inboundTokens: RouteTokens = emptyList(),
    val allowedRunwayConfigIds: MutableList<Byte> = mutableListOf(),
    var deprecated: Boolean = false,
)

data class SidStarRunwaySegmentDefinition(
    var initialClimbFt: Int,
    var routeTokens: RouteTokens,
)

data class ApproachDefinition(
    var name: String,
    var timeSlot: TimeSlot,
    var runwayName: String,
    var positionNm: NmPoint,
    var decisionAltitudeFt: Short,
    var rvrM: Short,
    var localizer: LocalizerDefinition? = null,
    var glideslope: GlideslopeDefinition? = null,
    val stepDownFixes: MutableList<StepDownFixDefinition> = mutableListOf(),
    var lineupDistanceNm: Float? = null,
    var circling: CirclingDefinition? = null,
    var routeTokens: RouteTokens = emptyList(),
    val transitions: MutableMap<String, RouteTokens> = mutableMapOf(),
    var missedApproachTokens: RouteTokens = emptyList(),
    val wakeInhibitApproachNames: MutableList<String> = mutableListOf(),
    val parallelWakeAffects: MutableList<ParallelWakeAffectsDefinition> = mutableListOf(),
    var visualAfterFaf: Boolean = false,
    val allowedRunwayConfigIds: MutableList<Byte> = mutableListOf(),
    var deprecated: Boolean = false,
)

data class LocalizerDefinition(
    var headingDeg: Float,
    var distanceNm: Byte,
)

data class GlideslopeDefinition(
    var angleDeg: Float,
    var offsetNm: Float,
    var maxInterceptAltitudeFt: Short,
)

data class StepDownFixDefinition(
    var altitudeFt: Short,
    var distanceNm: Float,
)

data class CirclingDefinition(
    var minBreakoutAltFt: Int,
    var maxBreakoutAltFt: Int,
    var turnDirection: TurnDirection,
)

data class ParallelWakeAffectsDefinition(
    var otherApproachName: String,
    var distanceNm: Float,
)

data class ApproachNozGroupDefinition(
    val zones: MutableList<ApproachNozZoneDefinition> = mutableListOf(),
)

data class ApproachNozZoneDefinition(
    var positionNm: NmPoint,
    var headingDeg: Float,
    var widthNm: Float,
    var lengthNm: Float,
    val approachNames: MutableList<String> = mutableListOf(),
)

data class CustomApproachSeparationDefinition(
    val group1ApproachNames: List<String>,
    val group2ApproachNames: List<String>,
    var separationNm: Float,
)

