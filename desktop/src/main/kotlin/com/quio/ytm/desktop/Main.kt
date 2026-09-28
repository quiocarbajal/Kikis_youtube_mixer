package com.quio.ytm.desktop

import com.quio.ytm.core.api.InnertubeClient
import com.quio.ytm.core.models.ActiveQueue
import com.quio.ytm.core.models.Track
import com.quio.ytm.core.shuffle.ShuffleEngine
import com.quio.ytm.core.state.ActiveQueueManager
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
data class StatusResponse(
    val authenticated: Boolean = true,
    val is_mac: Boolean = true,
    val has_synced_tracks: Boolean = true,
    val access_url: String = "http://localhost:8888",
    val app: String = "YouTube Music Player Studio",
    val service: String = "YouTube Music",
    val user: UserDto = UserDto("YouTube Music Listener", "local_ytm_user")
)

@Serializable
data class TrackDto(
    val id: String,
    val uri: String,
    val title: String,
    val artist: String,
    val album: String,
    val duration_ms: Long,
    val durationMs: Long,
    val thumbnailUrl: String,
    val album_art_url: String,
    val loudnessDb: Double
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
    val volume_percent: Int? = null
)

fun Track.toDto(): TrackDto = TrackDto(
    id = id,
    uri = "yt:track:$id",
    title = title,
    artist = artist,
    album = album,
    duration_ms = durationMs,
    durationMs = durationMs,
    thumbnailUrl = thumbnailUrl,
    album_art_url = thumbnailUrl,
    loudnessDb = loudnessDb
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
    println("Starting YouTube Music Player Studio (Desktop) on http://localhost:$port...")

    val desktopScope = CoroutineScope(SupervisorJob() + Dispatchers.Default)
    val queueRepository = InMemoryQueueRepository()
    val queueManager = ActiveQueueManager(queueRepository, desktopScope)
    val innertubeClient = InnertubeClient()
    val localPlaylistManager = LocalPlaylistManager()

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
                call.respond(StatusResponse())
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
                call.respond(emptyList<String>())
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
                    else -> localPlaylistManager.markSynced(id)
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

            get("/api/tracks") {
                val playlistId = call.request.queryParameters["playlist_id"]
                if (!playlistId.isNullOrEmpty() && playlistId != "all") {
                    val pl = localPlaylistManager.getPlaylist(playlistId)
                    if (pl != null) {
                        call.respond(TracksResponse(tracks = pl.tracks, count = pl.tracks.size))
                    } else {
                        call.respond(TracksResponse(tracks = emptyList(), count = 0))
                    }
                } else {
                    val q = queueManager.queueState.value
                    val dtos = q.tracks.map { it.toDto() }
                    call.respond(TracksResponse(tracks = dtos, count = dtos.size))
                }
            }

            get("/api/sync/status") {
                call.respond(SyncStatusResponse())
            }

            post("/api/credentials") {
                call.respond(GenericOkResponse(status = "ok"))
            }
        }
    }.start(wait = true)
}
