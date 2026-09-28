package com.quio.ytm.core.shuffle

import com.quio.ytm.core.models.Track
import java.security.SecureRandom
import kotlin.math.max

object ShuffleEngine {
    private val secureRandom = SecureRandom()

    /**
     * Executes a true mathematical shuffle while keeping the currently playing track pinned.
     * Optionally applies the sliding-window anti-clumping algorithm to space out artists.
     */
    fun shuffleQueue(
        currentTracks: List<Track>,
        currentIndex: Int,
        applyAntiClumping: Boolean = true
    ): List<Track> {
        if (currentTracks.isEmpty()) return emptyList()
        if (currentTracks.size == 1) return currentTracks

        // 1. Pin the currently playing track
        val pinnedTrack = currentTracks.getOrNull(currentIndex)
        val remainingTracks = currentTracks.toMutableList()
        
        if (pinnedTrack != null) {
            remainingTracks.removeAt(currentIndex)
        }

        // 2. Cryptographic Fisher-Yates Shuffle
        fisherYatesShuffle(remainingTracks)

        // 3. Artist Anti-Clumping Pass
        if (applyAntiClumping) {
            applyArtistAntiClumping(remainingTracks, pinnedTrack)
        }

        // 4. Re-insert the pinned track at the beginning
        val finalQueue = mutableListOf<Track>()
        if (pinnedTrack != null) {
            finalQueue.add(pinnedTrack)
        }
        finalQueue.addAll(remainingTracks)

        return finalQueue
    }

    private fun fisherYatesShuffle(list: MutableList<Track>) {
        for (i in list.indices.reversed()) {
            if (i > 0) {
                val j = secureRandom.nextInt(i + 1)
                val temp = list[i]
                list[i] = list[j]
                list[j] = temp
            }
        }
    }

    private fun applyArtistAntiClumping(list: MutableList<Track>, pinnedTrack: Track?) {
        // We ensure that an artist does not appear within `spacingWindow` tracks of their last appearance
        val spacingWindow = 2
        var attempts = 0
        val maxAttempts = list.size * 2

        var i = 0
        while (i < list.size) {
            val currentArtist = list[i].artist
            var clash = false
            
            // Check against pinned track if we are at the very beginning of the shuffled list
            if (i < spacingWindow && pinnedTrack != null && pinnedTrack.artist == currentArtist) {
                clash = true
            }

            // Check against recently placed tracks
            if (!clash) {
                val lookbackStart = max(0, i - spacingWindow)
                for (j in lookbackStart until i) {
                    if (list[j].artist == currentArtist) {
                        clash = true
                        break
                    }
                }
            }

            if (clash && attempts < maxAttempts) {
                // Find the next available valid position further down the list to swap with
                var swapTarget = -1
                for (k in (i + spacingWindow + 1) until list.size) {
                    if (list[k].artist != currentArtist) {
                        swapTarget = k
                        break
                    }
                }

                if (swapTarget != -1) {
                    val temp = list[i]
                    list[i] = list[swapTarget]
                    list[swapTarget] = temp
                    attempts++
                    // DO NOT increment i; we need to re-evaluate the newly swapped track at index i
                    continue 
                }
            }
            
            // If no clash, or we couldn't resolve it, move to the next index
            i++
        }
    }
}
