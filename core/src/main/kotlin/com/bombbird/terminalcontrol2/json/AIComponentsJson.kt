package com.bombbird.terminalcontrol2.json

import com.badlogic.ashley.core.Entity
import com.bombbird.terminalcontrol2.components.*
import com.squareup.moshi.FromJson
import com.squareup.moshi.JsonClass
import com.squareup.moshi.ToJson

/** Data class for storing takeoff roll information for JSON serialization */
@JsonClass(generateAdapter = true)
data class TakeoffRollJSON(val targetAccMps2: Float, val rwy: RunwayRefJSON)

/** Adapter object for serialization between [TakeoffRoll] and [TakeoffRollJSON] */
object TakeoffRollAdapter {
    @ToJson
    fun toTakeoffRollJson(takeoffRoll: TakeoffRoll): TakeoffRollJSON {
        return TakeoffRollJSON(takeoffRoll.targetAccMps2, toRunwayRefJSON(takeoffRoll.rwy))
    }

    @FromJson
    fun fromTakeoffRollJSON(takeoffRollJSON: TakeoffRollJSON): TakeoffRoll {
        return TakeoffRoll(takeoffRollJSON.targetAccMps2).apply {
            delayedEntityRetrieval.add { rwy = takeoffRollJSON.rwy.getRunwayEntity() }
        }
    }
}

/** Data class for storing landing roll information for JSON serialization */
@JsonClass(generateAdapter = true)
data class LandingRollJSON(val rwy: RunwayRefJSON)

/** Adapter object for serialization between [LandingRoll] and [LandingRollJSON] */
object LandingRollAdapter {
    @ToJson
    fun toLandingRollJSON(landingRoll: LandingRoll): LandingRollJSON {
        return LandingRollJSON(toRunwayRefJSON(landingRoll.rwy))
    }

    @FromJson
    fun fromLandingRollJSON(landingRollJSON: LandingRollJSON): LandingRoll {
        return LandingRoll().apply {
            delayedEntityRetrieval.add { rwy = landingRollJSON.rwy.getRunwayEntity() }
        }
    }
}

/** Data class for storing visual approach captured information for JSON serialization */
@JsonClass(generateAdapter = true)
data class VisualCapturedJSON(val visApp: ApproachRefJSON, val parentApp: ApproachRefJSON)

/** Adapter object for serialization between [VisualCaptured] and [VisualCapturedJSON] */
object VisualCapturedAdapter {
    @ToJson
    fun toVisualCapturedJSON(visualCaptured: VisualCaptured): VisualCapturedJSON {
        return VisualCapturedJSON(toApproachRefJSON(visualCaptured.visApp), toApproachRefJSON(visualCaptured.parentApp))
    }

    @FromJson
    fun fromVisualCapturedJSON(visualCapturedJSON: VisualCapturedJSON): VisualCaptured {
        return VisualCaptured().apply {
            visualCapturedJSON.visApp.delayedApproachEntityRetrieval(this)
            visualCapturedJSON.parentApp.delayedApproachEntityRetrieval(this)
        }
    }
}

/** Data class for storing localizer approach armed information for JSON serialization */
@JsonClass(generateAdapter = true)
data class LocalizerArmedJSON(val locApp: ApproachRefJSON)

/** Adapter object for serialization between [LocalizerArmed] and [LocalizerArmedJSON] */
object LocalizerArmedAdapter {
    @ToJson
    fun toLocalizerArmedJSON(locArmed: LocalizerArmed): LocalizerArmedJSON {
        return LocalizerArmedJSON(toApproachRefJSON(locArmed.locApp))
    }

    @FromJson
    fun fromLocalizerArmedJSON(locArmedJSON: LocalizerArmedJSON): LocalizerArmed {
        return LocalizerArmed().apply { locArmedJSON.locApp.delayedApproachEntityRetrieval(this) }
    }
}

/** Data class for storing localizer approach captured information for JSON serialization */
@JsonClass(generateAdapter = true)
data class LocalizerCapturedJSON(val locApp: ApproachRefJSON)

/** Adapter object for serialization between [LocalizerCaptured] and [LocalizerCapturedJSON] */
object LocalizerCapturedAdapter {
    @ToJson
    fun toLocalizerCapturedJSON(locCaptured: LocalizerCaptured): LocalizerCapturedJSON {
        return LocalizerCapturedJSON(toApproachRefJSON(locCaptured.locApp))
    }

    @FromJson
    fun fromLocalizerCapturedJSON(locCapturedJSON: LocalizerCapturedJSON): LocalizerCaptured {
        return LocalizerCaptured().apply { locCapturedJSON.locApp.delayedApproachEntityRetrieval(this) }
    }
}

/** Data class for storing glide slope approach armed information for JSON serialization */
@JsonClass(generateAdapter = true)
data class GlideSlopeArmedJSON(val gsApp: ApproachRefJSON)

/** Adapter object for serialization between [GlideSlopeArmed] and [GlideSlopeArmedJSON] */
object GlideSlopeArmedAdapter {
    @ToJson
    fun toGlideSlopeArmedJSON(gsArmed: GlideSlopeArmed): GlideSlopeArmedJSON {
        return GlideSlopeArmedJSON(toApproachRefJSON(gsArmed.gsApp))
    }

    @FromJson
    fun fromGlideSlopeArmedJSON(gsArmedJSON: GlideSlopeArmedJSON): GlideSlopeArmed {
        return GlideSlopeArmed().apply { gsArmedJSON.gsApp.delayedApproachEntityRetrieval(this) }
    }
}

/** Data class for storing glide slope approach captured information for JSON serialization */
@JsonClass(generateAdapter = true)
data class GlideSlopeCapturedJSON(val gsApp: ApproachRefJSON)

/** Adapter object for serialization between [LocalizerCaptured] and [LocalizerCapturedJSON] */
object GlideSlopeCapturedAdapter {
    @ToJson
    fun toGlideSlopeCapturedJSON(gsCap: GlideSlopeCaptured): GlideSlopeCapturedJSON {
        return GlideSlopeCapturedJSON(toApproachRefJSON(gsCap.gsApp))
    }

    @FromJson
    fun fromGlideSlopeCapturedJSON(gsCapJSON: GlideSlopeCapturedJSON): GlideSlopeCaptured {
        return GlideSlopeCaptured().apply { gsCapJSON.gsApp.delayedApproachEntityRetrieval(this) }
    }
}

/** Data class for storing step down approach information for JSON serialization */
@JsonClass(generateAdapter = true)
data class StepDownApproachJSON(val stepDownApp: ApproachRefJSON)

/** Adapter object for serialization between [StepDownApproach] and [StepDownApproachJSON] */
object StepDownApproachAdapter {
    @ToJson
    fun toStepDownApproachJSON(stepDown: StepDownApproach): StepDownApproachJSON {
        return StepDownApproachJSON(toApproachRefJSON(stepDown.stepDownApp))
    }

    @FromJson
    fun fromStepDownApproachJSON(stepDownJSON: StepDownApproachJSON): StepDownApproach {
        return StepDownApproach().apply { stepDownJSON.stepDownApp.delayedApproachEntityRetrieval(this) }
    }
}

/** Data class for storing circling approach information for JSON serialization */
@JsonClass(generateAdapter = true)
data class CirclingApproachJSON(val circlingApp: ApproachRefJSON, val breakoutAlt: Int, val phase: Byte,
                                val phase1Timer: Float, val phase3Timer: Float)

/** Adapter object for serialization between [CirclingApproach] and [CirclingApproachJSON] */
object CirclingApproachAdapter {
    @ToJson
    fun toCirclingApproachJSON(circling: CirclingApproach): CirclingApproachJSON {
        return CirclingApproachJSON(toApproachRefJSON(circling.circlingApp), circling.breakoutAlt, circling.phase,
            circling.phase1Timer, circling.phase3Timer)
    }

    @FromJson
    fun fromCirclingApproachJSON(circlingJSON: CirclingApproachJSON): CirclingApproach {
        return CirclingApproach(Entity(), circlingJSON.breakoutAlt, circlingJSON.phase,
            circlingJSON.phase1Timer, circlingJSON.phase3Timer).apply { circlingJSON.circlingApp.delayedApproachEntityRetrieval(this) }
    }
}
