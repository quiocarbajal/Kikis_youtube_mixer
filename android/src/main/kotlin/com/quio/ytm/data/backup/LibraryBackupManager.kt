package com.quio.ytm.data.backup

import android.content.Context
import android.content.Intent
import android.net.Uri
import androidx.core.content.FileProvider
import com.google.gson.Gson
import com.google.gson.GsonBuilder
import com.quio.ytm.data.remote.YtmCloudService
import com.quio.ytm.data.repository.YtmMixerRepository
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import java.io.File
import java.io.FileWriter
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale
import java.util.TimeZone

class LibraryBackupManager(
    private val context: Context,
    private val cloudService: YtmCloudService,
    private val repository: YtmMixerRepository
) {
    private val gson: Gson = GsonBuilder().setPrettyPrinting().create()

    private val backupDir: File by lazy {
        File(context.filesDir, "backups").apply {
            if (!exists()) mkdirs()
        }
    }

    /**
     * Checks if an initial library backup already exists on the device.
     */
    fun hasInitialBackup(): Boolean {
        val initialFile = File(backupDir, "initial_library_backup.json")
        return initialFile.exists() && initialFile.length() > 0
    }

    /**
     * Returns the latest or initial backup file if present.
     */
    fun getInitialBackupFile(): File? {
        val initialFile = File(backupDir, "initial_library_backup.json")
        return if (initialFile.exists()) initialFile else null
    }

    /**
     * Creates a complete machine-readable snapshot of all liked songs and all user playlists directly from YouTube Music repository.
     */
    suspend fun createFullSafetyBackup(
        accessToken: String = "",
        onProgress: (progress: Float, status: String) -> Unit = { _, _ -> }
    ): Result<File> = withContext(Dispatchers.IO) {
        try {
            onProgress(0.05f, "Obteniendo perfil de usuario...")
            val userEmail = repository.getSetting("ytm_user_email") ?: "user"

            // 1. Fetch All Liked Tracks from repository
            onProgress(0.15f, "Obteniendo Canciones que te gustan...")
            val likedEntities = repository.getTracksForPlaylistSync("liked_songs")
            val likedBackupTracks = likedEntities.map { t ->
                BackupTrack(
                    id = t.id,
                    uri = "https://music.youtube.com/watch?v=${t.id}",
                    title = t.title,
                    artist = t.artist,
                    album = t.album,
                    durationMs = t.durationMs,
                    addedAt = null
                )
            }
            onProgress(0.50f, "Respaldadas ${likedBackupTracks.size} Canciones que te gustan...")

            // 2. Fetch All Playlists and their tracks from repository
            onProgress(0.55f, "Obteniendo lista de Playlists...")
            val allPlaylists = repository.getAllPlaylistsSync().filter { it.id != "liked_songs" && it.id != "all_tracks" }
            val backupPlaylists = mutableListOf<BackupPlaylist>()
            allPlaylists.forEachIndexed { index, pl ->
                val plTracks = repository.getTracksForPlaylistSync(pl.id).map { t ->
                    BackupTrack(
                        id = t.id,
                        uri = "https://music.youtube.com/watch?v=${t.id}",
                        title = t.title,
                        artist = t.artist,
                        album = t.album,
                        durationMs = t.durationMs,
                        addedAt = null
                    )
                }
                backupPlaylists.add(
                    BackupPlaylist(
                        id = pl.id,
                        name = pl.name,
                        description = null,
                        isPublic = false,
                        isCollaborative = false,
                        snapshotId = null,
                        totalTracks = plTracks.size,
                        tracks = plTracks
                    )
                )
                val frac = (index + 1).toFloat() / allPlaylists.size.coerceAtLeast(1) * 0.35f
                onProgress(0.55f + frac, "Respaldando playlist ${index + 1} de ${allPlaylists.size}: ${pl.name}")
            }

            onProgress(0.92f, "Generando archivo de respaldo seguro...")
            val now = Date()
            val dateFormatUtc = SimpleDateFormat("yyyy-MM-dd'T'HH:mm:ss'Z'", Locale.US).apply {
                timeZone = TimeZone.getTimeZone("UTC")
            }
            val dateFileName = SimpleDateFormat("yyyyMMdd_HHmmss", Locale.US).format(now)

            val backup = YtmSafetyBackup(
                version = 1,
                createdAtUtc = dateFormatUtc.format(now),
                appVersion = "1.0",
                ytmUserId = userEmail,
                totalLikedTracks = likedBackupTracks.size,
                totalPlaylists = backupPlaylists.size,
                likedSongs = likedBackupTracks,
                playlists = backupPlaylists
            )

            // Save both timestamped backup and initial_library_backup.json
            val timestampedFile = File(backupDir, "kiki_ytm_backup_$dateFileName.json")
            FileWriter(timestampedFile).use { writer ->
                gson.toJson(backup, writer)
            }

            val initialFile = File(backupDir, "initial_library_backup.json")
            if (!initialFile.exists()) {
                timestampedFile.copyTo(initialFile, overwrite = true)
            }

            onProgress(1.0f, "¡Respaldo seguro completado!")
            Result.success(timestampedFile)
        } catch (e: Exception) {
            android.util.Log.e("LibraryBackupManager", "Error creating full safety backup", e)
            Result.failure(e)
        }
    }

    /**
     * Builds an Android Intent.ACTION_SEND to share/email the backup file.
     */
    fun createShareBackupIntent(backupFile: File): Intent {
        val uri: Uri = FileProvider.getUriForFile(
            context,
            "${context.packageName}.fileprovider",
            backupFile
        )

        return Intent(Intent.ACTION_SEND).apply {
            type = "application/json"
            putExtra(Intent.EXTRA_STREAM, uri)
            putExtra(Intent.EXTRA_SUBJECT, "🛡️ Respaldo de Seguridad YouTube Music - Kiki Mixer")
            putExtra(
                Intent.EXTRA_TEXT,
                "Adjunto encontrarás tu copia de seguridad completa de canciones favoritas y playlists de YouTube Music generada con Kiki's YouTube Music Mixer.\n\nGuarda este archivo en un lugar seguro (tu correo o Google Drive) como resguardo."
            )
            addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION)
        }
    }
}
