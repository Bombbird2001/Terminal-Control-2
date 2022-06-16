package com.bombbird.terminalcontrol2.traffic

import com.bombbird.terminalcontrol2.components.RunwayWindComponents
import com.bombbird.terminalcontrol2.entities.Airport
import com.bombbird.terminalcontrol2.entities.NoTransgressionZone
import ktx.ashley.get
import ktx.collections.GdxArray

/**
 * Class to store runway configuration data, including departure, arrival runways and No transgression zones (NTZs)
 *
 * A score will be calculated for each runway configuration depending on wind conditions
 * */
class RunwayConfiguration: Comparable<RunwayConfiguration> {
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
}