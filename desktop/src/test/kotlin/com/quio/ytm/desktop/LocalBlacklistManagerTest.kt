package com.quio.ytm.desktop

import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test
import java.io.File

class LocalBlacklistManagerTest {

    private lateinit var tempFile: File
    private lateinit var manager: LocalBlacklistManager

    @Before
    fun setUp() {
        tempFile = File.createTempFile("test_blacklist", ".json")
        tempFile.delete() // start empty
        manager = LocalBlacklistManager(tempFile)
    }

    @After
    fun tearDown() {
        if (tempFile.exists()) {
            tempFile.delete()
        }
    }

    @Test
    fun testAddAndGetArtists() {
        assertEquals(0, manager.count())

        val entry1 = manager.add("Drake")
        assertEquals("Drake", entry1.name)
        assertEquals(1, manager.count())
        assertTrue(manager.getNames().contains("Drake"))

        val entry2 = manager.add("Kanye West")
        assertEquals("Kanye West", entry2.name)
        assertEquals(2, manager.count())

        // Duplicate case-insensitive check
        val entryDup = manager.add("drake")
        assertEquals(entry1.id, entryDup.id)
        assertEquals(2, manager.count())
    }

    @Test
    fun testRemoveArtistByNameAndId() {
        val entry1 = manager.add("Bad Bunny")
        val entry2 = manager.add("Rosalia")
        assertEquals(2, manager.count())

        // Remove by name
        val removed1 = manager.remove("bad bunny")
        assertTrue(removed1)
        assertEquals(1, manager.count())
        assertFalse(manager.getNames().contains("Bad Bunny"))

        // Remove by ID
        val removed2 = manager.remove(entry2.id)
        assertTrue(removed2)
        assertEquals(0, manager.count())
    }

    @Test
    fun testTrackBlockedRules() {
        manager.add("Dua Lipa")

        // 1. Primary artist is blacklisted -> BLOCKED
        assertTrue(manager.isTrackBlocked("Dua Lipa"))
        assertTrue(manager.isTrackBlocked("Dua Lipa feat. Elton John"))
        assertTrue(manager.isTrackBlocked("dua lipa, Daft Punk"))

        // 2. Blacklisted artist is only a collaborator -> ALLOWED
        assertFalse(manager.isTrackBlocked("Calvin Harris feat. Dua Lipa"))
        assertFalse(manager.isTrackBlocked("Martin Garrix & Dua Lipa"))

        // 3. Unrelated artist -> ALLOWED
        assertFalse(manager.isTrackBlocked("The Weeknd"))
    }
}
