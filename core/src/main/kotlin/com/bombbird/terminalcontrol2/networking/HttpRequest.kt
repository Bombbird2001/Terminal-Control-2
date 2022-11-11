package com.bombbird.terminalcontrol2.networking

import com.badlogic.ashley.core.Entity
import com.bombbird.terminalcontrol2.global.GAME
import com.bombbird.terminalcontrol2.global.Secrets
import com.bombbird.terminalcontrol2.utilities.generateRandomWeather
import com.bombbird.terminalcontrol2.utilities.updateAirportMetar
import com.esotericsoftware.minlog.Log
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
                Log.info("HttpRequest", "Request failed")
                println(e)
                handleResult(null, retry, reqString, airportsForRandom)
            }

            override fun onResponse(call: Call, response: Response) {
                handleResult(response, retry, reqString, airportsForRandom)
            }
        })
    }

    /**
     * Handles the resulting response from a weather request
     * @param response the response received from the server; may be null if request was unsuccessful
     * @param retry whether to retry if the response is not successful
     * @param reqString the original request JSON string send in the request
     * @param airportsForRandom the list of airport entities to generate random weather if needed
     */
    private fun handleResult(response: Response?, retry: Boolean, reqString: String, airportsForRandom: List<Entity>) {
        fun tryGetWeatherAgain() {
            if (retry) sendMetarRequest(reqString, false, airportsForRandom)
            else generateRandomWeather(true, airportsForRandom)
        }

        if (!checkGameServerRunningStatus()) return response?.close() ?: Unit
        if (response == null) {
            tryGetWeatherAgain()
            return
        }
        if (!response.isSuccessful) {
            if (response.code == 503) Log.info("HttpRequest", "Error 503")
            tryGetWeatherAgain()
        } else {
            val responseText = response.body?.string()
            if (responseText == null) {
                Log.info("HttpRequest", "Null response body")
                tryGetWeatherAgain()
            } else {
                // METAR JSON has been received
                updateAirportMetar(responseText)
            }
        }
        response.close()
    }

    /**
     * Checks whether the status of the game server is still active for updating of weather
     * @return true if the game server is running or initialising the weather, else false
     */
    private fun checkGameServerRunningStatus(): Boolean {
        return GAME.gameServer?.let { it.gameRunning || it.initialisingWeather.get() } ?: false
    }
}