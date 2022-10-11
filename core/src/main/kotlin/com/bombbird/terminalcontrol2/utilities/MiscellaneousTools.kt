package com.bombbird.terminalcontrol2.utilities

val AT_ALT_REGEX = "(-?\\d+)".toRegex() // Altitude values of at least 1 digit
val ABOVE_ALT_REGEX = "A(-?\\d+)".toRegex() // Altitude values of at least 1 digit, with "A" as a prefix
val BELOW_ALT_REGEX = "B(-?\\d+)".toRegex() // Altitude values of at least 1 digit, with "B" as a prefix

/**
 * Gets this string split by new lines
 * @return the string split into lines by the new line separator(s)
 */
fun String.toLines(limit: Int = 0): List<String> {
    return split("\\r?\\n".toRegex(), limit)
}