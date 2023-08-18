package com.bombbird.terminalcontrol2.sounds

import com.bombbird.terminalcontrol2.global.COMMS_PILOT_VOICES
import com.bombbird.terminalcontrol2.global.COMMUNICATIONS_SOUND

class TextToSpeechManager {
    /** Speaks the initial contact for arrivals  */
//    fun initArrContact(aircraft: Aircraft, apchCallsign: String, greeting: String, action: String, star: String, starSaid: Boolean, direct: String, inboundSaid: Boolean, info: String) {
//        if (isVoiceDisabled()) return
//        val icao = Pronunciation.callsigns[getIcaoCode(aircraft.callsign)]
//        val newFlightNo = Pronunciation.convertNoToText(getFlightNo(aircraft.callsign))
//        val newAction = Pronunciation.convertToFlightLevel(action)
//        var starString = ""
//        if (starSaid) {
//            starString = " on the $star arrival"
//        }
//        var directString = ""
//        if (inboundSaid) {
//            val newDirect: String? = if (Pronunciation.waypointPronunciations.containsKey(direct)) {
//                Pronunciation.waypointPronunciations[direct]
//            } else {
//                Pronunciation.checkNumber(direct).lowercase(Locale.ROOT)
//            }
//            directString = ", inbound $newDirect"
//        }
//        var newInfo = ""
//        if (info.length >= 2) {
//            newInfo = info.split("information ".toRegex()).toTypedArray()[0] + "information " + Pronunciation.alphabetPronunciations[info[info.length - 1]]
//        }
//        val text = "$apchCallsign$greeting, $icao $newFlightNo ${aircraft.wakeString} with you, $newAction$starString$directString$newInfo"
//        sayText(text, aircraft.voice)
//    }

    private fun processInitialContactInfo(yourCallsign: String, randomGreeting: String, acCallsign: String, acWake: String) {

    }

    private fun isVoiceDisabled(): Boolean {
        return COMMUNICATIONS_SOUND >= COMMS_PILOT_VOICES
    }
}