package com.quio.ytm.data.local

import com.quio.ytm.core.db.QueueRepository
import com.quio.ytm.core.models.ActiveQueue
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.asStateFlow

class AndroidQueueRepository : QueueRepository {
    private val queueFlow = MutableStateFlow(ActiveQueue())

    override fun observeActiveQueue(): Flow<ActiveQueue> = queueFlow.asStateFlow()

    override suspend fun saveQueue(queue: ActiveQueue) {
        queueFlow.value = queue
    }

    override suspend fun getActiveQueue(): ActiveQueue? = queueFlow.value
}
