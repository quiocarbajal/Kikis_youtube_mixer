package com.quio.ytm.data.backup

import com.google.gson.annotations.SerializedName

/**
 * Machine-readable models for complete YouTube Music Library Safety Backups.
 */
data class YtmSafetyBackup(
    @SerializedName("version") val version: Int = 1,
    @SerializedName("created_at_utc") val createdAtUtc: String,
    @SerializedName("app_version") val appVersion: String = "1.0",
    @SerializedName("ytm_user_id", alternate = ["spotify_user_id"]) val ytmUserId: String? = null,
    @SerializedName("total_liked_tracks") val totalLikedTracks: Int,
    @SerializedName("total_playlists") val totalPlaylists: Int,
    @SerializedName("liked_songs") val likedSongs: List<BackupTrack>,
    @SerializedName("playlists") val playlists: List<BackupPlaylist>
)

data class BackupTrack(
    @SerializedName("id") val id: String,
    @SerializedName("uri") val uri: String,
    @SerializedName("title") val title: String,
    @SerializedName("artist") val artist: String,
    @SerializedName("album") val album: String? = null,
    @SerializedName("duration_ms") val durationMs: Long = 0L,
    @SerializedName("added_at") val addedAt: String? = null
)

data class BackupPlaylist(
    @SerializedName("id") val id: String,
    @SerializedName("name") val name: String,
    @SerializedName("description") val description: String? = null,
    @SerializedName("is_public") val isPublic: Boolean = false,
    @SerializedName("is_collaborative") val isCollaborative: Boolean = false,
    @SerializedName("snapshot_id") val snapshotId: String? = null,
    @SerializedName("total_tracks") val totalTracks: Int,
    @SerializedName("tracks") val tracks: List<BackupTrack>
)
