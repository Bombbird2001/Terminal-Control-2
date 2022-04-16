package com.bombbird.terminalcontrol2.networking

import com.badlogic.gdx.Gdx
import com.bombbird.terminalcontrol2.global.Constants
import com.bombbird.terminalcontrol2.global.Secrets
import okhttp3.*
import okhttp3.MediaType.Companion.toMediaType
import okhttp3.RequestBody.Companion.toRequestBody
import java.io.IOException

object HttpRequest {
    private val JSON_MEDIA_TYPE: MediaType = "application/json; charset=utf-8".toMediaType()
    private val client = OkHttpClient()

    fun sendMetarRequest(reqString: String, retry: Boolean) {
        val request = Request.Builder()
            .url(Secrets.GET_METAR_URL)
            .post(reqString.toRequestBody(JSON_MEDIA_TYPE))
            .build()
        client.newCall(request).enqueue(object: Callback {
            override fun onFailure(call: Call, e: IOException) {
                // TODO random weather generation
            }

            override fun onResponse(call: Call, response: Response) {
                if (!response.isSuccessful) {
                    if (response.code == 503 && retry) {
                        println("503 received: trying again")
                        response.close()
                        if (Constants.GAME.gameServer?.gameRunning == true) return
                        sendMetarRequest(reqString, false)
                    } else {
                        // Generate offline weather
                        response.close()
                        // TODO random weather generation
                    }
                } else {
                    val responseText = response.body?.string()
                    response.close()

                    if (responseText == null) {
                        Gdx.app.log("HttpRequest", "Null sendMetarRequest response")
                        // TODO random weather generation
                        return
                    }

                    println(responseText)
                    // METAR JSON has been received
                    // TODO Update METAR component for airports
                }
            }
        })
    }
}