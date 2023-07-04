package com.bombbird.terminalcontrol2.utilities

val AT_ALT_REGEX = "(-?\\d+)".toRegex() // Altitude values of at least 1 digit
val ABOVE_ALT_REGEX = "A(-?\\d+)".toRegex() // Altitude values of at least 1 digit, with "A" as a prefix
val BELOW_ALT_REGEX = "B(-?\\d+)".toRegex() // Altitude values of at least 1 digit, with "B" as a prefix

/**
 * Removes additional spaces, commas, trailing and leading commas and whitespace from the input string and returns the corrected string
 * @param msg the message to correct
 * @return a new message without additional characters
 */
fun removeExtraCharacters(msg: String): String {
    var correctMsg = msg
    // First replace any spaces before a comma
    val commaSpaceRegex = " +,".toRegex()
    correctMsg = correctMsg.replace(commaSpaceRegex, ",")

    // Next replace multi-spaces with a single space
    val multiSpaceRegex = " +".toRegex()
    correctMsg = correctMsg.replace(multiSpaceRegex, " ")

    // Next replace multi-commas with a single comma
    val multiCommaRegex = ",+".toRegex()
    correctMsg = correctMsg.replace(multiCommaRegex, ",")

    // Remove trailing commas
    val trailingCommaRegex = ", *\$".toRegex()
    correctMsg = correctMsg.replace(trailingCommaRegex, "")

    // Return the string with leading or trailing whitespaces removed if any
    return correctMsg.trim()
}

/**
 * Gets this string split by new lines
 * @return the string split into lines by the new line separator(s)
 */
fun String.toLines(limit: Int = 0): List<String> {
    return split("\\r?\\n".toRegex(), limit)
}