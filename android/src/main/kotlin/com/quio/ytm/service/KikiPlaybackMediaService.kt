package com.quio.ytm.service

import android.app.PendingIntent
import android.content.Intent
import androidx.annotation.OptIn
import androidx.media3.common.util.UnstableApi
import androidx.media3.session.MediaSession
import androidx.media3.session.MediaSessionService
import com.quio.ytm.MainActivity
import com.quio.ytm.audio.AndroidAudioPlayer

@OptIn(UnstableApi::class)
class KikiPlaybackMediaService : MediaSessionService() {

    private var mediaSession: MediaSession? = null

    companion object {
        var sharedPlayer: AndroidAudioPlayer? = null
    }

    override fun onCreate() {
        super.onCreate()
        val player = sharedPlayer?.player
        if (player != null) {
            val sessionActivityPendingIntent = PendingIntent.getActivity(
                this,
                0,
                Intent(this, MainActivity::class.java),
                PendingIntent.FLAG_IMMUTABLE or PendingIntent.FLAG_UPDATE_CURRENT
            )

            mediaSession = MediaSession.Builder(this, player)
                .setSessionActivity(sessionActivityPendingIntent)
                .build()
        }
    }

    override fun onGetSession(controllerInfo: MediaSession.ControllerInfo): MediaSession? {
        return mediaSession
    }

    override fun onDestroy() {
        mediaSession?.run {
            release()
            mediaSession = null
        }
        super.onDestroy()
    }
}
