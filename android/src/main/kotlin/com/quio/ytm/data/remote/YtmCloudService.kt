package com.quio.ytm.data.remote

import com.quio.ytm.core.api.InnertubeClient
import com.quio.ytm.data.local.entity.PlaylistEntity
import com.quio.ytm.data.local.entity.TrackEntity
import com.quio.ytm.data.local.entity.toEntity
import com.quio.ytm.data.repository.YtmMixerRepository
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext

sealed class PlaybackResult {
    data object Success : PlaybackResult()
    data object NoActiveDevice : PlaybackResult()
    data object Unauthorized : PlaybackResult()
    data class Error(val code: Int, val message: String) : PlaybackResult()
}

data class YtmDevice(
    val id: String = "local",
    val name: String = "Android Device",
    val isActive: Boolean = true
)

data class YtmPlaybackState(
    val track: TrackEntity? = null,
    val isPlaying: Boolean = false,
    val progressMs: Long = 0L,
    val durationMs: Long = 0L,
    val deviceName: String = "Android Device",
    val volumePercent: Int = 80,
    val device: YtmDevice = YtmDevice()
)

class YtmCloudService(
    private val innertubeClient: InnertubeClient? = null,
    private val repository: YtmMixerRepository? = null
) {
    companion object {
        fun sanitizeQuery(query: String): String = query.trim()
    }

    suspend fun getStreamUrl(trackId: String): String? = withContext(Dispatchers.IO) {
        innertubeClient?.getStreamInfo(trackId)?.streamUrl
    }

    suspend fun getStreamInfo(trackId: String): InnertubeClient.StreamInfo? = withContext(Dispatchers.IO) {
        innertubeClient?.getStreamInfo(trackId)
    }

    suspend fun syncLibrary(token: String = "", onProgress: (Float, String) -> Unit = { _, _ -> }): Result<Unit> = withContext(Dispatchers.IO) {
        try {
            if (innertubeClient == null || repository == null) {
                return@withContext Result.failure(IllegalStateException("InnertubeClient or Repository not initialized"))
            }

            if (token.isNotBlank()) {
                if (token.contains(";")) {
                    innertubeClient.setCookies(token)
                } else {
                    innertubeClient.setOAuthToken(token)
                }
            }

            // 1. Fetch Liked Songs
            onProgress(0.1f, "Fetching Liked Songs...")
            val likedTracks = try {
                innertubeClient.getLikedSongs()
            } catch (e: Exception) {
                android.util.Log.e("YtmCloudService", "Error fetching liked songs: ${e.message}", e)
                emptyList()
            }

            android.util.Log.i("YtmCloudService", "Fetched ${likedTracks.size} liked songs from YouTube Music")
            if (likedTracks.isNotEmpty()) {
                val entities = likedTracks.map { it.toEntity().copy(isLiked = true) }
                repository.upsertTracks(entities)
                repository.setPlaylistTracks("liked_songs", entities.map { it.id })
                repository.upsertPlaylist(
                    PlaylistEntity(
                        id = "liked_songs",
                        name = "Liked Songs",
                        totalTracks = entities.size,
                        isCustom = false
                    )
                )
            }

            // 2. Fetch Playlists
            onProgress(0.45f, "Fetching YouTube Music Playlists...")
            val playlists = try {
                innertubeClient.getLibraryPlaylists()
            } catch (e: Exception) {
                emptyList()
            }

            val totalPlaylists = playlists.size
            playlists.forEachIndexed { index, remotePl ->
                val plProgress = 0.5f + (index.toFloat() / totalPlaylists.coerceAtLeast(1) * 0.45f)
                onProgress(plProgress, "Syncing playlist ${index + 1}/$totalPlaylists: ${remotePl.title}")

                val tracks = try {
                    innertubeClient.getPlaylistTracks(remotePl.id)
                } catch (e: Exception) {
                    emptyList()
                }

                val trackEntities = tracks.map { it.toEntity() }
                if (trackEntities.isNotEmpty()) {
                    repository.upsertTracks(trackEntities)
                }

                repository.upsertPlaylist(
                    PlaylistEntity(
                        id = remotePl.id,
                        name = remotePl.title,
                        totalTracks = trackEntities.size,
                        isCustom = false
                    )
                )
                repository.setPlaylistTracks(remotePl.id, trackEntities.map { it.id })
            }

            onProgress(1.0f, "Library Sync Complete!")
            Result.success(Unit)
        } catch (e: Exception) {
            Result.failure(e)
        }
    }

    suspend fun searchCatalog(query: String): List<TrackEntity> = withContext(Dispatchers.IO) {
        if (innertubeClient == null) return@withContext emptyList()
        try {
            val tracks = innertubeClient.search(query, filter = "song")
            val entities = tracks.map { it.toEntity() }
            repository?.upsertTracks(entities)
            entities
        } catch (e: Exception) {
            emptyList()
        }
    }

    suspend fun searchTracks(token: String, query: String, limit: Int = 10, offset: Int = 0): List<TrackEntity> = withContext(Dispatchers.IO) {
        if (innertubeClient == null) return@withContext emptyList()
        try {
            val cleanQuery = query.replace("artist:\"", "")
                .replace("track:\"", "")
                .replace("genre:\"", "")
                .replace("\"", "")
                .trim()
            val tracks = innertubeClient.search(cleanQuery, filter = "song")
            val entities = tracks.map { it.toEntity() }
            repository?.upsertTracks(entities)
            entities.take(limit)
        } catch (e: Exception) {
            emptyList()
        }
    }

    suspend fun searchPlaylists(token: String, query: String, limit: Int = 10): List<String> = withContext(Dispatchers.IO) {
        emptyList()
    }

    suspend fun fetchPlaylistSampleTracks(token: String, playlistId: String, limit: Int = 20): List<TrackEntity> = withContext(Dispatchers.IO) {
        if (innertubeClient == null) return@withContext emptyList()
        try {
            val tracks = innertubeClient.getPlaylistTracks(playlistId).take(limit)
            val entities = tracks.map { it.toEntity() }
            repository?.upsertTracks(entities)
            entities
        } catch (e: Exception) {
            emptyList()
        }
    }

    suspend fun getRadioTracks(videoId: String): List<TrackEntity> = withContext(Dispatchers.IO) {
        if (innertubeClient == null) return@withContext emptyList()
        try {
            val tracks = innertubeClient.getRadioTracks(videoId)
            val entities = tracks.map { it.toEntity() }
            repository?.upsertTracks(entities)
            entities
        } catch (e: Exception) {
            emptyList()
        }
    }

    suspend fun savePlaylist(name: String, tracks: List<TrackEntity>): Boolean = withContext(Dispatchers.IO) {
        if (innertubeClient == null) return@withContext false
        try {
            val videoIds = tracks.map { it.id }
            val newPlaylistId = innertubeClient.createPlaylist(
                title = name,
                description = "Created with Kiki's YouTube Mixer",
                privacyStatus = "PRIVATE",
                videoIds = videoIds
            )
            !newPlaylistId.isNullOrEmpty()
        } catch (e: Exception) {
            false
        }
    }

    suspend fun getValidToken(existingToken: String?, forceRefresh: Boolean = false): String? {
        return existingToken ?: "ytm_token"
    }

    suspend fun saveTrackToLiked(token: String, trackId: String): Boolean = withContext(Dispatchers.IO) {
        true
    }

    suspend fun removeTrackFromLiked(token: String, trackId: String): Boolean = withContext(Dispatchers.IO) {
        true
    }

    suspend fun fetchRecentlyPlayedTrackIds(token: String, days: Int = 30): Set<String> = withContext(Dispatchers.IO) {
        repository?.getRecentlyPlayedTrackIds(days)?.toSet() ?: emptySet()
    }

    suspend fun fetchArtistGenres(token: String, artistId: String): List<String> = withContext(Dispatchers.IO) {
        emptyList()
    }

    suspend fun pausePlayback(token: String): PlaybackResult = PlaybackResult.Success
    suspend fun resumePlayback(token: String): PlaybackResult = PlaybackResult.Success
    suspend fun seekTo(token: String, positionMs: Long): PlaybackResult = PlaybackResult.Success
    suspend fun setVolume(token: String, volumePercent: Int): PlaybackResult = PlaybackResult.Success
    suspend fun playTrack(token: String, trackUri: String): PlaybackResult = PlaybackResult.Success
    suspend fun playTrackUri(token: String, trackUri: String): PlaybackResult = PlaybackResult.Success
    suspend fun playTracks(token: String, trackUris: List<String>, startIndex: Int = 0, positionMs: Long = 0L): PlaybackResult = PlaybackResult.Success
    suspend fun skipToNext(token: String): PlaybackResult = PlaybackResult.Success
    suspend fun skipToPrevious(token: String): PlaybackResult = PlaybackResult.Success
    suspend fun getDevices(token: String): List<YtmDevice> = listOf(YtmDevice())
    suspend fun getPlaybackState(token: String): YtmPlaybackState? = null
}
