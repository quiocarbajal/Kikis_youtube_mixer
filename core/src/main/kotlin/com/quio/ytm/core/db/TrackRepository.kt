package com.quio.ytm.core.db

import com.quio.ytm.core.models.Track
import kotlinx.coroutines.flow.Flow

interface TrackRepository {
    suspend fun insertOrUpdate(track: Track)
    suspend fun insertAll(tracks: List<Track>)
    suspend fun getTrackById(id: String): Track?
    
    // For the Liked Songs view
    fun getLikedSongs(): Flow<List<Track>>
    
    // For marking a track as downloaded
    suspend fun setDownloaded(id: String, isDownloaded: Boolean)
}
