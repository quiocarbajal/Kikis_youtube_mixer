package com.quio.ytm.desktop

import com.quio.ytm.core.discovery.ArtistUtils
import kotlinx.serialization.Serializable
import kotlinx.serialization.encodeToString
import kotlinx.serialization.json.Json
import java.io.File
import java.time.Instant
import java.util.UUID

@Serializable
data class BlacklistedArtistDto(
    val id: String = UUID.randomUUID().toString(),
    val name: String,
    val external_id: String? = null,
    val created_at: String = Instant.now().toString()
)

@Serializable
data class BlacklistResponse(
    val artists: List<BlacklistedArtistDto> = emptyList(),
    val count: Int = 0
)

@Serializable
data class AddBlacklistRequest(
    val name: String = "",
    val external_id: String? = null
)

@Serializable
data class AddBlacklistResponse(
    val status: String = "ok",
    val artist: BlacklistedArtistDto
)

class LocalBlacklistManager(
    private val storageFile: File = File(System.getProperty("user.dir"), "blacklist.json")
) {
    private val json = Json {
        prettyPrint = true
        isLenient = true
        ignoreUnknownKeys = true
    }

    private val artists = mutableListOf<BlacklistedArtistDto>()

    init {
        loadFromDisk()
    }

    @Synchronized
    private fun loadFromDisk() {
        if (!storageFile.exists()) {
            artists.clear()
            return
        }
        try {
            val content = storageFile.readText()
            if (content.isNotBlank()) {
                val loaded = json.decodeFromString<List<BlacklistedArtistDto>>(content)
                artists.clear()
                artists.addAll(loaded)
            }
        } catch (e: Exception) {
            System.err.println("Error reading blacklist.json: ${e.message}")
        }
    }

    @Synchronized
    private fun saveToDisk() {
        try {
            val encoded = json.encodeToString(artists)
            storageFile.writeText(encoded)
        } catch (e: Exception) {
            System.err.println("Error writing to blacklist.json: ${e.message}")
        }
    }

    @Synchronized
    fun getAll(): List<BlacklistedArtistDto> {
        return artists.toList()
    }

    @Synchronized
    fun getNames(): Set<String> {
        return artists.map { it.name.trim() }.toSet()
    }

    @Synchronized
    fun add(rawName: String, externalId: String? = null): BlacklistedArtistDto {
        val cleanName = rawName.trim()
        if (cleanName.isBlank()) {
            throw IllegalArgumentException("Artist name cannot be empty")
        }

        val existing = artists.find { it.name.equals(cleanName, ignoreCase = true) }
        if (existing != null) {
            return existing
        }

        val newEntry = BlacklistedArtistDto(
            id = UUID.randomUUID().toString(),
            name = cleanName,
            external_id = externalId,
            created_at = Instant.now().toString()
        )
        artists.add(0, newEntry)
        saveToDisk()
        return newEntry
    }

    @Synchronized
    fun remove(nameOrId: String): Boolean {
        val target = nameOrId.trim()
        val removed = artists.removeAll {
            it.id == target || it.name.equals(target, ignoreCase = true)
        }
        if (removed) {
            saveToDisk()
        }
        return removed
    }

    @Synchronized
    fun count(): Int {
        return artists.size
    }

    @Synchronized
    fun isTrackBlocked(trackArtistString: String): Boolean {
        val blacklistedNames = artists.map { it.name }
        return ArtistUtils.isTrackBlockedByBlacklist(trackArtistString, blacklistedNames)
    }
}
