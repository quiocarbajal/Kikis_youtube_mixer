package com.quio.ytm.core.api

import com.google.gson.JsonArray
import com.google.gson.JsonElement
import com.google.gson.JsonObject
import com.google.gson.JsonParser
import com.quio.ytm.core.models.Track
import io.ktor.client.HttpClient
import io.ktor.client.call.body
import io.ktor.client.engine.cio.CIO
import io.ktor.client.plugins.contentnegotiation.ContentNegotiation
import io.ktor.client.request.header
import io.ktor.client.request.post
import io.ktor.client.request.setBody
import io.ktor.client.statement.bodyAsText
import io.ktor.http.ContentType
import io.ktor.http.contentType
import io.ktor.serialization.kotlinx.json.json
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.JsonObject as KxJsonObject
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

    /**
     * Search YouTube Music catalog
     * @param query Search query string
     * @param filter Optional filter ('track' or 'song' forces Songs filter)
     */
    suspend fun search(query: String, filter: String? = null): List<Track> {
        val payload = buildJsonObject {
            put("context", buildContext())
            put("query", query)
            // Always request playable song tracks from YouTube Music
            put("params", "EgWKAQIIAWoKEAkQBRAKEAMQBA%3D%3D")
        }

        val responseText = client.post("https://music.youtube.com/youtubei/v1/search?alt=json") {
            contentType(ContentType.Application.Json)
            header("User-Agent", "Mozilla/5.0 (Macintosh; Intel Mac OS X 10_15_7) AppleWebKit/537.36 (KHTML, like Gecko) Chrome/120.0.0.0 Safari/537.36")
            if (cookieString.isNotEmpty()) {
                header("Cookie", cookieString)
            }
            if (sapisid.isNotEmpty()) {
                header("Authorization", InnertubeAuth.generateSapisidHashHeader(sapisid))
                header("X-Origin", "https://music.youtube.com")
            }
            setBody(payload)
        }.bodyAsText()

        return parseSearchResponse(responseText)
    }

    private fun parseSearchResponse(jsonStr: String): List<Track> {
        val results = mutableListOf<Track>()
        try {
            val root = JsonParser.parseString(jsonStr)
            val renderers = mutableListOf<JsonObject>()
            findRenderers(root, renderers)

            for (renderer in renderers) {
                val track = parseTrackRenderer(renderer)
                if (track != null) {
                    results.add(track)
                }
            }
        } catch (e: Exception) {
            System.err.println("Error parsing search response: ${e.message}")
        }
        return results
    }

    private fun findRenderers(element: JsonElement?, output: MutableList<JsonObject>) {
        if (element == null || element.isJsonNull) return
        if (element.isJsonObject) {
            val obj = element.asJsonObject
            if (obj.has("musicResponsiveListItemRenderer")) {
                output.add(obj.getAsJsonObject("musicResponsiveListItemRenderer"))
            }
            for (entry in obj.entrySet()) {
                findRenderers(entry.value, output)
            }
        } else if (element.isJsonArray) {
            for (item in element.asJsonArray) {
                findRenderers(item, output)
            }
        }
    }

    private fun parseTrackRenderer(renderer: JsonObject): Track? {
        try {
            // Extract Video ID
            var videoId = renderer.getAsJsonObject("playlistItemData")?.get("videoId")?.asString
            if (videoId.isNullOrEmpty()) {
                videoId = renderer.getAsJsonObject("navigationEndpoint")
                    ?.getAsJsonObject("watchEndpoint")
                    ?.get("videoId")?.asString
            }
            if (videoId.isNullOrEmpty()) {
                videoId = renderer.getAsJsonObject("overlay")
                    ?.getAsJsonObject("musicItemThumbnailOverlayRenderer")
                    ?.getAsJsonObject("content")
                    ?.getAsJsonObject("musicPlayButtonRenderer")
                    ?.getAsJsonObject("playNavigationEndpoint")
                    ?.getAsJsonObject("watchEndpoint")
                    ?.get("videoId")?.asString
            }
            if (videoId.isNullOrEmpty()) return null

            // Extract Title, Artist, Album, Duration from flexColumns
            val flexCols = renderer.getAsJsonArray("flexColumns") ?: return null
            var title = ""
            var artist = "Unknown Artist"
            var album = ""
            var durationMs = 0L

            if (flexCols.size() > 0) {
                val col0Runs = flexCols.get(0).asJsonObject
                    .getAsJsonObject("musicResponsiveListItemFlexColumnRenderer")
                    ?.getAsJsonObject("text")
                    ?.getAsJsonArray("runs")
                if (col0Runs != null && col0Runs.size() > 0) {
                    title = col0Runs.get(0).asJsonObject.get("text")?.asString ?: ""
                }
            }
            if (title.isEmpty()) return null

            if (flexCols.size() > 1) {
                val col1Runs = flexCols.get(1).asJsonObject
                    .getAsJsonObject("musicResponsiveListItemFlexColumnRenderer")
                    ?.getAsJsonObject("text")
                    ?.getAsJsonArray("runs")
                if (col1Runs != null) {
                    val sections = mutableListOf<String>()
                    val curr = StringBuilder()
                    for (r in col1Runs) {
                        val txt = r.asJsonObject.get("text")?.asString ?: ""
                        if (txt == " • ") {
                            if (curr.isNotEmpty()) {
                                sections.add(curr.toString().trim())
                                curr.clear()
                            }
                        } else {
                            curr.append(txt)
                        }
                    }
                    if (curr.isNotEmpty()) {
                        sections.add(curr.toString().trim())
                    }

                    val timeRegex = Regex("""^\d+:\d+(:\d+)?$""")
                    if (sections.isNotEmpty()) {
                        artist = sections[0]
                    }
                    if (sections.size > 2) {
                        album = sections[1]
                    }
                    if (sections.size > 1 && timeRegex.matches(sections.last())) {
                        durationMs = parseTimeString(sections.last())
                    }
                }
            }

            // Extract Thumbnail
            var thumbUrl = ""
            val thumbs = renderer.getAsJsonObject("thumbnail")
                ?.getAsJsonObject("musicThumbnailRenderer")
                ?.getAsJsonObject("thumbnail")
                ?.getAsJsonArray("thumbnails")
            if (thumbs != null && thumbs.size() > 0) {
                thumbUrl = thumbs.get(thumbs.size() - 1).asJsonObject.get("url")?.asString ?: ""
            }

            return Track(
                id = videoId,
                title = title,
                artist = artist,
                album = album,
                durationMs = durationMs,
                thumbnailUrl = thumbUrl,
                loudnessDb = -14.0
            )
        } catch (_: Exception) {
            return null
        }
    }

    private fun parseTimeString(timeStr: String): Long {
        val parts = timeStr.split(":")
        return when (parts.size) {
            2 -> ((parts[0].toLongOrNull() ?: 0L) * 60 + (parts[1].toLongOrNull() ?: 0L)) * 1000L
            3 -> ((parts[0].toLongOrNull() ?: 0L) * 3600 + (parts[1].toLongOrNull() ?: 0L) * 60 + (parts[2].toLongOrNull() ?: 0L)) * 1000L
            else -> 0L
        }
    }

    // Endpoint for getting queue streams and radio tracks
    suspend fun next(videoId: String, playlistId: String? = null): KxJsonObject {
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
