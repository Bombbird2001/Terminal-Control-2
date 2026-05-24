package com.bombbird.terminalcontrol2.global

import com.bombbird.terminalcontrol2.networking.relaygateway.RelayGatewayHost

object Secrets {
    const val GET_METAR_URL = ""
    const val GET_METAR_PW = ""
    const val SEND_ERROR_URL = ""
    const val SEND_ERROR_PW = ""
    const val BUG_REPORT_URL = ""
    const val BUG_REPORT_PW = ""
    val RELAY_INSTANCES: ArrayList<RelayGatewayHost> = arrayListOf()
    const val DISCORD_INVITE_LINK = ""
    const val DISCORD_GAME_SDK_APP_ID = 0L
}