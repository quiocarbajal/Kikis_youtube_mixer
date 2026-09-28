package com.quio.ytm.data.remote

import com.quio.ytm.core.api.InnertubeClient
import com.quio.ytm.data.local.entity.PlaylistEntity
import com.quio.ytm.data.local.entity.TrackEntity
import com.quio.ytm.data.repository.YtmMixerRepository

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

    suspend fun syncLibrary(token: String = "", onProgress: (Float, String) -> Unit = { _, _ -> }): Result<Unit> {
        return Result.success(Unit)
    }

    suspend fun searchCatalog(query: String): List<TrackEntity> {
        return emptyList()
    }

    suspend fun searchTracks(token: String, query: String, limit: Int = 10, offset: Int = 0): List<TrackEntity> {
        return emptyList()
    }

    suspend fun searchPlaylists(token: String, query: String, limit: Int = 10): List<String> {
        return emptyList()
    }

    suspend fun fetchPlaylistSampleTracks(token: String, playlistId: String, limit: Int = 20): List<TrackEntity> {
        return emptyList()
    }

    suspend fun savePlaylist(name: String, tracks: List<TrackEntity>): Boolean {
        return true
    }

    suspend fun getValidToken(existingToken: String?, forceRefresh: Boolean = false): String? {
        return existingToken ?: "ytm_token"
    }

    suspend fun saveTrackToLiked(token: String, trackId: String): Boolean {
        return true
    }

    suspend fun removeTrackFromLiked(token: String, trackId: String): Boolean {
        return true
    }

    suspend fun fetchRecentlyPlayedTrackIds(token: String, days: Int = 30): Set<String> {
        return emptySet()
    }

    suspend fun fetchArtistGenres(token: String, artistId: String): List<String> {
        return emptyList()
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
