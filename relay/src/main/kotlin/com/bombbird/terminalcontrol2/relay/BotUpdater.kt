package com.bombbird.terminalcontrol2.relay

import com.bombbird.terminalcontrol2.utilities.FileLog
import okhttp3.*
import okhttp3.MediaType.Companion.toMediaType
import okhttp3.RequestBody.Companion.toRequestBody
import java.io.IOException
import java.util.concurrent.TimeUnit

/** Object for handling discord bot server messages */
object BotUpdater {
    private val JSON_MEDIA_TYPE: MediaType = "application/json; charset=utf-8".toMediaType()
    private val client = OkHttpClient.Builder().connectTimeout(3, TimeUnit.SECONDS)
        .readTimeout(10, TimeUnit.SECONDS).build()

    fun updateServers(serverData: String) {
        val request = Request.Builder()
            .url(Secrets.BOT_UPDATE_SERVERS)
            .post(serverData.toRequestBody(JSON_MEDIA_TYPE))
            .build()
        client.newCall(request).enqueue(object: Callback {
            override fun onFailure(call: Call, e: IOException) {
                FileLog.info("BotUpdater", "Update servers request failed")
            }

            override fun onResponse(call: Call, response: Response) {
                response.close()
            }
        })
    }
}