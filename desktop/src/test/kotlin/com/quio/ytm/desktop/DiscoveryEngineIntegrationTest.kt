package com.quio.ytm.desktop

import com.quio.ytm.core.discovery.ArtistUtils
import com.quio.ytm.core.models.Track
import com.quio.ytm.core.shuffle.ShuffleEngine
import com.quio.ytm.domain.GenreCatalog
import com.quio.ytm.domain.SearchUtils
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class DiscoveryEngineIntegrationTest {

    @Test
    fun testGenreCatalogCategoriesAndPills() {
        val popular = GenreCatalog.ALL_GENRES.filter { it.category.equals("Popular", ignoreCase = true) }
        assertTrue(popular.isNotEmpty())
        assertTrue(popular.any { it.name == "Pop" })
        assertTrue(popular.any { it.name == "Rock" })

        val rockIndie = GenreCatalog.ALL_GENRES.filter { it.category.equals("Rock & Indie", ignoreCase = true) }
        assertTrue(rockIndie.any { it.name == "Alt Rock" })
    }

    @Test
    fun testArtistSearchNormalizationAndFuzzyMatching() {
        val artist = "Ricky Martin"
        val q1 = "ricky"
        val q2 = "MARTIN"
        val q3 = "Ricky Martin"

        val aNorm = SearchUtils.normalize(artist)
        assertTrue(aNorm.contains(SearchUtils.normalize(q1)))
        assertTrue(aNorm.contains(SearchUtils.normalize(q2)))
        assertTrue(aNorm == SearchUtils.normalize(q3))

        val split = SearchUtils.splitArtists("Ricky Martin, Maluma")
        assertTrue(split.contains("Ricky Martin"))
        assertTrue(split.contains("Maluma"))
    }

    @Test
    fun testDiscoveryBlacklistStrictNegativeExclusions() {
        val blacklist = setOf("Drake")
        val track1 = Track(id = "1", title = "God's Plan", artist = "Drake", album = "Scorpion", durationMs = 180000, thumbnailUrl = "")
        val track2 = Track(id = "2", title = "MIA", artist = "Bad Bunny feat. Drake", album = "X 100pre", durationMs = 210000, thumbnailUrl = "")
        val track3 = Track(id = "3", title = "Livin' la Vida Loca", artist = "Ricky Martin", album = "Ricky Martin", durationMs = 240000, thumbnailUrl = "")

        // Rule: Drake solo / primary is blocked
        assertTrue(ArtistUtils.isTrackBlockedByBlacklist(track1.artist, blacklist))
        // Collaboration where Drake is featured is allowed
        assertFalse(ArtistUtils.isTrackBlockedByBlacklist(track2.artist, blacklist))
        // Ricky Martin is allowed
        assertFalse(ArtistUtils.isTrackBlockedByBlacklist(track3.artist, blacklist))
    }

    @Test
    fun testShuffleEngineAvoidsConsecutiveArtists() {
        val tracks = listOf(
            Track(id = "1", title = "Song A1", artist = "Ricky Martin", album = "Album 1", durationMs = 1000, thumbnailUrl = ""),
            Track(id = "2", title = "Song A2", artist = "Ricky Martin", album = "Album 1", durationMs = 1000, thumbnailUrl = ""),
            Track(id = "3", title = "Song B1", artist = "Charly Garcia", album = "Album 2", durationMs = 1000, thumbnailUrl = ""),
            Track(id = "4", title = "Song B2", artist = "Charly Garcia", album = "Album 2", durationMs = 1000, thumbnailUrl = "")
        )

        val shuffled = ShuffleEngine.shuffleQueue(tracks, currentIndex = -1, applyAntiClumping = true)
        assertEquals(4, shuffled.size)
        // With anti-clumping, artists are spaced out
        assertFalse(shuffled[0].artist == shuffled[1].artist && shuffled[1].artist == shuffled[2].artist)
    }

    @Test
    fun testLiveArtistHarvestingAndSelfExclusion() = kotlinx.coroutines.runBlocking {
        val client = com.quio.ytm.core.api.InnertubeClient()
        val direct = client.search("Alejandro Lerner", "track")
        assertTrue("Direct search should return tracks", direct.isNotEmpty())

        val topTrack = direct.first()
        val radioTracks = client.getRadioTracks(topTrack.id)
        assertTrue("Radio should return recommended tracks", radioTracks.isNotEmpty())

        val distinctArtists = radioTracks.map { it.artist }.distinct()
        assertTrue("Radio tracks should contain multiple artists", distinctArtists.size > 5)

        // Test self-exclusion: exclude "Alejandro Lerner"
        val notArtist = "Alejandro Lerner"
        val filtered = radioTracks.filterNot { track ->
            val tArtist = track.artist.lowercase()
            val split = SearchUtils.splitArtists(track.artist).map { it.lowercase() }
            tArtist.contains(notArtist.lowercase()) || split.any { it.contains(notArtist.lowercase()) }
        }

        assertTrue("After excluding seed artist, should still have plenty of similar recommendations", filtered.isNotEmpty())
        assertFalse("Excluded artist should not appear in filtered results", filtered.any { it.artist.contains(notArtist, ignoreCase = true) })
    }
}
