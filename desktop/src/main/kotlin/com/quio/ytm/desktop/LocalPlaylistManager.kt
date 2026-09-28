package com.quio.ytm.desktop

import kotlinx.serialization.Serializable
import kotlinx.serialization.encodeToString
import kotlinx.serialization.json.Json
import java.io.File

@Serializable
data class LocalPlaylist(
    val id: String,
    val name: String,
    val description: String = "",
    val total_tracks: Int = 0,
    val is_local: Boolean = true,
    val yt_playlist_id: String? = null,
    val last_modified_locally: Long = System.currentTimeMillis(),
    val last_synced_at: Long? = null,
    val tracks: List<TrackDto> = emptyList()
) {
    val is_synced: Boolean
        get() = yt_playlist_id != null && last_synced_at != null && last_synced_at >= last_modified_locally

    fun toSummary(): PlaylistSummaryDto = PlaylistSummaryDto(
        id = id,
        name = name,
        description = description,
        total_tracks = tracks.size,
        is_local = is_local,
        yt_playlist_id = yt_playlist_id,
        is_synced = is_synced,
        last_modified_locally = last_modified_locally,
        last_synced_at = last_synced_at
    )
}

@Serializable
data class PlaylistSummaryDto(
    val id: String,
    val name: String,
    val description: String = "",
    val total_tracks: Int = 0,
    val is_local: Boolean = true,
    val yt_playlist_id: String? = null,
    val is_synced: Boolean = false,
    val last_modified_locally: Long = 0L,
    val last_synced_at: Long? = null
)

@Serializable
data class PlaylistsResponse(
    val playlists: List<PlaylistSummaryDto> = emptyList()
)

@Serializable
data class CreatePlaylistRequest(
    val name: String,
    val description: String = "",
    val track_ids: List<String> = emptyList(),
    val tracks: List<TrackDto> = emptyList(),
    val overwrite: Boolean = false,
    val playlist_id: String? = null
)

@Serializable
data class CreatePlaylistResponse(
    val status: String = "ok",
    val id: String,
    val name: String,
    val total_tracks: Int,
    val overwritten: Boolean = false,
    val is_synced: Boolean = false
)

@Serializable
data class RenamePlaylistRequest(
    val name: String = "",
    val new_name: String = ""
) {
    val resolvedName: String get() = if (name.isNotBlank()) name else new_name
}

@Serializable
data class AddTrackToPlaylistRequest(
    val track_id: String,
    val track: TrackDto? = null
)

@Serializable
data class SyncCheckResponse(
    val status: String = "ok",
    val playlist_id: String,
    val name: String,
    val yt_playlist_id: String?,
    val has_conflict: Boolean,
    val is_new_remote: Boolean,
    val local_count: Int,
    val local_updated_at: Long,
    val remote_count: Int = 0,
    val remote_updated_at: Long? = null,
    val recommended_direction: String = "app_to_yt"
)

@Serializable
data class ExecuteSyncRequest(
    val direction: String = "app_to_yt" // "app_to_yt", "yt_to_app", "merge"
)

@Serializable
data class ExecuteSyncResponse(
    val status: String = "ok",
    val message: String,
    val direction_applied: String,
    val playlist: PlaylistSummaryDto
)

class LocalPlaylistManager(
    private val storageFile: File = File(System.getProperty("user.dir"), "local_playlists.json")
) {
    private val json = Json {
        prettyPrint = true
        isLenient = true
        ignoreUnknownKeys = true
    }

    private val playlists = mutableListOf<LocalPlaylist>()

    init {
        loadFromDisk()
    }

    @Synchronized
    private fun loadFromDisk() {
        if (!storageFile.exists()) {
            playlists.clear()
            return
        }
        try {
            val content = storageFile.readText()
            if (content.isNotBlank()) {
                val loaded = json.decodeFromString<List<LocalPlaylist>>(content)
                playlists.clear()
                playlists.addAll(loaded)
            }
        } catch (e: Exception) {
            System.err.println("Error reading local_playlists.json: ${e.message}")
        }
    }

    @Synchronized
    private fun saveToDisk() {
        try {
            val content = json.encodeToString(playlists.toList())
            storageFile.writeText(content)
        } catch (e: Exception) {
            System.err.println("Error saving local_playlists.json: ${e.message}")
        }
    }

    @Synchronized
    fun getSummaries(): List<PlaylistSummaryDto> {
        return playlists.map { it.toSummary() }
    }

    @Synchronized
    fun getPlaylist(id: String): LocalPlaylist? {
        return playlists.find { it.id == id }
    }

    @Synchronized
    fun savePlaylist(
        name: String,
        description: String,
        tracks: List<TrackDto>,
        overwrite: Boolean,
        playlistId: String?
    ): LocalPlaylist {
        val existingIndex = if (!playlistId.isNullOrEmpty()) {
            playlists.indexOfFirst { it.id == playlistId }
        } else if (overwrite) {
            playlists.indexOfFirst { it.name.equals(name.trim(), ignoreCase = true) }
        } else {
            -1
        }

        val now = System.currentTimeMillis()
        val id = if (existingIndex != -1) playlists[existingIndex].id else "local_pl_${now}"
        val existingYtId = if (existingIndex != -1) playlists[existingIndex].yt_playlist_id else null

        val playlist = LocalPlaylist(
            id = id,
            name = name.trim(),
            description = description.trim(),
            total_tracks = tracks.size,
            is_local = true,
            yt_playlist_id = existingYtId,
            last_modified_locally = now,
            last_synced_at = null,
            tracks = tracks
        )

        if (existingIndex != -1) {
            playlists[existingIndex] = playlist
        } else {
            playlists.add(playlist)
        }

        saveToDisk()
        return playlist
    }

    @Synchronized
    fun renamePlaylist(id: String, newName: String): Boolean {
        val idx = playlists.indexOfFirst { it.id == id }
        if (idx == -1) return false
        val current = playlists[idx]
        playlists[idx] = current.copy(
            name = newName.trim(),
            last_modified_locally = System.currentTimeMillis()
        )
        saveToDisk()
        return true
    }

    @Synchronized
    fun deletePlaylist(id: String): Boolean {
        val removed = playlists.removeIf { it.id == id }
        if (removed) saveToDisk()
        return removed
    }

    @Synchronized
    fun addTrack(playlistId: String, track: TrackDto): Boolean {
        val idx = playlists.indexOfFirst { it.id == playlistId }
        if (idx == -1) return false
        val current = playlists[idx]
        if (current.tracks.any { it.id == track.id }) return true
        val updatedTracks = current.tracks + track
        playlists[idx] = current.copy(
            tracks = updatedTracks,
            total_tracks = updatedTracks.size,
            last_modified_locally = System.currentTimeMillis()
        )
        saveToDisk()
        return true
    }

    @Synchronized
    fun markSynced(id: String, ytPlaylistId: String? = null): PlaylistSummaryDto? {
        val idx = playlists.indexOfFirst { it.id == id }
        if (idx == -1) return null
        val current = playlists[idx]
        val now = System.currentTimeMillis()
        val resolvedYtId = ytPlaylistId ?: current.yt_playlist_id ?: "yt_pl_${id.removePrefix("local_pl_")}"
        val updated = current.copy(
            yt_playlist_id = resolvedYtId,
            last_synced_at = now
        )
        playlists[idx] = updated
        saveToDisk()
        return updated.toSummary()
    }

    @Synchronized
    fun applyRemoteTracks(id: String, remoteTracks: List<TrackDto>): PlaylistSummaryDto? {
        val idx = playlists.indexOfFirst { it.id == id }
        if (idx == -1) return null
        val current = playlists[idx]
        val now = System.currentTimeMillis()
        val updated = current.copy(
            tracks = remoteTracks,
            total_tracks = remoteTracks.size,
            last_modified_locally = now,
            last_synced_at = now
        )
        playlists[idx] = updated
        saveToDisk()
        return updated.toSummary()
    }

    @Synchronized
    fun mergeTracks(id: String, remoteTracks: List<TrackDto>): PlaylistSummaryDto? {
        val idx = playlists.indexOfFirst { it.id == id }
        if (idx == -1) return null
        val current = playlists[idx]
        val now = System.currentTimeMillis()
        // Deduplicate maintaining local tracks first then any remote-only tracks
        val seen = mutableSetOf<String>()
        val merged = mutableListOf<TrackDto>()
        for (t in current.tracks) {
            if (seen.add(t.id)) merged.add(t)
        }
        for (t in remoteTracks) {
            if (seen.add(t.id)) merged.add(t)
        }
        val updated = current.copy(
            tracks = merged,
            total_tracks = merged.size,
            last_modified_locally = now,
            last_synced_at = now
        )
        playlists[idx] = updated
        saveToDisk()
        return updated.toSummary()
    }
}
