package com.quio.ytm.core.api

import io.ktor.client.HttpClient
import io.ktor.client.call.body
import io.ktor.client.engine.cio.CIO
import io.ktor.client.plugins.contentnegotiation.ContentNegotiation
import io.ktor.client.request.header
import io.ktor.client.request.post
import io.ktor.client.request.setBody
import io.ktor.http.ContentType
import io.ktor.http.contentType
import io.ktor.serialization.kotlinx.json.json
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.buildJsonObject
import kotlinx.serialization.json.put
import kotlinx.serialization.json.putJsonObject

class InnertubeClient(
    private var cookieString: String = "",
    private var sapisid: String = ""
) {
    private val client = HttpClient(CIO) {
        install(ContentNegotiation) {
            json(Json { ignoreUnknownKeys = true })
        }
    }
    
    fun setCookies(cookies: String, sapisidCookie: String) {
        this.cookieString = cookies
        this.sapisid = sapisidCookie
    }

    private fun buildContext() = buildJsonObject {
        putJsonObject("client") {
            put("clientName", "WEB_REMIX")
            put("clientVersion", "1.20230508.01.00")
            put("hl", "en")
        }
    }

    // Endpoint for getting queue streams and radio tracks
    suspend fun next(videoId: String, playlistId: String? = null): JsonObject {
        val payload = buildJsonObject {
            put("context", buildContext())
            put("videoId", videoId)
            if (playlistId != null) {
                put("playlistId", playlistId)
            }
        }
        
        return client.post("https://music.youtube.com/youtubei/v1/next?alt=json") {
            contentType(ContentType.Application.Json)
            header("Cookie", cookieString)
            if (sapisid.isNotEmpty()) {
                header("Authorization", InnertubeAuth.generateSapisidHashHeader(sapisid))
                header("X-Origin", "https://music.youtube.com")
            }
            setBody(payload)
        }.body()
    }
}
