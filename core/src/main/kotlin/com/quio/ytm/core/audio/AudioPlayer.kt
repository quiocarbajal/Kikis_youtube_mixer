package com.quio.ytm.core.audio

import com.quio.ytm.core.models.Track

interface AudioPlayer {
    fun play(track: Track, streamUrl: String)
    fun pause()
    fun resume()
    fun seekTo(positionMs: Long)
    fun setVolume(volume: Float)
    
    // Called when loudnessDb metadata is available to normalize to -14 LUFS
    fun applyLoudnessNormalization(loudnessDb: Double)
}
