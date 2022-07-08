package com.bombbird.terminalcontrol2.entities

/** Helper interface to enforce methods required for serialising of entities */
interface SerialisableEntity<T> {
    /**
     * Returns an empty serialisable entity by default, due to a missing component
     * @param missingComponent the missing component, which can be logged to help with debugging
     * */
    fun emptySerialisableObject(missingComponent: String): T

    /** Gets a [SerialisableEntity] from current state */
    fun getSerialisableObject(): T
}