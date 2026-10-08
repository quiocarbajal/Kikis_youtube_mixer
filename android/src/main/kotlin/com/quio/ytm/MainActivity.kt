package com.quio.ytm

import android.content.Intent
import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import com.quio.ytm.service.KikiPlaybackMediaService
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.material3.Surface
import androidx.compose.ui.Modifier
import androidx.lifecycle.lifecycleScope
import com.quio.ytm.audio.AndroidAudioPlayer
import com.quio.ytm.core.api.InnertubeClient
import com.quio.ytm.core.state.ActiveQueueManager
import com.quio.ytm.data.backup.LibraryBackupManager
import com.quio.ytm.data.local.AndroidQueueRepository
import com.quio.ytm.data.local.AppDatabase
import com.quio.ytm.data.remote.YtmCloudService
import com.quio.ytm.data.repository.YtmMixerRepository
import com.quio.ytm.ui.navigation.AppScaffold
import com.quio.ytm.ui.theme.BgMain
import com.quio.ytm.ui.theme.YouTubeMusicPlayerTheme
import com.quio.ytm.ui.viewmodel.DiscoverViewModel
import com.quio.ytm.ui.viewmodel.LibraryViewModel
import com.quio.ytm.auth.AndroidOAuthManager
import com.quio.ytm.ui.viewmodel.PlayerViewModel
import com.quio.ytm.ui.viewmodel.QueueViewModel
import kotlinx.coroutines.launch

class MainActivity : ComponentActivity() {

    private lateinit var db: AppDatabase
    private lateinit var repository: YtmMixerRepository
    private lateinit var backupManager: LibraryBackupManager
    private lateinit var queueRepository: AndroidQueueRepository
    private lateinit var queueManager: ActiveQueueManager
    private lateinit var innertubeClient: InnertubeClient
    private lateinit var cloudService: YtmCloudService
    private lateinit var audioPlayer: AndroidAudioPlayer
    private lateinit var oauthManager: AndroidOAuthManager

    private lateinit var queueViewModel: QueueViewModel
    private lateinit var libraryViewModel: LibraryViewModel
    private lateinit var playerViewModel: PlayerViewModel
    private lateinit var discoverViewModel: DiscoverViewModel

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)

        // Initialize Core Dependencies
        db = AppDatabase.getInstance(applicationContext)
        repository = YtmMixerRepository(db)
        queueRepository = AndroidQueueRepository()
        queueManager = ActiveQueueManager(queueRepository, lifecycleScope)
        innertubeClient = InnertubeClient()
        cloudService = YtmCloudService(innertubeClient, repository)
        backupManager = LibraryBackupManager(applicationContext, cloudService, repository)
        audioPlayer = AndroidAudioPlayer(applicationContext)
        KikiPlaybackMediaService.sharedPlayer = audioPlayer
        try {
            startService(Intent(this, KikiPlaybackMediaService::class.java))
        } catch (e: Exception) {
            android.util.Log.e("MainActivity", "Error starting KikiPlaybackMediaService", e)
        }

        oauthManager = AndroidOAuthManager(innertubeClient, repository)

        // Initialize ViewModels
        queueViewModel = QueueViewModel(
            queueManager = queueManager,
            repository = repository,
            cloudService = cloudService
        )
        libraryViewModel = LibraryViewModel(
            repository = repository,
            cloudService = cloudService,
            backupManager = backupManager,
            oauthManager = oauthManager
        )
        playerViewModel = PlayerViewModel(
            repository = repository,
            cloudService = cloudService,
            audioPlayer = audioPlayer
        ).apply {
            setApplicationContext(this@MainActivity)
        }
        discoverViewModel = DiscoverViewModel(
            repository = repository,
            cloudService = cloudService
        )

        lifecycleScope.launch {
            val validToken = oauthManager.getValidAccessToken()
            if (!validToken.isNullOrBlank()) {
                innertubeClient.setOAuthToken(validToken)
                libraryViewModel.setAccessToken(validToken)
                playerViewModel.setAccessToken(validToken)
                libraryViewModel.syncLibrary()
            }
        }

        setContent {
            YouTubeMusicPlayerTheme {
                Surface(
                    modifier = Modifier.fillMaxSize(),
                    color = BgMain
                ) {
                    AppScaffold(
                        queueViewModel = queueViewModel,
                        libraryViewModel = libraryViewModel,
                        playerViewModel = playerViewModel,
                        discoverViewModel = discoverViewModel,
                        onConnectYouTubeMusic = {
                            oauthManager.launchGoogleLogin(
                                context = this@MainActivity,
                                onSuccess = { token ->
                                    libraryViewModel.setAccessToken(token)
                                    playerViewModel.setAccessToken(token)
                                    libraryViewModel.syncLibrary()
                                },
                                onError = { error ->
                                    android.util.Log.e("MainActivity", "Google login error: $error")
                                }
                            )
                        }
                    )
                }
            }
        }
    }

    override fun onDestroy() {
        super.onDestroy()
        if (::audioPlayer.isInitialized) {
            audioPlayer.release()
        }
        KikiPlaybackMediaService.sharedPlayer = null
    }
}
