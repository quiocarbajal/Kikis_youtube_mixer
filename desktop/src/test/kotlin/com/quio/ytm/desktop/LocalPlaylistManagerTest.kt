package com.quio.ytm.desktop

import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test
import java.io.File

class LocalPlaylistManagerTest {

    private lateinit var tempFile: File
    private lateinit var manager: LocalPlaylistManager

    @Before
    fun setUp() {
        tempFile = File.createTempFile("test_playlists", ".json")
        tempFile.delete() // start empty
        manager = LocalPlaylistManager(tempFile)
    }

    @After
    fun tearDown() {
        if (tempFile.exists()) {
            tempFile.delete()
        }
    }

    @Test
    fun testLikedSongsAutoInitialized() {
        // Liked Songs playlist should automatically be initialized
        val liked = manager.getPlaylist("liked_songs")
        assertNotNull(liked)
        assertEquals("Liked Songs", liked?.name)
        assertEquals(0, liked?.total_tracks)

        val summaries = manager.getSummaries()
        assertTrue(summaries.any { it.id == "liked_songs" })
    }

    @Test
    fun testAddAndRemoveTrackInLikedSongs() {
        val track1 = TrackDto(
            id = "t1",
            uri = "yt:track:t1",
            title = "Song 1",
            artist = "Artist A",
            primary_artist = "Artist A",
            album = "Album A",
            duration_ms = 180000,
            durationMs = 180000,
            thumbnailUrl = "http://thumb1",
            album_art_url = "http://thumb1",
            loudnessDb = -14.0
        )
        val track2 = TrackDto(
            id = "t2",
            uri = "yt:track:t2",
            title = "Song 2",
            artist = "Artist B",
            primary_artist = "Artist B",
            album = "Album B",
            duration_ms = 210000,
            durationMs = 210000,
            thumbnailUrl = "http://thumb2",
            album_art_url = "http://thumb2",
            loudnessDb = -14.0
        )

        // Add tracks
        assertTrue(manager.addTrack("liked_songs", track1))
        assertTrue(manager.addTrack("liked_songs", track2))

        val liked = manager.getPlaylist("liked_songs")
        assertEquals(2, liked?.total_tracks)
        assertEquals(2, liked?.tracks?.size)
        assertEquals("t1", liked?.tracks?.get(0)?.id)
        assertEquals("t2", liked?.tracks?.get(1)?.id)

        // Duplicate add is idempotent
        assertTrue(manager.addTrack("liked_songs", track1))
        assertEquals(2, manager.getPlaylist("liked_songs")?.total_tracks)

        // Remove track
        assertTrue(manager.removeTrack("liked_songs", "t1"))
        val afterRemove = manager.getPlaylist("liked_songs")
        assertEquals(1, afterRemove?.total_tracks)
        assertEquals("t2", afterRemove?.tracks?.get(0)?.id)
    }

    @Test
    fun testReorderTracksInPlaylist() {
        val t1 = TrackDto(id = "1", uri = "yt:track:1", title = "S1", artist = "A1", primary_artist = "A1", album = "", duration_ms = 1000, durationMs = 1000, thumbnailUrl = "", album_art_url = "", loudnessDb = 0.0)
        val t2 = TrackDto(id = "2", uri = "yt:track:2", title = "S2", artist = "A2", primary_artist = "A2", album = "", duration_ms = 1000, durationMs = 1000, thumbnailUrl = "", album_art_url = "", loudnessDb = 0.0)
        val t3 = TrackDto(id = "3", uri = "yt:track:3", title = "S3", artist = "A3", primary_artist = "A3", album = "", duration_ms = 1000, durationMs = 1000, thumbnailUrl = "", album_art_url = "", loudnessDb = 0.0)

        manager.addTrack("liked_songs", t1)
        manager.addTrack("liked_songs", t2)
        manager.addTrack("liked_songs", t3)

        // Reorder to 3, 1, 2
        assertTrue(manager.reorderTracks("liked_songs", listOf("3", "1", "2")))
        val reordered = manager.getPlaylist("liked_songs")?.tracks
        assertEquals("3", reordered?.get(0)?.id)
        assertEquals("1", reordered?.get(1)?.id)
        assertEquals("2", reordered?.get(2)?.id)
    }

    @Test
    fun testMarkSyncedAndExportState() {
        val t1 = TrackDto(id = "1", uri = "yt:track:1", title = "S1", artist = "A1", primary_artist = "A1", album = "", duration_ms = 1000, durationMs = 1000, thumbnailUrl = "", album_art_url = "", loudnessDb = 0.0)
        val created = manager.savePlaylist(
            name = "Export Mix",
            description = "Test Export",
            tracks = listOf(t1),
            overwrite = false,
            playlistId = null
        )

        assertEquals("Export Mix", created.name)
        assertEquals(false, created.is_synced)

        val synced = manager.markSynced(created.id, "PL_remote_123")
        assertNotNull(synced)
        assertEquals("PL_remote_123", synced?.yt_playlist_id)
        assertEquals(true, synced?.is_synced)

        val retrieved = manager.getPlaylist(created.id)
        assertEquals(true, retrieved?.is_synced)
        assertEquals("PL_remote_123", retrieved?.yt_playlist_id)
    }

    @Test
    fun testMergeTracksDeduplication() {
        val t1 = TrackDto(id = "1", uri = "yt:track:1", title = "S1", artist = "A1", primary_artist = "A1", album = "", duration_ms = 1000, durationMs = 1000, thumbnailUrl = "", album_art_url = "", loudnessDb = 0.0)
        val t2 = TrackDto(id = "2", uri = "yt:track:2", title = "S2", artist = "A2", primary_artist = "A2", album = "", duration_ms = 1000, durationMs = 1000, thumbnailUrl = "", album_art_url = "", loudnessDb = 0.0)
        val t3 = TrackDto(id = "3", uri = "yt:track:3", title = "S3", artist = "A3", primary_artist = "A3", album = "", duration_ms = 1000, durationMs = 1000, thumbnailUrl = "", album_art_url = "", loudnessDb = 0.0)

        val created = manager.savePlaylist("Merge Mix", "", listOf(t1, t2), false, null)
        val mergedSummary = manager.mergeTracks(created.id, listOf(t2, t3))

        assertNotNull(mergedSummary)
        assertEquals(3, mergedSummary?.total_tracks)

        val pl = manager.getPlaylist(created.id)
        assertEquals(listOf("1", "2", "3"), pl?.tracks?.map { it.id })
    }

    @Test
    fun testCreateAndRestoreBackupSnapshot() {
        val t1 = TrackDto(id = "1", uri = "yt:track:1", title = "Liked 1", artist = "A1", primary_artist = "A1", album = "", duration_ms = 1000, durationMs = 1000, thumbnailUrl = "", album_art_url = "", loudnessDb = 0.0)
        val t2 = TrackDto(id = "2", uri = "yt:track:2", title = "Playlist Song 1", artist = "A2", primary_artist = "A2", album = "", duration_ms = 1000, durationMs = 1000, thumbnailUrl = "", album_art_url = "", loudnessDb = 0.0)

        manager.addTrack("liked_songs", t1)
        val pl = manager.savePlaylist("Chill Mix", "Test Desc", listOf(t2), false, null)

        // 1. Create backup snapshot
        val backupFile = manager.createBackupSnapshot("test_snapshot")
        assertNotNull(backupFile)
        assertTrue(backupFile?.exists() == true)

        val latest = manager.getLatestBackup()
        assertNotNull(latest)
        assertTrue(latest?.exists() == true)

        // 2. Modify library (delete playlist and clear liked songs)
        manager.deletePlaylist(pl.id)
        manager.removeTrack("liked_songs", "1")
        assertEquals(0, manager.getPlaylist("liked_songs")?.total_tracks)
        assertEquals(null, manager.getPlaylist(pl.id))

        // 3. Restore backup
        val restored = manager.restoreBackup(backupFile!!)
        assertTrue(restored)

        val restoredLiked = manager.getPlaylist("liked_songs")
        assertEquals(1, restoredLiked?.total_tracks)
        assertEquals("1", restoredLiked?.tracks?.get(0)?.id)

        val restoredPl = manager.getPlaylist(pl.id)
        assertNotNull(restoredPl)
        assertEquals("Chill Mix", restoredPl?.name)
        assertEquals(1, restoredPl?.total_tracks)
        assertEquals("2", restoredPl?.tracks?.get(0)?.id)

        // Clean up backup dir
        manager.backupDir.deleteRecursively()
    }

    @Test
    fun testFilterPrivateAndDeletedVideos() {
        val t1 = TrackDto(id = "1", uri = "yt:track:1", title = "Real Song", artist = "A1", primary_artist = "A1", album = "", duration_ms = 180000, durationMs = 180000, thumbnailUrl = "", album_art_url = "", loudnessDb = 0.0)
        val tPrivate = TrackDto(id = "2", uri = "yt:track:2", title = "Private video", artist = "Unknown", primary_artist = "", album = "", duration_ms = 0, durationMs = 0, thumbnailUrl = "", album_art_url = "", loudnessDb = 0.0)
        val tDeleted = TrackDto(id = "3", uri = "yt:track:3", title = "[Deleted video]", artist = "Unknown", primary_artist = "", album = "", duration_ms = 0, durationMs = 0, thumbnailUrl = "", album_art_url = "", loudnessDb = 0.0)
        val tSpanish = TrackDto(id = "4", uri = "yt:track:4", title = "Vídeo eliminado", artist = "Unknown", primary_artist = "", album = "", duration_ms = 0, durationMs = 0, thumbnailUrl = "", album_art_url = "", loudnessDb = 0.0)

        assertTrue(manager.isPrivateOrDeletedTrack(tPrivate))
        assertTrue(manager.isPrivateOrDeletedTrack(tDeleted))
        assertTrue(manager.isPrivateOrDeletedTrack(tSpanish))
        assertTrue(!manager.isPrivateOrDeletedTrack(t1))

        manager.mergeTracks("liked_songs", listOf(t1, tPrivate, tDeleted, tSpanish))
        val liked = manager.getPlaylist("liked_songs")
        assertEquals(1, liked?.total_tracks)
        assertEquals("1", liked?.tracks?.get(0)?.id)
    }
}
