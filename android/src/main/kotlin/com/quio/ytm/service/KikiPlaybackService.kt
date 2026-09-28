package com.quio.ytm.service

import android.content.Context
import com.quio.ytm.data.local.entity.TrackEntity

object KikiPlaybackService {
    var onPlayPauseAction: (() -> Unit)? = null
    var onSkipNextAction: (() -> Unit)? = null
    var onSkipPrevAction: (() -> Unit)? = null
    var activeQueue: List<TrackEntity> = emptyList()

    fun startOrUpdate(context: Context, title: String, artist: String, isPlaying: Boolean, trackId: String) {}
    fun stopService(context: Context) {}
    fun stop(context: Context) {}
}
