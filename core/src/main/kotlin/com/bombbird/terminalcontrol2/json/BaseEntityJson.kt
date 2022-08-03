package com.bombbird.terminalcontrol2.json

import com.badlogic.ashley.core.Component
import com.badlogic.ashley.core.Entity
import com.squareup.moshi.JsonClass

/** Base entity data class that stores components of an entity for JSON serialization */
@JsonClass(generateAdapter = true)
data class BaseEntityJson(val components: ArrayList<Component>)

/** Gets the components of an entity as an [ArrayList] */
fun Entity.getComponentArrayList(): ArrayList<Component> {
    val array = ArrayList<Component>()
    for (i in 0 until components.size()) array.add(components[i])
    return array
}

/** Gets a component that is an instance of the input type from the array of components, or null if none is found */
inline fun <reified T> ArrayList<Component>.getComponent(): T? {
    forEach {
        if (it is T) return it
    }
    return null
}
