package com.quio.ytm.core.db

import com.quio.ytm.core.models.ActiveQueue
import kotlinx.coroutines.flow.Flow

interface QueueRepository {
    // Reactive stream of the queue state. UI collects this to always be perfectly in sync.
    fun observeActiveQueue(): Flow<ActiveQueue>
    
    // Saves the queue state locally in < 1ms
    suspend fun saveQueue(queue: ActiveQueue)
    
    // Fetches the queue synchronously for the audio engine
    suspend fun getActiveQueue(): ActiveQueue?
}
