package com.bombbird.terminalcontrol2.systems

import com.badlogic.ashley.core.Engine
import com.badlogic.ashley.core.Entity
import com.badlogic.ashley.core.EntityListener
import com.badlogic.ashley.core.Family
import com.badlogic.ashley.utils.ImmutableArray
import com.bombbird.terminalcontrol2.global.getEngine
import com.bombbird.terminalcontrol2.utilities.FileLog
import ktx.collections.GdxArray

/**
 * Convenience class that wraps around a family, and adds a listener to the engine to check for entities added/removed
 * from the family
 */
class FamilyWithListener private constructor(private val family: Family, private val onClient: Boolean) {
    companion object {
        private val allServerFamilies: GdxArray<FamilyWithListener> = GdxArray()
        private val allClientFamilies: GdxArray<FamilyWithListener> = GdxArray()

        /** Creates a family that should only exist on the server */
        fun newServerFamilyWithListener(family: Family): FamilyWithListener {
            val newFamily = FamilyWithListener(family, false)
            allServerFamilies.add(newFamily)
            return newFamily
        }

        /** Creates a family that should only exist on the client */
        fun newClientFamilyWithListener(family: Family): FamilyWithListener {
            val newFamily = FamilyWithListener(family, true)
            allClientFamilies.add(newFamily)
            return newFamily
        }

        /** Adds engine listeners belonging to all families on the server */
        fun addAllServerFamilyEntityListeners() {
            for (i in 0 until allServerFamilies.size) {
                allServerFamilies[i].addEngineListener()
            }
            FileLog.info("FamilyWithListener", "Added all server family entity listeners")
        }

        /** Adds engine listeners belonging to all families on the client */
        fun addAllClientFamilyEntityListeners() {
            for (i in 0 until allClientFamilies.size) {
                allClientFamilies[i].addEngineListener()
            }
            FileLog.info("FamilyWithListener", "Added all client family entity listeners")
        }

        /**
         * Clears engine listeners belonging to all families on the server
         * @param engine engine to remove listeners from
         */
        fun clearAllServerFamilyEntityListeners(engine: Engine) {
            for (i in 0 until allServerFamilies.size) {
                allServerFamilies[i].removeEngineListener(engine)
            }
            allServerFamilies.clear()
            FileLog.info("FamilyWithListener", "Cleared all server family entity listeners")
        }

        /**
         * Clears engine listeners belonging to all families on the client
         * @param engine engine to remove listeners from
         */
        fun clearAllClientFamilyEntityListeners(engine: Engine) {
            for (i in 0 until allClientFamilies.size) {
                allClientFamilies[i].removeEngineListener(engine)
            }
            allClientFamilies.clear()
            FileLog.info("FamilyWithListener", "Cleared all client family entity listeners")
        }
    }

    private var entityListener: EntityListener? = null
    private var familyEntities: ImmutableArray<Entity> = ImmutableArray(GdxArray())

    /** Adds this family's listener to the appropriate engine */
    private fun addEngineListener() {
        if (entityListener != null) {
            FileLog.warn("FamilyWithListener", "Entity listener already exists for family")
            return
        }
        val engine = getEngine(onClient)
        familyEntities = engine.getEntitiesFor(family)
        val newEntityListener = object : EntityListener {
            override fun entityAdded(entity: Entity?) {
                familyEntities = engine.getEntitiesFor(family)
            }

            override fun entityRemoved(entity: Entity?) {
                familyEntities = engine.getEntitiesFor(family)
            }
        }
        entityListener = newEntityListener
        engine.addEntityListener(family, newEntityListener)
    }

    /**
     * Removes this family's listener from the input engine
     * @param engine the engine to remove the listener from
     */
    private fun removeEngineListener(engine: Engine) {
        if (entityListener == null) {
            FileLog.warn("FamilyWithListener", "Entity listener does not exist for family")
            return
        }
        engine.removeEntityListener(entityListener)
        entityListener = null
        familyEntities = ImmutableArray(GdxArray())
    }

    /** Gets the entities in the family */
    fun getEntities(): ImmutableArray<Entity> {
        return familyEntities
    }
}