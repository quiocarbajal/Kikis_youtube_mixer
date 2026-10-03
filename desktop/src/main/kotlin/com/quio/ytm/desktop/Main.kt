package com.quio.ytm.desktop

import com.quio.ytm.core.api.InnertubeClient
import com.quio.ytm.core.discovery.ArtistUtils
import com.quio.ytm.core.models.ActiveQueue
import com.quio.ytm.core.models.Track
import com.quio.ytm.core.shuffle.ShuffleEngine
import com.quio.ytm.core.state.ActiveQueueManager
import com.quio.ytm.domain.GenreCatalog
import com.quio.ytm.domain.GenreItem
import com.quio.ytm.domain.SearchUtils
import io.ktor.http.HttpMethod
import io.ktor.http.HttpStatusCode
import io.ktor.serialization.kotlinx.json.json
import io.ktor.server.application.call
import io.ktor.server.application.install
import io.ktor.server.engine.embeddedServer
import io.ktor.server.http.content.staticResources
import io.ktor.server.netty.Netty
import io.ktor.server.plugins.contentnegotiation.ContentNegotiation
import io.ktor.server.plugins.cors.routing.CORS
import io.ktor.server.request.receive
import io.ktor.server.response.respond
import io.ktor.server.response.respondText
import io.ktor.server.routing.delete
import io.ktor.server.routing.get
import io.ktor.server.routing.post
import io.ktor.server.routing.routing
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import kotlinx.serialization.Serializable
import kotlinx.serialization.encodeToString
import kotlinx.serialization.decodeFromString
import kotlinx.serialization.json.Json
import java.io.File

@Serializable
data class MoveRequest(val fromIndex: Int, val toIndex: Int)

@Serializable
data class AppendTracksRequest(val tracks: List<Track>)

@Serializable
data class PlayIndexRequest(val index: Int)

@Serializable
data class PlayerItemDto(
    val id: String = "",
    val uri: String = "",
    val title: String = "",
    val artist: String = "",
    val album: String = "",
    val duration_ms: Long = 0L,
    val album_art_url: String = ""
)

@Serializable
data class PlayerStateDto(
    val is_playing: Boolean = false,
    val progress_ms: Long = 0L,
    val item: PlayerItemDto? = null,
    val volume_percent: Int = 80
)

@Serializable
data class PlayRequest(
    val uris: List<String> = emptyList(),
    val track_id: String? = null,
    val true_shuffle: Boolean = false,
    val device_id: String? = null
)

@Serializable
data class PlayerControlRequest(val action: String)

@Serializable
data class PlayerSeekRequest(val position_ms: Long)

@Serializable
data class PlayerVolumeRequest(val volume_percent: Int)

@Serializable
data class UserDto(val display_name: String, val id: String)

@Serializable
data class AuthData(
    val cookie: String = "",
    val sapisid: String = "",
    val access_token: String = "",
    val refresh_token: String = "",
    val expires_at: Long = 0L,
    val user_name: String = "",
    val user_email: String = ""
)

@Serializable
data class AuthCookieRequest(
    val cookie: String = "",
    val sapisid: String? = null
)

@Serializable
data class AuthLoginResponse(
    val status: String = "ok",
    val authenticated: Boolean = false,
    val auth_url: String? = null,
    val message: String = ""
)

@Serializable
data class SyncLibraryResponse(
    val status: String = "ok",
    val liked_count: Int = 0,
    val playlists_count: Int = 0,
    val warning: String? = null,
    val message: String = ""
)

@Serializable
data class StatusResponse(
    val authenticated: Boolean = true,
    val is_mac: Boolean = true,
    val has_synced_tracks: Boolean = true,
    val liked_tracks: Int = 0,
    val total_tracks: Int = 0,
    val access_url: String = "http://localhost:8888",
    val app: String = "kiki's youtube mixer",
    val service: String = "YouTube Music",
    val user: UserDto = UserDto("YouTube Music Listener", "local_ytm_user")
)

@Serializable
data class TrackDto(
    val id: String,
    val uri: String,
    val title: String,
    val artist: String,
    val primary_artist: String = "",
    val album: String,
    val duration_ms: Long,
    val durationMs: Long,
    val thumbnailUrl: String,
    val album_art_url: String,
    val loudnessDb: Double,
    val year: String? = null,
    val is_liked: Boolean = false
)

@Serializable
data class SearchResponse(
    val results: List<TrackDto> = emptyList(),
    val error: String? = null
)

@Serializable
data class TracksResponse(
    val tracks: List<TrackDto> = emptyList(),
    val count: Int = 0
)

@Serializable
data class LikedIdsResponse(
    val liked_ids: List<String> = emptyList()
)

@Serializable
data class BackupListResponse(
    val backups: List<BackupFileSummary> = emptyList(),
    val count: Int = 0
)

@Serializable
data class DeviceDto(
    val id: String,
    val name: String,
    val type: String = "Computer",
    val is_active: Boolean = true,
    val volume_percent: Int = 80
)

@Serializable
data class SyncStatusResponse(
    val is_syncing: Boolean = false,
    val synced_tracks: Int = 0
)

@Serializable
data class GenericOkResponse(
    val status: String = "ok",
    val is_playing: Boolean? = null,
    val progress_ms: Long? = null,
    val volume_percent: Int? = null,
    val message: String? = null
)

@Serializable
data class ExportPlaylistResponse(
    val status: String = "ok",
    val playlist_id: String,
    val yt_playlist_id: String,
    val name: String,
    val total_tracks: Int,
    val web_url: String? = null,
    val message: String = ""
)

@Serializable
data class BakeShuffleRequest(
    val name: String? = null,
    val description: String? = null,
    val privacy: String = "PRIVATE"
)

@Serializable
data class BakeShuffleResponse(
    val status: String = "ok",
    val playlist_id: String,
    val yt_playlist_id: String,
    val name: String,
    val total_tracks: Int,
    val web_url: String? = null,
    val message: String = ""
)

@Serializable
data class SuggestionDto(
    val id: String? = null,
    val name: String = "",
    val title: String? = null,
    val artist: String? = null,
    val album: String? = null,
    val image_url: String? = null,
    val album_art_url: String? = null,
    val subtitle: String? = null,
    val type: String = "artist"
)

@Serializable
data class SuggestResponse(
    val suggestions: List<SuggestionDto> = emptyList()
)

@Serializable
data class GenreItemDto(
    val id: String,
    val name: String,
    val category: String
)

@Serializable
data class GenresResponse(
    val genres: List<GenreItemDto> = emptyList()
)

@Serializable
data class DecadeItemDto(
    val id: String,
    val name: String
)

@Serializable
data class DecadesResponse(
    val decades: List<DecadeItemDto> = emptyList()
)

@Serializable
data class ActiveVibeResponse(
    val artists: List<String> = emptyList(),
    val tracks: List<TrackDto> = emptyList()
)

@Serializable
data class DiscoveryChipDto(
    val value: String = "",
    val id: String? = null,
    val modifier: String = "AND"
)

@Serializable
data class DiscoveryGenerateRequest(
    val artists: List<DiscoveryChipDto> = emptyList(),
    val tracks: List<DiscoveryChipDto> = emptyList(),
    val genres: List<DiscoveryChipDto> = emptyList(),
    val decades: List<DiscoveryChipDto> = emptyList(),
    val keywords: List<DiscoveryChipDto> = emptyList(),
    val use_active_vibe: Boolean = false,
    val active_playlist_id: String? = null,
    val not_liked_songs: Boolean = false,
    val not_in_playlists: Boolean = false,
    val not_recently_played_days: Int? = null,
    val not_live: Boolean = false,
    val not_remix: Boolean = false,
    val only_live: Boolean = false,
    val only_remix: Boolean = false,
    val low_popularity_only: Boolean = false,
    val hidden_gem_target: String = "artist",
    val target_count: Int = 25,
    val true_shuffle: Boolean = false,
    val avoid_consecutive_artists: Boolean = true,
    val ignore_blacklist: Boolean = false
)

@Serializable
data class DiscoveryResponse(
    val tracks: List<TrackDto> = emptyList(),
    val count: Int = 0
)

fun Track.toDto(): TrackDto = TrackDto(
    id = id,
    uri = "yt:track:$id",
    title = title,
    artist = artist,
    primary_artist = ArtistUtils.extractPrimaryArtist(artist),
    album = album,
    duration_ms = durationMs,
    durationMs = durationMs,
    thumbnailUrl = thumbnailUrl,
    album_art_url = thumbnailUrl,
    loudnessDb = loudnessDb,
    year = year
)

fun openAppWindow(url: String) {
    val os = System.getProperty("os.name").lowercase()
    if (os.contains("mac")) {
        val browsers = listOf(
            "/Applications/Google Chrome.app/Contents/MacOS/Google Chrome",
            "/Applications/Brave Browser.app/Contents/MacOS/Brave Browser",
            "/Applications/Microsoft Edge.app/Contents/MacOS/Microsoft Edge",
            "/Applications/Arc.app/Contents/MacOS/Arc"
        )
        for (browserPath in browsers) {
            val file = File(browserPath)
            if (file.exists()) {
                try {
                    println("Launching dedicated native app window via $browserPath...")
                    ProcessBuilder(browserPath, "--app=$url").start()
                    return
                } catch (_: Exception) {}
            }
        }
        try {
            ProcessBuilder("open", url).start()
            return
        } catch (_: Exception) {}
    }

    try {
        if (java.awt.Desktop.isDesktopSupported() && java.awt.Desktop.getDesktop().isSupported(java.awt.Desktop.Action.BROWSE)) {
            java.awt.Desktop.getDesktop().browse(java.net.URI(url))
        }
    } catch (_: Exception) {}
}

fun main() {
    val port = 8888
    println("Starting kiki's youtube mixer (Desktop) on http://localhost:$port...")

    val desktopScope = CoroutineScope(SupervisorJob() + Dispatchers.Default)
    val queueRepository = InMemoryQueueRepository()
    val queueManager = ActiveQueueManager(queueRepository, desktopScope)
    val innertubeClient = InnertubeClient()
    val localPlaylistManager = LocalPlaylistManager()
    val localBlacklistManager = LocalBlacklistManager()

    val authFile = File(System.getProperty("user.dir"), "ytm_auth.json")
    val googleOAuthManager = GoogleOAuthManager(
        innertubeClient = innertubeClient,
        authFile = authFile,
        port = port
    )

    var currentUserName = "Connected Account"
    var currentUserEmail = ""

    if (authFile.exists()) {
        try {
            val authObj = Json.decodeFromString<AuthData>(authFile.readText())
            if (authObj.access_token.isNotBlank()) {
                innertubeClient.setOAuthToken(authObj.access_token)
                if (authObj.user_name.isNotBlank()) currentUserName = authObj.user_name
                if (authObj.user_email.isNotBlank()) currentUserEmail = authObj.user_email
            }
            if (authObj.cookie.isNotBlank() || authObj.sapisid.isNotBlank()) {
                innertubeClient.setCookies(authObj.cookie, authObj.sapisid)
            }
        } catch (_: Exception) {}
    }

    val browserLoginManager = BrowserLoginManager(
        innertubeClient = innertubeClient,
        localPlaylistManager = localPlaylistManager,
        authFile = authFile
    )

    var currentPlayerState = PlayerStateDto()

    // Automatically pop open dedicated native application window
    desktopScope.launch {
        delay(600L)
        openAppWindow("http://localhost:$port")
    }

    embeddedServer(Netty, port = port, host = "127.0.0.1") {
        install(CORS) {
            anyHost()
            allowHeader("Content-Type")
            allowMethod(HttpMethod.Options)
            allowMethod(HttpMethod.Put)
            allowMethod(HttpMethod.Patch)
            allowMethod(HttpMethod.Delete)
        }
        install(ContentNegotiation) {
            json(Json {
                prettyPrint = true
                isLenient = true
                ignoreUnknownKeys = true
                encodeDefaults = true
            })
        }

        routing {
            // Serve frontend resources
            staticResources("/", "frontend", index = "index.html")

            get("/api/health") {
                call.respond(GenericOkResponse(status = "ok"))
            }

            get("/api/status") {
                val isAuthenticated = innertubeClient.hasAuth()
                val likedPl = localPlaylistManager.getPlaylist("liked_songs")
                val allPls = localPlaylistManager.getAll()
                val totalTracks = allPls.sumOf { it.total_tracks }
                call.respond(StatusResponse(
                    authenticated = isAuthenticated,
                    is_mac = System.getProperty("os.name").lowercase().contains("mac"),
                    has_synced_tracks = totalTracks > 0,
                    liked_tracks = likedPl?.total_tracks ?: 0,
                    total_tracks = totalTracks,
                    access_url = "http://localhost:$port",
                    app = "kiki's youtube mixer",
                    service = "YouTube Music",
                    user = UserDto(
                        display_name = if (isAuthenticated) currentUserName else "Guest Mode",
                        id = if (isAuthenticated) (currentUserEmail.ifBlank { "ytm_connected_user" }) else "guest"
                    )
                ))
            }

            get("/callback") {
                val code = call.request.queryParameters["code"]
                val error = call.request.queryParameters["error"]

                if (!code.isNullOrEmpty()) {
                    val authData = googleOAuthManager.exchangeCodeForToken(code)
                    if (authData != null) {
                        currentUserName = authData.user_name.ifBlank { "Connected Account" }
                        currentUserEmail = authData.user_email

                        // Trigger safe automatic background sync with dual snapshot backups
                        desktopScope.launch {
                            try {
                                localPlaylistManager.createBackupSnapshot("pre_sync_snapshot")
                                val likedTracks = innertubeClient.getLikedSongs()
                                if (likedTracks.isNotEmpty()) {
                                    localPlaylistManager.mergeTracks("liked_songs", likedTracks.map { it.toDto() })
                                    localPlaylistManager.markSynced("liked_songs", "LM")
                                }
                                val remotePlaylists = innertubeClient.getLibraryPlaylists()
                                for (remotePl in remotePlaylists) {
                                    val rTracks = innertubeClient.getPlaylistTracks(remotePl.id)
                                    val dtos = rTracks.map { it.toDto() }
                                    val existing = localPlaylistManager.getAll().find { it.yt_playlist_id == remotePl.id || it.name.equals(remotePl.title, ignoreCase = true) }
                                    if (existing != null) {
                                        localPlaylistManager.mergeTracks(existing.id, dtos)
                                        localPlaylistManager.markSynced(existing.id, remotePl.id)
                                    } else {
                                        val saved = localPlaylistManager.savePlaylist(
                                            name = remotePl.title,
                                            description = remotePl.description,
                                            tracks = dtos,
                                            overwrite = false,
                                            playlistId = null
                                        )
                                        localPlaylistManager.markSynced(saved.id, remotePl.id)
                                    }
                                }
                                localPlaylistManager.createBackupSnapshot("post_sync_snapshot")
                            } catch (e: Exception) {
                                System.err.println("Auto sync after OAuth failed: ${e.message}")
                            }
                        }

                        val html = """
                            <!DOCTYPE html>
                            <html>
                            <head>
                                <meta charset="utf-8">
                                <title>Connected to kiki's youtube mixer</title>
                                <style>
                                    body { font-family: -apple-system, BlinkMacSystemFont, "Segoe UI", Roboto, sans-serif; background: #0f0f12; color: #fff; display: flex; align-items: center; justify-content: center; height: 100vh; margin: 0; }
                                    .card { background: #1a1a24; border: 1px solid #2f2f3d; padding: 40px; border-radius: 16px; text-align: center; max-width: 440px; box-shadow: 0 10px 30px rgba(0,0,0,0.5); }
                                    h1 { font-size: 24px; margin-bottom: 12px; color: #00e676; }
                                    p { color: #a0a0b2; line-height: 1.6; margin-bottom: 24px; font-size: 15px; }
                                    .badge { display: inline-block; background: #252538; padding: 8px 16px; border-radius: 20px; font-weight: 600; color: #fff; margin-bottom: 20px; border: 1px solid #3d3d5c; }
                                    .btn { background: #ff0055; color: #fff; border: none; padding: 12px 24px; border-radius: 8px; font-weight: bold; cursor: pointer; text-decoration: none; display: inline-block; font-size: 14px; }
                                </style>
                            </head>
                            <body>
                                <div class="card">
                                    <div style="font-size: 48px; margin-bottom: 16px;">✨</div>
                                    <h1>Account Connected!</h1>
                                    <div class="badge">👤 ${authData.user_name.ifBlank { "Google User" }}</div>
                                    <p>Your Google account has been connected and your YouTube Music library is now synchronizing safely.</p>
                                    <a href="http://localhost:$port?auth=success" class="btn">Return to kiki's youtube mixer</a>
                                </div>
                                <script>
                                    setTimeout(() => { window.location.href = "http://localhost:$port?auth=success"; }, 1500);
                                </script>
                            </body>
                            </html>
                        """.trimIndent()
                        call.respondText(html, io.ktor.http.ContentType.Text.Html)
                        return@get
                    }
                }

                val failHtml = """
                    <!DOCTYPE html>
                    <html>
                    <head><meta charset="utf-8"><title>Connection Cancelled</title></head>
                    <body style="background:#0f0f12;color:#fff;font-family:sans-serif;text-align:center;padding-top:100px;">
                        <h2>Connection was not completed (${error ?: "cancelled"})</h2>
                        <p><a href="http://localhost:$port" style="color:#ff0055;">Back to app</a></p>
                    </body>
                    </html>
                """.trimIndent()
                call.respondText(failHtml, io.ktor.http.ContentType.Text.Html)
            }

            get("/api/auth/login") {
                val authUrl = googleOAuthManager.openBrowserForLogin()
                call.respond(AuthLoginResponse(
                    status = "ok",
                    authenticated = innertubeClient.hasAuth(),
                    auth_url = authUrl,
                    message = "Opened browser. Please choose your Google account."
                ))
            }

            post("/api/auth/login") {
                val authUrl = googleOAuthManager.openBrowserForLogin()
                call.respond(AuthLoginResponse(
                    status = "ok",
                    authenticated = innertubeClient.hasAuth(),
                    auth_url = authUrl,
                    message = "Opened browser. Please choose your Google account."
                ))
            }

            post("/api/auth/cookie") {
                val req = call.receive<AuthCookieRequest>()
                innertubeClient.setCookies(req.cookie, req.sapisid ?: "")
                try {
                    val authObj = AuthData(cookie = innertubeClient.getCookieString(), sapisid = innertubeClient.getSapisid())
                    authFile.writeText(Json.encodeToString(authObj))
                } catch (_: Exception) {}
                call.respond(GenericOkResponse(status = "ok", message = "Session cookie saved successfully"))
            }

            post("/api/auth/logout") {
                browserLoginManager.stopLogin()
                innertubeClient.clearAuth()
                currentUserName = "Connected Account"
                currentUserEmail = ""
                if (authFile.exists()) {
                    authFile.delete()
                }
                call.respond(GenericOkResponse(status = "ok", message = "Logged out successfully"))
            }

            post("/api/sync") {
                try {
                    // Pre-sync backup snapshot of current local library
                    localPlaylistManager.createBackupSnapshot("pre_sync_snapshot")

                    var likedCount = 0
                    var playlistCount = 0

                    // 1. Fetch Liked Songs from YouTube Music & Safe Merge
                    val likedTracks = innertubeClient.getLikedSongs()
                    if (likedTracks.isNotEmpty()) {
                        val likedDtos = likedTracks.map { it.toDto() }
                        // Safe non-destructive merge: preserve local offline liked songs + add remote liked songs
                        localPlaylistManager.mergeTracks("liked_songs", likedDtos)
                        localPlaylistManager.markSynced("liked_songs", "LM")
                        likedCount = localPlaylistManager.getPlaylist("liked_songs")?.total_tracks ?: likedTracks.size
                    }

                    // 2. Fetch User Playlists
                    val remotePlaylists = innertubeClient.getLibraryPlaylists()
                    for (remotePl in remotePlaylists) {
                        try {
                            val rTracks = innertubeClient.getPlaylistTracks(remotePl.id)
                            val dtos = rTracks.map { it.toDto() }
                            val existing = localPlaylistManager.getAll().find { it.yt_playlist_id == remotePl.id || it.name.equals(remotePl.title, ignoreCase = true) }
                            if (existing != null) {
                                localPlaylistManager.mergeTracks(existing.id, dtos)
                                localPlaylistManager.markSynced(existing.id, remotePl.id)
                            } else {
                                val saved = localPlaylistManager.savePlaylist(
                                    name = remotePl.title,
                                    description = remotePl.description,
                                    tracks = dtos,
                                    overwrite = false,
                                    playlistId = null
                                )
                                localPlaylistManager.markSynced(saved.id, remotePl.id)
                            }
                            playlistCount++
                        } catch (_: Exception) {}
                    }

                    // Post-sync full backup snapshot of complete consolidated library
                    val latestBackup = localPlaylistManager.createBackupSnapshot("post_sync_snapshot")

                    val warning = if (!innertubeClient.hasAuth() && likedTracks.isEmpty()) {
                        "Not connected with YouTube Music session. Please open Settings ⚙️ to add your session cookie."
                    } else null

                    call.respond(SyncLibraryResponse(
                        status = "ok",
                        liked_count = likedCount,
                        playlists_count = playlistCount,
                        warning = warning,
                        message = "Synced $likedCount Liked Songs and $playlistCount playlists. Local backup snapshot created."
                    ))
                } catch (e: Exception) {
                    System.err.println("Error during library sync: ${e.message}")
                    call.respond(HttpStatusCode.InternalServerError, SyncLibraryResponse(
                        status = "error",
                        warning = "Failed to sync library: ${e.message}"
                    ))
                }
            }

            get("/api/backup/list") {
                val list = localPlaylistManager.listBackups()
                call.respond(BackupListResponse(backups = list, count = list.size))
            }

            get("/api/backup/latest") {
                val latest = localPlaylistManager.getLatestBackup()
                if (latest != null && latest.exists()) {
                    call.respondText(latest.readText(), io.ktor.http.ContentType.Application.Json)
                } else {
                    call.respond(HttpStatusCode.NotFound, GenericOkResponse(status = "error", message = "No backups found"))
                }
            }

            get("/api/backup/file/{filename}") {
                val filename = call.parameters["filename"] ?: ""
                val f = localPlaylistManager.getBackupFile(filename)
                if (f != null && f.exists()) {
                    call.respondText(f.readText(), io.ktor.http.ContentType.Application.Json)
                } else {
                    call.respond(HttpStatusCode.NotFound, GenericOkResponse(status = "error", message = "Backup not found: $filename"))
                }
            }

            post("/api/backup/create") {
                val f = localPlaylistManager.createBackupSnapshot("manual_backup")
                if (f != null) {
                    call.respond(GenericOkResponse(status = "ok", message = "Backup snapshot created: ${f.name}"))
                } else if (localPlaylistManager.getLatestBackup() != null) {
                    call.respond(GenericOkResponse(status = "ok", message = "Library content is unchanged since previous snapshot. No duplicate created."))
                } else {
                    call.respond(HttpStatusCode.InternalServerError, GenericOkResponse(status = "error", message = "Failed to create backup"))
                }
            }

            post("/api/backup/restore/{filename}") {
                val filename = call.parameters["filename"] ?: ""
                val f = localPlaylistManager.getBackupFile(filename)
                if (f != null && f.exists()) {
                    localPlaylistManager.createBackupSnapshot("pre_restore_safety_snapshot", force = true)
                    val ok = localPlaylistManager.restoreBackup(f)
                    if (ok) {
                        call.respond(GenericOkResponse(status = "ok", message = "Library restored from ${f.name}"))
                    } else {
                        call.respond(HttpStatusCode.InternalServerError, GenericOkResponse(status = "error", message = "Failed to restore backup"))
                    }
                } else {
                    call.respond(HttpStatusCode.NotFound, GenericOkResponse(status = "error", message = "Backup file not found: $filename"))
                }
            }

            delete("/api/backup/file/{filename}") {
                val filename = call.parameters["filename"] ?: ""
                val ok = localPlaylistManager.deleteBackup(filename)
                if (ok) {
                    call.respond(GenericOkResponse(status = "ok", message = "Deleted backup snapshot: $filename"))
                } else {
                    call.respond(HttpStatusCode.NotFound, GenericOkResponse(status = "error", message = "Could not delete backup: $filename"))
                }
            }

            // Live YouTube Music Search endpoint
            get("/api/search") {
                val query = call.request.queryParameters["q"] ?: ""
                val type = call.request.queryParameters["type"]
                if (query.isBlank()) {
                    call.respond(SearchResponse(results = emptyList()))
                } else {
                    try {
                        val filter = if (type == "track" || type == "song") "track" else if (type == "artist") "artist" else null
                        var tracks = innertubeClient.search(query, filter)
                        
                        // If searching by song/track title and none of the results contain the query in their title,
                        // also query YouTube with quotes to prioritize songs whose title specifically matches
                        if (type == "track" || type == "song") {
                            val hasTitleMatch = tracks.any { it.title.contains(query, ignoreCase = true) }
                            if (!hasTitleMatch) {
                                try {
                                    val quotedTracks = innertubeClient.search("\"$query\"", "track")
                                    tracks = (quotedTracks + tracks).distinctBy { it.id }
                                } catch (_: Exception) {}
                            }
                        } else if (type == "artist") {
                            val hasArtistMatch = tracks.any { it.artist.contains(query, ignoreCase = true) }
                            if (!hasArtistMatch) {
                                try {
                                    val moreTracks = innertubeClient.search("$query songs", "track")
                                    tracks = (tracks + moreTracks).distinctBy { it.id }
                                } catch (_: Exception) {}
                            }
                        }

                        val dtos = tracks.map { it.toDto() }
                        call.respond(SearchResponse(results = dtos))
                    } catch (e: Exception) {
                        System.err.println("Search error: ${e.message}")
                        call.respond(SearchResponse(results = emptyList(), error = e.message ?: "Search failed"))
                    }
                }
            }

            // Active Queue Endpoints
            get("/api/queue") {
                call.respond(queueManager.queueState.value)
            }

            post("/api/queue/shuffle") {
                val current = queueManager.queueState.value
                val shuffled = ShuffleEngine.shuffleQueue(
                    currentTracks = current.tracks,
                    currentIndex = current.currentIndex,
                    applyAntiClumping = true
                )
                queueManager.setQueue(shuffled, startIndex = 0)
                call.respond(queueManager.queueState.value)
            }

            post("/api/queue/move") {
                val req = call.receive<MoveRequest>()
                queueManager.moveTrack(req.fromIndex, req.toIndex)
                call.respond(queueManager.queueState.value)
            }

            post("/api/queue/append") {
                val req = call.receive<AppendTracksRequest>()
                queueManager.appendTracks(req.tracks)
                call.respond(queueManager.queueState.value)
            }

            post("/api/queue/play-next") {
                queueManager.playNext()
                call.respond(queueManager.queueState.value)
            }

            post("/api/queue/play-prev") {
                queueManager.playPrevious()
                call.respond(queueManager.queueState.value)
            }

            // Player State & Playback Controls
            get("/api/player/state") {
                call.respond(currentPlayerState)
            }

            post("/api/player/state") {
                val newState = call.receive<PlayerStateDto>()
                currentPlayerState = newState
                call.respond(GenericOkResponse(status = "ok"))
            }

            post("/api/player/play") {
                val req = call.receive<PlayRequest>()
                var targetId = req.track_id
                if (targetId.isNullOrEmpty() && req.uris.isNotEmpty()) {
                    val firstUri = req.uris.first()
                    targetId = firstUri.removePrefix("yt:track:").removePrefix("spotify:track:")
                }

                if (!targetId.isNullOrEmpty()) {
                    val q = queueManager.queueState.value
                    val existingIdx = q.tracks.indexOfFirst { it.id == targetId }
                    if (existingIdx != -1) {
                        queueManager.skipToIndex(existingIdx)
                        val track = q.tracks[existingIdx]
                        currentPlayerState = currentPlayerState.copy(
                            is_playing = true,
                            progress_ms = 0L,
                            item = PlayerItemDto(
                                id = track.id,
                                uri = "yt:track:${track.id}",
                                title = track.title,
                                artist = track.artist,
                                album = track.album,
                                duration_ms = track.durationMs,
                                album_art_url = track.thumbnailUrl
                            )
                        )
                    }
                }
                call.respond(GenericOkResponse(status = "ok"))
            }

            post("/api/player/control") {
                val req = call.receive<PlayerControlRequest>()
                when (req.action) {
                    "playpause" -> {
                        currentPlayerState = currentPlayerState.copy(is_playing = !currentPlayerState.is_playing)
                    }
                    "play" -> {
                        currentPlayerState = currentPlayerState.copy(is_playing = true)
                    }
                    "pause" -> {
                        currentPlayerState = currentPlayerState.copy(is_playing = false)
                    }
                    "next" -> {
                        queueManager.playNext()
                        val q = queueManager.queueState.value
                        val cur = q.currentTrack
                        if (cur != null) {
                            currentPlayerState = currentPlayerState.copy(
                                is_playing = true,
                                progress_ms = 0L,
                                item = PlayerItemDto(
                                    id = cur.id,
                                    uri = "yt:track:${cur.id}",
                                    title = cur.title,
                                    artist = cur.artist,
                                    album = cur.album,
                                    duration_ms = cur.durationMs,
                                    album_art_url = cur.thumbnailUrl
                                )
                            )
                        }
                    }
                    "prev" -> {
                        queueManager.playPrevious()
                        val q = queueManager.queueState.value
                        val cur = q.currentTrack
                        if (cur != null) {
                            currentPlayerState = currentPlayerState.copy(
                                is_playing = true,
                                progress_ms = 0L,
                                item = PlayerItemDto(
                                    id = cur.id,
                                    uri = "yt:track:${cur.id}",
                                    title = cur.title,
                                    artist = cur.artist,
                                    album = cur.album,
                                    duration_ms = cur.durationMs,
                                    album_art_url = cur.thumbnailUrl
                                )
                            )
                        }
                    }
                }
                call.respond(GenericOkResponse(status = "ok", is_playing = currentPlayerState.is_playing))
            }

            post("/api/player/seek") {
                val req = call.receive<PlayerSeekRequest>()
                currentPlayerState = currentPlayerState.copy(progress_ms = req.position_ms)
                call.respond(GenericOkResponse(status = "ok", progress_ms = req.position_ms))
            }

            post("/api/player/volume") {
                val req = call.receive<PlayerVolumeRequest>()
                currentPlayerState = currentPlayerState.copy(volume_percent = req.volume_percent)
                call.respond(GenericOkResponse(status = "ok", volume_percent = req.volume_percent))
            }

            get("/api/player/devices") {
                call.respond(listOf(
                    DeviceDto(
                        id = "mac_speakers",
                        name = "Mac Desktop Output",
                        volume_percent = currentPlayerState.volume_percent
                    )
                ))
            }

            get("/api/tracks/liked-ids") {
                val likedPl = localPlaylistManager.ensureLikedSongsPlaylist()
                val ids = likedPl.tracks.map { it.id }
                call.respond(LikedIdsResponse(liked_ids = ids))
            }

            post("/api/tracks/like") {
                val req = call.receive<LikeTrackRequest>()
                localPlaylistManager.ensureLikedSongsPlaylist()
                if (req.liked) {
                    var trackDto = req.track
                    if (trackDto == null && req.track_id.isNotEmpty()) {
                        val qTrack = queueManager.queueState.value.tracks.find { it.id == req.track_id }
                        if (qTrack != null) trackDto = qTrack.toDto()
                    }
                    if (trackDto != null) {
                        localPlaylistManager.addTrack("liked_songs", trackDto)
                    }
                } else {
                    localPlaylistManager.removeTrack("liked_songs", req.track_id)
                }
                call.respond(GenericOkResponse(status = "ok"))
            }

            get("/api/tracks/resolve-decade") {
                val artist = call.request.queryParameters["artist"] ?: ""
                val title = call.request.queryParameters["title"] ?: ""
                var decade: String? = null
                var year: Int? = null

                val yearRegex = Regex("""\b(19\d\d|20\d\d)\b""")
                val quickMatch = yearRegex.find("$title $artist")?.value
                if (quickMatch != null) {
                    year = quickMatch.toIntOrNull()
                } else if (title.isNotBlank() || artist.isNotBlank()) {
                    try {
                        val query = if (artist.isNotBlank() && title.isNotBlank()) "$title $artist" else "$title$artist"
                        val results = innertubeClient.search(query, "track")
                        val match = results.firstOrNull { 
                            (title.isNotBlank() && it.title.contains(title, ignoreCase = true)) ||
                            (artist.isNotBlank() && it.artist.contains(artist, ignoreCase = true))
                        } ?: results.firstOrNull()

                        if (match != null) {
                            val foundYear = match.year ?: yearRegex.find("${match.album} ${match.title}")?.value
                            if (foundYear != null) {
                                year = foundYear.toIntOrNull()
                            }
                        }
                    } catch (_: Exception) {}
                }

                if (year != null) {
                    decade = when (year) {
                        in 1960..1969 -> "60s"
                        in 1970..1979 -> "70s"
                        in 1980..1989 -> "80s"
                        in 1990..1999 -> "90s"
                        in 2000..2009 -> "00s"
                        in 2010..2019 -> "10s"
                        in 2020..2029 -> "20s"
                        else -> null
                    }
                }

                call.respond(mapOf("decade" to decade, "year" to year?.toString()))
            }

            get("/api/playlists") {
                call.respond(PlaylistsResponse(playlists = localPlaylistManager.getSummaries()))
            }

            post("/api/playlists/create") {
                val req = call.receive<CreatePlaylistRequest>()
                val resolvedTracks: List<TrackDto> = if (req.tracks.isNotEmpty()) {
                    req.tracks
                } else if (req.track_ids.isNotEmpty()) {
                    val currentQueue = queueManager.queueState.value.tracks
                    req.track_ids.mapNotNull { id ->
                        currentQueue.find { it.id == id }?.toDto()
                    }
                } else {
                    emptyList()
                }

                val pl = localPlaylistManager.savePlaylist(
                    name = req.name,
                    description = req.description,
                    tracks = resolvedTracks,
                    overwrite = req.overwrite,
                    playlistId = req.playlist_id
                )
                call.respond(CreatePlaylistResponse(
                    status = "ok",
                    id = pl.id,
                    name = pl.name,
                    total_tracks = pl.total_tracks,
                    overwritten = req.overwrite
                ))
            }

            post("/api/playlists/{id}/rename") {
                val id = call.parameters["id"] ?: ""
                val req = call.receive<RenamePlaylistRequest>()
                val success = localPlaylistManager.renamePlaylist(id, req.resolvedName)
                if (success) {
                    call.respond(GenericOkResponse(status = "ok"))
                } else {
                    call.respond(HttpStatusCode.NotFound, GenericOkResponse(status = "error"))
                }
            }

            delete("/api/playlists/{id}") {
                val id = call.parameters["id"] ?: ""
                val success = localPlaylistManager.deletePlaylist(id)
                if (success) {
                    call.respond(GenericOkResponse(status = "ok"))
                } else {
                    call.respond(HttpStatusCode.NotFound, GenericOkResponse(status = "error"))
                }
            }

            post("/api/playlists/{id}/sync-check") {
                val id = call.parameters["id"] ?: ""
                val pl = localPlaylistManager.getPlaylist(id)
                if (pl == null) {
                    call.respond(HttpStatusCode.NotFound, GenericOkResponse(status = "error"))
                    return@post
                }

                val hasConflict = pl.yt_playlist_id != null && pl.last_synced_at != null && pl.last_modified_locally > pl.last_synced_at
                call.respond(SyncCheckResponse(
                    status = "ok",
                    playlist_id = pl.id,
                    name = pl.name,
                    yt_playlist_id = pl.yt_playlist_id,
                    has_conflict = hasConflict,
                    is_new_remote = pl.yt_playlist_id != null && hasConflict,
                    local_count = pl.tracks.size,
                    local_updated_at = pl.last_modified_locally,
                    remote_count = pl.tracks.size,
                    remote_updated_at = pl.last_synced_at,
                    recommended_direction = "app_to_yt"
                ))
            }

            post("/api/playlists/{id}/sync") {
                val id = call.parameters["id"] ?: ""
                val req = try { call.receive<ExecuteSyncRequest>() } catch (_: Exception) { ExecuteSyncRequest() }
                val pl = localPlaylistManager.getPlaylist(id)
                if (pl == null) {
                    call.respond(HttpStatusCode.NotFound, GenericOkResponse(status = "error"))
                    return@post
                }

                val direction = req.direction
                val updatedSummary = when (direction) {
                    "yt_to_app" -> localPlaylistManager.applyRemoteTracks(id, pl.tracks)
                    "merge" -> localPlaylistManager.mergeTracks(id, pl.tracks)
                    else -> {
                        // Push to YouTube Music if needed
                        var ytId = pl.yt_playlist_id
                        if (ytId.isNullOrEmpty() || ytId.startsWith("yt_pl_") || ytId == "LM") {
                            val remoteId = try {
                                innertubeClient.createPlaylist(
                                    title = pl.name,
                                    description = pl.description.ifEmpty { "Curated with kiki's youtube mixer" },
                                    privacyStatus = "PRIVATE",
                                    videoIds = pl.tracks.map { it.id }
                                )
                            } catch (_: Exception) { null }
                            if (remoteId != null) ytId = remoteId
                        } else {
                            try {
                                innertubeClient.addTracksToPlaylist(ytId, pl.tracks.map { it.id })
                            } catch (_: Exception) {}
                        }
                        localPlaylistManager.markSynced(id, ytId)
                    }
                } ?: pl.toSummary()

                val msg = when (direction) {
                    "yt_to_app" -> "⬇️ Playlist \"${pl.name}\" updated from YouTube (${updatedSummary.total_tracks} tracks)."
                    "merge" -> "🔀 Playlist \"${pl.name}\" merged successfully (${updatedSummary.total_tracks} tracks)."
                    else -> "☁️ Playlist \"${pl.name}\" synced to YouTube Music (${updatedSummary.total_tracks} tracks)."
                }

                call.respond(ExecuteSyncResponse(
                    status = "ok",
                    message = msg,
                    direction_applied = direction,
                    playlist = updatedSummary
                ))
            }

            post("/api/playlists/{id}/export") {
                val id = call.parameters["id"] ?: ""
                val pl = localPlaylistManager.getPlaylist(id)
                if (pl == null) {
                    call.respond(HttpStatusCode.NotFound, GenericOkResponse(status = "error", message = "Playlist not found"))
                    return@post
                }

                var ytId = pl.yt_playlist_id
                val videoIds = pl.tracks.map { it.id }
                var isCreatedNew = false

                if (ytId.isNullOrEmpty() || ytId.startsWith("yt_pl_") || ytId == "LM") {
                    val desc = pl.description.ifEmpty { "Curated with kiki's youtube mixer" }
                    val remoteId = try {
                        innertubeClient.createPlaylist(title = pl.name, description = desc, privacyStatus = "PRIVATE", videoIds = videoIds)
                    } catch (_: Exception) { null }
                    ytId = remoteId ?: "yt_pl_${pl.id.removePrefix("local_pl_")}"
                    isCreatedNew = true
                } else {
                    try {
                        innertubeClient.addTracksToPlaylist(ytId, videoIds)
                    } catch (_: Exception) {}
                }

                val updatedSummary = localPlaylistManager.markSynced(id, ytId) ?: pl.toSummary()
                val webUrl = if (ytId.startsWith("PL") || ytId.length >= 10) "https://music.youtube.com/playlist?list=$ytId" else null
                val message = if (isCreatedNew) {
                    "☁️ Playlist \"${pl.name}\" exported to YouTube Music (${pl.tracks.size} tracks)."
                } else {
                    "☁️ Playlist \"${pl.name}\" synced with YouTube Music (${pl.tracks.size} tracks)."
                }

                call.respond(ExportPlaylistResponse(
                    status = "ok",
                    playlist_id = id,
                    yt_playlist_id = ytId,
                    name = pl.name,
                    total_tracks = pl.tracks.size,
                    web_url = webUrl,
                    message = message
                ))
            }

            post("/api/queue/bake-shuffle") {
                val req = try { call.receive<BakeShuffleRequest>() } catch (_: Exception) { BakeShuffleRequest() }
                val currentQueue = queueManager.queueState.value.tracks
                if (currentQueue.isEmpty()) {
                    call.respond(HttpStatusCode.BadRequest, GenericOkResponse(status = "error", message = "Active queue is empty"))
                    return@post
                }

                val timestamp = java.text.SimpleDateFormat("yyyy-MM-dd HH:mm").format(java.util.Date())
                val plName = req.name?.ifBlank { null } ?: "Bake Shuffle - $timestamp"
                val plDesc = req.description?.ifBlank { null } ?: "Randomized sequence baked from kiki's youtube mixer"
                val trackDtos = currentQueue.map { it.toDto() }
                val videoIds = currentQueue.map { it.id }

                val localPl = localPlaylistManager.savePlaylist(
                    name = plName,
                    description = plDesc,
                    tracks = trackDtos,
                    overwrite = false,
                    playlistId = null
                )

                val remoteYtId = try {
                    innertubeClient.createPlaylist(
                        title = plName,
                        description = plDesc,
                        privacyStatus = req.privacy,
                        videoIds = videoIds
                    )
                } catch (_: Exception) { null }

                val finalYtId = remoteYtId ?: "yt_pl_${localPl.id.removePrefix("local_pl_")}"
                localPlaylistManager.markSynced(localPl.id, finalYtId)

                val webUrl = if (finalYtId.startsWith("PL") || finalYtId.length >= 10) "https://music.youtube.com/playlist?list=$finalYtId" else null
                val msg = "🔥 Shuffled queue baked and exported to YouTube Music as \"$plName\" (${trackDtos.size} tracks)!"

                call.respond(BakeShuffleResponse(
                    status = "ok",
                    playlist_id = localPl.id,
                    yt_playlist_id = finalYtId,
                    name = plName,
                    total_tracks = trackDtos.size,
                    web_url = webUrl,
                    message = msg
                ))
            }

            post("/api/playlists/{id}/add-track") {
                val id = call.parameters["id"] ?: ""
                val req = call.receive<AddTrackToPlaylistRequest>()
                var trackDto = req.track
                if (trackDto == null && req.track_id.isNotEmpty()) {
                    val qTrack = queueManager.queueState.value.tracks.find { it.id == req.track_id }
                    if (qTrack != null) trackDto = qTrack.toDto()
                }
                if (trackDto != null) {
                    localPlaylistManager.addTrack(id, trackDto)
                }
                call.respond(GenericOkResponse(status = "ok"))
            }

            post("/api/playlists/reorder") {
                val req = call.receive<ReorderPlaylistRequest>()
                if (req.playlist_id == "liked_songs") {
                    localPlaylistManager.ensureLikedSongsPlaylist()
                }
                val success = localPlaylistManager.reorderTracks(req.playlist_id, req.track_ids)
                if (success) {
                    call.respond(GenericOkResponse(status = "ok"))
                } else {
                    call.respond(HttpStatusCode.NotFound, GenericOkResponse(status = "error"))
                }
            }

            post("/api/playlists/{id}/remove-track") {
                val id = call.parameters["id"] ?: ""
                val req = call.receive<RemoveTrackFromPlaylistRequest>()
                if (id == "liked_songs") {
                    localPlaylistManager.ensureLikedSongsPlaylist()
                }
                val success = localPlaylistManager.removeTrack(id, req.track_id)
                if (success) {
                    call.respond(GenericOkResponse(status = "ok"))
                } else {
                    call.respond(HttpStatusCode.NotFound, GenericOkResponse(status = "error"))
                }
            }

            get("/api/tracks") {
                val playlistId = call.request.queryParameters["playlist_id"]
                val searchQuery = (call.request.queryParameters["search"] ?: "").trim()
                val sortBy = call.request.queryParameters["sort_by"] ?: "order_index"
                val sortDirection = call.request.queryParameters["sort_direction"] ?: "asc"

                val likedPl = localPlaylistManager.ensureLikedSongsPlaylist()
                val likedIdSet = likedPl.tracks.map { it.id }.toSet()

                var rawTracks: List<TrackDto> = if (!playlistId.isNullOrEmpty() && playlistId != "all") {
                    if (playlistId == "liked_songs") {
                        likedPl.tracks
                    } else {
                        val pl = localPlaylistManager.getPlaylist(playlistId)
                        pl?.tracks ?: emptyList()
                    }
                } else {
                    val q = queueManager.queueState.value
                    q.tracks.map { it.toDto() }
                }

                // Filter out any private or deleted video entries
                rawTracks = rawTracks.filter { !localPlaylistManager.isPrivateOrDeletedTrack(it) }

                // Tag is_liked accurately for all tracks
                rawTracks = rawTracks.map { t ->
                    val liked = (playlistId == "liked_songs") || likedIdSet.contains(t.id)
                    if (t.is_liked != liked) t.copy(is_liked = liked) else t
                }

                if (searchQuery.isNotEmpty()) {
                    rawTracks = rawTracks.filter { t ->
                        t.title.contains(searchQuery, ignoreCase = true) ||
                        t.artist.contains(searchQuery, ignoreCase = true) ||
                        t.album.contains(searchQuery, ignoreCase = true)
                    }
                }

                if (sortBy != "order_index") {
                    rawTracks = when (sortBy) {
                        "title" -> if (sortDirection == "desc") rawTracks.sortedByDescending { it.title.lowercase() } else rawTracks.sortedBy { it.title.lowercase() }
                        "artist" -> if (sortDirection == "desc") rawTracks.sortedByDescending { it.artist.lowercase() } else rawTracks.sortedBy { it.artist.lowercase() }
                        "album" -> if (sortDirection == "desc") rawTracks.sortedByDescending { it.album.lowercase() } else rawTracks.sortedBy { it.album.lowercase() }
                        "duration" -> if (sortDirection == "desc") rawTracks.sortedByDescending { it.durationMs } else rawTracks.sortedBy { it.durationMs }
                        else -> rawTracks
                    }
                }

                call.respond(TracksResponse(tracks = rawTracks, count = rawTracks.size))
            }

            get("/api/sync/status") {
                call.respond(SyncStatusResponse())
            }

            post("/api/credentials") {
                call.respond(GenericOkResponse(status = "ok"))
            }

            // Artist Blacklist Endpoints
            get("/api/blacklist/artists") {
                val artists = localBlacklistManager.getAll()
                call.respond(BlacklistResponse(artists = artists, count = artists.size))
            }

            post("/api/blacklist/artists") {
                val req = call.receive<AddBlacklistRequest>()
                if (req.name.isBlank()) {
                    call.respond(HttpStatusCode.BadRequest, GenericOkResponse(status = "error"))
                    return@post
                }
                val added = localBlacklistManager.add(req.name, req.external_id)
                call.respond(AddBlacklistResponse(status = "ok", artist = added))
            }

            delete("/api/blacklist/artists/{name_or_id}") {
                val nameOrId = call.parameters["name_or_id"] ?: ""
                val success = localBlacklistManager.remove(nameOrId)
                if (success) {
                    call.respond(GenericOkResponse(status = "ok"))
                } else {
                    call.respond(HttpStatusCode.NotFound, GenericOkResponse(status = "error"))
                }
            }

            // Discovery Studio Endpoints
            get("/api/discovery/suggest") {
                val q = (call.request.queryParameters["q"] ?: "").trim()
                val type = call.request.queryParameters["type"] ?: "all"
                val limit = call.request.queryParameters["limit"]?.toIntOrNull() ?: 8

                if (q.isBlank()) {
                    call.respond(SuggestResponse(suggestions = emptyList()))
                    return@get
                }

                try {
                    val suggestions = mutableListOf<SuggestionDto>()

                    if (type == "artist" || type == "all") {
                        val tracks = try { innertubeClient.search(q, "track") } catch (_: Exception) { emptyList() }
                        val localTracks = localPlaylistManager.getAll().flatMap { it.tracks } +
                                queueManager.queueState.value.tracks.map { it.toDto() }

                        val candidateArtists = mutableMapOf<String, String>() // artistName -> thumbnail
                        for (t in tracks) {
                            if (t.artist.isNotBlank() && t.artist != "Unknown Artist") {
                                candidateArtists.putIfAbsent(t.artist, t.thumbnailUrl)
                                for (splitA in SearchUtils.splitArtists(t.artist)) {
                                    candidateArtists.putIfAbsent(splitA, t.thumbnailUrl)
                                }
                            }
                        }
                        for (lt in localTracks) {
                            if (lt.artist.isNotBlank() && lt.artist != "Unknown Artist") {
                                candidateArtists.putIfAbsent(lt.artist, lt.thumbnailUrl)
                                for (splitA in SearchUtils.splitArtists(lt.artist)) {
                                    candidateArtists.putIfAbsent(splitA, lt.thumbnailUrl)
                                }
                            }
                        }

                        val qNorm = SearchUtils.normalize(q)
                        val matchedArtists = candidateArtists.keys
                            .filter { artistName ->
                                val aNorm = SearchUtils.normalize(artistName)
                                aNorm.contains(qNorm) || SearchUtils.fuzzyMatches(q, artistName)
                            }
                            .sortedWith(
                                compareBy<String> { artistName ->
                                    val aNorm = SearchUtils.normalize(artistName)
                                    when {
                                        aNorm == qNorm -> 0
                                        aNorm.startsWith(qNorm) -> 1
                                        else -> 2
                                    }
                                }.thenBy { it.length }
                            )

                        val artistLimit = if (type == "all") minOf(limit / 2, 4) else limit
                        for (artistName in matchedArtists.take(artistLimit)) {
                            suggestions.add(
                                SuggestionDto(
                                    id = artistName,
                                    name = artistName,
                                    title = artistName,
                                    artist = artistName,
                                    image_url = candidateArtists[artistName],
                                    album_art_url = candidateArtists[artistName],
                                    subtitle = "Artist",
                                    type = "artist"
                                )
                            )
                        }
                    }

                    if (type == "track" || type == "all") {
                        val tracks = try { innertubeClient.search(q, "track") } catch (_: Exception) { emptyList() }
                        val trackLimit = if (type == "all") limit - suggestions.size else limit
                        for (t in tracks.take(trackLimit)) {
                            suggestions.add(
                                SuggestionDto(
                                    id = t.id,
                                    name = t.title,
                                    title = t.title,
                                    artist = t.artist,
                                    album = t.album,
                                    image_url = t.thumbnailUrl,
                                    album_art_url = t.thumbnailUrl,
                                    subtitle = if (t.album.isNotBlank()) "${t.artist} • ${t.album}" else t.artist,
                                    type = "track"
                                )
                            )
                        }
                    }

                    if (type == "genre") {
                        val qNorm = SearchUtils.normalize(q)
                        val matchedGenres = GenreCatalog.ALL_GENRES.filter {
                            val nameNorm = SearchUtils.normalize(it.name)
                            nameNorm.contains(qNorm) || it.id.contains(qNorm) || SearchUtils.fuzzyMatches(q, it.name)
                        }.take(limit)

                        for (g in matchedGenres) {
                            suggestions.add(
                                SuggestionDto(
                                    id = g.id,
                                    name = g.name,
                                    title = g.name,
                                    subtitle = g.category,
                                    image_url = null,
                                    type = "genre"
                                )
                            )
                        }
                    }

                    call.respond(SuggestResponse(suggestions = suggestions))
                } catch (e: Exception) {
                    System.err.println("Discovery suggest error: ${e.message}")
                    call.respond(SuggestResponse(suggestions = emptyList()))
                }
            }

            get("/api/discovery/genres") {
                val category = call.request.queryParameters["category"] ?: "Popular"
                val filtered = if (category.isBlank() || category.equals("All", ignoreCase = true)) {
                    GenreCatalog.ALL_GENRES
                } else {
                    GenreCatalog.ALL_GENRES.filter { it.category.equals(category, ignoreCase = true) }
                }
                val dtos = filtered.map { GenreItemDto(id = it.id, name = it.name, category = it.category) }
                call.respond(GenresResponse(genres = dtos))
            }

            get("/api/discovery/decades") {
                val list = listOf(
                    DecadeItemDto("60s", "60s"),
                    DecadeItemDto("70s", "70s"),
                    DecadeItemDto("80s", "80s"),
                    DecadeItemDto("90s", "90s"),
                    DecadeItemDto("00s", "00s"),
                    DecadeItemDto("10s", "10s"),
                    DecadeItemDto("20s", "20s"),
                    DecadeItemDto("fresh", "Fresh")
                )
                call.respond(DecadesResponse(decades = list))
            }

            get("/api/discovery/active-vibe") {
                val qTracks = queueManager.queueState.value.tracks
                val artists = qTracks.map { it.artist }.distinct().take(5)
                call.respond(ActiveVibeResponse(artists = artists, tracks = qTracks.take(10).map { it.toDto() }))
            }

            post("/api/discovery/generate") {
                try {
                    val req = call.receive<DiscoveryGenerateRequest>()
                    val candidates = mutableListOf<Track>()

                    // 1. Positive seeds harvesting
                    for (artistChip in req.artists.filter { it.modifier == "AND" && it.value.isNotBlank() }) {
                        val tracks = innertubeClient.search(artistChip.value, "track")
                        candidates.addAll(tracks)

                        // 1a. Launch YouTube Music Recommendation Radio via RDAMVM using top track
                        val topTrack = tracks.firstOrNull()
                        if (topTrack != null) {
                            try {
                                val radioTracks = innertubeClient.getRadioTracks(topTrack.id)
                                candidates.addAll(radioTracks)
                            } catch (_: Exception) {}
                        }

                        // 1b. Also search YouTube Music mix for artist & related music
                        try {
                            val mixTracks = innertubeClient.search("${artistChip.value} mix", "track")
                            candidates.addAll(mixTracks)
                        } catch (_: Exception) {}

                        // 1c. Discover collaborators from top tracks
                        for (t in tracks.take(6)) {
                            for (collab in SearchUtils.splitArtists(t.artist)) {
                                if (!collab.equals(artistChip.value, ignoreCase = true) && collab.length > 2) {
                                    try {
                                        val collabTracks = innertubeClient.search(collab, "track")
                                        candidates.addAll(collabTracks.take(4))
                                    } catch (_: Exception) {}
                                }
                            }
                        }
                    }

                    for (trackChip in req.tracks.filter { it.modifier == "AND" && it.value.isNotBlank() }) {
                        if (!trackChip.id.isNullOrBlank()) {
                            try {
                                val radioTracks = innertubeClient.getRadioTracks(trackChip.id)
                                candidates.addAll(radioTracks)
                            } catch (_: Exception) {}
                        }
                        val tracks = innertubeClient.search(trackChip.value, "track")
                        candidates.addAll(tracks)
                        val topTrack = tracks.firstOrNull()
                        if (topTrack != null) {
                            try {
                                val radioTracks = innertubeClient.getRadioTracks(topTrack.id)
                                candidates.addAll(radioTracks)
                            } catch (_: Exception) {}
                        }
                    }

                    for (genreChip in req.genres.filter { it.modifier == "AND" && it.value.isNotBlank() }) {
                        candidates.addAll(innertubeClient.search("${genreChip.value} music", "track"))
                        candidates.addAll(innertubeClient.search("${genreChip.value} hits", "track"))
                    }

                    for (decadeChip in req.decades.filter { it.modifier == "AND" && it.value.isNotBlank() }) {
                        candidates.addAll(innertubeClient.search("${decadeChip.value} songs", "track"))
                        candidates.addAll(innertubeClient.search("${decadeChip.value} hits", "track"))
                    }

                    for (kwChip in req.keywords.filter { it.modifier == "AND" && it.value.isNotBlank() }) {
                        candidates.addAll(innertubeClient.search(kwChip.value, "track"))
                    }

                    if (req.use_active_vibe) {
                        val qArtists = queueManager.queueState.value.tracks.map { it.artist }.distinct().take(3)
                        for (a in qArtists) {
                            if (a.isNotBlank() && a != "Unknown Artist") {
                                candidates.addAll(innertubeClient.search(a, "track"))
                            }
                        }
                    }

                    // Fallback if no positive seeds specified
                    if (candidates.isEmpty()) {
                        candidates.addAll(innertubeClient.search("trending music", "track"))
                        candidates.addAll(innertubeClient.search("top hits", "track"))
                    }

                    var pool = candidates.distinctBy { it.id }

                    // 2. Strict Negative Exclusions
                    // A. Permanent Blacklist (can be momentarily unlocked/ignored via req.ignore_blacklist)
                    if (!req.ignore_blacklist) {
                        val blacklistedNames = localBlacklistManager.getNames()
                        if (blacklistedNames.isNotEmpty()) {
                            pool = pool.filterNot { ArtistUtils.isTrackBlockedByBlacklist(it.artist, blacklistedNames) }
                        }
                    }

                    // B. Excluded Artists (modifier == "NOT")
                    val notArtists = req.artists.filter { it.modifier == "NOT" && it.value.isNotBlank() }.map { it.value.trim().lowercase() }
                    if (notArtists.isNotEmpty()) {
                        pool = pool.filterNot { track ->
                            val tArtist = track.artist.lowercase()
                            val split: List<String> = SearchUtils.splitArtists(track.artist).map { it.lowercase() }
                            notArtists.any { na -> tArtist.contains(na) || split.any { s: String -> s.contains(na) || na.contains(s) } }
                        }
                    }

                    // C. Excluded Tracks (modifier == "NOT")
                    val notTracks = req.tracks.filter { it.modifier == "NOT" && it.value.isNotBlank() }.map { 
                        val raw = it.value.trim().lowercase()
                        if (raw.contains(" - ")) raw.substringBefore(" - ").trim() else raw
                    }
                    if (notTracks.isNotEmpty()) {
                        pool = pool.filterNot { track ->
                            val tTitle = track.title.lowercase()
                            notTracks.any { nt -> tTitle.contains(nt) }
                        }
                    }

                    // D. Excluded Genres / Keywords (modifier == "NOT")
                    val notKeywords = (req.genres.filter { it.modifier == "NOT" && it.value.isNotBlank() } +
                            req.keywords.filter { it.modifier == "NOT" && it.value.isNotBlank() })
                        .map { it.value.trim().lowercase() }
                    if (notKeywords.isNotEmpty()) {
                        pool = pool.filterNot { track ->
                            val combined = "${track.title} ${track.artist} ${track.album}".lowercase()
                            notKeywords.any { combined.contains(it) }
                        }
                    }

                    // E. Live mode filter
                    val liveKeywords = listOf("live", "en vivo", "ao vivo", "concert", "unplugged", "tour")
                    if (req.not_live) {
                        pool = pool.filterNot { track ->
                            val tLower = track.title.lowercase()
                            liveKeywords.any { tLower.contains(it) }
                        }
                    }
                    if (req.only_live) {
                        pool = pool.filter { track ->
                            val tLower = track.title.lowercase()
                            liveKeywords.any { tLower.contains(it) }
                        }
                    }

                    // F. Remix mode filter
                    val remixKeywords = listOf("remix", "mix", "edit", "rework", "dub", "extended mix", "club mix")
                    if (req.not_remix) {
                        pool = pool.filterNot { track ->
                            val tLower = track.title.lowercase()
                            remixKeywords.any { tLower.contains(it) }
                        }
                    }
                    if (req.only_remix) {
                        pool = pool.filter { track ->
                            val tLower = track.title.lowercase()
                            remixKeywords.any { tLower.contains(it) }
                        }
                    }

                    // G. Exclude local playlist songs
                    if (req.not_in_playlists) {
                        val playlistTrackIds = localPlaylistManager.getAll().flatMap { it.tracks }.map { it.id }.toSet()
                        pool = pool.filterNot { playlistTrackIds.contains(it.id) }
                    }

                    // 3. Shuffle & Limits
                    var resultList = pool
                    if (req.true_shuffle) {
                        resultList = ShuffleEngine.shuffleQueue(
                            currentTracks = resultList,
                            currentIndex = -1,
                            applyAntiClumping = req.avoid_consecutive_artists
                        )
                    }

                    val targetCount = req.target_count.coerceIn(1, 100)
                    resultList = resultList.take(targetCount)

                    val dtos = resultList.map { it.toDto() }
                    call.respond(DiscoveryResponse(tracks = dtos, count = dtos.size))
                } catch (e: Exception) {
                    System.err.println("Discovery generate error: ${e.message}")
                    call.respond(DiscoveryResponse(tracks = emptyList(), count = 0))
                }
            }
        }
    }.start(wait = true)
}
