package com.bombbird.terminalcontrol2.utilities

import java.time.temporal.ChronoUnit
import java.time.temporal.Temporal

/**
 * Gets the difference between two temporals [d1], [d2] in the relevant human-readable format
 *
 * For example, a difference of 1 year will return "1 year ago", a difference of 2 months will return "2 months ago", etc.
 */
fun getDatetimeDifferenceString(d1: Temporal, d2: Temporal): String {
    val diffY = ChronoUnit.YEARS.between(d1, d2)
    if (diffY > 0) return "$diffY year${if (diffY > 1) "s" else ""} ago"
    val diffM = ChronoUnit.MONTHS.between(d1, d2)
    if (diffM > 0) return "$diffM month${if (diffM > 1) "s" else ""} ago"
    val diffD = ChronoUnit.DAYS.between(d1, d2)
    if (diffD > 0) return "$diffD day${if (diffD > 1) "s" else ""} ago"
    val diffH = ChronoUnit.HOURS.between(d1, d2)
    if (diffH > 0) return "$diffH hour${if (diffH > 1) "s" else ""} ago"
    val diffMin = ChronoUnit.MINUTES.between(d1, d2)
    return "$diffMin minute${if (diffMin != 1.toLong()) "s" else ""} ago"
}