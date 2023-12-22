package com.bombbird.terminalcontrol2.utilities

import com.bombbird.terminalcontrol2.ui.isMobile
import ktx.collections.GdxArray

private val tips = GdxArray<String>()

fun loadTips() {
    if (!tips.isEmpty) return
    tips.add("Minimise your datatag by double-${if (isMobile()) "tapping" else "clicking"} on it")
    tips.add("Create your own datatag layout in the datatag settings")
    tips.add("Only the host of the game can change game settings, such as weather, traffic and runway configuration")
    tips.add("You can change the weather in the game settings, including setting your own custom weather")
    tips.add("Enabling night mode will enforce night procedures, if any, at an airport")
    tips.add("Activation of night mode will follow the time on the host's device")
    tips.add("Change the frequency of emergencies in the game settings")
    tips.add("You can change the traffic mode in the \"Manage Traffic\" section of the game settings")
    tips.add("Enable/disable arrivals and departures for each airport in the \"Manage Traffic\" section of the game settings")
    tips.add("Change various display settings, such as the trajectory line, aircraft trail and range rings")
    tips.add("Cancel upcoming altitude or speed restrictions for an aircraft by going to \"Edit Route\" and tapping on the restriction")
    tips.add("In multiplayer games, you can request to swap sectors with another player by selecting the \"Sectors\" tab,"
            + " selecting the sector you want, and ${if (isMobile()) "tapping" else "clicking"} \"Request Swap\"")
    tips.add("If an aircraft deviates from its original route, MVA checks will be performed as if the aircraft is being vectored, be careful!")
    tips.add("Turboprops like the ATR72 have a lower performance than jet aircraft and will climb slower and have a lower cruise speed")
    tips.add("Parallel approaches without a non-transgression zone (NTZ) require 2nm separation between aircraft")
    tips.add("Parallel approaches or departures with a non-transgression zone (NTZ) do not require separation between aircraft as long as"
            + " they are established within their respective green normal operating zone (NOZ) and do not cross into the red NTZ")
    tips.add("Aircraft can go around if the visibility conditions are worse than the approach minimums")
    tips.add("Join our Discord server from the main menu!")
    tips.add("An aircraft will reduce its descent rate if it is below the glideslope")
    tips.add("You can enable pilot voices in the sound settings")
    tips.add("Aircraft are assigned random load factors - this will affect their climb and descent rates")
    tips.add("Aircraft will go around if the tailwind is too strong")
    tips.add("Multiplayer games are seriously fun - try it out!")
    tips.add("Be sure to use vectors when needed to maintain efficient spacing between aircraft!")
    tips.add("Want more of a challenge? Change your traffic mode to \"Arrivals to control\" and set the slider to a much higher value!")
    tips.add("Airports getting congested? Put some aircraft in holding patterns to give yourself some breathing space!")
    tips.add("Departures are dependant on arrivals - the more arrivals, the more departures you can expect!")
    tips.add("Make sure to sequence aircraft on approach with sufficient spacing in between - they slow down a lot on final approach!")
    tips.add("Managing aircraft speeds properly can help reduce the amount of vectoring needed for spacing")
    tips.add("Take note of wake categories of aircraft - strategic sequencing can increase the efficiency of your airport!")
    tips.add("Multiplayer games with more than 1 player in-game do not pause even if you are in the pause screen!")
    tips.add("The \"Status\" tab gives you a general overview of the current status of the game")
    tips.add("Some approaches are non-precision approaches - aircraft will follow a series of step-downs instead of a glideslope")
    tips.add("Some SIDs are radar vector departures - you have to vector the aircraft to its first waypoint")
    if (isMobile()) {
        tips.add("To measure distance on the radar screen, hold 2 fingers down on the screen for a short while until the distance appears -"
                + " now you can drag your fingers around to measure the distance between 2 points")
    } else {
        tips.add("To measure distance on the radar screen, hold down the right mouse button and drag the mouse around to measure the distance"
                + " between 2 points")
    }
    tips.add("You can enable display of distance to go to a waypoint on an aircraft's route in the display settings")
    tips.add("You can enable display of waypoint restrictions on an aircraft's route in the display settings")
}

fun getRandomTip(): String {
    return tips.random() ?: ""
}
