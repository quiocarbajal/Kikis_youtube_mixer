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

            // 0. Fetch Local Playlists for Diffing
            var localPlaylists = repository.getAllPlaylistsSync().associateBy { it.id }

            // 1. Export local unlinked playlists to YouTube (Two-way sync)
            onProgress(0.05f, "Exporting local playlists...")
            localPlaylists.values.forEach { localPl ->
                if (localPl.isCustom || (localPl.ytPlaylistId == null && localPl.id.startsWith("custom_"))) {
                    val tracks = repository.getTracksForPlaylistSync(localPl.id)
                    val newYtId = try {
                        innertubeClient.createPlaylist(
                            title = localPl.name,
                            description = localPl.description ?: "Created via kiki's youtube mixer",
                            privacyStatus = "PRIVATE",
                            videoIds = tracks.map { it.id }
                        )
                    } catch (e: Exception) { null }

                    if (newYtId != null) {
                        repository.upsertPlaylist(
                            localPl.copy(
                                ytPlaylistId = newYtId,
                                isCustom = false,
                                remoteTrackCount = tracks.size
                            )
                        )
                    }
                }
            }

            // Re-fetch local playlists to get the newly exported ytPlaylistId links
            localPlaylists = repository.getAllPlaylistsSync().associateBy { it.id }

            // 2. Fetch Liked Songs
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
                        isCustom = false,
                        remoteTrackCount = entities.size
                    )
                )
            }

            // 3. Fetch Remote Playlists (Quick Summary)
            onProgress(0.4f, "Fetching YouTube Music Playlists...")
            val remotePlaylists = try {
                innertubeClient.getLibraryPlaylists()
            } catch (e: Exception) {
                emptyList()
            }

            // 4. Handle Deletions: Remove local playlists that no longer exist on YouTube
            val remotePlaylistIds = remotePlaylists.map { it.id }.toSet()
            localPlaylists.values.forEach { localPl ->
                val isLinked = localPl.ytPlaylistId != null || (!localPl.isCustom && localPl.id != "liked_songs")
                val isMissing = !remotePlaylistIds.contains(localPl.ytPlaylistId ?: localPl.id)
                if (isLinked && isMissing && localPl.id != "liked_songs") {
                    repository.deletePlaylist(localPl.id)
                }
            }

            // 5. Handle Updates / Additions (Delta Quick Sync)
            val totalPlaylists = remotePlaylists.size
            remotePlaylists.forEachIndexed { index, remotePl ->
                val plProgress = 0.4f + (index.toFloat() / totalPlaylists.coerceAtLeast(1) * 0.6f)
                
                val localPl = localPlaylists.values.find { it.ytPlaylistId == remotePl.id }
                    ?: localPlaylists[remotePl.id]
                    ?: localPlaylists.values.find { it.name.equals(remotePl.title, ignoreCase = true) }

                // 🚀 THE QUICK SYNC DIFF CHECK
                if (localPl != null && localPl.remoteTrackCount == remotePl.trackCount) {
                    onProgress(plProgress, "Skipping ${remotePl.title} (Up to date)")
                    if (localPl.ytPlaylistId != remotePl.id) {
                        repository.upsertPlaylist(localPl.copy(ytPlaylistId = remotePl.id, isCustom = false))
                    }
                    return@forEachIndexed // skip this one!
                }

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

                val targetId = localPl?.id ?: remotePl.id

                repository.upsertPlaylist(
                    PlaylistEntity(
                        id = targetId,
                        name = remotePl.title,
                        totalTracks = trackEntities.size,
                        isCustom = false,
                        ytPlaylistId = remotePl.id,
                        remoteTrackCount = remotePl.trackCount
                    )
                )
                repository.setPlaylistTracks(targetId, trackEntities.map { it.id })
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

    suspend fun exportPlaylist(localId: String): Boolean = withContext(Dispatchers.IO) {
        if (innertubeClient == null || repository == null) return@withContext false
        try {
            val localPl = repository.getAllPlaylistsSync().find { it.id == localId } ?: return@withContext false
            if (localPl.ytPlaylistId != null) return@withContext true // Already exported

            val tracks = repository.getTracksForPlaylistSync(localId)
            val newPlaylistId = innertubeClient.createPlaylist(
                title = localPl.name,
                description = localPl.description ?: "Created with Kiki's YouTube Mixer",
                privacyStatus = "PRIVATE",
                videoIds = tracks.map { it.id }
            )
            
            if (!newPlaylistId.isNullOrEmpty()) {
                repository.upsertPlaylist(
                    localPl.copy(
                        ytPlaylistId = newPlaylistId,
                        isCustom = false,
                        remoteTrackCount = tracks.size
                    )
                )
                return@withContext true
            }
            false
        } catch (e: Exception) {
            false
        }
    }

    suspend fun getValidToken(existingToken: String?, forceRefresh: Boolean = false): String? {
        return existingToken ?: "ytm_token"
    }

    suspend fun saveTrackToLiked(token: String, trackId: String): Boolean = withContext(Dispatchers.IO) {
        if (innertubeClient == null) return@withContext false
        try {
            if (token.isNotBlank()) {
                if (token.contains(";")) innertubeClient.setCookies(token)
                else innertubeClient.setOAuthToken(token)
            }
            innertubeClient.rateSong(trackId, "like")
        } catch (e: Exception) {
            false
        }
    }

    suspend fun removeTrackFromLiked(token: String, trackId: String): Boolean = withContext(Dispatchers.IO) {
        if (innertubeClient == null) return@withContext false
        try {
            if (token.isNotBlank()) {
                if (token.contains(";")) innertubeClient.setCookies(token)
                else innertubeClient.setOAuthToken(token)
            }
            innertubeClient.rateSong(trackId, "none")
        } catch (e: Exception) {
            false
        }
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
