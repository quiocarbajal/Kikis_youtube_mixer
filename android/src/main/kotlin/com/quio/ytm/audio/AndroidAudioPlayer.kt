package com.quio.ytm.audio

import android.content.Context
import android.media.audiofx.DynamicsProcessing
import android.os.Build
import android.os.Handler
import android.os.Looper
import android.webkit.JavascriptInterface
import android.webkit.WebChromeClient
import android.webkit.WebSettings
import android.webkit.WebView
import android.webkit.WebViewClient
import androidx.annotation.OptIn
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

    private val audioAttributes = androidx.media3.common.AudioAttributes.Builder()
        .setContentType(androidx.media3.common.C.AUDIO_CONTENT_TYPE_MUSIC)
        .setUsage(androidx.media3.common.C.USAGE_MEDIA)
        .build()

    val player: ExoPlayer = ExoPlayer.Builder(context)
        .setMediaSourceFactory(mediaSourceFactory)
        .setAudioAttributes(audioAttributes, true)
        .setHandleAudioBecomingNoisy(true)
        .build()

    private var dynamicsProcessing: DynamicsProcessing? = null
    private var baseVolume: Float = 1.0f

    // Official YouTube IFrame Player Engine
    private val mainHandler = Handler(Looper.getMainLooper())
    private var webView: WebView? = null
    private var isPlayerReady = false
    private var pendingPlayId: String? = null
    private var pendingStartSec: Double = 0.0

    var onStateChangedListener: ((state: Int, durationSec: Double) -> Unit)? = null
    var onErrorListener: ((code: Int) -> Unit)? = null

    init {
        setupDynamicsProcessing()
    }

    fun getOrCreateWebView(ctx: Context): WebView {
        if (webView == null) {
            initWebView(ctx)
        }
        val v = webView!!
        (v.parent as? android.view.ViewGroup)?.removeView(v)
        return v
    }

    private fun initWebView(ctx: Context) {
        if (webView != null) return
        try {
            try {
                WebView.setWebContentsDebuggingEnabled(true)
            } catch (_: Exception) {}

            val wv = WebView(ctx)
            wv.settings.apply {
                javaScriptEnabled = true
                mediaPlaybackRequiresUserGesture = false
                domStorageEnabled = true
                databaseEnabled = true
                cacheMode = WebSettings.LOAD_DEFAULT
            }
            wv.webChromeClient = object : WebChromeClient() {
                override fun onConsoleMessage(consoleMessage: android.webkit.ConsoleMessage?): Boolean {
                    android.util.Log.d("YouTubeWebView", "${consoleMessage?.message()} (line ${consoleMessage?.lineNumber()})")
                    return true
                }
            }
            wv.webViewClient = object : WebViewClient() {
                override fun onReceivedError(view: WebView?, errorCode: Int, description: String?, failingUrl: String?) {
                    android.util.Log.e("YouTubeWebView", "WebView load error: $description ($errorCode) on $failingUrl")
                }
            }
            wv.addJavascriptInterface(WebAppBridge(), "AndroidBridge")

            val appOrigin = "https://${ctx.packageName}"
            val html = """
                <!DOCTYPE html>
                <html>
                <head>
                  <meta name="viewport" content="width=device-width, initial-scale=1.0">
                  <meta name="referrer" content="strict-origin-when-cross-origin">
                  <style>
                    html, body { margin: 0; padding: 0; width: 100%; height: 100%; background: black; overflow: hidden; }
                    #player { width: 100%; height: 100%; }
                  </style>
                  <script defer src="https://www.youtube.com/iframe_api"></script>
                </head>
                <body>
                  <div id="player"></div>
                  <script>
                    var player = null;
                    var isApiReady = false;
                    var pendingId = null;
                    var pendingSec = 0;

                    function onYouTubeIframeAPIReady() {
                      isApiReady = true;
                      console.log("YouTube IFrame API Ready");
                      if (window.AndroidBridge) {
                        window.AndroidBridge.onReady();
                      }
                      if (pendingId) {
                        playVideo(pendingId, pendingSec);
                        pendingId = null;
                      }
                    }

                    function onPlayerStateChange(event) {
                      var dur = (player && player.getDuration) ? player.getDuration() : 0;
                      console.log("Player State Change: " + event.data + " (dur=" + dur + ")");
                      if (window.AndroidBridge) {
                        window.AndroidBridge.onStateChange(event.data, dur);
                        if (event.data === 1 && (!dur || dur === 0)) {
                          setTimeout(function() {
                            if (player && player.getDuration) {
                              var d2 = player.getDuration();
                              if (d2 > 0) window.AndroidBridge.onStateChange(1, d2);
                            }
                          }, 500);
                        }
                      }
                    }

                    function onPlayerError(event) {
                      console.warn("Player Error: " + event.data);
                      if (window.AndroidBridge) {
                        window.AndroidBridge.onError(event.data);
                      }
                    }

                    function playVideo(id, startSec) {
                      if (!id) return;
                      if (!isApiReady) {
                        pendingId = id;
                        pendingSec = startSec || 0;
                        return;
                      }
                      if (!player) {
                        player = new YT.Player('player', {
                          height: '100%',
                          width: '100%',
                          videoId: id,
                          playerVars: {
                            'autoplay': 1,
                            'controls': 0,
                            'playsinline': 1,
                            'disablekb': 1,
                            'fs': 0,
                            'rel': 0,
                            'enablejsapi': 1,
                            'origin': '$appOrigin',
                            'start': startSec || 0
                          },
                          events: {
                            'onReady': function(event) {
                              event.target.playVideo();
                            },
                            'onStateChange': onPlayerStateChange,
                            'onError': onPlayerError
                          }
                        });
                      } else {
                        if (player.loadVideoById) {
                          player.loadVideoById({videoId: id, startSeconds: startSec || 0});
                          player.playVideo();
                        }
                      }
                    }

                    function pauseVideo() {
                      if (player && player.pauseVideo) {
                        player.pauseVideo();
                      }
                    }

                    function resumeVideo() {
                      if (player && player.playVideo) {
                        player.playVideo();
                      }
                    }

                    function seekTo(sec) {
                      if (player && player.seekTo) {
                        player.seekTo(sec, true);
                      }
                    }

                    function setVolume(vol) {
                      if (player && player.setVolume) {
                        player.setVolume(vol);
                      }
                    }
                  </script>
                </body>
                </html>
            """.trimIndent()

            wv.loadDataWithBaseURL(appOrigin, html, "text/html", "UTF-8", null)
            webView = wv
            android.util.Log.i("AndroidAudioPlayer", "Initialized YouTube WebView Audio Engine with origin: $appOrigin")
        } catch (e: Exception) {
            android.util.Log.e("AndroidAudioPlayer", "Error initializing WebView audio engine", e)
        }
    }

    inner class WebAppBridge {
        @JavascriptInterface
        fun onReady() {
            android.util.Log.i("AndroidAudioPlayer", "YouTube IFrame Engine is ready")
            isPlayerReady = true
            val id = pendingPlayId
            val sec = pendingStartSec
            if (id != null) {
                pendingPlayId = null
                mainHandler.post {
                    webView?.evaluateJavascript("playVideo('$id', $sec);", null)
                }
            }
        }

        @JavascriptInterface
        fun onStateChange(state: Int, durationSec: Double) {
            android.util.Log.d("AndroidAudioPlayer", "YouTube Audio state: $state, duration: $durationSec")
            onStateChangedListener?.invoke(state, durationSec)
        }

        @JavascriptInterface
        fun onError(code: Int) {
            android.util.Log.e("AndroidAudioPlayer", "YouTube Audio Error code: $code")
            onErrorListener?.invoke(code)
        }
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
                    val limiter = DynamicsProcessing.Limiter(
                        true,
                        true,
                        0,
                        1.0f,
                        50.0f,
                        10.0f,
                        LIMITER_CEILING_DB,
                        0.0f
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
        val videoId = track.id
        android.util.Log.i("AndroidAudioPlayer", "play track: ${track.title} ($videoId)")

        mainHandler.post {
            if (webView == null) {
                initWebView(context)
            }
            if (!isPlayerReady) {
                pendingPlayId = videoId
                pendingStartSec = 0.0
            } else {
                webView?.evaluateJavascript("playVideo('$videoId', 0);", null)
            }
        }

        applyLoudnessNormalization(track.loudnessDb)
    }

    override fun pause() {
        player.pause()
        mainHandler.post {
            webView?.evaluateJavascript("pauseVideo();", null)
        }
    }

    override fun resume() {
        player.play()
        mainHandler.post {
            webView?.evaluateJavascript("resumeVideo();", null)
        }
    }

    override fun seekTo(positionMs: Long) {
        player.seekTo(positionMs)
        val sec = positionMs / 1000.0
        mainHandler.post {
            webView?.evaluateJavascript("seekTo($sec);", null)
        }
    }

    override fun setVolume(volume: Float) {
        baseVolume = volume.coerceIn(0.0f, 1.0f)
        player.volume = baseVolume
        val volInt = (baseVolume * 100).toInt()
        mainHandler.post {
            webView?.evaluateJavascript("setVolume($volInt);", null)
        }
    }

    /**
     * Mathematical Normalization to -14.0 LUFS:
     * YouTube's loudnessDb is relative to -14.0 LUFS.
     * linearGain = 10^(-loudnessDb / 20)
     */
    override fun applyLoudnessNormalization(loudnessDb: Double) {
        val gainDb = -loudnessDb
        val linearMultiplier = 10.0.pow(gainDb / 20.0).toFloat()
        val normalizedVolume = (baseVolume * linearMultiplier).coerceIn(0.0f, 1.0f)
        player.volume = normalizedVolume
        val volInt = (normalizedVolume * 100).toInt()
        mainHandler.post {
            webView?.evaluateJavascript("setVolume($volInt);", null)
        }

        android.util.Log.d(
            "AndroidAudioPlayer",
            "Applied -14 LUFS Normalization: trackLoudness=${loudnessDb}dB, gainDb=${gainDb}dB, multiplier=$linearMultiplier, finalVol=$volInt%"
        )
    }

    fun release() {
        dynamicsProcessing?.release()
        dynamicsProcessing = null
        player.release()
        val run = {
            webView?.destroy()
            webView = null
        }
        if (Looper.myLooper() == Looper.getMainLooper()) run() else mainHandler.post { run() }
    }
}
