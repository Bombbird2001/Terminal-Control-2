package com.bombbird.terminalcontrol2.entities

import com.badlogic.gdx.math.Vector2
import com.badlogic.gdx.utils.ArrayMap.Entries
import com.bombbird.terminalcontrol2.components.*
import com.bombbird.terminalcontrol2.entities.Airport.Runway.SerialisedRunway
import com.bombbird.terminalcontrol2.global.*
import com.bombbird.terminalcontrol2.json.BaseComponentJSONInterface
import com.bombbird.terminalcontrol2.json.DoNotOverwriteSavedJSON
import com.bombbird.terminalcontrol2.navigation.Approach
import com.bombbird.terminalcontrol2.navigation.SidStar
import com.bombbird.terminalcontrol2.traffic.RunwayConfiguration
import com.bombbird.terminalcontrol2.traffic.getAvailableApproaches
import com.bombbird.terminalcontrol2.ui.panes.CommsPane
import com.bombbird.terminalcontrol2.utilities.*
import com.bombbird.terminalcontrol2.utilities.FileLog
import ktx.ashley.*
import ktx.collections.GdxArray
import ktx.math.times

/** Airport class that creates an airport entity with the required components on instantiation */
class Airport(id: Byte, icao: String, arptName: String, trafficRatio: Byte, advDep: Int, posX: Float, posY: Float,
              elevation: Short, realLifeMetarIcao: String,
              onClient: Boolean = true): SerialisableEntity<Airport.SerialisedAirport> {
    val entity = getEngine(onClient).entityOnMainThread(onClient) {
        with<Position> {
            x = posX
            y = posY
        }
        with<Altitude> {
            altitudeFt = elevation.toFloat()
        }
        with<AirportInfo> {
            arptId = id
            icaoCode = icao
            name = arptName
            tfcRatio = trafficRatio
        }
        with<RunwayChildren>()
        with<SIDChildren>()
        with<STARChildren>()
        with<ApproachChildren>()
        with<RunwayConfigurationChildren>()
        with<RealLifeMetarIcao> {
            realLifeIcao = realLifeMetarIcao
        }
        with<MetarInfo>()
        with<ApproachNOZChildren>()
        with<AirportArrivalStats>()
        if (!onClient) {
            with<RandomMetarInfo>()
            with<RandomAirlineData>()
            with<DepartureInfo> {
                backlog = (advDep * 0.25f).toInt() * -1
            }
            with<MaxAdvancedDepartures> {
                maxAdvanceDepartures = advDep
            }
            with<TimeSinceLastDeparture>()
            with<CustomApproachSeparationChildren>()
        }
    }

    /** Empty airport constructor for loading of saves */
    private constructor(): this(-1, "XXXX", "Empty", 1, 0, 0f, 0f, 0, "XXXX", false)

    companion object {
        /** Empty airport constructor to help with loading of runways in saves */
        val EMPTY_AIRPORT by lazy { Airport() }

        /** Empty airport constructor to help with loading of airports in saves */
        fun newEmptyAirport(): Airport {
            return Airport()
        }

        /** De-serialises a [SerialisedAirport] and creates a new [Airport] object from it */
        fun fromSerialisedObject(serialisedAirport: SerialisedAirport): Airport {
            return Airport(
                serialisedAirport.arptId, serialisedAirport.icaoCode, serialisedAirport.name, serialisedAirport.tfcRatio,
                0, serialisedAirport.x, serialisedAirport.y, serialisedAirport.altitude, "XXXX"
            ).also { arpt ->
                arpt.entity.apply {
                    val rwys = get(RunwayChildren.mapper)?.apply {
                        rwyMap.clear()
                        for (sRwy in serialisedAirport.rwys) {
                            rwyMap.put(sRwy.rwyId, Runway.fromSerialisedObject(arpt, sRwy))
                        }
                        updatedRwyMapping.clear()
                        for (sMap in serialisedAirport.rwyMapping) {
                            updatedRwyMapping.put(sMap.rwyName, sMap.rwyId)
                        }
                        arpt.assignOppositeRunways()
                    }
                    get(SIDChildren.mapper)?.apply {
                        sidMap.clear()
                        for (sSid in serialisedAirport.sids) {
                            sidMap.put(sSid.name, SidStar.SID.fromSerialisedObject(sSid))
                        }
                    }
                    get(STARChildren.mapper)?.apply {
                        starMap.clear()
                        for (sStar in serialisedAirport.stars) {
                            starMap.put(sStar.name, SidStar.STAR.fromSerialisedObject(sStar))
                        }
                    }
                    get(ApproachChildren.mapper)?.apply {
                        approachMap.clear()
                        for (sApp in serialisedAirport.approaches) {
                            approachMap.put(sApp.name, Approach.fromSerialisedObject(sApp).apply {
                                assignGlideSlopeCircles(rwys?.rwyMap?.get(sApp.rwyId))
                            })
                        }
                    }
                    get(RunwayConfigurationChildren.mapper)?.apply {
                        rwyConfigs.clear()
                        for (sConfig in serialisedAirport.rwyConfigs) {
                            rwyConfigs.put(sConfig.id, RunwayConfiguration.fromSerialisedObject(sConfig, get(RunwayChildren.mapper)?.rwyMap ?: continue))
                        }
                    }
                    get(ApproachNOZChildren.mapper)?.apply {
                        nozGroups.clear()
                        for (i in 0 until serialisedAirport.appNOZGroups.size) {
                            val serialisedNOZGroup = serialisedAirport.appNOZGroups[i]
                            val nozGroup = ApproachNOZGroup()
                            for (zone in serialisedNOZGroup) {
                                nozGroup.appNoz.add(ApproachNormalOperatingZone.fromSerialisedObject(zone))
                            }
                            nozGroups.add(nozGroup)
                        }
                    }
                }
                arpt.updateFromSerialisedMetar(serialisedAirport.metar)
            }
        }
    }

    /** Object that contains [Airport] data to be serialised by Kryo */
    class SerialisedAirport(val x: Float = 0f, val y: Float = 0f,
                            val altitude: Short = 0,
                            val arptId: Byte = -1, val icaoCode: String = "", val name: String = "", val tfcRatio: Byte = 0,
                            val rwys: Array<SerialisedRunway> = arrayOf(),
                            val rwyMapping: Array<SerialisedRunwayMapping> = arrayOf(),
                            val sids: Array<SidStar.SID.SerialisedSID> = arrayOf(),
                            val stars: Array<SidStar.STAR.SerialisedSTAR> = arrayOf(),
                            val approaches: Array<Approach.SerialisedApproach> = arrayOf(),
                            val rwyConfigs: Array<RunwayConfiguration.SerialisedRwyConfig> = arrayOf(),
                            val metar: SerialisedMetar = SerialisedMetar(),
                            val appNOZGroups: Array<Array<ApproachNormalOperatingZone.SerialisedApproachNOZ>> = arrayOf()
    )

    /**
     * Returns a default empty [SerialisedAirport] due to missing component, and logs a message to the console
     * @param missingComponent the missing aircraft component
     */
    override fun emptySerialisableObject(missingComponent: String): SerialisedAirport {
        FileLog.info("Airport", "Empty serialised airport returned due to missing $missingComponent component")
        return SerialisedAirport()
    }

    /** Gets a [SerialisedAirport] from current state */
    override fun getSerialisableObject(): SerialisedAirport {
        entity.apply {
            val position = get(Position.mapper) ?: return emptySerialisableObject("Position")
            val altitude = get(Altitude.mapper) ?: return emptySerialisableObject("Altitude")
            val arptInfo = get(AirportInfo.mapper) ?: return emptySerialisableObject("AirportInfo")
            val rwys = get(RunwayChildren.mapper) ?: return emptySerialisableObject("RunwayChildren")
            val sids = get(SIDChildren.mapper) ?: return emptySerialisableObject("SIDChildren")
            val stars = get(STARChildren.mapper) ?: return emptySerialisableObject("STARChildren")
            val approaches = get(ApproachChildren.mapper) ?: return emptySerialisableObject("ApproachChildren")
            val rwyConfigs = get(RunwayConfigurationChildren.mapper) ?: return emptySerialisableObject("RunwayConfigurationChildren")
            val appNOZGroups = get(ApproachNOZChildren.mapper) ?: return emptySerialisableObject("ApproachNOZChildren")
            val allNOZZones = Array(appNOZGroups.nozGroups.size) { groupNo ->
                Array(appNOZGroups.nozGroups[groupNo].appNoz.size) { zoneNo ->
                    appNOZGroups.nozGroups[groupNo].appNoz[zoneNo].getSerialisableObject()
                }
            }
            return SerialisedAirport(
                position.x, position.y,
                altitude.altitudeFt.toInt().toShort(),
                arptInfo.arptId, arptInfo.icaoCode, arptInfo.name, arptInfo.tfcRatio,
                rwys.rwyMap.map { it.value.getSerialisableObject() }.toTypedArray(),
                rwys.updatedRwyMapping.map { SerialisedRunwayMapping(it.key, it.value) }.toTypedArray(),
                sids.sidMap.map { it.value.getSerialisedObject() }.toTypedArray(),
                stars.starMap.map { it.value.getSerialisedObject() }.toTypedArray(),
                approaches.approachMap.map { it.value.getSerialisableObject() }.toTypedArray(),
                rwyConfigs.rwyConfigs.map { it.value.getSerialisedObject() }.toTypedArray(),
                getSerialisedMetar(), allNOZZones
            )
        }
    }

    /** Object that contains METAR data to be serialised by Kryo */
    class SerialisedMetar(val arptId: Byte = 0,
                          val letterCode: Char? = null, val rawMetar: String? = null,
                          val windHeadingDeg: Short = 360, val windSpeedKt: Short = 0, val windGustKt: Short = 0,
                          val visibilityM: Short = 10000, val ceilingFtAGL: Short? = null, val windshear: String = "")

    /** Gets a [SerialisedMetar] from current METAR state */
    fun getSerialisedMetar(): SerialisedMetar {
        val arptId = entity[AirportInfo.mapper]?.arptId ?: return SerialisedMetar()
        return entity[MetarInfo.mapper]?.let {
            SerialisedMetar(arptId, it.letterCode, it.rawMetar, it.windHeadingDeg, it.windSpeedKt, it.windGustKt, it.visibilityM, it.ceilingHundredFtAGL, it.windshear)
        } ?: SerialisedMetar()
    }

    /** De-serialises a [SerialisedMetar] and updates this airport's [MetarInfo] from it */
    fun updateFromSerialisedMetar(serialisedMetar: SerialisedMetar) {
        entity[MetarInfo.mapper]?.apply {
            letterCode = serialisedMetar.letterCode
            rawMetar = serialisedMetar.rawMetar
            windHeadingDeg = serialisedMetar.windHeadingDeg
            windSpeedKt = serialisedMetar.windSpeedKt
            windGustKt = serialisedMetar.windGustKt
            visibilityM = serialisedMetar.visibilityM
            ceilingHundredFtAGL = serialisedMetar.ceilingFtAGL
            windshear = serialisedMetar.windshear
            updateWindVector(windVectorPx, windHeadingDeg, windSpeedKt)
            updateRunwayWindComponents(entity)
            calculateRunwayConfigScores(entity)
        }
    }

    /** Runway mapping class that contains data to be serialised by Kryo */
    class SerialisedRunwayMapping(val rwyName: String = "", val rwyId: Byte = 0)

    /** Runway class that creates a runway entity with the required components on instantiation */
    class Runway(parentAirport: Airport, id: Byte, name: String, posX: Float, posY: Float, trueHdg: Float,
                 runwayLengthM: Short, displacedM: Short, intersectionM: Short, elevation: Short, labelPos: Byte,
                 towerName: String, towerFreq: String, onClient: Boolean = true): SerialisableEntity<SerialisedRunway> {
        val entity = getEngine(onClient).entityOnMainThread(onClient) {
            with<Position> {
                x = posX
                y = posY
            }
            with<Altitude> {
                altitudeFt = elevation.toFloat()
            }
            with<Direction> {
                trackUnitVector = Vector2(Vector2.Y).rotateDeg(-trueHdg)
            }
            with<RunwayInfo> {
                rwyId = id
                rwyName = name
                lengthM = runwayLengthM
                airport = parentAirport
                displacedThresholdM = displacedM
                intersectionTakeoffM = intersectionM
                tower = towerName
                freq = towerFreq
            }
            with<CustomPosition> {
                // Custom position component will store the position of the actual runway threshold (i.e. taking into
                // any displaced threshold)
                val displacementVector = Vector2(Vector2.Y).rotateDeg(-trueHdg) * mToPx(displacedM.toFloat())
                x = posX + displacementVector.x
                y = posY + displacementVector.y
            }
            with<VisualApproach> {
                val totalDisplacementM = displacedM + 150 // TDZ is 150m after the threshold (with displacement if any)
                val displacementVector = Vector2(Vector2.Y).rotateDeg(-trueHdg) * mToPx(totalDisplacementM)
                visual += ApproachInfo("VIS $name", parentAirport.entity[AirportInfo.mapper]?.arptId ?: 0, id)
                visual += Position(posX + displacementVector.x, posY + displacementVector.y)
                visual += Direction(Vector2(Vector2.Y).rotateDeg(180 - trueHdg))
                visual += Visual()
            }
            with<RunwayWindComponents>()
            with<RunwayLabel> {
                if (labelPos in RunwayLabel.LEFT..RunwayLabel.RIGHT) positionToRunway = labelPos
                else {
                    positionToRunway = 0
                    FileLog.info("Runway", "Invalid labelPos $labelPos set, using default value 0")
                }
            }
            if (onClient) {
                with<GRect> {
                    width = mToPx(runwayLengthM.toInt())
                }
                with<GenericLabel> {
                    updateStyle("Runway")
                    updateText(name)
                }
                with<ConstantZoomSize>()
            } else {
                with<DependentParallelDepartureRunway>()
            }
        }

        /** Empty runway constructor for loading of saves */
        constructor(): this(EMPTY_AIRPORT, 0, "EMPTY", 0f, 0f,
            0f, 4000, 0, 0, 0, RunwayLabel.BEFORE, "", "", false)

        companion object {
            /** De-serialises a [SerialisedRunway] and creates a new [Runway] object from it */
            fun fromSerialisedObject(parentAirport: Airport, serialisedRunway: SerialisedRunway): Runway {
                return Runway(
                    parentAirport, serialisedRunway.rwyId, serialisedRunway.rwyName,
                    serialisedRunway.x, serialisedRunway.y,
                    serialisedRunway.trueHdg,
                    serialisedRunway.lengthM,
                    serialisedRunway.displacedM, serialisedRunway.intersectionM,
                    serialisedRunway.altitude,
                    serialisedRunway.rwyLabelPos,
                    serialisedRunway.towerName, serialisedRunway.towerFreq
                ).apply {
                    if (serialisedRunway.landing) entity += ActiveLanding()
                    if (serialisedRunway.takeoff) entity += ActiveTakeoff()
                    if (serialisedRunway.closed) entity += RunwayClosed()
                    serialisedRunway.departureNOZ?.let { entity += DepartureNOZ(DepartureNormalOperatingZone.fromSerialisedObject(it)) }
                }
            }
        }

        /** Object that contains [Runway] data to be serialised by Kryo */
        class SerialisedRunway(val x: Float = 0f, val y: Float = 0f,
                               val altitude: Short = 0,
                               val trueHdg: Float = 0f,
                               val rwyId: Byte = -1, val rwyName: String = "", val lengthM: Short = 0,
                               val displacedM: Short = 0, val intersectionM: Short = 0,
                               val rwyLabelPos: Byte = 0,
                               val towerName: String = "", val towerFreq: String = "",
                               val landing: Boolean = false, val takeoff: Boolean = false, val closed: Boolean = false,
                               val departureNOZ: DepartureNormalOperatingZone.SerialisedDepartureNOZ? = null)

        /**
         * Returns a default empty [SerialisedRunway] due to missing component, and logs a message to the console
         * @param missingComponent the missing aircraft component
         */
        override fun emptySerialisableObject(missingComponent: String): SerialisedRunway {
            FileLog.info("Airport", "Empty serialised runway returned due to missing $missingComponent component")
            return SerialisedRunway()
        }

        /** Gets a [SerialisedRunway] from current state */
        override fun getSerialisableObject(): SerialisedRunway {
            entity.apply {
                val position = get(Position.mapper) ?: return emptySerialisableObject("Position")
                val altitude = get(Altitude.mapper) ?: return emptySerialisableObject("Altitude")
                val direction = get(Direction.mapper) ?: return emptySerialisableObject("Direction")
                val rwyInfo = get(RunwayInfo.mapper) ?: return emptySerialisableObject("RunwayInfo")
                val rwyLabel = get(RunwayLabel.mapper) ?: return emptySerialisableObject("RunwayLabel")
                val departureNOZ = get(DepartureNOZ.mapper)
                return SerialisedRunway(
                    position.x, position.y,
                    altitude.altitudeFt.toInt().toShort(),
                    convertWorldAndRenderDeg(direction.trackUnitVector.angleDeg()),
                    rwyInfo.rwyId, rwyInfo.rwyName, rwyInfo.lengthM,
                    rwyInfo.displacedThresholdM, rwyInfo.intersectionTakeoffM,
                    rwyLabel.positionToRunway,
                    rwyInfo.tower, rwyInfo.freq,
                    has(ActiveLanding.mapper), has(ActiveTakeoff.mapper), has(RunwayClosed.mapper),
                    departureNOZ?.depNoz?.getSerialisableObject()
                )
            }
        }

        /** Clears all relational components for runway dependencies */
        fun clearRunwayDependencies() {
            entity.remove<DependentOppositeRunway>()
            entity.remove<CrossingRunway>()
            entity.remove<DepartureDependency>()
        }
    }

    /** Creates a runway entity with the required components, and adds it to airport component's runway map */
    fun addRunway(id: Byte, name: String, posX: Float, posY: Float, trueHdg: Float,
                  runwayLengthM: Short, displacedThresholdM: Short, intersectionTakeoffM: Short, elevation: Short,
                  towerName: String, towerFreq: String, labelPos: Byte) {
        Runway(this, id, name, posX, posY, trueHdg, runwayLengthM, displacedThresholdM, intersectionTakeoffM,
            elevation, labelPos, towerName, towerFreq, false).also { rwy ->
            // Check if a runway with the same ID already exists; if it does, overwrite the base runway components
            val loadedRwy = entity[RunwayChildren.mapper]?.rwyMap?.get(id)?.let {
                for (i in 0 until rwy.entity.components.size()) {
                    val comp = rwy.entity.components[i]
                    if (comp !is BaseComponentJSONInterface ||
                        comp !is DoNotOverwriteSavedJSON ||
                        it.entity.getComponent(comp::class.java) == null) it.entity += comp
                }
                it.clearRunwayDependencies()
                it
            }
            if (loadedRwy == null) {
                rwy.clearRunwayDependencies()
                entity[RunwayChildren.mapper]?.rwyMap?.put(id, rwy)
            }
            else getEngine(false).removeEntityOnMainThread(rwy.entity, false)
        }
    }

    /**
     * Maps the given runway name to a certain ID - this method should be used only when loading runways from
     * internal game files, and not during save file loads since they may contain old runways with the same name
     * (runway added/renamed/etc.) leading to incorrect mappings
     */
    fun setRunwayMapping(rwyName: String, rwyId: Byte) {
        entity[RunwayChildren.mapper]?.updatedRwyMapping?.put(rwyName, rwyId)
    }

    /**
     * Gets the runway given its name
     *
     * Note that this will use the latest updated runway name to ID mapping, hence old runways with the same name as new
     * runways will not be returned
     * @param rwyName the name of the runway
     * @return the [Runway], or null if none found
     */
    fun getRunway(rwyName: String): Runway? {
        val rwyNameMap = entity[RunwayChildren.mapper]?.updatedRwyMapping ?: return null
        val rwyIdMap = entity[RunwayChildren.mapper]?.rwyMap ?: return null
        return rwyIdMap[rwyNameMap[rwyName]]
    }

    /** Maps all the runways to their opposite counterparts, and adds it as a relational component */
    fun assignOppositeRunways() {
        val runwayEntries = Entries(entity[RunwayChildren.mapper]?.rwyMap ?: return)
        runwayEntries.forEach { it.value.entity.apply {
            val rwyName = get(RunwayInfo.mapper)?.rwyName ?: return@forEach
            val oppRwyName = if (charArrayOf('L', 'C', 'R').contains(rwyName.last())) {
                var letter = rwyName.last()
                var number = rwyName.substring(0, rwyName.length - 1).toInt()
                letter = when (letter) {
                    'L' -> 'R'
                    'R' -> 'L'
                    else -> letter
                }
                number += 18
                if (number > 36) number -= 36
                "${if (number < 10) "0" else ""}$number$letter"
            } else {
                var number = rwyName.toInt() + 18
                if (number > 36) number -= 36
                "${if (number < 10) "0" else ""}$number"
            }
            this += OppositeRunway(getRunway(oppRwyName)?.entity ?: return@forEach)
        }}
    }

    /**
     * Activates a new runway configuration, replacing the current active one
     * @param newConfigId the ID of the new config to use
     * @param informPlayer whether to inform the player of the change in commsPane
     */
    fun activateRunwayConfig(newConfigId: Byte, informPlayer: Boolean = false) {
        val currId = entity[ActiveRunwayConfig.mapper]?.configId
        if (currId == newConfigId) return
        entity += ActiveRunwayConfig(newConfigId)
        entity[RunwayConfigurationChildren.mapper]?.rwyConfigs?.let { configMap ->
            Entries(configMap).forEach {
                val config = it.value
                if (config.id != newConfigId) config.setNTZVisibility(false)
                else {
                    val rwyMap = entity[RunwayChildren.mapper]?.rwyMap ?: return@forEach
                    Entries(rwyMap).forEach { rwyEntry ->
                        val rwyObj = rwyEntry.value
                        // Clear all current runways
                        rwyObj.entity.let { rwy ->
                            rwy.remove<ActiveLanding>()
                            rwy.remove<ActiveTakeoff>()
                            rwy[DepartureNOZ.mapper]?.depNoz?.entity?.plusAssign(DoNotRenderShape())
                            rwy += DoNotRenderLabel()
                            rwy.remove<DoNotRenderShape>()
                            rwy[DependentParallelDepartureRunway.mapper]?.depParRwys?.clear()
                        }
                    }

                    val arrRwyNames = Array(config.arrRwys.size) {""}
                    for (i in 0 until config.arrRwys.size) {
                        rwyMap[config.arrRwys[i]?.entity?.get(RunwayInfo.mapper)?.rwyId]?.entity?.let { rwy ->
                            rwy += ActiveLanding()
                            rwy.remove<DoNotRenderLabel>()
                            rwy[OppositeRunway.mapper]?.oppRwy?.add(DoNotRenderShape())
                            arrRwyNames[i] = rwy[RunwayInfo.mapper]?.rwyName ?: ""
                        }
                    }

                    val depRwyNames = Array(config.depRwys.size) {""}
                    val allDepNoz = GdxArray<DepartureNormalOperatingZone>()
                    for (i in 0 until config.depRwys.size) {
                        rwyMap[config.depRwys[i]?.entity?.get(RunwayInfo.mapper)?.rwyId]?.entity?.let {  rwy ->
                            rwy += ActiveTakeoff()
                            rwy[DepartureNOZ.mapper]?.depNoz?.let { allDepNoz.add(it) }
                            rwy.remove<DoNotRenderLabel>()
                            rwy[OppositeRunway.mapper]?.oppRwy?.add(DoNotRenderShape())
                            depRwyNames[i] = rwy[RunwayInfo.mapper]?.rwyName ?: ""
                        }
                    }
                    if (allDepNoz.size > 1) {
                        for (i in 0 until allDepNoz.size) {
                            allDepNoz[i].entity.remove<DoNotRenderShape>()
                        }
                    }

                    for (i in 0 until config.depParRwyPairs.size) {
                        val rwy1 = config.depParRwyPairs[i].first.entity
                        val rwy2 = config.depParRwyPairs[i].second.entity
                        rwy1[DependentParallelDepartureRunway.mapper]?.depParRwys?.add(rwy2)
                        rwy2[DependentParallelDepartureRunway.mapper]?.depParRwys?.add(rwy1)
                    }

                    config.setNTZVisibility(true)
                    val airportName = entity[AirportInfo.mapper]?.icaoCode ?: ""
                    if (informPlayer)
                        GAME.gameClientScreen?.uiPane?.commsPane?.addMessage("""
                        $airportName runway${if (arrRwyNames.size > 1) "s" else ""} ${arrRwyNames.joinToString(", ")} ${if (arrRwyNames.size > 1) "are" else "is"} now active for landings.
                        $airportName runway${if (depRwyNames.size > 1) "s" else ""} ${depRwyNames.joinToString(", ")} ${if (depRwyNames.size > 1) "are" else "is"} now active for takeoffs.
                    """.trimIndent(), CommsPane.ALERT)
                }
            }
        }
        CLIENT_SCREEN?.uiPane?.mainInfoObj?.setAirportRunwayConfigPaneState(entity)

        // Find NOZ group to render
        val availableApproaches = getAvailableApproaches(entity, null, includeClosedRunway = true)
        availableApproaches.removeIndex(0)
        val appNOZGroups = entity[ApproachNOZChildren.mapper]?.nozGroups
        if (appNOZGroups != null) {
            var foundGroupToRender = false
            for (i in 0 until appNOZGroups.size) {
                val nozGroup = appNOZGroups[i]
                var foundZoneWithNoApp = false

                if (!foundGroupToRender) for (j in 0 until nozGroup.appNoz.size) {
                    val appNames = nozGroup.appNoz[j].entity[ApproachList.mapper]
                    if (appNames == null) {
                        foundZoneWithNoApp = true
                        break
                    }
                    if (!availableApproaches.containsAny(GdxArray(appNames.approachList), false)) {
                        foundZoneWithNoApp = true
                        break
                    }
                }
                // We will not render this group if a first group has already been found
                else foundZoneWithNoApp = true

                for (j in 0 until nozGroup.appNoz.size) {
                    if (foundZoneWithNoApp) nozGroup.appNoz[j].entity += DoNotRenderShape()
                    else nozGroup.appNoz[j].entity.remove<DoNotRenderShape>()
                }
                if (!foundZoneWithNoApp) foundGroupToRender = true
            }
        }
    }

    /**
     * Updates closed state of runway with the provided ID, will also update the status of the opposite runway
     * @param rwyId the ID of the runway to update
     * @param closed whether the runway should be set to closed, else open
     */
    fun setRunwayClosed(rwyId: Byte, closed: Boolean) {
        val arptId = entity[AirportInfo.mapper]?.arptId ?: return
        entity[RunwayChildren.mapper]?.rwyMap?.get(rwyId)?.entity?.let { rwy ->
            val oppRwy = rwy[OppositeRunway.mapper]?.oppRwy ?: return
            if (closed) {
                rwy += RunwayClosed()
                oppRwy += RunwayClosed()
            } else {
                rwy.remove<RunwayClosed>()
                oppRwy.remove<RunwayClosed>()
            }
            GAME.gameServer?.sendRunwayClosedState(arptId, rwyId, closed)
        }
    }

    /**
     * Sets a runway configuration to be pending on client side; will update the UI as necessary
     * @param pendingConfigId the ID of the pending config, or null if cancelling a pending config change
     */
    fun pendingRunwayConfigClient(pendingConfigId: Byte?) {
        val currPendingId = entity[PendingRunwayConfig.mapper]?.pendingId
        if (pendingConfigId == null) entity.remove<PendingRunwayConfig>()
        else if (currPendingId != pendingConfigId) entity += PendingRunwayConfig(pendingConfigId, 300f)
        CLIENT_SCREEN?.uiPane?.mainInfoObj?.setAirportRunwayConfigPaneState(entity)
    }
}