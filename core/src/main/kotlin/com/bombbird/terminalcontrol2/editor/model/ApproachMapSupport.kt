package com.bombbird.terminalcontrol2.editor.model

/** Reserved transition name; must exist with empty route tokens on every approach. */
const val DEFAULT_APPROACH_VECTOR_TRANSITION_NAME = "vectors"

/** Ensures the reserved `vectors` transition exists (default empty). It may hold route tokens; it must not be removed. */
fun ApproachDefinition.ensureDefaultVectorTransition() {
    if (!transitions.containsKey(DEFAULT_APPROACH_VECTOR_TRANSITION_NAME)) {
        transitions[DEFAULT_APPROACH_VECTOR_TRANSITION_NAME] = emptyList()
    }
}
