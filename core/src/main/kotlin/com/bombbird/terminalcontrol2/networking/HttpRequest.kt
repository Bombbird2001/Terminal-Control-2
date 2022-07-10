package com.bombbird.terminalcontrol2.networking

import com.badlogic.ashley.core.Entity
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

    /**
     * Sends an HTTP request to the METAR server, with the string query and whether the program should try another
     * request in case of failure
     * @param reqString the METAR request object in string format
     * @param airportsForRandom the list of airports to generate random weather for in case of failure
     * */
    fun sendMetarRequest(reqString: String, retry: Boolean, airportsForRandom: List<Entity>) {
        // return generateRandomWeather(false, airportsForRandom)
        val request = Request.Builder()
            .url(Secrets.GET_METAR_URL)
            .post(reqString.toRequestBody(JSON_MEDIA_TYPE))
            .build()
        client.newCall(request).enqueue(object: Callback {
            override fun onFailure(call: Call, e: IOException) {
                Gdx.app.log("HttpRequest", "Request failed")
                println(e)
                if (!checkGameServerRunningStatus()) return
                if (retry) sendMetarRequest(reqString, false, airportsForRandom)
                else generateRandomWeather(true, airportsForRandom) // Generate offline weather
            }

            override fun onResponse(call: Call, response: Response) {
                if (!response.isSuccessful) {
                    if (response.code == 503 && retry) {
                        Gdx.app.log("HttpRequest", "503 received: trying again")
                        response.close()
                        if (!checkGameServerRunningStatus()) return
                        sendMetarRequest(reqString, false, airportsForRandom)
                    } else {
                        // Generate offline weather
                        response.close()
                        generateRandomWeather(false, airportsForRandom)
                    }
                } else {
                    val responseText = response.body?.string()
                    response.close()

                    if (responseText == null) {
                        Gdx.app.log("HttpRequest", "Null sendMetarRequest response")
                        generateRandomWeather(false, airportsForRandom)
                        return
                    }

                    // METAR JSON has been received
                    updateAirportMetar(responseText)
                }
            }
        })
    }

    /**
     * Checks whether the status of the game server allows for a retry in case of failure to retrieve METAR
     * @return true if the game server is running or initialising the weather, else false
     */
    private fun checkGameServerRunningStatus(): Boolean {
        return GAME.gameServer?.let { it.gameRunning || it.initialisingWeather.get() } ?: false
    }
}