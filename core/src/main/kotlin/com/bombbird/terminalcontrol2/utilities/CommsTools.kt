package com.bombbird.terminalcontrol2.utilities

import com.bombbird.terminalcontrol2.global.TRANS_LVL
import ktx.assets.toInternalFile
import ktx.collections.GdxArray
import kotlin.math.roundToInt

private const val CALLSIGN_PATH = "Data/icao.callsign"

private val NATO_ALPHABET_PHONETIC = hashMapOf(
    'A' to "Alpha",
    'B' to "Bravo",
    'C' to "Charlie",
    'D' to "Delta",
    'E' to "Echo",
    'F' to "Foxtrot",
    'G' to "Golf",
    'H' to "Hotel",
    'I' to "India",
    'J' to "Juliet",
    'K' to "Kilo",
    'L' to "Lima",
    'M' to "Mike",
    'N' to "November",
    'O' to "Oscar",
    'P' to "Papa",
    'Q' to "Quebec",
    'R' to "Romeo",
    'S' to "Sierra",
    'T' to "Tango",
    'U' to "Uniform",
    'V' to "Victor",
    'W' to "Whiskey",
    'X' to "X-ray",
    'Y' to "Yankee",
    'Z' to "Zulu",
    '1' to "One",
    '2' to "Two",
    '3' to "Tree",
    '4' to "Four",
    '5' to "Five",
    '6' to "Six",
    '7' to "Seven",
    '8' to "Eight",
    '9' to "Niner",
    '0' to "Zero",
    '.' to "Decimal"
)

private val CALLSIGN_TO_TTS = hashMapOf<String, String>()

/** Loads the callsign pronunciation file */
fun loadCallsigns() {
    CALLSIGN_PATH.toInternalFile().readString().toLines().toTypedArray().apply {
        for (line in this) {
            val split = line.split(" ")
            CALLSIGN_TO_TTS[split[0]] = split.subList(1, split.size).joinToString(" ")
        }
    }
}

/** Class for abstracting a collection of [CommsToken] to be made into a text or TTS sentence */
class TokenSentence {
    companion object {
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
        return removeExtraCharacters(builder.toString()).lowercase()
    }

    /** Adds a token to the sentence */
    fun addToken(token: CommsToken): TokenSentence {
        tokenList.add(token)
        return this
    }

    /** Adds multiple tokens to the sentence */
    fun addTokens(vararg tokens: CommsToken): TokenSentence {
        tokenList.addAll(*tokens)
        return this
    }

    /** Adds a comma and space to the sentence */
    fun addComma(): TokenSentence {
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
class CallsignToken(private val callsign: String, private val wakeString: String, private val emergency: Boolean): CommsToken() {
    override fun toString(): String {
        return "$callsign $wakeString${if (emergency) " mayday" else ""}"
    }

    override fun toTTSString(): String {
        // Variation 1: 3 letter airline prefix, followed by 1 number, then 0 to 3 alphanumeric characters
        if (callsign.length >= 4 && callsign[0].isLetter() && callsign[1].isLetter() && callsign[2].isLetter() && callsign[3].isDigit()) {
            val airlineCallsign = callsign.substring(0, 3)
            val flightNo = callsign.substring(3)
            val phoneticCallsign = CALLSIGN_TO_TTS[airlineCallsign]
            if (phoneticCallsign != null) {
                return "$phoneticCallsign ${splitCharactersToNatoPhonetic(flightNo)} $wakeString${if (emergency) " mayday" else ""}"
            } else {
                FileLog.info("CommsTools", "No phonetic callsign for $airlineCallsign")
            }
        }

        // Variation 2: Any other format - split into individual characters to convert to NATO phonetic
        return "${splitCharactersToNatoPhonetic(callsign)} $wakeString${if (emergency) " mayday" else ""}"
    }
}

/** Contains a [Pronounceable] name to be spoken */
class PronounceableToken(private val text: String, private val pronounceable: Pronounceable): CommsToken() {
    override fun toString(): String {
        return text
    }

    override fun toTTSString(): String {
        return pronounceable.pronunciation
    }
}

/** Contains a waypoint to be spoken - converts the waypoint name to its spoken name */
class WaypointToken(private val waypointName: String): CommsToken() {
    override fun toString(): String {
        return waypointName
    }

    override fun toTTSString(): String {
        // Convert to individual NATO alphabets for waypoints with 3 or fewer letters
        if (waypointName.length <= 3) return splitCharactersToNatoPhonetic(waypointName)
        return waypointName.lowercase()
    }
}

/** Contains a heading to be spoken - converts the heading to its NATO digits, padding with 0 if needed */
class HeadingToken(private val heading: Short): CommsToken() {
    override fun toString(): String {
        return when {
            heading < 10 -> "00$heading"
            heading < 100 -> "0$heading"
            else -> heading.toString()
        }
    }

    override fun toTTSString(): String {
        return splitCharactersToNatoPhonetic(toString())
    }
}

/** Contains a radio frequency to be spoken */
class FrequencyToken(private val frequency: String): CommsToken() {
    override fun toString(): String {
        return frequency
    }

    override fun toTTSString(): String {
        return splitCharactersToNatoPhonetic(frequency)
    }
}

/** Contains an altitude to be spoken - takes into account transition level automatically */
class AltitudeToken(private val altitude: Float): CommsToken() {
    override fun toString(): String {
        val roundedFL = (altitude / 100f).roundToInt()
        return if (roundedFL * 100 >= TRANS_LVL * 100) "FL$roundedFL"
        else "${roundedFL * 100} feet"
    }

    override fun toTTSString(): String {
        val roundedFL = (altitude / 100f).roundToInt()
        return if (roundedFL * 100 >= TRANS_LVL * 100) "flight level ${replaceDigitsOnlyToNatoPhonetic(roundedFL.toString())}"
        else "${roundedFL * 100} feet"
    }
}

/** Contains an ATIS letter to be spoken - converts the letter to its NATO phonetic token */
class AtisToken(private val atisCode: Char): CommsToken() {
    override fun toString(): String {
        return atisCode.uppercase()
    }

    override fun toTTSString(): String {
        return NATO_ALPHABET_PHONETIC[atisCode.uppercaseChar()] ?: atisCode.toString()
    }
}

/** Contains an integer to be spoken - converts the number to its NATO phonetic token */
class NumberToken(private val number: Int): CommsToken() {
    override fun toString(): String {
        return number.toString()
    }

    override fun toTTSString(): String {
        return replaceDigitsOnlyToNatoPhonetic(number.toString())
    }
}

/**
 * Splits the input [text] to follow NATO phonetic standards for both alphabets and digits
 * @param text the string to split and transform into individual NATO phonetic tokens
 */
fun splitCharactersToNatoPhonetic(text: String): String {
    return text.toCharArray().joinToString(" ") { NATO_ALPHABET_PHONETIC[it.uppercaseChar()] ?: it.toString() }
}

/**
 * Replaces all digits in [text] to be pronounced in NATO phonetic
 * @param text the string to replace digits with
 */
fun replaceDigitsOnlyToNatoPhonetic(text: String): String {
    return text.toCharArray().joinToString("") {
        if (it.isDigit()) " ${NATO_ALPHABET_PHONETIC[it] ?: it.toString()} "
        else it.toString()
    }
}