package com.bombbird.terminalcontrol2.entities

import com.bombbird.terminalcontrol2.components.CommandTarget
import com.bombbird.terminalcontrol2.components.PublishedHoldInfo
import com.bombbird.terminalcontrol2.global.Constants
import ktx.ashley.entity
import ktx.ashley.get
import ktx.ashley.with

/** Hold class that creates a published hold entity with the required components on instantiation */
class PublishedHold(id: Short, maxAlt: Int?, minAlt: Int?,
                    maxSpdLower: Short, maxSpdHigher: Short, inboundHdg: Short, legDist: Byte,
                    dir: Byte, onClient: Boolean = true) {
    val entity = Constants.getEngine(onClient).entity {
        with<PublishedHoldInfo> {
            wptId = id
            maxAltFt = maxAlt
            minAltFt = minAlt
            maxSpdKtLower = maxSpdLower
            maxSpdKtHigher = maxSpdHigher
            inboundHdgDeg = inboundHdg
            legDistNm = legDist
            turnDir = dir
        }
    }

    companion object {
        /** De-serialises a [SerialisedPublishedHold] and creates a new [PublishedHold] object from it */
        fun fromSerialisedObject(serialisedHold: SerialisedPublishedHold): PublishedHold {
            return PublishedHold(
                serialisedHold.id, serialisedHold.maxAlt, serialisedHold.minAlt,
                serialisedHold.maxSpdLower, serialisedHold.maxSpdHigher, serialisedHold.inboundHdg, serialisedHold.legDist,
                serialisedHold.dir
            )
        }
    }

    /** Object that contains [PublishedHold] data to be serialised by Kryo */
    class SerialisedPublishedHold(val id: Short = 0, val maxAlt: Int? = null, val minAlt: Int? = null,
                                  val maxSpdLower: Short = 230, val maxSpdHigher: Short = 240, val inboundHdg: Short = 360, val legDist: Byte = 5,
                                  val dir: Byte = CommandTarget.TURN_RIGHT
    )

    /** Gets a [SerialisedPublishedHold] from current state */
    fun getSerialisableObject(): SerialisedPublishedHold {
        entity.apply {
            val holdInfo = get(PublishedHoldInfo.mapper) ?: return SerialisedPublishedHold()
            return SerialisedPublishedHold(
                holdInfo.wptId, holdInfo.maxAltFt, holdInfo.minAltFt,
                holdInfo.maxSpdKtLower, holdInfo.maxSpdKtHigher, holdInfo.inboundHdgDeg, holdInfo.legDistNm,
                holdInfo.turnDir
            )
        }
    }
}