package com.quio.ytm.core.models

import kotlinx.serialization.Serializable

@Serializable
data class Playlist(
    val id: String, // Local UUID or YouTube Playlist ID
    val name: String,
    val description: String = "",
    val thumbnailUrl: String = "",
    val lastSyncedAt: Long = 0L,
    val isLocalOnly: Boolean = false, // True if strictly a local draft
    val tracks: List<Track> = emptyList()
)
