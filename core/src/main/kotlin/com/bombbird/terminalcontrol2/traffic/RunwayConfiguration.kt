package com.bombbird.terminalcontrol2.traffic

import com.bombbird.terminalcontrol2.components.DoNotRender
import com.bombbird.terminalcontrol2.components.RunwayInfo
import com.bombbird.terminalcontrol2.components.RunwayWindComponents
import com.bombbird.terminalcontrol2.entities.Airport
import com.bombbird.terminalcontrol2.entities.NoTransgressionZone
import com.bombbird.terminalcontrol2.utilities.UsabilityFilter
import ktx.ashley.get
import ktx.ashley.plusAssign
import ktx.ashley.remove
import ktx.collections.GdxArray
import ktx.collections.GdxArrayMap
import ktx.collections.toGdxArray

/**
 * Class to store runway configuration data, including departure, arrival runways and No transgression zones (NTZs)
 *
 * A score will be calculated for each runway configuration depending on wind conditions
 * */
class RunwayConfiguration(val id: Byte, override val timeRestriction: Byte): Comparable<RunwayConfiguration>, UsabilityFilter {
    val depRwys: GdxArray<Airport.Runway> = GdxArray(5)
    val arrRwys: GdxArray<Airport.Runway> = GdxArray(5)
    val ntzs: GdxArray<NoTransgressionZone> = GdxArray(5)

    var rwyAvailabilityScore = 0
    private var windScore = 0f

    /**
     * Calculate scores for this configuration
     *
     * [rwyAvailabilityScore] is calculated by multiplying the available takeoff runways and available landing runways
     * in the configuration
     *
     * [windScore] is calculated by summing the headwind component of all runways in the configuration
     * */
    fun calculateScores() {
        windScore = 0f

        var depAvailable = 0
        for (i in 0 until depRwys.size) { depRwys[i]?.entity?.apply {
            val winds = get(RunwayWindComponents.mapper) ?: return@apply
            if (winds.tailwindKt < 10) depAvailable++
            windScore -= winds.tailwindKt
        }}

        var arrAvailable = 0
        for (i in 0 until arrRwys.size) { arrRwys[i]?.entity?.apply {
            val winds = get(RunwayWindComponents.mapper) ?: return@apply
            if (winds.tailwindKt < 10) arrAvailable++
            windScore -= winds.tailwindKt
        }}

        rwyAvailabilityScore = depAvailable * arrAvailable
    }

    /**
     * Sets whether to show or hide the NTZs for this configuration
     * @param show whether to show or hide the NTZs
     */
    fun setNTZVisibility(show: Boolean) {
        for (i in 0 until ntzs.size) {
            ntzs[i]?.entity?.apply {
                if (show) remove<DoNotRender>()
                else this += DoNotRender()
            }
        }
    }

    companion object {
        /**
         * De-serialises a [SerialisedRwyConfig] and creates a new [RunwayConfiguration] object from it
         * @param serialisedRwyConfig the object to de-serialise
         * @param rwyMap the runway ID map of the airport that this runway configuration belongs to
         * @return a newly created [RunwayConfiguration] object
         * */
        fun fromSerialisedObject(serialisedRwyConfig: SerialisedRwyConfig, rwyMap: GdxArrayMap<Byte, Airport.Runway>): RunwayConfiguration {
            return RunwayConfiguration(serialisedRwyConfig.id, serialisedRwyConfig.timeRestriction).apply {
                depRwys.addAll(serialisedRwyConfig.depRwys.map { rwyMap[it] }.filterNotNull().toGdxArray())
                arrRwys.addAll(serialisedRwyConfig.arrRwys.map { rwyMap[it] }.filterNotNull().toGdxArray())
                ntzs.addAll(serialisedRwyConfig.ntzs.map { NoTransgressionZone.fromSerialisedObject(it) }.toGdxArray())
            }
        }
    }

    /**
     * Compares this runway configuration to another configuration
     *
     * [rwyAvailabilityScore] will take priority before [windScore]
     * @return a negative number if it's less than [other], or a positive number
     * if it's greater than [other]
     */
    override fun compareTo(other: RunwayConfiguration): Int {
        if (rwyAvailabilityScore < other.rwyAvailabilityScore) return -1
        if (rwyAvailabilityScore > other.rwyAvailabilityScore) return 1
        return if (windScore <= other.windScore) -1 else 1
    }

    /** Gets a [SerialisedRwyConfig] from current state */
    fun getSerialisedObject(): SerialisedRwyConfig {
        return SerialisedRwyConfig(
            id,
            arrRwys.mapNotNull { it.entity[RunwayInfo.mapper]?.rwyId }.toByteArray(),
            depRwys.mapNotNull { it.entity[RunwayInfo.mapper]?.rwyId }.toByteArray(),
            ntzs.map { it.getSerialisableObject() }.toTypedArray(), timeRestriction)
    }

    /** Object that contains [RunwayConfiguration] data to be serialised by Kryo */
    class SerialisedRwyConfig(val id: Byte = 0,
                              val arrRwys: ByteArray = byteArrayOf(), val depRwys: ByteArray = byteArrayOf(),
                              val ntzs: Array<NoTransgressionZone.SerialisedNTZ> = arrayOf(),
                              val timeRestriction: Byte = UsabilityFilter.DAY_AND_NIGHT)
}