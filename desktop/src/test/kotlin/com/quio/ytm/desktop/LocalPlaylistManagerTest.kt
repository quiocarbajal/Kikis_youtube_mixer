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
}
