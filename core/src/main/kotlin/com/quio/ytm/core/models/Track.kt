package com.quio.ytm.core.models

import kotlinx.serialization.Serializable

@Serializable
data class Track(
    val id: String, // Unique YouTube Video ID
    val title: String,
    val artist: String,
    val album: String,
    val durationMs: Long,
    val thumbnailUrl: String,
    val loudnessDb: Double = -14.0, // Critical for our -14 LUFS normalizer
    val likedAt: Long? = null, // Used for sorting the "Liked Songs" view, null if not liked
    val syncedYoutubeId: String? = null, // If it's part of a specific synced playlist
    val isDownloaded: Boolean = false,
    val year: String? = null
)
