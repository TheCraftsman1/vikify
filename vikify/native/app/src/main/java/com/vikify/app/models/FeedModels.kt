package com.vikify.app.models

import androidx.compose.ui.graphics.Color
import com.vikify.app.models.MediaMetadata
import com.zionhuang.innertube.models.ArtistItem

sealed class FeedSection {
    abstract val id: String

    data class QuickResumeGrid(
        override val id: String = "quick_resume",
        val items: List<QuickResumeItem>
    ) : FeedSection()

    data class NowPlayingHero(
        override val id: String = "now_playing",
        val song: MediaMetadata,
        val isPlaying: Boolean
    ) : FeedSection()

    data class HorizontalRail(
        override val id: String,
        val title: String,
        val subtitle: String? = null,
        val items: List<RailItem>
    ) : FeedSection()

    data class LargeSquareRail(
        override val id: String,
        val title: String,
        val subtitle: String? = null,
        val items: List<RailItem>
    ) : FeedSection()

    data class CircleArtistRail(
        override val id: String,
        val title: String,
        val artists: List<ArtistItem>
    ) : FeedSection()

    data class HeroCard(
        override val id: String,
        val title: String,
        val subtitle: String,
        val imageUrl: String?,
        val label: String = "NEW RELEASE",
        val actionId: String
    ) : FeedSection()

    data class VerticalTrackList(
        override val id: String,
        val title: String,
        val tracks: List<RailItem>
    ) : FeedSection()
    
    data class MoodChipRow(
        override val id: String,
        val title: String,
        val moods: List<com.zionhuang.innertube.pages.MoodAndGenres.Item>
    ) : FeedSection()
}

data class QuickResumeItem(
    val id: String,
    val title: String,
    val imageUrl: String?,
    val type: QuickResumeType
)

enum class QuickResumeType {
    PLAYLIST, LIKED_SONGS, DOWNLOADED, RECENT_SONG
}

enum class RailItemType {
    SONG, ALBUM, PLAYLIST
}

data class RailItem(
    val id: String,
    val title: String,
    val subtitle: String,
    val imageUrl: String?,
    val stripeColor: Int? = null,
    val isPlaying: Boolean = false,
    val itemType: RailItemType = RailItemType.SONG
)

