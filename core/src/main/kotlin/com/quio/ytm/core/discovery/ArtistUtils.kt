package com.quio.ytm.core.discovery

import java.text.Normalizer

object ArtistUtils {
    private val COLLAB_DELIMITERS = listOf(
        ",",
        " feat.",
        " ft.",
        " feat ",
        " ft ",
        " featuring ",
        " & ",
        " / ",
        " with ",
        " x ",
        " X "
    )

    /**
     * Extracts the primary / main artist name from a combined artist string.
     * e.g.:
     *   "Daft Punk, Pharrell Williams" -> "Daft Punk"
     *   "Calvin Harris feat. Dua Lipa" -> "Calvin Harris"
     *   "Drake & 21 Savage" -> "Drake"
     *   "Taylor Swift" -> "Taylor Swift"
     */
    fun extractPrimaryArtist(artistString: String): String {
        var clean = artistString.trim()
        if (clean.isBlank()) return ""

        for (delim in COLLAB_DELIMITERS) {
            val idx = clean.indexOf(delim, ignoreCase = true)
            if (idx > 0) {
                clean = clean.substring(0, idx).trim()
            }
        }
        return clean.trim()
    }

    /**
     * Checks if a track's artist string indicates a collaboration or featured artist.
     */
    fun hasCollaborators(artistString: String): Boolean {
        if (artistString.isBlank()) return false
        return COLLAB_DELIMITERS.any { artistString.contains(it, ignoreCase = true) }
    }

    /**
     * Normalizes text for robust comparison (strips accents, lowercase, trimmed).
     */
    fun normalizeArtistName(name: String): String {
        val trimmed = name.trim().lowercase()
        return Normalizer.normalize(trimmed, Normalizer.Form.NFD)
            .replace("\\p{InCombiningDiacriticalMarks}+".toRegex(), "")
    }

    /**
     * Evaluates whether a track should be blocked by the blacklist.
     * Rule:
     * - If the track's primary artist matches any blacklisted artist, returns true (BLOCKED).
     * - If the blacklisted artist only appears as a secondary collaborator, returns false (ALLOWED).
     */
    fun isTrackBlockedByBlacklist(trackArtistString: String, blacklistedArtists: Collection<String>): Boolean {
        if (blacklistedArtists.isEmpty() || trackArtistString.isBlank()) return false

        val primary = normalizeArtistName(extractPrimaryArtist(trackArtistString))
        if (primary.isBlank()) return false

        return blacklistedArtists.any { blacklisted ->
            val normBlocked = normalizeArtistName(blacklisted)
            normBlocked.isNotEmpty() && (primary == normBlocked || primary.startsWith(normBlocked) || normBlocked.startsWith(primary))
        }
    }
}
