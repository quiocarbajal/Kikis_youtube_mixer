package com.quio.ytm.core.state

import com.quio.ytm.core.db.QueueRepository
import com.quio.ytm.core.models.ActiveQueue
import com.quio.ytm.core.models.Track
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch

class ActiveQueueManager(
    private val queueRepository: QueueRepository,
    private val scope: CoroutineScope
) {
    private val _queueState = MutableStateFlow(ActiveQueue())
    val queueState: StateFlow<ActiveQueue> = _queueState.asStateFlow()

    init {
        scope.launch {
            // Restore from disk on startup
            val restored = queueRepository.getActiveQueue()
            if (restored != null) {
                _queueState.value = restored
            }
        }
    }

    private fun updateState(block: (ActiveQueue) -> ActiveQueue) {
        _queueState.update(block)
        scope.launch {
            queueRepository.saveQueue(_queueState.value) // Persist in < 1ms
        }
    }

    fun setQueue(tracks: List<Track>, startIndex: Int = 0) {
        updateState { it.copy(tracks = tracks, currentIndex = startIndex) }
    }

    fun appendTracks(newTracks: List<Track>) {
        updateState { it.copy(tracks = it.tracks + newTracks) }
    }

    fun playNext() {
        updateState { 
            if (it.currentIndex < it.tracks.size - 1) {
                it.copy(currentIndex = it.currentIndex + 1)
            } else {
                it
            }
        }
    }

    fun playPrevious() {
        updateState {
            if (it.currentIndex > 0) {
                it.copy(currentIndex = it.currentIndex - 1)
            } else {
                it
            }
        }
    }

    fun skipToIndex(index: Int) {
        updateState {
            if (index in 0 until it.tracks.size) {
                it.copy(currentIndex = index)
            } else {
                it
            }
        }
    }

    fun moveTrack(fromIndex: Int, toIndex: Int) {
        updateState { state ->
            val list = state.tracks.toMutableList()
            val item = list.removeAt(fromIndex)
            list.add(toIndex, item)
            
            // Adjust currentIndex if necessary so the playing song doesn't change
            var newCurrent = state.currentIndex
            if (state.currentIndex == fromIndex) {
                newCurrent = toIndex
            } else if (state.currentIndex in (fromIndex + 1)..toIndex) {
                newCurrent--
            } else if (state.currentIndex in toIndex..<fromIndex) {
                newCurrent++
            }
            
            state.copy(tracks = list, currentIndex = newCurrent)
        }
    }
}
