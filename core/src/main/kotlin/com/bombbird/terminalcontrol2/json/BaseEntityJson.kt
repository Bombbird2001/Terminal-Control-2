package com.bombbird.terminalcontrol2.json

import com.badlogic.ashley.core.Entity
import com.squareup.moshi.JsonClass

/** Base entity data class that stores components of an entity for JSON serialization */
@JsonClass(generateAdapter = true)
data class BaseEntityJson(val components: List<BaseComponentJSONInterface>)

/** Gets the serializable components of an entity as an [ArrayList] */
fun Entity.getComponentArrayList(): ArrayList<BaseComponentJSONInterface> {
    val array = ArrayList<BaseComponentJSONInterface>()
    for (i in 0 until components.size()) components[i]?.let { if (it is BaseComponentJSONInterface) array.add(it) }
    return array
}

/** Gets a component that is an instance of the input type from the array of components, or null if none is found */
inline fun <reified T> List<BaseComponentJSONInterface>.getComponent(): T? {
    forEach {
        if (it is T) return it
    }
    return null
}
