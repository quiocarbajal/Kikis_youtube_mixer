package com.quio.ytm.data.local

import android.content.Context
import androidx.room.Database
import androidx.room.Room
import androidx.room.RoomDatabase
import androidx.room.migration.Migration
import androidx.sqlite.db.SupportSQLiteDatabase
import com.quio.ytm.data.local.dao.ArtistBlacklistDao
import com.quio.ytm.data.local.dao.PlaybackHistoryDao
import com.quio.ytm.data.local.dao.PlaylistDao
import com.quio.ytm.data.local.dao.PlaylistTrackDao
import com.quio.ytm.data.local.dao.SettingDao
import com.quio.ytm.data.local.dao.TrackDao
import com.quio.ytm.data.local.entity.ArtistBlacklistEntity
import com.quio.ytm.data.local.entity.PlaybackHistoryEntity
import com.quio.ytm.data.local.entity.PlaylistEntity
import com.quio.ytm.data.local.entity.PlaylistTrackCrossRef
import com.quio.ytm.data.local.entity.SettingEntity
import com.quio.ytm.data.local.entity.TrackEntity

@Database(
    entities = [
        TrackEntity::class,
        PlaylistEntity::class,
        PlaylistTrackCrossRef::class,
        SettingEntity::class,
        PlaybackHistoryEntity::class,
        ArtistBlacklistEntity::class
    ],
    version = 4,
    exportSchema = false
)
abstract class AppDatabase : RoomDatabase() {

    abstract fun trackDao(): TrackDao
    abstract fun playlistDao(): PlaylistDao
    abstract fun playlistTrackDao(): PlaylistTrackDao
    abstract fun settingDao(): SettingDao
    abstract fun playbackHistoryDao(): PlaybackHistoryDao
    abstract fun artistBlacklistDao(): ArtistBlacklistDao

    companion object {
        private const val OLD_DATABASE_NAME = "spotify_mixer.db"
        private const val DATABASE_NAME = "ytm_mixer.db"

        private fun migrateLegacyDatabaseFile(context: Context) {
            try {
                val oldDb = context.getDatabasePath(OLD_DATABASE_NAME)
                val newDb = context.getDatabasePath(DATABASE_NAME)
                if (oldDb.exists() && !newDb.exists()) {
                    oldDb.renameTo(newDb)
                    val oldWal = context.getDatabasePath("$OLD_DATABASE_NAME-wal")
                    if (oldWal.exists()) oldWal.renameTo(context.getDatabasePath("$DATABASE_NAME-wal"))
                    val oldShm = context.getDatabasePath("$OLD_DATABASE_NAME-shm")
                    if (oldShm.exists()) oldShm.renameTo(context.getDatabasePath("$DATABASE_NAME-shm"))
                }
            } catch (_: Exception) {}
        }

        val MIGRATION_1_2 = object : Migration(1, 2) {
            override fun migrate(db: SupportSQLiteDatabase) {
                db.execSQL("ALTER TABLE tracks ADD COLUMN popularity INTEGER NOT NULL DEFAULT 50")
                db.execSQL("ALTER TABLE tracks ADD COLUMN artist_popularity INTEGER NOT NULL DEFAULT 50")
                db.execSQL("ALTER TABLE tracks ADD COLUMN is_hidden_gem INTEGER NOT NULL DEFAULT 0")
                db.execSQL("ALTER TABLE tracks ADD COLUMN hidden_gem_type TEXT DEFAULT NULL")
            }
        }

        val MIGRATION_2_3 = object : Migration(2, 3) {
            override fun migrate(db: SupportSQLiteDatabase) {
                db.execSQL("""
                    CREATE TABLE IF NOT EXISTS artist_blacklist (
                        id TEXT PRIMARY KEY NOT NULL,
                        name TEXT NOT NULL,
                        external_id TEXT,
                        created_at INTEGER NOT NULL
                    )
                """.trimIndent())
                db.execSQL("CREATE UNIQUE INDEX IF NOT EXISTS index_artist_blacklist_name ON artist_blacklist(name)")
            }
        }

        val MIGRATION_3_4 = object : Migration(3, 4) {
            override fun migrate(db: SupportSQLiteDatabase) {
                db.execSQL("ALTER TABLE playlists ADD COLUMN yt_playlist_id TEXT DEFAULT NULL")
                db.execSQL("ALTER TABLE playlists ADD COLUMN remote_track_count INTEGER NOT NULL DEFAULT 0")
            }
        }

        @Volatile
        private var instance: AppDatabase? = null

        fun getInstance(context: Context): AppDatabase {
            return instance ?: synchronized(this) {
                migrateLegacyDatabaseFile(context.applicationContext)
                instance ?: Room.databaseBuilder(
                    context.applicationContext,
                    AppDatabase::class.java,
                    DATABASE_NAME
                )
                    .addMigrations(MIGRATION_1_2, MIGRATION_2_3, MIGRATION_3_4)
                    .fallbackToDestructiveMigration()
                    .build()
                    .also { instance = it }
            }
        }
    }
}
