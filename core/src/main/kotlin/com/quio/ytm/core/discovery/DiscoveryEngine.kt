package com.quio.ytm.core.discovery

import com.quio.ytm.core.api.InnertubeClient
import com.quio.ytm.core.db.QueueRepository
import com.quio.ytm.core.db.TrackRepository
import com.quio.ytm.core.models.Track

class DiscoveryEngine(
    private val innertubeClient: InnertubeClient,
    private val trackRepository: TrackRepository,
    private val queueRepository: QueueRepository
) {
    
    data class DiscoveryParams(
        val includedArtists: List<String> = emptyList(),
        val excludedArtists: List<String> = emptyList(),
        val blacklistedArtists: List<String> = emptyList(),
        val includedGenres: List<String> = emptyList(),
        val excludedGenres: List<String> = emptyList(),
        val trackSeeds: List<String> = emptyList(),
        val excludeLikedSongs: Boolean = true,
        val excludePlaylistSongs: Boolean = true
    )

    suspend fun generateSurpriseMix(params: DiscoveryParams): List<Track> {
        // 1. Map constraints to Innertube Radio parameters
        val seedVideoId = params.trackSeeds.firstOrNull() ?: "RDAMVM"
        
        // Fetch raw graph from Innertube
        val jsonGraph = innertubeClient.next(seedVideoId)
        
        // 2. Parse tracks (Simulated parser placeholder)
        val rawTracks = parseNextEndpointResponse(jsonGraph)
        
        // 3. Apply Strict Negative Exclusions
        val filteredTracks = rawTracks.filter { track ->
            var keep = true
            
            // Check Permanent Blacklist (Primary Artist rule)
            if (params.blacklistedArtists.isNotEmpty() && ArtistUtils.isTrackBlockedByBlacklist(track.artist, params.blacklistedArtists)) {
                keep = false
            }

            // Check NOT constraints
            if (keep && params.excludedArtists.any { track.artist.contains(it, ignoreCase = true) }) {
                keep = false
            }
            
            // Check Liked Songs exclusion
            if (keep && params.excludeLikedSongs) {
                val dbTrack = trackRepository.getTrackById(track.id)
                if (dbTrack != null && dbTrack.likedAt != null) {
                    keep = false // It is a liked song, skip it!
                }
            }
            
            keep
        }
        
        return filteredTracks
    }
    
    private fun parseNextEndpointResponse(jsonObject: kotlinx.serialization.json.JsonObject): List<Track> {
        // TODO: Deep JSON AST navigation for YouTube's twoColumnWatchNextResults
        return emptyList()
    }
}
