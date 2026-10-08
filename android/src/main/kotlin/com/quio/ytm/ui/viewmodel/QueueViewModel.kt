package com.quio.ytm.ui.viewmodel

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.quio.ytm.core.models.Track
import com.quio.ytm.core.shuffle.ShuffleEngine
import com.quio.ytm.core.state.ActiveQueueManager
import com.quio.ytm.data.local.entity.PlaylistEntity
import com.quio.ytm.data.local.entity.TrackEntity
import com.quio.ytm.data.local.entity.toDomain
import com.quio.ytm.data.local.entity.toEntity
import com.quio.ytm.data.repository.YtmMixerRepository
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch

data class QueueUiState(
    val tracks: List<TrackEntity> = emptyList(),
    val currentTrack: TrackEntity? = null,
    val isPlaying: Boolean = false,
    val activePlaylistName: String = "Active Listening Queue",
    val searchQuery: String = "",
    val isAntiClumpingEnabled: Boolean = true,
    val isLocked: Boolean = false,
    val isShuffled: Boolean = false,
    val isLoading: Boolean = false,
    val userMessage: String? = null
)

class QueueViewModel(
    private val queueManager: ActiveQueueManager,
    private val repository: YtmMixerRepository? = null,
    private val cloudService: com.quio.ytm.data.remote.YtmCloudService? = null
) : ViewModel() {

    private val _uiState = MutableStateFlow(QueueUiState())
    val uiState: StateFlow<QueueUiState> = _uiState.asStateFlow()

    val filteredTracks: StateFlow<List<TrackEntity>> = _uiState.map { state ->
        if (state.searchQuery.isBlank()) {
            state.tracks
        } else {
            state.tracks.filter {
                it.title.contains(state.searchQuery, ignoreCase = true) ||
                it.artist.contains(state.searchQuery, ignoreCase = true)
            }
        }
    }.stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), emptyList())

    val showColdStartBanner: StateFlow<Boolean> = MutableStateFlow(false)

    init {
        viewModelScope.launch {
            queueManager.queueState.collect { queue ->
                val entities = queue.tracks.map { it.toEntity() }
                val currentEntity = queue.currentTrack?.toEntity()
                _uiState.update { state ->
                    state.copy(
                        tracks = entities,
                        currentTrack = currentEntity,
                        isShuffled = queue.isShuffled
                    )
                }
            }
        }
    }

    fun onPlayTrack(index: Int) {
        val tracks = _uiState.value.tracks
        if (index in tracks.indices) {
            queueManager.setQueue(tracks.map { it.toDomain() }, startIndex = index)
        }
    }

    fun onShuffle() {
        executeTrueShuffle()
    }

    fun executeTrueShuffle() {
        val currentTracks = queueManager.queueState.value.tracks
        val currentIdx = queueManager.queueState.value.currentIndex
        val shuffled = ShuffleEngine.shuffleQueue(
            currentTracks = currentTracks,
            currentIndex = currentIdx,
            applyAntiClumping = _uiState.value.isAntiClumpingEnabled
        )
        queueManager.setQueue(shuffled, startIndex = 0)
    }

    fun toggleAntiClumping() {
        _uiState.update { it.copy(isAntiClumpingEnabled = !it.isAntiClumpingEnabled) }
    }

    fun toggleLock() {
        _uiState.update { it.copy(isLocked = !it.isLocked) }
    }

    fun clearQueue() {
        queueManager.setQueue(emptyList(), startIndex = 0)
    }

    fun setSearchQuery(query: String) {
        _uiState.update { it.copy(searchQuery = query) }
    }

    fun reorderTracks(fromIndex: Int, toIndex: Int) {
        queueManager.moveTrack(fromIndex, toIndex)
    }

    fun reorderTracks(fromTrackId: String, toTrackId: String) {
        val tracks = _uiState.value.tracks
        val from = tracks.indexOfFirst { it.id == fromTrackId }
        val to = tracks.indexOfFirst { it.id == toTrackId }
        if (from != -1 && to != -1) {
            queueManager.moveTrack(from, to)
        }
    }

    fun onMove(fromIndex: Int, toIndex: Int) {
        reorderTracks(fromIndex, toIndex)
    }

    fun clearUserMessage() {
        _uiState.update { it.copy(userMessage = null) }
    }

    fun dismissColdStartBanner(doNotShowAgain: Boolean) {
        // Dismissed
    }

    fun loadPlaylistIntoQueue(playlistId: String, playlistName: String) {
        _uiState.update { it.copy(activePlaylistName = playlistName, isLoading = true) }
        viewModelScope.launch {
            val tracks = repository?.getTracksForPlaylistSync(playlistId) ?: emptyList()
            queueManager.setQueue(tracks.map { it.toDomain() }, startIndex = 0)
            _uiState.update { it.copy(isLoading = false) }
        }
    }

    fun appendPlaylistToQueue(playlistId: String, playlistName: String) {
        viewModelScope.launch {
            val tracks = repository?.getTracksForPlaylistSync(playlistId) ?: emptyList()
            queueManager.appendTracks(tracks.map { it.toDomain() })
            _uiState.update { it.copy(userMessage = "Added ${tracks.size} tracks from '$playlistName' to Queue") }
        }
    }

    fun replaceQueue(tracks: List<TrackEntity>, newName: String? = null) {
        if (newName != null) {
            _uiState.update { it.copy(activePlaylistName = newName) }
        }
        queueManager.setQueue(tracks.map { it.toDomain() }, startIndex = 0)
    }

    fun appendTracksToQueue(tracks: List<TrackEntity>, sourceName: String? = null) {
        queueManager.appendTracks(tracks.map { it.toDomain() })
    }

    fun saveQueueAsNewPlaylist(name: String, description: String = "") {
        viewModelScope.launch {
            val currentTracks = _uiState.value.tracks
            val playlistId = "custom_" + System.currentTimeMillis()
            val pl = PlaylistEntity(
                id = playlistId,
                name = name,
                totalTracks = currentTracks.size,
                isCustom = true
            )
            repository?.upsertPlaylist(pl)
            repository?.setPlaylistTracks(playlistId, currentTracks.map { it.id })
            _uiState.update { it.copy(userMessage = "Playlist '$name' saved! Syncing to YouTube...") }
            
            launch(kotlinx.coroutines.Dispatchers.IO) {
                val success = cloudService?.exportPlaylist(playlistId) == true
                if (success) {
                    _uiState.update { it.copy(userMessage = "Playlist '$name' successfully exported to YouTube!") }
                }
            }
        }
    }

    fun overwritePlaylistWithQueue(playlist: PlaylistEntity) {
        viewModelScope.launch {
            val currentTracks = _uiState.value.tracks
            repository?.setPlaylistTracks(playlist.id, currentTracks.map { it.id })
            _uiState.update { it.copy(userMessage = "Playlist '${playlist.name}' updated!") }
        }
    }
}
