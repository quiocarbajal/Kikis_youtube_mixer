package com.quio.ytm.data.local.entity

import androidx.room.ColumnInfo
import androidx.room.Entity
import androidx.room.PrimaryKey
import com.quio.ytm.core.models.Track

@Entity(tableName = "tracks")
data class TrackEntity(
    @PrimaryKey
    val id: String,

    @ColumnInfo(name = "uri")
    val uri: String = "",

    @ColumnInfo(name = "title")
    val title: String,

    @ColumnInfo(name = "artist")
    val artist: String,

    @ColumnInfo(name = "album")
    val album: String = "",

    @ColumnInfo(name = "album_art_url")
    val albumArtUrl: String? = null,

    @ColumnInfo(name = "duration_ms")
    val durationMs: Long = 0L,

    @ColumnInfo(name = "added_at")
    val addedAt: Long = System.currentTimeMillis(),

    @ColumnInfo(name = "popularity", defaultValue = "50")
    val popularity: Int = 50,

    @ColumnInfo(name = "artist_popularity", defaultValue = "50")
    val artistPopularity: Int = 50,

    @ColumnInfo(name = "is_hidden_gem", defaultValue = "0")
    val isHiddenGem: Boolean = false,

    @ColumnInfo(name = "hidden_gem_type")
    val hiddenGemType: String? = null,

    val isLiked: Boolean = false
) {
    val name: String get() = title
    val imageUrl: String? get() = albumArtUrl

    fun toDomainTrack(): Track = toDomain()

    companion object {
        fun fromDomainTrack(track: Track): TrackEntity = track.toEntity()
    }
}

fun TrackEntity.toDomain(): Track = Track(
    id = id,
    title = title,
    artist = artist,
    album = album,
    durationMs = durationMs,
    thumbnailUrl = albumArtUrl ?: "",
    loudnessDb = -14.0,
    likedAt = if (isLiked) addedAt else null,
    isDownloaded = false
)

fun Track.toEntity(): TrackEntity = TrackEntity(
    id = id,
    uri = "https://music.youtube.com/watch?v=${id}",
    title = title,
    artist = artist,
    album = album,
    albumArtUrl = thumbnailUrl,
    durationMs = durationMs,
    addedAt = likedAt ?: System.currentTimeMillis(),
    isLiked = likedAt != null
)
