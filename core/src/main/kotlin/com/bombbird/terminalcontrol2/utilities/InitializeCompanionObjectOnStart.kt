package com.bombbird.terminalcontrol2.utilities

import kotlin.reflect.KClass

/**
 * Convenience object for classes with companion objects that need to be initialised on start to delegate to
 */
object InitializeCompanionObjectOnStart {
    fun initialise(initialisedClass: KClass<*>) {
        FileLog.debug("InitializeCompanionObjectOnStart", "${initialisedClass.qualifiedName}")
    }
}