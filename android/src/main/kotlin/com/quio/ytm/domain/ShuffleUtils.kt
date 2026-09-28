package com.quio.ytm.domain

import com.quio.ytm.core.models.Track
import com.quio.ytm.core.shuffle.ShuffleEngine
import com.quio.ytm.data.local.entity.TrackEntity

object ShuffleUtils {
    fun shuffleTrackEntities(tracks: List<TrackEntity>, applyAntiClumping: Boolean = true): List<TrackEntity> {
        val domainTracks: List<Track> = tracks.map { track ->
            Track(
                id = track.id,
                title = track.title,
                artist = track.artist,
                album = track.album,
                durationMs = track.durationMs,
                thumbnailUrl = track.albumArtUrl ?: "",
                loudnessDb = -14.0,
                likedAt = track.addedAt,
                isDownloaded = false
            )
        }
        val shuffled = ShuffleEngine.shuffleQueue(domainTracks, 0, applyAntiClumping)
        return shuffled.map { t ->
            TrackEntity(
                id = t.id,
                uri = "https://music.youtube.com/watch?v=${t.id}",
                title = t.title,
                artist = t.artist,
                album = t.album,
                albumArtUrl = t.thumbnailUrl,
                durationMs = t.durationMs,
                addedAt = t.likedAt ?: System.currentTimeMillis()
            )
        }
    }
}
