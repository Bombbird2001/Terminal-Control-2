package com.bombbird.terminalcontrol2.networking

import com.badlogic.ashley.core.Entity
import com.bombbird.terminalcontrol2.global.*
import com.bombbird.terminalcontrol2.screens.JoinGame
import com.bombbird.terminalcontrol2.utilities.generateRandomWeather
import com.bombbird.terminalcontrol2.utilities.updateAirportMetar
import com.esotericsoftware.minlog.Log
import com.squareup.moshi.JsonClass
import com.squareup.moshi.JsonDataException
import com.squareup.moshi.Moshi
import com.squareup.moshi.adapter
import okhttp3.*
import okhttp3.MediaType.Companion.toMediaType
import okhttp3.RequestBody.Companion.toRequestBody
import java.io.IOException

object HttpRequest {
    private val JSON_MEDIA_TYPE: MediaType = "application/json; charset=utf-8".toMediaType()
    private val TEXT_MEDIA_TYPE: MediaType = "text/plain; charset=utf-8".toMediaType()
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
            .url("${Secrets.RELAY_ENDPOINT_URL}:$RELAY_ENDPOINT_PORT$RELAY_GAMES_PATH")
            .post("".toRequestBody(TEXT_MEDIA_TYPE))
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

    /** Class representing data sent to authorization endpoint to obtain symmetric key for room data encryption */
    @JsonClass(generateAdapter = true)
    data class AuthorizationRequest(val roomId: Short, val uuid: String)

    /** Class representing data sent by authorization endpoint with symmetric key for room data encryption */
    @JsonClass(generateAdapter = true)
    data class AuthorizationResponse(val success: Boolean, val key: String, val nonce: String, val iv: String)

    private val moshi = Moshi.Builder().build()

    @OptIn(ExperimentalStdlibApi::class)
    private val moshiAuthReqAdapter = moshi.adapter<AuthorizationRequest>()

    @OptIn(ExperimentalStdlibApi::class)
    private val moshiRoomAuthAdapter = moshi.adapter<AuthorizationResponse>()

    /**
     * Sends a request to the relay endpoint to join a game, and retrieve the symmetric key for encrypting data in the
     * room
     */
    fun sendGameAuthorizationRequest(roomId: Short): AuthorizationResponse? {
        val request = Request.Builder()
            .url("${Secrets.RELAY_ENDPOINT_URL}:$RELAY_ENDPOINT_PORT$RELAY_GAME_AUTH_PATH")
            .post(moshiAuthReqAdapter.toJson(AuthorizationRequest(roomId, myUuid.toString())).toRequestBody(TEXT_MEDIA_TYPE))
            .build()
        // Blocking call
        val response = client.newCall(request).execute()

        val roomAuthResponse = if (!response.isSuccessful) {
            Log.info("HttpRequest", "Public games authorization error ${response.code}")
            null
        } else {
            val responseText = response.body?.string()
            if (responseText == null) {
                Log.info("HttpRequest", "Public games authorization null response body")
                null
            } else {
                // Parse JSON to room creation status
                try {
                    moshiRoomAuthAdapter.fromJson(responseText)
                } catch (e: JsonDataException) {
                    Log.info("HttpRequest", "Public games authorization failed to parse response $responseText")
                    null
                }
            }
        }
        response.close()

        return roomAuthResponse
    }

    /**
     * Class representing data sent to the host with the new room data for a new game with symmetric key for room data
     * encryption (in Base64 encoding)
     */
    @JsonClass(generateAdapter = true)
    data class RoomCreationStatus(val success: Boolean, val roomId: Short, val authResponse: AuthorizationResponse)

    @OptIn(ExperimentalStdlibApi::class)
    private val moshiRoomCreationAdapter = Moshi.Builder().build().adapter<RoomCreationStatus>()

    /**
     * Sends a request to the relay endpoint to create a game, and retrieve the room ID and the symmetric key for
     * encrypting data in the room
     */
    fun sendCreateGameRequest(): RoomCreationStatus? {
        val request = Request.Builder()
            .url("${Secrets.RELAY_ENDPOINT_URL}:$RELAY_ENDPOINT_PORT$RELAY_GAME_CREATE_PATH")
            .post("".toRequestBody(TEXT_MEDIA_TYPE))
            .build()
        // Blocking call
        val response = client.newCall(request).execute()

        val roomCreationStatus = if (!response.isSuccessful) {
            Log.info("HttpRequest", "Public games creation error ${response.code}")
            null
        } else {
            val responseText = response.body?.string()
            if (responseText == null) {
                Log.info("HttpRequest", "Public games creation null response body")
                null
            } else {
                // Parse JSON to room creation status
                try {
                    moshiRoomCreationAdapter.fromJson(responseText)
                } catch (e: JsonDataException) {
                    Log.info("HttpRequest", "Public games creation failed to parse response $responseText")
                    null
                }
            }
        }
        response.close()

        return roomCreationStatus
    }
}