package com.bombbird.terminalcontrol2.json

import com.badlogic.gdx.math.CumulativeDistribution
import com.badlogic.gdx.utils.Queue
import com.bombbird.terminalcontrol2.components.*
import com.bombbird.terminalcontrol2.global.GAME
import com.squareup.moshi.FromJson
import com.squareup.moshi.JsonClass
import com.squareup.moshi.ToJson
import ktx.ashley.get

/** Data class storing runway information */
@JsonClass(generateAdapter = true)
data class RunwayInfoJSON(val rwyId: Byte, val rwyName: String, val lengthM: Short, val displacedThresholdM: Short,
                          val intersectionTakeoffM: Short, val tower: String, val freq: String, val airportId: Byte)

/** Adapter object for serialization between [RunwayInfo] and [RunwayInfoJSON] */
object RunwayInfoAdapter {
    @ToJson
    fun toJson(runwayInfo: RunwayInfo): RunwayInfoJSON {
        return RunwayInfoJSON(runwayInfo.rwyId, runwayInfo.rwyName, runwayInfo.lengthM, runwayInfo.displacedThresholdM,
            runwayInfo.intersectionTakeoffM, runwayInfo.tower, runwayInfo.freq, runwayInfo.airport.entity[AirportInfo.mapper]?.arptId ?: -1)
    }

    @FromJson
    fun fromJson(runwayInfoJSON: RunwayInfoJSON): RunwayInfo {
        return RunwayInfo(runwayInfoJSON.rwyId, runwayInfoJSON.rwyName, runwayInfoJSON.lengthM, runwayInfoJSON.displacedThresholdM,
            runwayInfoJSON.intersectionTakeoffM, runwayInfoJSON.tower, runwayInfoJSON.freq).apply {
            delayedEntityRetrieval.add { GAME.gameServer?.airports?.get(runwayInfoJSON.airportId)?.let { airport = it } }
        }
    }
}

/** Data class storing data for each value in the distribution for JSON serialization */
@JsonClass(generateAdapter = true)
data class DistributionValueJSON(val value: Short, val interval: Float)

/** Data class storing data for the coefficients and distributions used when generating random weather for JSON serialization */
@JsonClass(generateAdapter = true)
data class RandomMetarDistributionJSON(
    val windDirDist: List<DistributionValueJSON>,
    val windSpdDist: List<DistributionValueJSON>,
    val visibilityDist: List<DistributionValueJSON>,
    val ceilingDist: List<DistributionValueJSON>,
    val windshearCoeff1: Float?, val windshearCoeff2: Float?
)

/** Adapter object for serialization between [RandomMetarInfo] and [RandomMetarDistributionJSON] */
object RandomMetarDistributionAdapter {
    @ToJson
    fun toJson(metar: RandomMetarInfo): RandomMetarDistributionJSON {
        return RandomMetarDistributionJSON(
            getDistributionArray(metar.windDirDist),
            getDistributionArray(metar.windSpdDist),
            getDistributionArray(metar.visibilityDist),
            getDistributionArray(metar.ceilingDist),
            metar.windshearLogCoefficients?.first, metar.windshearLogCoefficients?.second
        )
    }

    /**
     * Gets the representation of the input distribution in array form
     * @param dist the cumulative distribution to turn into an array
     * @return an arrayList of [DistributionValueJSON] containing interval data for each value in the distribution
     */
    private fun getDistributionArray(dist: CumulativeDistribution<Short>): ArrayList<DistributionValueJSON> {
        val array = ArrayList<DistributionValueJSON>()
        for (i in 0 until dist.size()) {
            val value = dist.getValue(i)
            val interval = dist.getInterval(i)
            array.add(DistributionValueJSON(value, interval))
        }
        return array
    }

    @FromJson
    fun fromJson(metarJson: RandomMetarDistributionJSON): RandomMetarInfo {
        return RandomMetarInfo(
            getDistribution(metarJson.windDirDist),
            getDistribution(metarJson.windSpdDist),
            getDistribution(metarJson.visibilityDist),
            getDistribution(metarJson.ceilingDist),
            if (metarJson.windshearCoeff1 != null && metarJson.windshearCoeff2 != null)
                Pair(metarJson.windshearCoeff1, metarJson.windshearCoeff2)
            else null
        )
    }

    /**
     * Gets the cumulative distribution for the input arrayList of distribution values
     * @param distJson the arrayList of values to turn into a distribution
     * @return a [CumulativeDistribution] containing the distribution values in the arrayList
     */
    private fun getDistribution(distJson: List<DistributionValueJSON>): CumulativeDistribution<Short> {
        val dist = CumulativeDistribution<Short>()
        distJson.forEach { dist.add(it.value, it.interval) }
        return dist
    }
}

/** Data class storing data for each trail dot position for the aircraft */
@JsonClass(generateAdapter = true)
data class TrailInfoJSON(val trails: List<Position>)

/** Adapter object for serialization between [TrailInfo] and [TrailInfoJSON] */
object TrailInfoAdapter {
    @ToJson
    fun toJson(trailInfo: TrailInfo): TrailInfoJSON {
        val trails = ArrayList<Position>()
        for (trail in Queue.QueueIterator(trailInfo.positions)) {
            trails.add(trail)
        }
        return TrailInfoJSON(trails)
    }

    @FromJson
    fun fromJson(trailInfoJSON: TrailInfoJSON): TrailInfo {
        val trailInfo = TrailInfo()
        for (trail in trailInfoJSON.trails) {
            trailInfo.positions.addLast(trail)
        }
        return trailInfo
    }
}
