package com.quio.ytm.core.discovery

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class ArtistUtilsTest {

    @Test
    fun testExtractPrimaryArtist() {
        assertEquals("Taylor Swift", ArtistUtils.extractPrimaryArtist("Taylor Swift"))
        assertEquals("Calvin Harris", ArtistUtils.extractPrimaryArtist("Calvin Harris feat. Dua Lipa"))
        assertEquals("Calvin Harris", ArtistUtils.extractPrimaryArtist("Calvin Harris ft. Dua Lipa"))
        assertEquals("Daft Punk", ArtistUtils.extractPrimaryArtist("Daft Punk, Pharrell Williams"))
        assertEquals("Drake", ArtistUtils.extractPrimaryArtist("Drake & 21 Savage"))
        assertEquals("David Guetta", ArtistUtils.extractPrimaryArtist("David Guetta / Bebe Rexha"))
        assertEquals("Coldplay", ArtistUtils.extractPrimaryArtist("Coldplay with BTS"))
        assertEquals("Clean Bandit", ArtistUtils.extractPrimaryArtist("Clean Bandit featuring Zara Larsson"))
    }

    @Test
    fun testHasCollaborators() {
        assertTrue(ArtistUtils.hasCollaborators("Calvin Harris feat. Dua Lipa"))
        assertTrue(ArtistUtils.hasCollaborators("Daft Punk, Pharrell Williams"))
        assertTrue(ArtistUtils.hasCollaborators("Drake & 21 Savage"))
        assertFalse(ArtistUtils.hasCollaborators("Taylor Swift"))
        assertFalse(ArtistUtils.hasCollaborators("Daft Punk"))
    }

    @Test
    fun testIsTrackBlockedByBlacklist() {
        val blacklist = listOf("Dua Lipa", "Drake", "Bad Bunny")

        // 1. Primary artist is blacklisted -> BLOCKED
        assertTrue(ArtistUtils.isTrackBlockedByBlacklist("Dua Lipa", blacklist))
        assertTrue(ArtistUtils.isTrackBlockedByBlacklist("Dua Lipa feat. Elton John", blacklist))
        assertTrue(ArtistUtils.isTrackBlockedByBlacklist("Drake & 21 Savage", blacklist))
        assertTrue(ArtistUtils.isTrackBlockedByBlacklist("Bad Bunny, Chencho Corleone", blacklist))

        // 2. Blacklisted artist is ONLY a collaborator -> NOT BLOCKED
        assertFalse(ArtistUtils.isTrackBlockedByBlacklist("Calvin Harris feat. Dua Lipa", blacklist))
        assertFalse(ArtistUtils.isTrackBlockedByBlacklist("21 Savage & Drake", blacklist))
        assertFalse(ArtistUtils.isTrackBlockedByBlacklist("J Balvin, Bad Bunny", blacklist))

        // 3. Unrelated artists -> NOT BLOCKED
        assertFalse(ArtistUtils.isTrackBlockedByBlacklist("The Weeknd", blacklist))
        assertFalse(ArtistUtils.isTrackBlockedByBlacklist("Daft Punk", blacklist))
    }
}
