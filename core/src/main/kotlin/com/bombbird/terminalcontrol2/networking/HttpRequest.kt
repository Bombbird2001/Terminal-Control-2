package com.bombbird.terminalcontrol2.networking

import com.badlogic.gdx.Gdx
import com.bombbird.terminalcontrol2.global.GAME
import com.bombbird.terminalcontrol2.global.Secrets
import com.bombbird.terminalcontrol2.utilities.generateRandomWeather
import com.bombbird.terminalcontrol2.utilities.updateAirportMetar
import okhttp3.*
import okhttp3.MediaType.Companion.toMediaType
import okhttp3.RequestBody.Companion.toRequestBody
import java.io.IOException

object HttpRequest {
    private val JSON_MEDIA_TYPE: MediaType = "application/json; charset=utf-8".toMediaType()
    private val client = OkHttpClient()

    /** Sends an HTTP request to the METAR server, with the [reqString] query and [retry] which denotes whether the program
     * should try another request in case of failure
     * */
    fun sendMetarRequest(reqString: String, retry: Boolean) {
        val request = Request.Builder()
            .url(Secrets.GET_METAR_URL)
            .post(reqString.toRequestBody(JSON_MEDIA_TYPE))
            .build()
        client.newCall(request).enqueue(object: Callback {
            override fun onFailure(call: Call, e: IOException) {
                Gdx.app.log("HttpRequest", "Request failed")
                println(e)
                if (GAME.gameServer?.gameRunning != true) return
                if (retry) sendMetarRequest(reqString, false)
                else generateRandomWeather() // Generate offline weather
            }

            override fun onResponse(call: Call, response: Response) {
                if (!response.isSuccessful) {
                    if (response.code == 503 && retry) {
                        Gdx.app.log("HttpRequest", "503 received: trying again")
                        response.close()
                        if (GAME.gameServer?.gameRunning != true) return
                        sendMetarRequest(reqString, false)
                    } else {
                        // Generate offline weather
                        response.close()
                        generateRandomWeather()
                    }
                } else {
                    val responseText = response.body?.string()
                    response.close()

                    if (responseText == null) {
                        Gdx.app.log("HttpRequest", "Null sendMetarRequest response")
                        generateRandomWeather()
                        return
                    }

                    // METAR JSON has been received
                    updateAirportMetar(responseText)
                }
            }
        })
    }
}