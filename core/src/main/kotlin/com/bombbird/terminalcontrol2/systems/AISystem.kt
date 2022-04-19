package com.bombbird.terminalcontrol2.systems

import com.badlogic.ashley.core.EntitySystem
import com.bombbird.terminalcontrol2.components.CommandTarget

/** Main AI system, which handles aircraft flight controls, implementing behaviour for various basic and advanced flight modes
 *
 * Flight modes will directly alter [CommandTarget], which will then interact with PhysicsSystem to execute the required behaviour
 * */
class AISystem: EntitySystem() {
    /** Main update function */
    override fun update(deltaTime: Float) {

    }
}