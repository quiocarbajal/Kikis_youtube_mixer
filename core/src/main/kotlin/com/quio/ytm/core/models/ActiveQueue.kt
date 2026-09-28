package com.quio.ytm.core.models

import kotlinx.serialization.Serializable

@Serializable
data class ActiveQueue(
    val id: String = "singleton_queue",
    val tracks: List<Track> = emptyList(),
    val currentIndex: Int = 0,
    val isShuffled: Boolean = false,
    val repeatMode: RepeatMode = RepeatMode.OFF,
    val vibeContext: String? = null // Used for "Blend Active Queue Vibe" Discovery Engine
) {
    val currentTrack: Track?
        get() = tracks.getOrNull(currentIndex)
}

enum class RepeatMode {
    OFF, ALL, ONE
}
