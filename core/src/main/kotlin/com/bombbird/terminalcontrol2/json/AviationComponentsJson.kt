package com.bombbird.terminalcontrol2.json

import com.badlogic.gdx.math.CumulativeDistribution
import com.bombbird.terminalcontrol2.components.RandomMetarInfo
import com.squareup.moshi.FromJson
import com.squareup.moshi.JsonClass
import com.squareup.moshi.ToJson

/** Data class storing data for each value in the distribution for JSON serialization */
@JsonClass(generateAdapter = true)
data class DistributionValueJSON(val value: Short, val interval: Float)

/** Class storing data for the coefficients and distributions used when generating random weather for JSON serialization */
@JsonClass(generateAdapter = true)
class RandomMetarDistributionJSON(
    val windDirDist: ArrayList<DistributionValueJSON>,
    val windSpdDist: ArrayList<DistributionValueJSON>,
    val visibilityDist: ArrayList<DistributionValueJSON>,
    val ceilingDist: ArrayList<DistributionValueJSON>,
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
    private fun getDistribution(distJson: ArrayList<DistributionValueJSON>): CumulativeDistribution<Short> {
        val dist = CumulativeDistribution<Short>()
        distJson.forEach { dist.add(it.value, it.interval) }
        return dist
    }
}
