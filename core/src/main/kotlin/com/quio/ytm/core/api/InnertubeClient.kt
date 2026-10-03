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
import io.ktor.client.request.get
import io.ktor.client.request.header
import io.ktor.client.request.post
import io.ktor.client.request.setBody
import io.ktor.client.statement.bodyAsText
import io.ktor.http.ContentType
import io.ktor.http.contentType
import io.ktor.serialization.kotlinx.json.json
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.JsonObject as KxJsonObject
import kotlinx.serialization.json.buildJsonArray
import kotlinx.serialization.json.buildJsonObject
import kotlinx.serialization.json.add
import kotlinx.serialization.json.put
import kotlinx.serialization.json.putJsonObject

class InnertubeClient(
    private var cookieString: String = "",
    private var sapisid: String = "",
    private var oauthToken: String = ""
) {
    private val client = HttpClient(CIO) {
        install(ContentNegotiation) {
            json(Json { ignoreUnknownKeys = true })
        }
    }
    
    fun hasAuth(): Boolean = oauthToken.isNotBlank() || cookieString.isNotBlank() || sapisid.isNotBlank()

    fun getCookieString(): String = cookieString
    fun getSapisid(): String = sapisid
    fun getOAuthToken(): String = oauthToken

    fun setOAuthToken(token: String) {
        this.oauthToken = token.trim()
    }

    fun setCookies(cookies: String, sapisidCookie: String = "") {
        this.cookieString = cookies.trim()
        var extractedSapisid = sapisidCookie.trim()
        if (extractedSapisid.isBlank() && this.cookieString.isNotBlank()) {
            val sapisidRegex = Regex("""(?:^|;\s*)(?:SAPISID|__Secure-3PAPISID)=([^;]+)""")
            extractedSapisid = sapisidRegex.find(this.cookieString)?.groupValues?.get(1)?.trim() ?: ""
        }
        this.sapisid = extractedSapisid
    }

    fun clearAuth() {
        this.cookieString = ""
        this.sapisid = ""
        this.oauthToken = ""
    }

    private fun io.ktor.client.request.HttpRequestBuilder.applyAuthHeaders() {
        header("User-Agent", "Mozilla/5.0 (Macintosh; Intel Mac OS X 10_15_7) AppleWebKit/537.36 (KHTML, like Gecko) Chrome/120.0.0.0 Safari/537.36")
        if (oauthToken.isNotEmpty()) {
            header("Authorization", "Bearer $oauthToken")
        } else {
            if (cookieString.isNotEmpty()) {
                header("Cookie", cookieString)
            }
            if (sapisid.isNotEmpty()) {
                header("Authorization", InnertubeAuth.generateSapisidHashHeader(sapisid))
                header("X-Origin", "https://music.youtube.com")
            }
        }
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
            if (title.isEmpty() || isPrivateOrDeletedVideo(title, null)) return null

            // Extract Thumbnail
            var thumbUrl = ""
            val thumbs = renderer.getAsJsonObject("thumbnail")
                ?.getAsJsonObject("musicThumbnailRenderer")
                ?.getAsJsonObject("thumbnail")
                ?.getAsJsonArray("thumbnails")
            if (thumbs != null && thumbs.size() > 0) {
                thumbUrl = thumbs.get(thumbs.size() - 1).asJsonObject.get("url")?.asString ?: ""
            }

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
                    val yearRegex = Regex("""\b(19\d\d|20\d\d)\b""")
                    if (sections.isNotEmpty()) {
                        artist = sections[0]
                    }
                    if (sections.size > 2) {
                        album = sections[1]
                    }
                    if (sections.size > 1 && timeRegex.matches(sections.last())) {
                        durationMs = parseTimeString(sections.last())
                    }
                    val foundYear = sections.firstOrNull { yearRegex.matches(it.trim()) }?.trim()
                        ?: yearRegex.find(album)?.value
                        ?: yearRegex.find(title)?.value

                    return Track(
                        id = videoId,
                        title = title,
                        artist = artist,
                        album = album,
                        durationMs = durationMs,
                        thumbnailUrl = thumbUrl,
                        loudnessDb = -14.0,
                        year = foundYear
                    )
                }
            }

            val yearRegex = Regex("""\b(19\d\d|20\d\d)\b""")
            val fallbackYear = yearRegex.find(album)?.value ?: yearRegex.find(title)?.value

            return Track(
                id = videoId,
                title = title,
                artist = artist,
                album = album,
                durationMs = durationMs,
                thumbnailUrl = thumbUrl,
                loudnessDb = -14.0,
                year = fallbackYear
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

    /**
     * Fetches Automix / Radio tracks for a seed track using YouTube Music's RDAMVM radio playlist.
     * Returns 25-50 recommended tracks by the seed artist and similar artists.
     */
    suspend fun getRadioTracks(videoId: String): List<Track> {
        val radioPlaylistId = "RDAMVM$videoId"
        val payload = buildJsonObject {
            put("context", buildContext())
            put("videoId", videoId)
            put("playlistId", radioPlaylistId)
        }

        val jsonStr = client.post("https://music.youtube.com/youtubei/v1/next?alt=json") {
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

        return parseRadioResponse(jsonStr)
    }

    private fun parseRadioResponse(jsonStr: String): List<Track> {
        val results = mutableListOf<Track>()
        try {
            val root = JsonParser.parseString(jsonStr)
            val renderers = mutableListOf<JsonObject>()
            findRadioRenderers(root, renderers)

            for (renderer in renderers) {
                val track = parsePlaylistPanelRenderer(renderer)
                if (track != null) {
                    results.add(track)
                }
            }
        } catch (e: Exception) {
            System.err.println("Error parsing radio response: ${e.message}")
        }
        return results
    }

    private fun findRadioRenderers(element: JsonElement?, output: MutableList<JsonObject>) {
        if (element == null || element.isJsonNull) return
        if (element.isJsonObject) {
            val obj = element.asJsonObject
            if (obj.has("playlistPanelVideoRenderer")) {
                output.add(obj.getAsJsonObject("playlistPanelVideoRenderer"))
            }
            for (entry in obj.entrySet()) {
                findRadioRenderers(entry.value, output)
            }
        } else if (element.isJsonArray) {
            for (item in element.asJsonArray) {
                findRadioRenderers(item, output)
            }
        }
    }

    private fun parsePlaylistPanelRenderer(renderer: JsonObject): Track? {
        try {
            val videoId = renderer.get("videoId")?.asString ?: return null

            // Title
            val titleRuns = renderer.getAsJsonObject("title")?.getAsJsonArray("runs") ?: return null
            val title = if (titleRuns.size() > 0) titleRuns.get(0).asJsonObject.get("text")?.asString ?: "" else ""
            if (title.isEmpty()) return null

            // Artist & Album from longBylineText or shortBylineText
            var artist = "Unknown Artist"
            var album = ""
            val longRuns = renderer.getAsJsonObject("longBylineText")?.getAsJsonArray("runs")
            if (longRuns != null && longRuns.size() > 0) {
                artist = longRuns.get(0).asJsonObject.get("text")?.asString ?: "Unknown Artist"
                if (longRuns.size() > 2) {
                    album = longRuns.get(2).asJsonObject.get("text")?.asString ?: ""
                }
            } else {
                val shortRuns = renderer.getAsJsonObject("shortBylineText")?.getAsJsonArray("runs")
                if (shortRuns != null && shortRuns.size() > 0) {
                    artist = shortRuns.get(0).asJsonObject.get("text")?.asString ?: "Unknown Artist"
                }
            }

            // Duration
            var durationMs = 0L
            val lengthRuns = renderer.getAsJsonObject("lengthText")?.getAsJsonArray("runs")
            if (lengthRuns != null && lengthRuns.size() > 0) {
                val timeStr = lengthRuns.get(0).asJsonObject.get("text")?.asString ?: ""
                durationMs = parseTimeString(timeStr)
            }

            // Thumbnail
            var thumbUrl = ""
            val thumbs = renderer.getAsJsonObject("thumbnail")?.getAsJsonArray("thumbnails")
            if (thumbs != null && thumbs.size() > 0) {
                thumbUrl = thumbs.get(thumbs.size() - 1).asJsonObject.get("url")?.asString ?: ""
            }

            val yearRegex = Regex("""\b(19\d\d|20\d\d)\b""")
            val foundYear = yearRegex.find(album)?.value ?: yearRegex.find(title)?.value

            return Track(
                id = videoId,
                title = title,
                artist = artist,
                album = album,
                durationMs = durationMs,
                thumbnailUrl = thumbUrl,
                loudnessDb = -14.0,
                year = foundYear
            )
        } catch (_: Exception) {
            return null
        }
    }

    /**
     * Creates a new playlist on YouTube Music using Innertube or YouTube Data API.
     * @param title Title of the playlist
     * @param description Optional description
     * @param privacyStatus "PRIVATE", "UNLISTED", or "PUBLIC"
     * @param videoIds Initial list of YouTube video IDs
     * @return Created YouTube playlist ID or null on failure
     */
    suspend fun createPlaylist(
        title: String,
        description: String = "",
        privacyStatus: String = "PRIVATE",
        videoIds: List<String> = emptyList()
    ): String? {
        if (oauthToken.isNotEmpty()) {
            val oauthRes = createPlaylistOAuth(title, description, privacyStatus, videoIds)
            if (oauthRes != null) return oauthRes
        }

        val payload = buildJsonObject {
            put("context", buildContext())
            put("title", title)
            if (description.isNotEmpty()) {
                put("description", description)
            }
            put("privacyStatus", privacyStatus)
            if (videoIds.isNotEmpty()) {
                put("videoIds", buildJsonArray {
                    videoIds.forEach { add(it) }
                })
            }
        }

        return try {
            val responseText = client.post("https://music.youtube.com/youtubei/v1/playlist/create?alt=json") {
                contentType(ContentType.Application.Json)
                applyAuthHeaders()
                setBody(payload)
            }.bodyAsText()

            val root = JsonParser.parseString(responseText).asJsonObject
            root.get("playlistId")?.asString
        } catch (e: Exception) {
            System.err.println("Error creating playlist via Innertube: ${e.message}")
            null
        }
    }

    private suspend fun createPlaylistOAuth(
        title: String,
        description: String,
        privacyStatus: String,
        videoIds: List<String>
    ): String? {
        return try {
            val payload = buildJsonObject {
                putJsonObject("snippet") {
                    put("title", title)
                    if (description.isNotEmpty()) put("description", description)
                }
                putJsonObject("status") {
                    put("privacyStatus", privacyStatus.lowercase())
                }
            }
            val respStr = client.post("https://www.googleapis.com/youtube/v3/playlists?part=snippet,status") {
                contentType(ContentType.Application.Json)
                header("Authorization", "Bearer $oauthToken")
                setBody(payload)
            }.bodyAsText()

            val root = JsonParser.parseString(respStr).asJsonObject
            val createdId = root.get("id")?.asString
            if (!createdId.isNullOrEmpty() && videoIds.isNotEmpty()) {
                addTracksToPlaylistOAuth(createdId, videoIds)
            }
            createdId
        } catch (e: Exception) {
            System.err.println("Error creating playlist via YouTube Data API: ${e.message}")
            null
        }
    }

    /**
     * Adds tracks to an existing YouTube Music playlist in batches.
     * @param playlistId Target YouTube playlist ID
     * @param videoIds List of video IDs to append
     */
    suspend fun addTracksToPlaylist(playlistId: String, videoIds: List<String>): Boolean {
        if (videoIds.isEmpty()) return true

        if (oauthToken.isNotEmpty()) {
            val ok = addTracksToPlaylistOAuth(playlistId, videoIds)
            if (ok) return true
        }

        return try {
            val actions = buildJsonArray {
                videoIds.forEach { vid ->
                    add(buildJsonObject {
                        put("action", "ACTION_ADD_VIDEO")
                        put("addedVideoId", vid)
                    })
                }
            }
            val payload = buildJsonObject {
                put("context", buildContext())
                put("playlistId", playlistId)
                put("actions", actions)
            }

            val responseText = client.post("https://music.youtube.com/youtubei/v1/browse/edit_playlist?alt=json") {
                contentType(ContentType.Application.Json)
                applyAuthHeaders()
                setBody(payload)
            }.bodyAsText()

            val root = JsonParser.parseString(responseText).asJsonObject
            root.get("status")?.asString == "STATUS_SUCCEEDED" || root.has("actions")
        } catch (e: Exception) {
            System.err.println("Error adding tracks to playlist via Innertube: ${e.message}")
            false
        }
    }

    private suspend fun addTracksToPlaylistOAuth(playlistId: String, videoIds: List<String>): Boolean {
        return try {
            val cleanId = if (playlistId.startsWith("VL")) playlistId.removePrefix("VL") else playlistId
            for (vid in videoIds) {
                val payload = buildJsonObject {
                    putJsonObject("snippet") {
                        put("playlistId", cleanId)
                        putJsonObject("resourceId") {
                            put("kind", "youtube#video")
                            put("videoId", vid)
                        }
                    }
                }
                client.post("https://www.googleapis.com/youtube/v3/playlistItems?part=snippet") {
                    contentType(ContentType.Application.Json)
                    header("Authorization", "Bearer $oauthToken")
                    setBody(payload)
                }
            }
            true
        } catch (e: Exception) {
            System.err.println("Error adding playlist items via YouTube Data API: ${e.message}")
            false
        }
    }

    /**
     * Fetches the user's Liked Songs from YouTube Music or YouTube Data API.
     */
    suspend fun getLikedSongs(): List<Track> {
        if (oauthToken.isNotEmpty()) {
            val oauthLiked = getLikedSongsOAuth()
            if (oauthLiked.isNotEmpty()) return oauthLiked
        }

        val payload = buildJsonObject {
            put("context", buildContext())
            put("browseId", "FEmusic_liked_videos")
        }

        return try {
            val responseText = client.post("https://music.youtube.com/youtubei/v1/browse?alt=json") {
                contentType(ContentType.Application.Json)
                applyAuthHeaders()
                setBody(payload)
            }.bodyAsText()

            parseSearchResponse(responseText)
        } catch (e: Exception) {
            System.err.println("Error fetching liked songs from YouTube Music: ${e.message}")
            emptyList()
        }
    }

    private suspend fun getLikedSongsOAuth(): List<Track> {
        val tracks = mutableListOf<Track>()
        try {
            // First attempt: Query YouTube Music's dedicated Liked Music playlist 'LM'
            val lmTracks = getPlaylistTracksOAuth("LM")
            if (lmTracks.isNotEmpty()) {
                return lmTracks
            }

            // Fallback: Query liked videos with Music Category (categoryId = 10)
            var pageToken: String? = null
            var fetchedCount = 0
            val maxFetch = 250

            do {
                val url = "https://www.googleapis.com/youtube/v3/videos?myRating=like&part=snippet,contentDetails&maxResults=50" +
                        (if (pageToken != null) "&pageToken=$pageToken" else "")
                val respStr = client.get(url) {
                    header("Authorization", "Bearer $oauthToken")
                }.bodyAsText()

                val root = JsonParser.parseString(respStr).asJsonObject
                val items = root.getAsJsonArray("items") ?: break
                for (item in items) {
                    val obj = item.asJsonObject
                    val snippet = obj.getAsJsonObject("snippet") ?: continue
                    val categoryId = snippet.get("categoryId")?.asString ?: ""
                    
                    // Only include music category (10) if using the general video endpoint
                    if (categoryId.isNotEmpty() && categoryId != "10") {
                        continue
                    }

                    val vid = obj.get("id")?.asString ?: continue
                    val title = snippet.get("title")?.asString ?: continue
                    if (isPrivateOrDeletedVideo(title, snippet)) continue
                    val channelTitle = snippet.get("channelTitle")?.asString ?: "Unknown Artist"
                    val publishedAt = snippet.get("publishedAt")?.asString ?: ""
                    val year = if (publishedAt.length >= 4) publishedAt.take(4) else null

                    val thumbs = snippet.getAsJsonObject("thumbnails")
                    var thumbUrl = thumbs?.getAsJsonObject("high")?.get("url")?.asString
                        ?: thumbs?.getAsJsonObject("medium")?.get("url")?.asString
                        ?: thumbs?.getAsJsonObject("default")?.get("url")?.asString ?: ""

                    val contentDetails = obj.getAsJsonObject("contentDetails")
                    val durationIso = contentDetails?.get("duration")?.asString ?: ""
                    val durationMs = parseIsoDuration(durationIso)

                    tracks.add(
                        Track(
                            id = vid,
                            title = title,
                            artist = channelTitle,
                            album = "Liked Songs",
                            durationMs = durationMs,
                            thumbnailUrl = thumbUrl,
                            loudnessDb = -14.0,
                            year = year
                        )
                    )
                }
                fetchedCount += items.size()
                pageToken = root.get("nextPageToken")?.asString
            } while (!pageToken.isNullOrEmpty() && fetchedCount < maxFetch)
        } catch (e: Exception) {
            System.err.println("Error fetching liked songs via YouTube Data API: ${e.message}")
        }
        return tracks
    }

    /**
     * Fetches all tracks from a remote YouTube Music playlist.
     */
    suspend fun getPlaylistTracks(playlistId: String): List<Track> {
        if (oauthToken.isNotEmpty()) {
            val oauthTracks = getPlaylistTracksOAuth(playlistId)
            if (oauthTracks.isNotEmpty()) return oauthTracks
        }

        val browseId = if (playlistId.startsWith("VL")) playlistId else "VL$playlistId"
        val payload = buildJsonObject {
            put("context", buildContext())
            put("browseId", browseId)
        }

        return try {
            val responseText = client.post("https://music.youtube.com/youtubei/v1/browse?alt=json") {
                contentType(ContentType.Application.Json)
                applyAuthHeaders()
                setBody(payload)
            }.bodyAsText()

            parseSearchResponse(responseText)
        } catch (e: Exception) {
            System.err.println("Error fetching playlist tracks from YouTube Music: ${e.message}")
            emptyList()
        }
    }

    private suspend fun getPlaylistTracksOAuth(playlistId: String): List<Track> {
        val tracks = mutableListOf<Track>()
        try {
            val cleanId = if (playlistId.startsWith("VL")) playlistId.removePrefix("VL") else playlistId
            var pageToken: String? = null
            var fetchedCount = 0
            val maxFetch = 500

            do {
                val url = "https://www.googleapis.com/youtube/v3/playlistItems?playlistId=$cleanId&part=snippet,contentDetails&maxResults=50" +
                        (if (pageToken != null) "&pageToken=$pageToken" else "")
                val respStr = client.get(url) {
                    header("Authorization", "Bearer $oauthToken")
                }.bodyAsText()

                val root = JsonParser.parseString(respStr).asJsonObject
                val items = root.getAsJsonArray("items") ?: break
                val pageTracks = mutableListOf<Track>()
                val videoIds = mutableListOf<String>()

                for (item in items) {
                    val obj = item.asJsonObject
                    val snippet = obj.getAsJsonObject("snippet") ?: continue
                    val resId = snippet.getAsJsonObject("resourceId") ?: continue
                    val vid = resId.get("videoId")?.asString ?: continue
                    val title = snippet.get("title")?.asString ?: continue
                    val cleanTitle = title.trim()

                    if (isPrivateOrDeletedVideo(cleanTitle, snippet)) {
                        continue
                    }

                    val artist = snippet.get("videoOwnerChannelTitle")?.asString
                        ?: snippet.get("channelTitle")?.asString ?: "Unknown Artist"
                    val publishedAt = snippet.get("publishedAt")?.asString ?: ""
                    val year = if (publishedAt.length >= 4) publishedAt.take(4) else null

                    val thumbs = snippet.getAsJsonObject("thumbnails")
                    var thumbUrl = thumbs?.getAsJsonObject("high")?.get("url")?.asString
                        ?: thumbs?.getAsJsonObject("medium")?.get("url")?.asString
                        ?: thumbs?.getAsJsonObject("default")?.get("url")?.asString ?: ""

                    videoIds.add(vid)
                    pageTracks.add(
                        Track(
                            id = vid,
                            title = cleanTitle,
                            artist = artist,
                            album = if (cleanId == "LM") "Liked Songs" else "",
                            durationMs = 0L,
                            thumbnailUrl = thumbUrl,
                            loudnessDb = -14.0,
                            year = year
                        )
                    )
                }

                // Batch fetch real durations for this page of videos
                val durationMap = fetchVideoDurations(videoIds)
                for (track in pageTracks) {
                    val dur = durationMap[track.id] ?: 0L
                    tracks.add(track.copy(durationMs = dur))
                }

                fetchedCount += items.size()
                pageToken = root.get("nextPageToken")?.asString
            } while (!pageToken.isNullOrEmpty() && fetchedCount < maxFetch)
        } catch (e: Exception) {
            System.err.println("Error fetching playlist tracks via YouTube Data API: ${e.message}")
        }
        return tracks
    }

    /**
     * Fetches the user's saved playlists from YouTube Music library.
     */
    suspend fun getLibraryPlaylists(): List<RemotePlaylistInfo> {
        if (oauthToken.isNotEmpty()) {
            val oauthPlaylists = getLibraryPlaylistsOAuth()
            if (oauthPlaylists.isNotEmpty()) return oauthPlaylists
        }

        val payload = buildJsonObject {
            put("context", buildContext())
            put("browseId", "FEmusic_library_playlists")
        }

        return try {
            val responseText = client.post("https://music.youtube.com/youtubei/v1/browse?alt=json") {
                contentType(ContentType.Application.Json)
                applyAuthHeaders()
                setBody(payload)
            }.bodyAsText()

            parsePlaylistsResponse(responseText)
        } catch (e: Exception) {
            System.err.println("Error fetching library playlists from YouTube Music: ${e.message}")
            emptyList()
        }
    }

    private suspend fun getLibraryPlaylistsOAuth(): List<RemotePlaylistInfo> {
        val playlists = mutableListOf<RemotePlaylistInfo>()
        try {
            var pageToken: String? = null
            var fetchedCount = 0
            val maxFetch = 50

            do {
                val url = "https://www.googleapis.com/youtube/v3/playlists?mine=true&part=snippet,contentDetails&maxResults=50" +
                        (if (pageToken != null) "&pageToken=$pageToken" else "")
                val respStr = client.get(url) {
                    header("Authorization", "Bearer $oauthToken")
                }.bodyAsText()

                val root = JsonParser.parseString(respStr).asJsonObject
                val items = root.getAsJsonArray("items") ?: break
                for (item in items) {
                    val obj = item.asJsonObject
                    val plId = obj.get("id")?.asString ?: continue
                    val snippet = obj.getAsJsonObject("snippet") ?: continue
                    val title = snippet.get("title")?.asString ?: continue
                    val desc = snippet.get("description")?.asString ?: ""
                    val thumbs = snippet.getAsJsonObject("thumbnails")
                    val thumbUrl = thumbs?.getAsJsonObject("high")?.get("url")?.asString
                        ?: thumbs?.getAsJsonObject("medium")?.get("url")?.asString
                        ?: thumbs?.getAsJsonObject("default")?.get("url")?.asString ?: ""
                    val contentDetails = obj.getAsJsonObject("contentDetails")
                    val itemCount = contentDetails?.get("itemCount")?.asInt ?: 0

                    playlists.add(
                        RemotePlaylistInfo(
                            id = plId,
                            title = title,
                            description = desc,
                            thumbnailUrl = thumbUrl,
                            trackCount = itemCount
                        )
                    )
                }
                fetchedCount += items.size()
                pageToken = root.get("nextPageToken")?.asString
            } while (!pageToken.isNullOrEmpty() && fetchedCount < maxFetch)
        } catch (e: Exception) {
            System.err.println("Error fetching library playlists via YouTube Data API: ${e.message}")
        }
        return playlists
    }

    private fun parseIsoDuration(isoDuration: String): Long {
        if (isoDuration.isBlank()) return 0L
        return try {
            java.time.Duration.parse(isoDuration).toMillis()
        } catch (_: Exception) {
            0L
        }
    }

    private suspend fun fetchVideoDurations(videoIds: List<String>): Map<String, Long> {
        if (videoIds.isEmpty()) return emptyMap()
        val durationMap = mutableMapOf<String, Long>()
        for (chunk in videoIds.chunked(50)) {
            try {
                val idsParam = chunk.joinToString(",")
                val url = "https://www.googleapis.com/youtube/v3/videos?part=contentDetails&id=$idsParam"
                val respStr = client.get(url) {
                    if (oauthToken.isNotEmpty()) {
                        header("Authorization", "Bearer $oauthToken")
                    }
                }.bodyAsText()
                val root = JsonParser.parseString(respStr).asJsonObject
                val items = root.getAsJsonArray("items") ?: continue
                for (item in items) {
                    val obj = item.asJsonObject
                    val vid = obj.get("id")?.asString ?: continue
                    val contentDetails = obj.getAsJsonObject("contentDetails") ?: continue
                    val durIso = contentDetails.get("duration")?.asString ?: ""
                    val ms = parseIsoDuration(durIso)
                    if (ms > 0) {
                        durationMap[vid] = ms
                    }
                }
            } catch (e: Exception) {
                System.err.println("Error fetching video durations: ${e.message}")
            }
        }
        return durationMap
    }

    private fun isPrivateOrDeletedVideo(title: String, snippet: JsonObject?): Boolean {
        val clean = title.trim().lowercase().removePrefix("[").removeSuffix("]").trim()
        if (clean.isEmpty()) return true
        if (clean == "private video" ||
            clean == "deleted video" ||
            clean == "vídeo privado" ||
            clean == "vídeo eliminado" ||
            clean == "video privado" ||
            clean == "video eliminado" ||
            clean.startsWith("private video") ||
            clean.startsWith("deleted video") ||
            clean.startsWith("vídeo privado") ||
            clean.startsWith("vídeo eliminado") ||
            clean.startsWith("video privado") ||
            clean.startsWith("video eliminado")) {
            return true
        }
        if (snippet != null) {
            val desc = snippet.get("description")?.asString?.lowercase() ?: ""
            if (desc.contains("this video is private") || desc.contains("this video has been removed")) {
                return true
            }
            val thumbs = snippet.getAsJsonObject("thumbnails")
            if (thumbs == null || thumbs.size() == 0) {
                if (clean.contains("private") || clean.contains("deleted") || clean.contains("eliminado")) {
                    return true
                }
            }
        }
        return false
    }

    private fun parsePlaylistsResponse(jsonStr: String): List<RemotePlaylistInfo> {
        val playlists = mutableListOf<RemotePlaylistInfo>()
        try {
            val root = JsonParser.parseString(jsonStr)
            val renderers = mutableListOf<JsonObject>()
            findTwoRowRenderers(root, renderers)
            for (r in renderers) {
                val titleRuns = r.getAsJsonObject("title")?.getAsJsonArray("runs")
                val title = if (titleRuns != null && titleRuns.size() > 0) titleRuns.get(0).asJsonObject.get("text")?.asString ?: "" else ""
                val browseEndpoint = r.getAsJsonObject("navigationEndpoint")?.getAsJsonObject("browseEndpoint")
                var browseId = browseEndpoint?.get("browseId")?.asString ?: ""
                if (browseId.startsWith("VL")) {
                    browseId = browseId.removePrefix("VL")
                }
                if (browseId.isNotEmpty() && title.isNotEmpty()) {
                    var thumb = ""
                    val thumbs = r.getAsJsonObject("thumbnailRenderer")
                        ?.getAsJsonObject("musicThumbnailRenderer")
                        ?.getAsJsonObject("thumbnail")
                        ?.getAsJsonArray("thumbnails")
                    if (thumbs != null && thumbs.size() > 0) {
                        thumb = thumbs.get(thumbs.size() - 1).asJsonObject.get("url")?.asString ?: ""
                    }
                    playlists.add(RemotePlaylistInfo(id = browseId, title = title, thumbnailUrl = thumb))
                }
            }
        } catch (e: Exception) {
            System.err.println("Error parsing playlists: ${e.message}")
        }
        return playlists
    }

    private fun findTwoRowRenderers(element: JsonElement?, output: MutableList<JsonObject>) {
        if (element == null || element.isJsonNull) return
        if (element.isJsonObject) {
            val obj = element.asJsonObject
            if (obj.has("musicTwoRowItemRenderer")) {
                output.add(obj.getAsJsonObject("musicTwoRowItemRenderer"))
            }
            for (entry in obj.entrySet()) {
                findTwoRowRenderers(entry.value, output)
            }
        } else if (element.isJsonArray) {
            for (item in element.asJsonArray) {
                findTwoRowRenderers(item, output)
            }
        }
    }
}

data class RemotePlaylistInfo(
    val id: String,
    val title: String,
    val description: String = "",
    val thumbnailUrl: String = "",
    val trackCount: Int = 0
)
