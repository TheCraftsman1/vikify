package com.vikify.app.data

import com.zionhuang.innertube.YouTube
import com.zionhuang.innertube.models.AlbumItem
import com.zionhuang.innertube.models.ArtistItem
import com.zionhuang.innertube.models.SongItem
import com.zionhuang.innertube.pages.ArtistPage

/**
 * YouTube-Only Artist Details
 * 
 * Single source of truth from YouTube Music / InnerTube.
 * No external dependencies (Last.fm, Spotify).
 */
data class ArtistDetails(
    val id: String,
    val name: String,
    val description: String?,
    val thumbnailUrl: String?,
    val subscriberCount: String?,
    
    // Content sections
    val topSongs: List<SongItem>,
    val albums: List<AlbumItem>,
    val singles: List<AlbumItem>,
    val relatedArtists: List<ArtistItem>
)

/**
 * YouTube Artist Repository
 * 
 * Fetches artist data exclusively from YouTube Music (InnerTube).
 * 
 * Data available:
 * - Artist name, image, description
 * - Top Songs
 * - Albums & Singles
 * - Related Artists ("Fans also like")
 */
object YouTubeArtistRepository {
    
    /**
     * Get artist details by browse ID (YouTube Music artist page ID)
     * 
     * @param browseId YouTube Music artist ID (e.g., "UCxxxxxxx")
     * @return Result containing ArtistDetails or error
     */
    suspend fun getArtist(browseId: String): Result<ArtistDetails> = runCatching {
        val artistPage = YouTube.artist(browseId).getOrThrow()
        mapArtistPageToDetails(browseId, artistPage)
    }
    
    /**
     * Search for an artist by name and get their details
     * 
     * Useful when clicking on artist name in player (only have name, not ID)
     * 
     * @param artistName The name of the artist to search for
     * @return Result containing ArtistDetails or error
     */
    suspend fun searchArtistByName(artistName: String): Result<ArtistDetails> = runCatching {
        // Search YouTube Music for the artist
        val searchResult = YouTube.search(artistName, YouTube.SearchFilter.FILTER_ARTIST)
        val artistItem = searchResult.getOrThrow().items
            .filterIsInstance<ArtistItem>()
            .firstOrNull() ?: throw Exception("Artist not found: $artistName")
        
        // Fetch full artist page
        val artistPage = YouTube.artist(artistItem.id).getOrThrow()
        mapArtistPageToDetails(artistItem.id, artistPage)
    }
    
    /**
     * Map InnerTube ArtistPage to our simplified ArtistDetails model
     */
    private fun mapArtistPageToDetails(browseId: String, artistPage: ArtistPage): ArtistDetails {
        val topSongs = mutableListOf<SongItem>()
        val albums = mutableListOf<AlbumItem>()
        val singles = mutableListOf<AlbumItem>()
        val relatedArtists = mutableListOf<ArtistItem>()
        
        // Parse sections from artist page
        for (section in artistPage.sections) {
            val title = section.title.lowercase()
            when {
                title.contains("song") || title.contains("track") || title.contains("popular") -> {
                    topSongs.addAll(section.items.filterIsInstance<SongItem>())
                }
                title.contains("album") && !title.contains("single") -> {
                    albums.addAll(section.items.filterIsInstance<AlbumItem>())
                }
                title.contains("single") || title.contains("ep") -> {
                    singles.addAll(section.items.filterIsInstance<AlbumItem>())
                }
                title.contains("fan") || title.contains("like") || title.contains("similar") || title.contains("related") -> {
                    relatedArtists.addAll(section.items.filterIsInstance<ArtistItem>())
                }
            }
        }
        
        return ArtistDetails(
            id = browseId,
            name = artistPage.artist.title,
            description = artistPage.description,
            thumbnailUrl = artistPage.artist.thumbnail,
            subscriberCount = null, // Not directly exposed in the current API
            topSongs = topSongs.take(10), // Limit to top 10
            albums = albums,
            singles = singles,
            relatedArtists = relatedArtists
        )
    }
}
