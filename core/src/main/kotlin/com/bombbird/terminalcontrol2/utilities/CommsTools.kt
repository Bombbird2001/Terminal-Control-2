package com.bombbird.terminalcontrol2.utilities

import ktx.collections.GdxArray

/** Class for abstracting a collection of [CommsToken] to be made into a text or TTS sentence */
class SentenceToken {
    companion object {
        val SPACE_TOKEN = LiteralToken(" ")
        val COMMA_TOKEN = LiteralToken(",")
    }

    private val tokenList = GdxArray<CommsToken>()

    /** Converts the sentence token to a text sentence */
    fun toTextSentence(): String {
        val builder = StringBuilder()
        for (token in tokenList) {
            if (!tokenList.isEmpty) builder.append(' ')
            builder.append(token.toString())
        }
        return removeExtraCharacters(builder.toString())
    }

    /** Converts the sentence token to a TTS sentence */
    fun toTTSSentence(): String {
        val builder = StringBuilder()
        for (token in tokenList) {
            if (!tokenList.isEmpty) builder.append(' ')
            builder.append(token.toTTSString())
        }
        return removeExtraCharacters(builder.toString())
    }

    /** Adds a token to the sentence */
    fun addToken(token: CommsToken): SentenceToken {
        tokenList.add(token)
        return this
    }

    /** Adds multiple tokens to the sentence */
    fun addTokens(vararg tokens: CommsToken): SentenceToken {
        tokenList.addAll(*tokens)
        return this
    }

    /** Adds a space to the sentence */
    fun addSpace(): SentenceToken {
        return addToken(SPACE_TOKEN)
    }

    /** Adds a comma and space to the sentence */
    fun addComma(): SentenceToken {
        return addToken(COMMA_TOKEN)
    }
}

/** Token to be used in comms pane as well as converting comms pane text to speakable text for text-to-speech */
abstract class CommsToken {
    /** Returns the text representation of this token */
    abstract override fun toString(): String

    /** Returns the text-to-speech representation of this token */
    abstract fun toTTSString(): String
}

/** Contains a literal string to be spoken directly - no difference between text and TTS text */
class LiteralToken(private val literal: String): CommsToken() {
    override fun toString(): String {
        return literal
    }

    override fun toTTSString(): String {
        return literal
    }
}

/** Contains a callsign to be spoken - converts the callsign to the phonetic callsign */
class CallsignToken(private val callsign: String): CommsToken() {
    override fun toString(): String {
        return callsign
    }

    override fun toTTSString(): String {
        // TODO Add spoken callsign
        return callsign
    }
}

/** Contains a SID/STAR name to be spoken - converts the SID/STAR name to its spoken name */
class SidStarToken(private val sidStarName: String): CommsToken() {
    override fun toString(): String {
        return sidStarName
    }

    override fun toTTSString(): String {
        // TODO Add spoken SID/STAR name
        return sidStarName
    }
}

/** Contains a waypoint to be spoken - converts the waypoint name to its spoken name */
class WaypointToken(private val waypointName: String): CommsToken() {
    override fun toString(): String {
        return waypointName
    }

    override fun toTTSString(): String {
        // TODO Add spoken waypoint name
        return waypointName
    }
}

/** Contains a radio frequency to be spoken  */
class FrequencyToken(private val frequency: String): CommsToken() {
    override fun toString(): String {
        return frequency
    }

    override fun toTTSString(): String {
        // TODO Add spoken frequency
        return frequency
    }
}