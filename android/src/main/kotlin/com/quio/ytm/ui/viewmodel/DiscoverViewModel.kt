package com.quio.ytm.ui.viewmodel
 
import com.quio.ytm.core.discovery.ArtistUtils
import com.quio.ytm.core.models.Track
import com.quio.ytm.core.shuffle.ShuffleEngine
import com.quio.ytm.domain.SearchUtils
import com.quio.ytm.domain.GenreCatalog
import com.quio.ytm.domain.GenreItem

import com.quio.ytm.data.repository.YtmMixerRepository
import com.quio.ytm.data.remote.YtmCloudService

import com.quio.ytm.data.local.entity.TrackEntity

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope







import com.quio.ytm.ui.theme.Strings
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.Job
import kotlinx.coroutines.async
import kotlinx.coroutines.awaitAll
import kotlinx.coroutines.coroutineScope
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import java.text.Normalizer

enum class ChipModifier {
    INCLUDE, // [+ AND / + Y]
    EXCLUDE  // [- NOT / - NO]
}

data class ModifierChip(
    val term: String,
    val modifier: ChipModifier
)

enum class RecentlyHeardFilter(val days: Int, val label: String) {
    NONE(0, "Ninguno"),
    LAST_7_DAYS(7, "7 días"),
    LAST_30_DAYS(30, "30 días")
}

enum class CatalogSearchType {
    ALL,    // 🌟 Todo
    ARTIST, // 👤 Artista
    TRACK,  // 🎵 Canción
    LYRICS  // 📜 Letra
}

enum class SearchLogicOperator {
    AND, // Y (Todas las palabras)
    OR   // O (Cualquiera de las palabras)
}

data class DiscoverUiState(
    val activeSubTab: Int = 0, // 0 = Surprise Me!, 1 = Catalog Search
    val searchQuery: String = "",
    val searchResults: List<TrackEntity> = emptyList(),
    val catalogSearchType: CatalogSearchType = CatalogSearchType.ALL,
    val catalogOperator: SearchLogicOperator = SearchLogicOperator.AND,
    val suggestedCatalogQueries: List<String> = emptyList(),
    val catalogModifiers: Set<ModifierChip> = emptySet(),
    val isSearchingCatalog: Boolean = false,
    val artistInputText: String = "",
    val suggestedArtists: List<String> = emptyList(),
    val artistModifiers: Set<ModifierChip> = emptySet(),
    val genreInputText: String = "",
    val suggestedGenres: List<String> = emptyList(),
    val selectedGenreCategory: String = "All",
    val genreModifiers: Set<ModifierChip> = emptySet(),
    val trackInputText: String = "",
    val suggestedTracks: List<TrackEntity> = emptyList(),
    val trackModifiers: Set<ModifierChip> = emptySet(),
    val selectedDecades: Set<String> = emptySet(),
    val excludeLibrarySongs: Boolean = true,
    val recentlyHeardFilter: RecentlyHeardFilter = RecentlyHeardFilter.NONE,
    val targetCount: Int = 30, // 30, 50, 75, 100 (matching Mac app)
    val lowPopularityOnly: Boolean = false, // 💎 Hidden Gems switch
    val hiddenGemTarget: String = "artist", // "artist", "track", "both"
    val isGeneratingMix: Boolean = false,
    val discoveredMix: List<TrackEntity> = emptyList(),
    val infoBannerMessage: String? = null
)

class DiscoverViewModel(
    private val repository: YtmMixerRepository,
    private val cloudService: YtmCloudService? = null
) : ViewModel() {

    private val _uiState = MutableStateFlow(DiscoverUiState())
    val uiState: StateFlow<DiscoverUiState> = _uiState.asStateFlow()

    private val allKnownArtists = listOf(
        "Soda Stereo", "Gustavo Cerati", "Charly García", "León Gieco", "Los Enanitos Verdes", "Luis Alberto Spinetta",
        "Babasónicos", "Fito Páez", "Virus", "Patricio Rey", "Andrés Calamaro", "Los Fabulosos Cadillacs",
        "The Cranberries", "Cranberries", "Daft Punk", "The Weeknd", "M83",
        "Arctic Monkeys", "Queen", "Fleetwood Mac", "Tame Impala", "Gorillaz",
        "New Order", "Depeche Mode", "Michael Jackson", "Nirvana", "Radiohead",
        "Dua Lipa", "Billie Eilish", "Coldplay", "The Beatles", "Pink Floyd",
        "Led Zeppelin", "David Bowie", "The Rolling Stones", "Red Hot Chili Peppers",
        "Oasis", "Blur", "The Cure", "The Smiths", "U2", "AC/DC",
        "Guns N' Roses", "Metallica", "Aerosmith", "The Police", "Stevie Wonder",
        "Prince", "Madonna", "ABBA", "Elton John", "Bee Gees", "Bob Marley",
        "The Strokes", "Phoenix", "The Killers", "Franz Ferdinand", "MGMT",
        "Foster The People", "The White Stripes", "Interpol", "Two Door Cinema Club", "Vampire Weekend",
        "Pearl Jam", "Smashing Pumpkins", "Tears for Fears", "Joy Division", "Talking Heads", "a-ha"
    )



    fun setSubTab(index: Int) {
        _uiState.update { it.copy(activeSubTab = index) }
    }

    private var searchJob: Job? = null
    private var artistJob: Job? = null
    private var trackJob: Job? = null
    private var catalogQueryJob: Job? = null

    private fun normalize(text: String): String {
        return SearchUtils.normalize(text)
    }

    private suspend fun getValidAccessToken(): String? {
        return "ytm_token"
    }

    fun setCatalogSearchType(type: CatalogSearchType) {
        _uiState.update { it.copy(catalogSearchType = type) }
        executeCatalogSearch()
    }

    fun setCatalogOperator(operator: SearchLogicOperator) {
        _uiState.update { it.copy(catalogOperator = operator) }
        executeCatalogSearch()
    }

    fun addCatalogModifier(term: String, modifier: ChipModifier) {
        val trimmed = term.trim()
        if (trimmed.isBlank()) return
        _uiState.update { state ->
            val updated = state.catalogModifiers.toMutableSet()
            updated.add(ModifierChip(trimmed, modifier))
            state.copy(catalogModifiers = updated, searchQuery = "")
        }
        executeCatalogSearch()
    }

    fun removeCatalogModifier(chip: ModifierChip) {
        _uiState.update { state ->
            val updated = state.catalogModifiers.toMutableSet()
            updated.remove(chip)
            state.copy(catalogModifiers = updated)
        }
        executeCatalogSearch()
    }

    fun removeCatalogModifier(term: String) {
        _uiState.update { state ->
            val updated = state.catalogModifiers.toMutableSet()
            updated.removeIf { it.term.equals(term, ignoreCase = true) }
            state.copy(catalogModifiers = updated)
        }
        executeCatalogSearch()
    }

    fun setSearchQuery(query: String) {
        _uiState.update { it.copy(searchQuery = query) }
        val trimmed = query.trim()
        if (trimmed.isBlank()) {
            catalogQueryJob?.cancel()
            _uiState.update { it.copy(suggestedCatalogQueries = emptyList()) }
        } else {
            catalogQueryJob?.cancel()
            catalogQueryJob = viewModelScope.launch(Dispatchers.IO) {
                delay(60)
                val localTracks = repository.getAllTracksSync()
                val artistMatches = (allKnownArtists + localTracks.flatMap { SearchUtils.splitArtists(it.artist) })
                    .distinct()
                    .filter { SearchUtils.fuzzyMatches(trimmed, it) }
                    .sortedWith(
                        compareBy<String> { SearchUtils.matchScore(trimmed, it) }
                            .thenBy { it.lowercase() }
                    )
                    .take(4)
                val trackMatches = localTracks.map { it.title }
                    .distinct()
                    .filter { SearchUtils.fuzzyMatches(trimmed, it) }
                    .sortedWith(
                        compareBy<String> { SearchUtils.matchScore(trimmed, it) }
                            .thenBy { it.lowercase() }
                    )
                    .take(4)
                val suggestions = (artistMatches + trackMatches).distinct().take(6)
                withContext(Dispatchers.Main) {
                    _uiState.update { it.copy(suggestedCatalogQueries = suggestions) }
                }
            }
        }
        executeCatalogSearch()
    }

    private fun executeCatalogSearch() {
        searchJob?.cancel()
        searchJob = viewModelScope.launch {
            delay(200)
            val state = _uiState.value
            val query = state.searchQuery.trim()
            val modifiers = state.catalogModifiers

            if (query.isBlank() && modifiers.isEmpty()) {
                _uiState.update { it.copy(searchResults = emptyList(), isSearchingCatalog = false) }
                return@launch
            }

            _uiState.update { it.copy(isSearchingCatalog = true) }

            val candidates = mutableListOf<TrackEntity>()
            val seenIds = mutableSetOf<String>()

            // 1. Search YouTube Music Cloud Service if token and service are available
            val token = getValidAccessToken()
            if (!token.isNullOrBlank() && cloudService != null && query.isNotBlank()) {
                try {
                    val cleanQuery = YtmCloudService.sanitizeQuery(query)
                    val searchQuery = cleanQuery
                    for (offset in listOf(0, 10, 20)) {
                        val cloudResults = cloudService.searchTracks(token, searchQuery, limit = 10, offset = offset)
                        for (t in cloudResults) {
                            if (seenIds.add(t.id)) {
                                candidates.add(t)
                            }
                        }
                        if (cloudResults.size < 10) break
                    }
                } catch (e: Exception) {
                    android.util.Log.w("DiscoverVM", "Cloud catalog search error", e)
                }
            } else if (!token.isNullOrBlank() && cloudService != null && query.isBlank() && modifiers.isNotEmpty()) {
                try {
                    for (mod in modifiers) {
                        if (mod.modifier == ChipModifier.INCLUDE) {
                            val cleanMod = YtmCloudService.sanitizeQuery(mod.term)
                            val modQuery = when (state.catalogSearchType) {
                                CatalogSearchType.ARTIST -> "artist:\"$cleanMod\""
                                CatalogSearchType.TRACK -> "track:\"$cleanMod\""
                                else -> cleanMod
                            }
                            for (offset in listOf(0, 10, 20)) {
                                val cloudResults = cloudService.searchTracks(token, modQuery, limit = 10, offset = offset)
                                for (t in cloudResults) {
                                    if (seenIds.add(t.id)) {
                                        candidates.add(t)
                                    }
                                }
                                if (cloudResults.size < 10) break
                            }
                        }
                    }
                } catch (e: Exception) {
                    android.util.Log.w("DiscoverVM", "Cloud modifier search error", e)
                }
            }

            // 2. Query Local Library & Master Catalog
            val localTracks = withContext(Dispatchers.IO) { repository.getAllTracksSync() }
            for (t in localTracks) {
                if (seenIds.add(t.id)) {
                    candidates.add(t)
                }
            }

            // 3. Filter candidates based on catalogSearchType, catalogOperator, and catalogModifiers
            val tokens = SearchUtils.normalize(query).split("\\s+".toRegex()).filter { it.isNotBlank() }

            val filtered = candidates.filter { track ->
                if (tokens.isNotEmpty()) {
                    val targetField = when (state.catalogSearchType) {
                        CatalogSearchType.ALL -> "${track.title} ${track.artist} ${track.album}"
                        CatalogSearchType.ARTIST -> track.artist
                        CatalogSearchType.TRACK -> track.title
                        CatalogSearchType.LYRICS -> "${track.title} ${track.artist} ${track.album}"
                    }
                    val matches = when (state.catalogOperator) {
                        SearchLogicOperator.AND -> tokens.all { token -> SearchUtils.fuzzyMatches(token, targetField) }
                        SearchLogicOperator.OR -> tokens.any { token -> SearchUtils.fuzzyMatches(token, targetField) }
                    }
                    if (!matches) return@filter false
                }

                if (modifiers.isNotEmpty()) {
                    val fullTrackText = "${track.title} ${track.artist} ${track.album}"
                    for (mod in modifiers) {
                        val containsTerm = SearchUtils.fuzzyMatches(mod.term, fullTrackText)
                        if (mod.modifier == ChipModifier.INCLUDE && !containsTerm) {
                            return@filter false
                        }
                        if (mod.modifier == ChipModifier.EXCLUDE && containsTerm) {
                            return@filter false
                        }
                    }
                }
                true
            }.sortedWith(
                compareBy<TrackEntity> { track ->
                    minOf(
                        SearchUtils.matchScore(query, track.title),
                        SearchUtils.matchScore(query, track.artist)
                    )
                }.thenBy { it.title.lowercase() }
            )

            _uiState.update { it.copy(searchResults = filtered, isSearchingCatalog = false) }
        }
    }

    fun setArtistInputText(text: String) {
        _uiState.update { it.copy(artistInputText = text) }
        val trimmed = text.trim()
        if (trimmed.isEmpty()) {
            artistJob?.cancel()
            _uiState.update { it.copy(suggestedArtists = emptyList()) }
            return
        }
        artistJob?.cancel()
        artistJob = viewModelScope.launch(Dispatchers.IO) {
            delay(60)
            val localArtists = repository.getAllTracksSync()
                .flatMap { SearchUtils.splitArtists(it.artist) }
                .filter { SearchUtils.fuzzyMatches(trimmed, it) }
                .distinct()
            val catalogArtists = allKnownArtists
                .flatMap { SearchUtils.splitArtists(it) }
                .filter { SearchUtils.fuzzyMatches(trimmed, it) }
                .distinct()
            val combined = (localArtists + catalogArtists).distinct()
            val remoteArtists = if (combined.size < 5 && cloudService != null && trimmed.length >= 2) {
                try {
                    val tracks = cloudService.searchCatalog(trimmed)
                    tracks.flatMap { SearchUtils.splitArtists(it.artist) }
                        .filter { SearchUtils.fuzzyMatches(trimmed, it) }
                        .distinct()
                } catch (_: Exception) {
                    emptyList()
                }
            } else {
                emptyList()
            }
            val finalCombined = (combined + remoteArtists)
                .distinct()
                .sortedWith(
                    compareBy<String> { SearchUtils.matchScore(trimmed, it) }
                        .thenBy { it.lowercase() }
                )
                .take(10)
            withContext(Dispatchers.Main) {
                android.util.Log.d("DiscoverVM", "setArtistInputText: trimmed='$trimmed', found=${finalCombined.size}: $finalCombined")
                _uiState.update { it.copy(suggestedArtists = finalCombined) }
            }
        }
    }

    fun addArtistModifier(artist: String, modifier: ChipModifier) {
        val trimmed = artist.trim()
        if (trimmed.isBlank()) return
        _uiState.update { state ->
            val updated = state.artistModifiers.toMutableSet()
            updated.add(ModifierChip(trimmed, modifier))
            state.copy(artistModifiers = updated, artistInputText = "", suggestedArtists = emptyList())
        }
    }

    fun removeArtistModifier(chip: ModifierChip) {
        _uiState.update { state ->
            val updated = state.artistModifiers.toMutableSet()
            updated.remove(chip)
            state.copy(artistModifiers = updated)
        }
    }

    fun removeArtistModifier(artist: String) {
        _uiState.update { state ->
            val updated = state.artistModifiers.toMutableSet()
            updated.removeIf { it.term.equals(artist, ignoreCase = true) }
            state.copy(artistModifiers = updated)
        }
    }

    fun setGenreInputText(text: String) {
        val trimmed = text.trim()
        val suggestions = if (trimmed.isNotEmpty()) {
            GenreCatalog.searchGenres(trimmed).take(10).map { it.name }
        } else {
            emptyList()
        }
        _uiState.update { it.copy(genreInputText = text, suggestedGenres = suggestions) }
    }

    fun setSelectedGenreCategory(category: String) {
        _uiState.update { it.copy(selectedGenreCategory = category) }
    }

    fun addGenreModifier(genre: String, modifier: ChipModifier) {
        val trimmed = genre.trim()
        if (trimmed.isBlank()) return
        _uiState.update { state ->
            val updated = state.genreModifiers.toMutableSet()
            updated.add(ModifierChip(trimmed, modifier))
            state.copy(genreModifiers = updated, genreInputText = "", suggestedGenres = emptyList())
        }
    }

    fun removeGenreModifier(chip: ModifierChip) {
        _uiState.update { state ->
            val updated = state.genreModifiers.toMutableSet()
            updated.remove(chip)
            state.copy(genreModifiers = updated)
        }
    }

    fun removeGenreModifier(genre: String) {
        _uiState.update { state ->
            val updated = state.genreModifiers.toMutableSet()
            updated.removeIf { it.term.equals(genre, ignoreCase = true) }
            state.copy(genreModifiers = updated)
        }
    }

    // --- Song Seed Methods (Similar to Song) ---

    fun setTrackInputText(text: String) {
        _uiState.update { it.copy(trackInputText = text) }
        val trimmed = text.trim()
        if (trimmed.isEmpty()) {
            trackJob?.cancel()
            _uiState.update { it.copy(suggestedTracks = emptyList()) }
            return
        }
        trackJob?.cancel()
        trackJob = viewModelScope.launch(Dispatchers.IO) {
            delay(60)
            val localMatches = repository.getAllTracksSync().filter {
                SearchUtils.fuzzyMatches(trimmed, it.title) || SearchUtils.fuzzyMatches(trimmed, it.artist)
            }
            val combined = localMatches
                .distinctBy { "${SearchUtils.normalize(it.title)}-${SearchUtils.normalize(it.artist)}" }
                .sortedWith(
                    compareBy<TrackEntity> { track ->
                        minOf(
                            SearchUtils.matchScore(trimmed, track.title),
                            SearchUtils.matchScore(trimmed, track.artist)
                        )
                    }.thenBy { it.title.lowercase() }
                )
                .take(8)
            withContext(Dispatchers.Main) {
                _uiState.update { it.copy(suggestedTracks = combined) }
            }
        }
    }

    fun addTrackModifier(trackName: String, modifier: ChipModifier) {
        val trimmed = trackName.trim()
        if (trimmed.isBlank()) return
        _uiState.update { state ->
            val updated = state.trackModifiers.toMutableSet()
            updated.add(ModifierChip(trimmed, modifier))
            state.copy(trackModifiers = updated, trackInputText = "", suggestedTracks = emptyList())
        }
    }

    fun removeTrackModifier(chip: ModifierChip) {
        _uiState.update { state ->
            val updated = state.trackModifiers.toMutableSet()
            updated.remove(chip)
            state.copy(trackModifiers = updated)
        }
    }

    fun removeTrackModifier(trackName: String) {
        _uiState.update { state ->
            val updated = state.trackModifiers.toMutableSet()
            updated.removeIf { it.term.equals(trackName, ignoreCase = true) }
            state.copy(trackModifiers = updated)
        }
    }

    /**
     * Toggles like/unlike status for a track from Discover results with YouTube Music sync.
     */
    fun saveTrackToLiked(track: TrackEntity, onFeedback: ((String) -> Unit)? = null) {
        viewModelScope.launch {
            val isLiked = repository.isTrackInLiked(track.id)
            val token = getValidAccessToken()
            if (isLiked) {
                if (!token.isNullOrBlank() && cloudService != null) {
                    cloudService.removeTrackFromLiked(token, track.id)
                }
                repository.removeTrackFromLiked(track.id)
                onFeedback?.invoke("Eliminada de tus Canciones que te gustan")
            } else {
                if (!token.isNullOrBlank() && cloudService != null) {
                    cloudService.saveTrackToLiked(token, track.id)
                }
                repository.saveTrackToLiked(track)
                onFeedback?.invoke("¡Agregada a Canciones que te gustan!")
            }
        }
    }

    fun toggleGenreModifier(genre: String) {
        _uiState.update { state ->
            val updated = state.genreModifiers.toMutableSet()
            val hasInclude = updated.any { it.term.equals(genre, ignoreCase = true) && it.modifier == ChipModifier.INCLUDE }
            val hasExclude = updated.any { it.term.equals(genre, ignoreCase = true) && it.modifier == ChipModifier.EXCLUDE }
            if (!hasInclude && !hasExclude) {
                updated.add(ModifierChip(genre, ChipModifier.INCLUDE))
            } else if (hasInclude && !hasExclude) {
                updated.removeIf { it.term.equals(genre, ignoreCase = true) }
                updated.add(ModifierChip(genre, ChipModifier.EXCLUDE))
            } else {
                updated.removeIf { it.term.equals(genre, ignoreCase = true) }
            }
            state.copy(genreModifiers = updated)
        }
    }

    fun toggleDecade(decade: String) {
        _uiState.update { state ->
            val updated = state.selectedDecades.toMutableSet()
            if (updated.contains(decade)) {
                updated.remove(decade)
            } else {
                updated.add(decade)
            }
            state.copy(selectedDecades = updated)
        }
    }

    private var accessToken: String? = null

    fun setAccessToken(token: String) {
        accessToken = token
    }

    fun setExcludeLibrary(exclude: Boolean) {
        _uiState.update { it.copy(excludeLibrarySongs = exclude) }
    }

    fun setRecentlyHeardFilter(filter: RecentlyHeardFilter) {
        _uiState.update { it.copy(recentlyHeardFilter = filter) }
    }

    fun setTargetCount(count: Int) {
        _uiState.update { it.copy(targetCount = count) }
    }

    fun setLowPopularityOnly(enabled: Boolean) {
        _uiState.update { it.copy(lowPopularityOnly = enabled) }
    }

    fun setHiddenGemTarget(target: String) {
        _uiState.update { it.copy(hiddenGemTarget = target) }
    }

    fun dismissInfoBanner() {
        _uiState.update { it.copy(infoBannerMessage = null) }
    }

    fun generateDiscoveryMix() {
        viewModelScope.launch {
            _uiState.update { it.copy(isGeneratingMix = true, infoBannerMessage = null) }

            val state = _uiState.value
            val targetCount = state.targetCount
            val token = getValidAccessToken()

            val includedArtists = state.artistModifiers
                .filter { it.modifier == ChipModifier.INCLUDE }
                .map { it.term }
            val excludedArtists = state.artistModifiers
                .filter { it.modifier == ChipModifier.EXCLUDE }
                .map { it.term.lowercase().trim() }

            val includedGenres = state.genreModifiers
                .filter { it.modifier == ChipModifier.INCLUDE }
                .map { it.term }
            val excludedGenres = state.genreModifiers
                .filter { it.modifier == ChipModifier.EXCLUDE }
                .map { it.term.lowercase().trim() }

            val includedTracks = state.trackModifiers
                .filter { it.modifier == ChipModifier.INCLUDE }
                .map { it.term }
            val excludedTracks = state.trackModifiers
                .filter { it.modifier == ChipModifier.EXCLUDE }
                .map { it.term.lowercase().trim() }

            val hasPositiveSeeds = includedArtists.isNotEmpty() || includedGenres.isNotEmpty() ||
                    includedTracks.isNotEmpty() || state.selectedDecades.isNotEmpty()

            // Pre-load library tracks and recently heard exclusion sets
            val libraryTracks = withContext(Dispatchers.IO) { repository.getAllTracksSync() }
            val libraryTrackIds = if (state.excludeLibrarySongs) {
                libraryTracks.map { it.id.lowercase().trim() }.toSet()
            } else emptySet()
            val libraryKeys = if (state.excludeLibrarySongs) {
                libraryTracks.map { "${it.title.lowercase().trim()} - ${it.artist.lowercase().trim()}" }.toSet()
            } else emptySet()

            val recentTrackIds: Set<String> = if (state.recentlyHeardFilter != RecentlyHeardFilter.NONE) {
                val localRecentIds = withContext(Dispatchers.IO) {
                    repository.getRecentlyPlayedTrackIds(days = state.recentlyHeardFilter.days)
                }.toSet()
                val cloudRecentIds = if (!token.isNullOrBlank() && cloudService != null) {
                    withContext(Dispatchers.IO) {
                        try {
                            cloudService.fetchRecentlyPlayedTrackIds(token)
                        } catch (_: Exception) {
                            emptySet()
                        }
                    }
                } else emptySet()
                localRecentIds + cloudRecentIds
            } else emptySet()

            val blacklistedArtists = withContext(Dispatchers.IO) {
                repository.getBlacklistedArtistNamesSync()
            }

            fun isAllowedCandidate(track: TrackEntity): Boolean {
                // Validate YouTube Music track: must have non-blank ID
                if (track.id.isBlank()) return false

                // Strictly enforce Library Exclusion: zero library tracks admitted when enabled
                if (state.excludeLibrarySongs) {
                    val id = track.id.lowercase().trim()
                    val key = "${track.title.lowercase().trim()} - ${track.artist.lowercase().trim()}"
                    if (libraryTrackIds.contains(id) || libraryKeys.contains(key)) return false
                }

                // Strictly enforce Recently Heard Exclusion
                if (recentTrackIds.contains(track.id)) return false

                // Hard Prune: Permanent Artist Blacklist (Primary Artist rule: collaborations allowed)
                if (blacklistedArtists.isNotEmpty() && ArtistUtils.isTrackBlockedByBlacklist(track.artist, blacklistedArtists)) {
                    return false
                }

                // Hard Prune: EXCLUDED artists (- NO)
                if (excludedArtists.isNotEmpty()) {
                    if (excludedArtists.any { SearchUtils.fuzzyMatches(it, track.artist) || track.artist.lowercase().contains(it) }) return false
                }

                // Hard Prune: EXCLUDED genres (- NO)
                if (excludedGenres.isNotEmpty()) {
                    if (excludedGenres.any { SearchUtils.fuzzyMatches(it, track.album) || track.album.lowercase().contains(it) }) return false
                }

                // Hard Prune: EXCLUDED song seeds (- NO)
                if (excludedTracks.isNotEmpty()) {
                    if (excludedTracks.any { SearchUtils.fuzzyMatches(it, track.title) || track.title.lowercase().contains(it) }) return false
                }

                return true
            }

            val candidates = mutableListOf<TrackEntity>()
            val seenKeys = mutableSetOf<String>()

            fun addCandidate(track: TrackEntity): Boolean {
                if (!isAllowedCandidate(track)) return false
                val key = "${track.title.lowercase().trim()} - ${track.artist.lowercase().trim()}"
                if (seenKeys.add(key)) {
                    candidates.add(track)
                    return true
                }
                return false
            }

            // A. Local Library Seeding (ONLY when NOT excluding library songs)
            if (!state.excludeLibrarySongs && hasPositiveSeeds) {
                for (t in libraryTracks) {
                    var matchesSeed = false
                    for (art in includedArtists) {
                        if (SearchUtils.fuzzyMatches(art, t.artist) || SearchUtils.splitArtists(t.artist).any { SearchUtils.fuzzyMatches(art, it) }) {
                            matchesSeed = true
                            break
                        }
                    }
                    if (!matchesSeed) {
                        for (tr in includedTracks) {
                            if (SearchUtils.fuzzyMatches(tr, t.title)) {
                                matchesSeed = true
                                break
                            }
                        }
                    }
                    if (!matchesSeed) {
                        for (gen in includedGenres) {
                            if (SearchUtils.fuzzyMatches(gen, t.album)) {
                                matchesSeed = true
                                break
                            }
                        }
                    }
                    if (matchesSeed) {
                        addCandidate(t)
                    }
                }
            }

            // B. Cloud Seeding with Iterative / Recursive Orbit Expansion (Depth 0 -> 1 -> 2)
            val visitedArtists = mutableSetOf<String>()
            for (a in excludedArtists) visitedArtists.add(a.lowercase().trim())

            val targetBuffer = targetCount * 2

            withContext(Dispatchers.IO) {
                if (!token.isNullOrBlank() && cloudService != null) {
                    coroutineScope {
                        // 1. Initial Seeds Harvesting (Depth 0)
                        val initialJobs = mutableListOf<kotlinx.coroutines.Deferred<List<TrackEntity>>>()

                        // Artist Seeds (Depth 0: direct catalog tracks)
                        for (artist in includedArtists) {
                            val cleanArtist = YtmCloudService.sanitizeQuery(artist)
                            val norm = cleanArtist.lowercase().trim()
                            if (visitedArtists.contains(norm)) continue
                            visitedArtists.add(norm)

                            for (offset in listOf(0, 10, 20, 30, 40, 50)) {
                                initialJobs.add(async {
                                    try {
                                        cloudService.searchTracks(token, "artist:\"$cleanArtist\"", limit = 10, offset = offset)
                                    } catch (_: Exception) { emptyList() }
                                })
                            }
                        }

                        // Genre Seeds
                        for (genre in includedGenres) {
                            val cleanGenre = YtmCloudService.sanitizeQuery(genre)
                            for (offset in listOf(0, 10, 20)) {
                                initialJobs.add(async {
                                    try {
                                        cloudService.searchTracks(token, "genre:\"$cleanGenre\"", limit = 10, offset = offset)
                                    } catch (_: Exception) { emptyList() }
                                })
                            }
                        }

                        // Song Seeds
                        for (trackSeed in includedTracks) {
                            val cleanSeed = YtmCloudService.sanitizeQuery(trackSeed)
                            for (offset in listOf(0, 10)) {
                                initialJobs.add(async {
                                    try {
                                        cloudService.searchTracks(token, "track:\"$cleanSeed\"", limit = 10, offset = offset)
                                    } catch (_: Exception) { emptyList() }
                                })
                            }
                        }

                        // Decade Seeds
                        val decadeQueryMap = mapOf(
                            "60s" to "year:1960-1969",
                            "70s" to "year:1970-1979",
                            "80s" to "year:1980-1989",
                            "90s" to "year:1990-1999",
                            "00s" to "year:2000-2009",
                            "10s" to "year:2010-2019"
                        )
                        for (dec in state.selectedDecades) {
                            val q = decadeQueryMap[dec] ?: "year:1980-1999"
                            for (page in 0..1) {
                                initialJobs.add(async {
                                    try {
                                        cloudService.searchTracks(token, q, limit = 10, offset = page * 10)
                                    } catch (_: Exception) { emptyList() }
                                })
                            }
                        }

                        // If no positive filters specified, explore broad discovery
                        if (!hasPositiveSeeds) {
                            val discoveryQueries = listOf(
                                "rock", "indie", "alternative", "synth-pop",
                                "latin rock", "classic rock", "pop", "disco", "year:1990-2023"
                            ).shuffled().take(6)
                            for (q in discoveryQueries) {
                                val randomOffset = (0..3).random() * 10
                                for (page in 0..1) {
                                    initialJobs.add(async {
                                        try {
                                            cloudService.searchTracks(token, q, limit = 10, offset = randomOffset + (page * 10))
                                        } catch (_: Exception) { emptyList() }
                                    })
                                }
                            }
                        }

                        // Await initial batch, add valid candidates, and harvest co-artists
                        val initialTracks = initialJobs.awaitAll().flatten()
                        val discoveredCollaborators = mutableSetOf<String>()

                        initialTracks.forEach { track ->
                            val trackArtists = SearchUtils.splitArtists(track.artist)
                            val matchesSeedArtist = includedArtists.isEmpty() || includedArtists.any { seed ->
                                trackArtists.any { SearchUtils.fuzzyMatches(seed, it) }
                            }
                            if (matchesSeedArtist) {
                                addCandidate(track)
                                // Harvest co-artists and collaborators ONLY from tracks where seed artist actually appears
                                for (p in trackArtists) {
                                    val cleanP = YtmCloudService.sanitizeQuery(p).trim()
                                    val normP = cleanP.lowercase()
                                    val isSelf = includedArtists.any { SearchUtils.fuzzyMatches(it, cleanP) }
                                    val isExcluded = excludedArtists.any { it.equals(normP, ignoreCase = true) || SearchUtils.fuzzyMatches(it, normP) }
                                    if (!isSelf && !isExcluded && cleanP.length > 2 && !visitedArtists.contains(normP)) {
                                        discoveredCollaborators.add(cleanP)
                                    }
                                }
                            }
                        }

                        val seedArtists = if (includedArtists.isNotEmpty()) {
                            includedArtists
                        } else {
                            candidates.map { it.artist }.distinct().take(6)
                        }

                        // --- TIER 1 (Priority 1): YouTube Music Radio Automix ---
                        // For the seed tracks or top candidates, fetch YouTube Music's algorithmic radio queue
                        if (candidates.size < targetBuffer) {
                            val seedTrackIds = mutableListOf<String>()
                            for (c in candidates.take(4)) {
                                if (c.id.length == 11 && !seedTrackIds.contains(c.id)) {
                                    seedTrackIds.add(c.id)
                                }
                            }
                            if (seedTrackIds.isNotEmpty()) {
                                android.util.Log.d("DiscoverVM", "Fetching Radio Automix for seeds: $seedTrackIds")
                                val radioJobs = seedTrackIds.map { vid ->
                                    async {
                                        try {
                                            cloudService.getRadioTracks(vid)
                                        } catch (_: Exception) { emptyList() }
                                    }
                                }
                                val radioTracks = radioJobs.awaitAll().flatten()
                                radioTracks.forEach { addCandidate(it) }
                            }
                        }

                        // --- TIER 2 (Priority 2): Thematic & Artist Mix Search ---
                        if (candidates.size < targetBuffer && seedArtists.isNotEmpty()) {
                            val mixJobs = mutableListOf<kotlinx.coroutines.Deferred<List<TrackEntity>>>()
                            for (seed in seedArtists.take(4)) {
                                val cleanSeed = YtmCloudService.sanitizeQuery(seed)
                                listOf("$cleanSeed mix", "$cleanSeed radio").forEach { query ->
                                    mixJobs.add(async {
                                        try {
                                            cloudService.searchTracks(token, query, limit = 15)
                                        } catch (_: Exception) { emptyList() }
                                    })
                                }
                            }
                            val mixTracks = mixJobs.awaitAll().flatten()
                            mixTracks.forEach { addCandidate(it) }
                        }

                        // --- TIER 3 (Priority 3): Co-Artists & Collaborators ---
                        if (candidates.size < targetBuffer && discoveredCollaborators.isNotEmpty()) {
                            val collabList = discoveredCollaborators
                                .filter { visitedArtists.add(it.lowercase().trim()) }
                                .take(6)
                            val collabJobs = collabList.map { collab ->
                                async {
                                    try {
                                        cloudService.searchTracks(token, collab, limit = 10)
                                    } catch (_: Exception) { emptyList() }
                                }
                            }
                            val collabTracks = collabJobs.awaitAll().flatten()
                            collabTracks.forEach { addCandidate(it) }
                        }
                    }
                }
            }

            // 4. Dynamic YouTube Music additions to fill quota
            if (candidates.size < targetBuffer) {
                val seedQueries = if (libraryTracks.isNotEmpty()) {
                    libraryTracks.map { it.artist }.distinct().shuffled().take(4)
                } else {
                    listOf("Rock", "Pop", "Indie", "Alternative")
                }
                for (seed in seedQueries) {
                    try {
                        val tracks = cloudService?.searchCatalog("$seed mix") ?: emptyList()
                        tracks.forEach { addCandidate(it) }
                    } catch (_: Exception) {}
                }
            }

            android.util.Log.d("DiscoverVM", "Total filtered non-library candidates harvested: ${candidates.size}")

            // 5. Hidden Gems / Low Popularity Only filter
            var workingPool = candidates.toList()
            if (state.lowPopularityOnly) {
                val gemFiltered = workingPool.filter { track ->
                    when (state.hiddenGemTarget) {
                        "track" -> track.popularity <= 45
                        "artist" -> track.artistPopularity <= 48
                        "both" -> track.popularity <= 45 && track.artistPopularity <= 48
                        else -> track.popularity <= 45 || track.artistPopularity <= 48
                    }
                }.map { track ->
                    track.copy(
                        isHiddenGem = true,
                        hiddenGemType = state.hiddenGemTarget
                    )
                }
                workingPool = if (gemFiltered.size >= targetCount / 2) gemFiltered else workingPool.map {
                    it.copy(isHiddenGem = true, hiddenGemType = state.hiddenGemTarget)
                }
            }

            // 6. Diversity Enforcement & Per-Artist Capping
            val maxPerArtist = maxOf(2, minOf(4, targetCount / 10))
            val diverseSelection = mutableListOf<TrackEntity>()
            val artistCounts = mutableMapOf<String, Int>()
            val overflowTracks = mutableListOf<TrackEntity>()

            val isSeedArtist = { art: String ->
                includedArtists.isEmpty() || includedArtists.any { SearchUtils.fuzzyMatches(it, art) }
            }
            // Non-seed / related artists are strictly capped at at most 2 tracks tops
            val hardMaxPerNonSeedArtist = 2

            fun getPrimaryArtist(raw: String): String {
                val parts = SearchUtils.splitArtists(raw)
                return if (parts.size > 1) parts[1].lowercase().trim() else raw.lowercase().trim()
            }

            for (t in workingPool) {
                val normArtist = getPrimaryArtist(t.artist)
                val cnt = artistCounts.getOrDefault(normArtist, 0)
                val initialLimit = if (isSeedArtist(t.artist)) maxPerArtist else hardMaxPerNonSeedArtist
                if (cnt < initialLimit) {
                    diverseSelection.add(t)
                    artistCounts[normArtist] = cnt + 1
                } else {
                    overflowTracks.add(t)
                }
            }

            for (ot in overflowTracks) {
                if (diverseSelection.size >= targetCount) break
                val normArtist = getPrimaryArtist(ot.artist)
                val currentCnt = artistCounts.getOrDefault(normArtist, 0)
                val limit = if (isSeedArtist(ot.artist)) maxPerArtist * 2 else hardMaxPerNonSeedArtist
                if (currentCnt < limit) {
                    diverseSelection.add(ot)
                    artistCounts[normArtist] = currentCnt + 1
                }
            }

            // 7. Final Selection & Quota Shortage Check
            // No library songs are backfilled. If quota is not reached, show what was found and inform the user.
            val finalTracks = diverseSelection.take(targetCount)
            val finalNotice = if (finalTracks.size < targetCount) {
                Strings.MixQuotaNotice.format(finalTracks.size, targetCount)
            } else {
                null
            }

            android.util.Log.d("DiscoverVM", "Generated final mix of ${finalTracks.size} tracks (target: $targetCount, notice: $finalNotice)")

            // 8. True Shuffle with Anti-Clumping
            val shuffled = com.quio.ytm.domain.ShuffleUtils.shuffleTrackEntities(finalTracks, applyAntiClumping = true)

            _uiState.update {
                it.copy(
                    isGeneratingMix = false,
                    discoveredMix = shuffled,
                    infoBannerMessage = finalNotice
                )
            }
        }
    }

    /**
     * 1-Tap Block: Adds the track's primary artist to the persistent blacklist
     * and purges all tracks where that artist is the main artist from the current discovery results.
     */
    fun blockArtistFromDiscover(track: TrackEntity) {
        val mainArtist = ArtistUtils.extractPrimaryArtist(track.artist)
        if (mainArtist.isBlank()) return
        viewModelScope.launch {
            repository.addArtistToBlacklist(mainArtist)
            _uiState.update { current ->
                val updatedMix = current.discoveredMix.filter { t ->
                    val ma = ArtistUtils.extractPrimaryArtist(t.artist)
                    !ma.equals(mainArtist, ignoreCase = true)
                }
                val updatedSearch = current.searchResults.filter { t ->
                    val ma = ArtistUtils.extractPrimaryArtist(t.artist)
                    !ma.equals(mainArtist, ignoreCase = true)
                }
                current.copy(
                    discoveredMix = updatedMix,
                    searchResults = updatedSearch,
                    infoBannerMessage = "🚫 \"$mainArtist\" agregado a la lista negra"
                )
            }
        }
    }
}
