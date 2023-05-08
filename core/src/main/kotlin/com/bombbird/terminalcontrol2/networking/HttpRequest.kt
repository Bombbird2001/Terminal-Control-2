package com.bombbird.terminalcontrol2.networking

import com.badlogic.ashley.core.Entity
import com.bombbird.terminalcontrol2.global.GAME
import com.bombbird.terminalcontrol2.global.RELAY_ENDPOINT_PORT
import com.bombbird.terminalcontrol2.global.Secrets
import com.bombbird.terminalcontrol2.screens.JoinGame
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
        val request = Request.Builder()
            .url(Secrets.GET_METAR_URL)
            .post(reqString.toRequestBody(JSON_MEDIA_TYPE))
            .build()
        client.newCall(request).enqueue(object: Callback {
            override fun onFailure(call: Call, e: IOException) {
                Log.info("HttpRequest", "METAR request failed")
                println(e)
                handleMetarResult(null, retry, reqString, airportsForRandom)
            }

            override fun onResponse(call: Call, response: Response) {
                handleMetarResult(response, retry, reqString, airportsForRandom)
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
    private fun handleMetarResult(response: Response?, retry: Boolean, reqString: String, airportsForRandom: List<Entity>) {
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
            Log.info("HttpRequest", "METAR request error ${response.code}")
            tryGetWeatherAgain()
        } else {
            val responseText = response.body?.string()
            if (responseText == null) {
                Log.info("HttpRequest", "METAR request null response body")
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

    /**
     * Sends an HTTP request to the relay server
     * @param joinGame the [JoinGame] screen to handle the response
     * */
    fun sendPublicGamesRequest(joinGame: JoinGame) {
        val request = Request.Builder()
            .url("${Secrets.RELAY_ENDPOINT_URL}:${RELAY_ENDPOINT_PORT}/games")
            .get()
            .build()
        client.newCall(request).enqueue(object: Callback {
            override fun onFailure(call: Call, e: IOException) {
                Log.info("HttpRequest", "Public games request failed")
                println(e)
            }

            override fun onResponse(call: Call, response: Response) {
                handlePublicGamesResult(response, joinGame)
            }
        })
    }

    /**
     * Handles the response from the public games endpoint query
     * @param response the response received from the server; may be null if request was unsuccessful
     * @param joinGame the [JoinGame] screen to handle the response
     */
    private fun handlePublicGamesResult(response: Response?, joinGame: JoinGame) {
        if (response == null) return

        if (!response.isSuccessful) {
            Log.info("HttpRequest", "Public games request error ${response.code}")
        } else {
            val responseText = response.body?.string()
            if (responseText == null) {
                Log.info("HttpRequest", "Public games request null response body")
            } else {
                // Parse JSON to multiplayer games info
                joinGame.parsePublicGameInfo(responseText)
            }
        }
        response.close()
    }
}