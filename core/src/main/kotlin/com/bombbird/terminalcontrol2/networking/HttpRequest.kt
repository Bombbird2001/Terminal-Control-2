package com.bombbird.terminalcontrol2.networking

import com.badlogic.gdx.Application
import com.badlogic.gdx.Gdx
import com.bombbird.terminalcontrol2.files.PlayerSettingsJSON
import com.bombbird.terminalcontrol2.files.getJsonFromPlayerSettings
import com.bombbird.terminalcontrol2.global.*
import com.bombbird.terminalcontrol2.networking.relaygateway.RelayGatewayHost
import com.bombbird.terminalcontrol2.networking.relaygateway.RelayReachability
import com.bombbird.terminalcontrol2.screens.JoinGame
import com.bombbird.terminalcontrol2.utilities.updateAirportMetar
import com.bombbird.terminalcontrol2.utilities.FileLog
import com.squareup.moshi.*
import okhttp3.*
import okhttp3.MediaType.Companion.toMediaType
import okhttp3.RequestBody.Companion.toRequestBody
import java.io.IOException
import java.util.concurrent.TimeUnit

object HttpRequest {
    private val JSON_MEDIA_TYPE: MediaType = "application/json; charset=utf-8".toMediaType()
    private val TEXT_MEDIA_TYPE: MediaType = "text/plain; charset=utf-8".toMediaType()
    private val client = OkHttpClient.Builder().connectTimeout(3, TimeUnit.SECONDS)
        .readTimeout(10, TimeUnit.SECONDS).build()

    /** Helper class that specifies the JSON format to send METAR requests to the server */
    @JsonClass(generateAdapter = true)
    data class MetarRequest(val airports: List<MetarMapper>, val password: String = Secrets.GET_METAR_PW) {

        /** METAR request data format for an airport */
        @JsonClass(generateAdapter = true)
        data class MetarMapper(val realIcaoCode: String, val arptId: Byte)
    }

    @OptIn(ExperimentalStdlibApi::class)
    private val metarRequestMoshi = Moshi.Builder().build().adapter<MetarRequest>()

    /**
     * Sends an HTTP request to the METAR server with the list of airports to retrieve live weather for
     * @param reqList list of airport ICAO codes and their respective in-game airport IDs
     * @param onGenerateRandom the function to be called to generate random weather if error occurs in retrieving live
     * weather
     */
    fun sendMetarRequest(reqList: List<MetarRequest.MetarMapper>, onGenerateRandom: () -> Unit) {
        sendMetarRequest(metarRequestMoshi.toJson(MetarRequest(reqList)), true, onGenerateRandom)
    }

    /**
     * Sends an HTTP request to the METAR server, with the string query and whether the program should try another
     * request in case of failure
     * @param reqString the METAR request object in string format
     * @param retry whether to retry the request a second time if it fails
     * @param onGenerateRandom the function to be called to generate random weather if error occurs in retrieving live
     * weather
     */
    private fun sendMetarRequest(reqString: String, retry: Boolean, onGenerateRandom: () -> Unit) {
        val request = Request.Builder()
            .url(Secrets.GET_METAR_URL)
            .post(reqString.toRequestBody(JSON_MEDIA_TYPE))
            .build()
        client.newCall(request).enqueue(object: Callback {
            override fun onFailure(call: Call, e: IOException) {
                FileLog.info("HttpRequest", "METAR request failed")
                handleMetarResult(null, retry, reqString, onGenerateRandom)
            }

            override fun onResponse(call: Call, response: Response) {
                handleMetarResult(response, retry, reqString, onGenerateRandom)
            }
        })
    }

    /**
     * Handles the resulting response from a weather request
     * @param response the response received from the server; may be null if request was unsuccessful
     * @param retry whether to retry if the response is not successful
     * @param reqString the original request JSON string send in the request
     * @param onGenerateRandom the function to be called to generate random weather if error occurs in retrieving live
     * weather
     */
    private fun handleMetarResult(response: Response?, retry: Boolean, reqString: String, onGenerateRandom: () -> Unit) {
        fun tryGetWeatherAgain() {
            if (retry) sendMetarRequest(reqString, false, onGenerateRandom)
            else onGenerateRandom()
        }

        if (!checkGameServerRunningStatus()) return response?.close() ?: Unit
        if (response == null) {
            tryGetWeatherAgain()
            return
        }
        if (!response.isSuccessful) {
            FileLog.info("HttpRequest", "METAR request error ${response.code}")
            tryGetWeatherAgain()
        } else {
            val responseText = response.body?.string()
            if (responseText == null) {
                FileLog.info("HttpRequest", "METAR request null response body")
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

    /** Sends an HTTP request to the relay server to check its aliveness */
    fun sendPublicServerAlive(relayGatewayHost: RelayGatewayHost): RelayReachability {
        val request = Request.Builder()
            .url("${relayGatewayHost.relayEndpoint}:${relayGatewayHost.relayEndpointPort}$RELAY_GAME_ALIVE_PATH")
            .get()
            .build()
        return try {
            client.newCall(request).execute().use { response ->
                if (!response.isSuccessful) {
                    FileLog.info("HttpRequest", "Public games alive check error ${response.code}")
                    RelayReachability.DOWN
                } else {
                    RelayReachability.UP
                }
            }
        } catch (_: IOException) {
            FileLog.info("HttpRequest", "Public games alive check failed")
            RelayReachability.NETWORK_ISSUE
        }
    }

    /** Sends an HTTP request to the relay server to request for open public games */
    fun sendPublicGamesRequest(relayGatewayHost: RelayGatewayHost): List<JoinGame.MultiplayerGameInfo> {
        val request = Request.Builder()
            .url("${relayGatewayHost.relayEndpoint}:${relayGatewayHost.relayEndpointPort}$RELAY_GAMES_PATH")
            .post("".toRequestBody(TEXT_MEDIA_TYPE))
            .build()
        return try {
            client.newCall(request).execute().use { response ->
                if (!response.isSuccessful) {
                    FileLog.info("HttpRequest", "Public games request error ${response.code}")
                    listOf()
                } else {
                    handlePublicGamesResult(response)
                }
            }
        } catch (_: IOException) {
            FileLog.info("HttpRequest", "Public games request failed")
            listOf()
        }
    }

    /**
     * Handles the response from the public games endpoint query
     * @param response the response received from the server; may be null if request was unsuccessful
     */
    private fun handlePublicGamesResult(response: Response): List<JoinGame.MultiplayerGameInfo> {
        val publicGamesData = ArrayList<JoinGame.MultiplayerGameInfo>()

        if (!response.isSuccessful) {
            FileLog.info("HttpRequest", "Public games request error ${response.code}")
        } else {
            val responseText = response.body?.string()
            if (responseText == null) {
                FileLog.info("HttpRequest", "Public games request null response body")
            } else {
                // Parse JSON to multiplayer games info
                val type = Types.newParameterizedType(List::class.java, JoinGame.MultiplayerGameInfo::class.java)
                Moshi.Builder().build().adapter<List<JoinGame.MultiplayerGameInfo>>(type).fromJson(responseText)?.apply {
                    for (game in this) publicGamesData.add(game)
                }
            }
        }

        return publicGamesData
    }

    /** Class representing data sent to authorization endpoint to obtain symmetric key for room data encryption */
    @JsonClass(generateAdapter = true)
    data class AuthorizationRequest(val roomId: Short, val uuid: String, val pw: String)

    /** Class representing data sent by authorization endpoint with symmetric key for room data encryption */
    @JsonClass(generateAdapter = true)
    data class AuthorizationResponse(
        val success: Boolean, val roomKey: String, val clientKey: String, val nonce: String,
        val iv: String
    )

    private val moshi = Moshi.Builder().build()

    @OptIn(ExperimentalStdlibApi::class)
    private val moshiAuthReqAdapter = moshi.adapter<AuthorizationRequest>()

    @OptIn(ExperimentalStdlibApi::class)
    private val moshiRoomAuthAdapter = moshi.adapter<AuthorizationResponse>()

    /**
     * Sends a request to the relay endpoint to join a game, and retrieve the symmetric key for encrypting data in the
     * room
     */
    fun sendGameAuthorizationRequest(roomId: Short, relayGatewayHost: RelayGatewayHost): AuthorizationResponse? {
        val request = Request.Builder()
            .url("${relayGatewayHost.relayEndpoint}:${relayGatewayHost.relayEndpointPort}$RELAY_GAME_AUTH_PATH")
            .post(moshiAuthReqAdapter.toJson(
                AuthorizationRequest(roomId, myUuid.toString(), relayGatewayHost.relayEndpointAuthPw)
            ).toRequestBody(TEXT_MEDIA_TYPE))
            .build()
        // Blocking call
        val response = client.newCall(request).execute()

        val roomAuthResponse = if (!response.isSuccessful) {
            FileLog.info("HttpRequest", "Public games authorization error ${response.code}")
            null
        } else {
            val responseText = response.body?.string()
            if (responseText == null) {
                FileLog.info("HttpRequest", "Public games authorization null response body")
                null
            } else {
                // Parse JSON to room creation status
                try {
                    moshiRoomAuthAdapter.fromJson(responseText)
                } catch (_: JsonDataException) {
                    FileLog.info("HttpRequest", "Public games authorization failed to parse response $responseText")
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
    data class RoomCreationStatus(
        val success: Boolean, val roomId: Short, val authResponse: AuthorizationResponse,
        val tcpPort: Int, val udpPort: Int
    )

    @OptIn(ExperimentalStdlibApi::class)
    private val moshiRoomCreationAdapter = Moshi.Builder().build().adapter<RoomCreationStatus>()

    /**
     * Sends a request to the relay endpoint to create a game, and retrieve the room ID and the symmetric key for
     * encrypting data in the room
     */
    fun sendCreateGameRequest(relayGatewayHost: RelayGatewayHost): RoomCreationStatus? {
        val request = Request.Builder()
            .url("${relayGatewayHost.relayEndpoint}:${relayGatewayHost.relayEndpointPort}$RELAY_GAME_CREATE_PATH")
            .post(relayGatewayHost.relayEndpointCreatePw.toRequestBody(TEXT_MEDIA_TYPE))
            .build()
        // Blocking call
        val response = client.newCall(request).execute()

        val roomCreationStatus = if (!response.isSuccessful) {
            FileLog.info("HttpRequest", "Public games creation error ${response.code}")
            null
        } else {
            val responseText = response.body?.string()
            if (responseText == null) {
                FileLog.info("HttpRequest", "Public games creation null response body")
                null
            } else {
                // Parse JSON to room creation status
                try {
                    moshiRoomCreationAdapter.fromJson(responseText)
                } catch (e: JsonDataException) {
                    e.printStackTrace()
                    FileLog.info("HttpRequest", "Public games creation failed to parse response $responseText")
                    null
                }
            }
        }
        response.close()

        return roomCreationStatus
    }

    /** Class representing the crash information sent to the server together with the password */
    @JsonClass(generateAdapter = true)
    data class ErrorRequest(val errorString: String, val crashLocation: String, val gameVersion: String = GAME_VERSION,
                            val buildVersion: Int = BUILD_VERSION, val platform: String,
                            val multiplayerType: String, val password: String = Secrets.SEND_ERROR_PW)

    /**
     * Sends an HTTP request to the crash report server
     * @param e the crash exception
     * @param crashLocation the location where the crash occurred (GameServer, RadarScreen, etc.)
     * @param multiplayerType the type of game (singleplayer, multiplayer)
     */
    @OptIn(ExperimentalStdlibApi::class)
    fun sendCrashReport(e: Exception, crashLocation: String, multiplayerType: String) {
        e.printStackTrace()
        val platformName = when (Gdx.app.type) {
            Application.ApplicationType.Android -> "Android"
            Application.ApplicationType.Desktop -> "Desktop"
            Application.ApplicationType.iOS -> "iOS"
            else -> "Unknown platform"
        }

        val jsonString = Moshi.Builder().build().adapter<ErrorRequest>().toJson(
            ErrorRequest(e.stackTraceToString(), crashLocation, platform = platformName, multiplayerType = multiplayerType)
        )

        val request = Request.Builder()
            .url(Secrets.SEND_ERROR_URL)
            .post(jsonString.toRequestBody(JSON_MEDIA_TYPE))
            .build()

        client.newCall(request).enqueue(object: Callback {
            override fun onFailure(call: Call, e: IOException) {
                FileLog.info("HttpRequest", "Error request failed")
            }

            override fun onResponse(call: Call, response: Response) {
                FileLog.info("HttpRequest", response.body?.string() ?: "Null response body")
            }
        })
    }

    /** Class representing the bug report sent to the server together with the password */
    @JsonClass(generateAdapter = true)
    data class BugReportRequest(val description: String, val logs: String, val gameSave: String,
                                val playerSettings: PlayerSettingsJSON,
                                val gameVersion: String = GAME_VERSION, val buildVersion: Int = BUILD_VERSION,
                                val platform: String, val multiplayerType: String,
                                val password: String = Secrets.BUG_REPORT_PW)

    /**
     * Sends an HTTP request to the bug report server
     * @param description the description of the bug
     * @param logs the game logs
     * @param gameSave the save file where the bug occurred
     * @param multiplayerType the type of game (singleplayer, multiplayer)
     * @param onSuccess callback function called when the request is successful, with the response body as parameter
     * @param onFailure callback function called when the request fails
     */
    @OptIn(ExperimentalStdlibApi::class)
    fun sendBugReport(description: String, logs: String, gameSave: String, multiplayerType: String,
                      onSuccess: (String) -> Unit, onFailure: () -> Unit) {
        val platformName = when (Gdx.app.type) {
            Application.ApplicationType.Android -> "Android"
            Application.ApplicationType.Desktop -> "Desktop"
            Application.ApplicationType.iOS -> "iOS"
            else -> "Unknown platform"
        }

        val jsonString = Moshi.Builder().build().adapter<BugReportRequest>().toJson(
            BugReportRequest(description, logs, gameSave, getJsonFromPlayerSettings(), platform = platformName,
                multiplayerType = multiplayerType)
        )

        val request = Request.Builder()
            .url(Secrets.BUG_REPORT_URL)
            .post(jsonString.toRequestBody(JSON_MEDIA_TYPE))
            .build()

        client.newCall(request).enqueue(object: Callback {
            override fun onFailure(call: Call, e: IOException) {
                FileLog.info("HttpRequest", "Bug report request failed")
                onFailure()
            }

            override fun onResponse(call: Call, response: Response) {
                val responseStr = response.body?.string()
                FileLog.info("HttpRequest", responseStr ?: "Null response body")
                if (responseStr == null) onFailure() else onSuccess(responseStr)
            }
        })
    }
}