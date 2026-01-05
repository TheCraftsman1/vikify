package com.vikify.app.db.entities

import androidx.compose.runtime.Immutable
import androidx.room.ColumnInfo
import androidx.room.Entity
import androidx.room.Index
import androidx.room.PrimaryKey
import com.vikify.app.utils.LocalArtworkPath
import com.vikify.app.utils.syncCoroutine
import com.zionhuang.innertube.YouTube
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.cancel
import kotlinx.coroutines.launch
import org.apache.commons.lang3.RandomStringUtils
import java.time.LocalDateTime
import java.time.Month
import java.time.ZoneOffset

@Immutable
@Entity(
    tableName = "song",
    indices = [
        Index(
            value = ["albumId"]
        ),
        Index(
            value = ["spotifyId"],
            unique = true
        )
    ]
)
data class SongEntity(
    @PrimaryKey val id: String,
    val title: String,
    val duration: Int = -1, // in seconds TODO: in milliseconds
    val thumbnailUrl: String? = null,
    val inLibrary: LocalDateTime? = null, // doubles as "date added"
    @ColumnInfo(name = "isLocal", defaultValue = false.toString())
    val isLocal: Boolean = false,
    @ColumnInfo(index = true)
    val localPath: String?,
    val dateDownload: LocalDateTime? = null, // doubles as "isDownloaded" for new downloader system
    val liked: Boolean = false,
    val likedDate: LocalDateTime? = null,

    // misc non-critical tags
    val trackNumber: Int? = null,
    val discNumber: Int? = null,
    val albumId: String? = null,
    val albumName: String? = null,
//    val albumArtist // if anyone wants to implement album artists in a sane way, pull requests are welcome.
    val year: Int? = null,
    val date: LocalDateTime? = null, // ID3 tag property
    val dateModified: LocalDateTime? = null, // file property
    
    // === SPOTIFY MIGRATION FIELDS ===
    // These allow instant playlist loading with background YouTube resolution
    @ColumnInfo(name = "spotifyId", defaultValue = "NULL")
    val spotifyId: String? = null,  // Original Spotify track ID for mapping
    
    @ColumnInfo(name = "spotifyTitle", defaultValue = "NULL")
    val spotifyTitle: String? = null,  // Clean Spotify title (often better than YouTube)
    
    @ColumnInfo(name = "spotifyArtist", defaultValue = "NULL")
    val spotifyArtist: String? = null,  // Clean Spotify artist name
    
    @ColumnInfo(name = "spotifyArtworkUrl", defaultValue = "NULL")
    val spotifyArtworkUrl: String? = null,  // High-res Spotify artwork URL

    // === JIT RESOLUTION FIELDS ===
    // These allow lazy resolution of audio to prevent API throttling
    @ColumnInfo(name = "streamUrl")
    val streamUrl: String? = null, // Cached playable URL (googlevideo.com...)
    
    @ColumnInfo(name = "lastResolved")
    val lastResolved: Long? = null, // Epoch millis when streamUrl was fetched
) {

    fun localToggleLike() = copy(
        liked = !liked,
        likedDate = if (!liked) LocalDateTime.now() else null,
    )

    fun toggleLike() = copy(
        liked = !liked,
        likedDate = if (!liked) LocalDateTime.now() else null,
        inLibrary = if (!liked) inLibrary ?: LocalDateTime.now() else inLibrary
    ).also {
        CoroutineScope(syncCoroutine).launch {
            YouTube.likeVideo(id, !liked)
            this.cancel()
        }
    }

    fun toggleLibrary() = copy(
        inLibrary = if (inLibrary == null) LocalDateTime.now() else null,
        liked = if (inLibrary == null) liked else false,
        likedDate = if (inLibrary == null) likedDate else null
    )

    /**
     * Returns a full date string. If no full date is present, returns the year.
     * This is the song's tag's date/year, NOT dateModified.
     */
    fun getDateString(): String? {
        return date?.toLocalDate()?.toString()
            ?: if (year != null) {
                return year.toString()
            } else {
                return null
            }
    }

    /**
     * Get the value of the date released in Epoch Seconds
     */
    fun getDateLong(): Long? {
        return date?.toEpochSecond(ZoneOffset.UTC)
            ?: if (year != null) {
                LocalDateTime.of(year, Month.JANUARY, 1, 0, 0).toEpochSecond(ZoneOffset.UTC)
            } else {
                null
            }
    }

    /**
     * Get the value of the date modified in Epoch Seconds
     */
    fun getDateModifiedLong(): Long? = dateModified?.toEpochSecond(ZoneOffset.UTC)

    fun getThumbnailModel(sizeX: Int = -1, sizeY: Int = -1): Any? {
        return if (isLocal) {
            LocalArtworkPath(thumbnailUrl ?: localPath, sizeX, sizeY)
        } else {
            thumbnailUrl
        }
    }

    companion object {
        fun generateSongId() = "LS" + RandomStringUtils.insecure().next(8, true, false)
    }
}
