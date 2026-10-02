package com.quio.ytm.data.local.dao

import androidx.room.Dao
import androidx.room.Insert
import androidx.room.OnConflictStrategy
import androidx.room.Query
import com.quio.ytm.data.local.entity.ArtistBlacklistEntity
import kotlinx.coroutines.flow.Flow

@Dao
interface ArtistBlacklistDao {

    @Query("SELECT * FROM artist_blacklist ORDER BY created_at DESC")
    fun getAllBlacklisted(): Flow<List<ArtistBlacklistEntity>>

    @Query("SELECT * FROM artist_blacklist ORDER BY created_at DESC")
    suspend fun getAllBlacklistedSync(): List<ArtistBlacklistEntity>

    @Query("SELECT COUNT(*) FROM artist_blacklist")
    fun getBlacklistCount(): Flow<Int>

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insert(entity: ArtistBlacklistEntity)

    @Query("DELETE FROM artist_blacklist WHERE LOWER(TRIM(name)) = LOWER(TRIM(:nameOrId)) OR id = :nameOrId")
    suspend fun delete(nameOrId: String)
}
