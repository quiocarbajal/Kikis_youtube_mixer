package com.quio.ytm.audio

import android.content.Context
import android.media.audiofx.DynamicsProcessing
import android.net.Uri
import android.os.Build
import androidx.annotation.OptIn
import androidx.media3.common.MediaItem
import androidx.media3.common.util.UnstableApi
import androidx.media3.database.StandaloneDatabaseProvider
import androidx.media3.datasource.DefaultHttpDataSource
import androidx.media3.datasource.cache.CacheDataSource
import androidx.media3.datasource.cache.LeastRecentlyUsedCacheEvictor
import androidx.media3.datasource.cache.SimpleCache
import androidx.media3.exoplayer.ExoPlayer
import androidx.media3.exoplayer.source.DefaultMediaSourceFactory
import com.quio.ytm.core.audio.AudioPlayer
import com.quio.ytm.core.models.Track
import java.io.File
import kotlin.math.pow

@OptIn(UnstableApi::class)
class AndroidAudioPlayer(
    private val context: Context
) : AudioPlayer {

    companion object {
        private const val MAX_CACHE_SIZE_BYTES = 2L * 1024 * 1024 * 1024 // 2 GB
        private const val TARGET_LUFS = -14.0
        private const val LIMITER_CEILING_DB = -1.0f

        @Volatile
        private var simpleCacheInstance: SimpleCache? = null

        fun getSimpleCache(context: Context): SimpleCache {
            return simpleCacheInstance ?: synchronized(this) {
                simpleCacheInstance ?: run {
                    val cacheDir = File(context.cacheDir, "ytm_audio_cache")
                    val evictor = LeastRecentlyUsedCacheEvictor(MAX_CACHE_SIZE_BYTES)
                    val databaseProvider = StandaloneDatabaseProvider(context)
                    SimpleCache(cacheDir, evictor, databaseProvider).also {
                        simpleCacheInstance = it
                    }
                }
            }
        }
    }

    private val cache = getSimpleCache(context)

    // Http upstream with valid modern User-Agent
    private val httpDataSourceFactory = DefaultHttpDataSource.Factory()
        .setUserAgent("Mozilla/5.0 (Android; Mobile; rv:128.0) Gecko/128.0 Firefox/128.0")
        .setAllowCrossProtocolRedirects(true)
        .setConnectTimeoutMs(15000)
        .setReadTimeoutMs(20000)

    // Cache-aware data source factory for 100% offline auto-caching
    private val cacheDataSourceFactory = CacheDataSource.Factory()
        .setCache(cache)
        .setUpstreamDataSourceFactory(httpDataSourceFactory)
        .setFlags(CacheDataSource.FLAG_IGNORE_CACHE_ON_ERROR)

    private val mediaSourceFactory = DefaultMediaSourceFactory(context)
        .setDataSourceFactory(cacheDataSourceFactory)

    val player: ExoPlayer = ExoPlayer.Builder(context)
        .setMediaSourceFactory(mediaSourceFactory)
        .build()

    private var dynamicsProcessing: DynamicsProcessing? = null
    private var baseVolume: Float = 1.0f

    init {
        setupDynamicsProcessing()
    }

    private fun setupDynamicsProcessing() {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.P) {
            try {
                val audioSessionId = player.audioSessionId
                val config = DynamicsProcessing.Config.Builder(
                    DynamicsProcessing.VARIANT_FAVOR_FREQUENCY_RESOLUTION,
                    2,     // 2 channels (Stereo)
                    false, // preEqInUse
                    0,     // preEqBandCount
                    false, // mbcInUse
                    0,     // mbcBandCount
                    false, // postEqInUse
                    0,     // postEqBandCount
                    true   // limiterInUse
                ).build()

                dynamicsProcessing = DynamicsProcessing(0, audioSessionId, config).apply {
                    // Set True-Peak Limiter: threshold = -1.0 dB, attack = 1ms, release = 50ms, ratio = 10:1
                    val limiter = DynamicsProcessing.Limiter(
                        true,  // inUse
                        true,  // enabled
                        0,     // linkGroup
                        1.0f,  // attackTime (ms)
                        50.0f, // releaseTime (ms)
                        10.0f, // ratio
                        LIMITER_CEILING_DB, // threshold in dB
                        0.0f   // postGain in dB
                    )
                    setLimiterByChannelIndex(0, limiter)
                    setLimiterByChannelIndex(1, limiter)
                    enabled = true
                }
            } catch (e: Exception) {
                android.util.Log.w("AndroidAudioPlayer", "DynamicsProcessing limiter not supported on this device", e)
            }
        }
    }

    override fun play(track: Track, streamUrl: String) {
        val mediaItem = MediaItem.Builder()
            .setUri(Uri.parse(streamUrl))
            .setMediaId(track.id)
            .build()

        player.setMediaItem(mediaItem)
        player.prepare()
        player.play()

        // Apply real-time loudness normalization for this track
        applyLoudnessNormalization(track.loudnessDb)
    }

    override fun pause() {
        player.pause()
    }

    override fun resume() {
        player.play()
    }

    override fun seekTo(positionMs: Long) {
        player.seekTo(positionMs)
    }

    override fun setVolume(volume: Float) {
        baseVolume = volume.coerceIn(0.0f, 1.0f)
        player.volume = baseVolume
    }

    /**
     * Mathematical Normalization to -14.0 LUFS:
     * YouTube's loudnessDb is relative to -14.0 LUFS.
     * linearGain = 10^(-loudnessDb / 20)
     * Routed through our DynamicsProcessing -1.0 dB limiter.
     */
    override fun applyLoudnessNormalization(loudnessDb: Double) {
        val gainDb = -loudnessDb
        // Calculate linear multiplier: 10^(gainDb / 20)
        val linearMultiplier = 10.0.pow(gainDb / 20.0).toFloat()
        
        // Apply normalized volume clamped safely to prevent extreme hardware overamplification
        val normalizedVolume = (baseVolume * linearMultiplier).coerceIn(0.0f, 2.0f)
        player.volume = normalizedVolume

        android.util.Log.d(
            "AndroidAudioPlayer",
            "Applied -14 LUFS Normalization: trackLoudness=${loudnessDb}dB, gainDb=${gainDb}dB, volumeMultiplier=$linearMultiplier, finalVol=$normalizedVolume"
        )
    }

    fun release() {
        dynamicsProcessing?.release()
        dynamicsProcessing = null
        player.release()
    }
}
