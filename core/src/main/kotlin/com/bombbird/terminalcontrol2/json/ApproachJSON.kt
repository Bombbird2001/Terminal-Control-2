package com.bombbird.terminalcontrol2.json

import com.bombbird.terminalcontrol2.navigation.Approach
import com.squareup.moshi.FromJson
import com.squareup.moshi.JsonClass
import com.squareup.moshi.ToJson
import ktx.ashley.plusAssign

/** Approach JSON data class which handles the serialization and de-serialization of approach entities specifically */
@JsonClass(generateAdapter = true)
data class ApproachJSON(val entity: BaseEntityJson)

/** Adapter object for serialization between [Approach] and [ApproachJSON] */
object ApproachAdapter {
    @ToJson
    fun toJson(app: Approach): ApproachJSON {
        return ApproachJSON(BaseEntityJson(app.entity.getComponentArrayList()))
    }

    @FromJson
    fun fromJson(appJSON: ApproachJSON): Approach {
        return Approach().apply {
            appJSON.entity.components.forEach { entity += it }
        }
    }
}
