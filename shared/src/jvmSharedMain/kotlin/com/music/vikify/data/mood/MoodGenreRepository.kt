package com.music.vikify.data.mood

import com.music.vikify.data.YtMusicRepository
import com.music.vikify.data.model.HomeShelf
import com.music.vikify.data.model.SearchFilter
import com.music.vikify.data.model.SearchResult
import com.music.vikify.data.model.ShelfItem
import com.music.vikify.data.model.Song
import com.music.vikify.data.model.durationMillis
import com.music.vikify.data.personalization.LocalMixKind
import com.music.vikify.data.personalization.LocalMixPlatform
import java.util.concurrent.ConcurrentHashMap

data class MoodBox(
    val id: String,
    val title: String,
    val subtitle: String,
    val assetFileName: String,
    val query: String,
    val section: MoodSection,
) {
    val assetUrl: String
        get() = "file:///android_asset/moods/$assetFileName"

    fun toShelfItem(): ShelfItem = ShelfItem(
        title = title,
        subtitle = subtitle,
        thumbnailUrl = assetUrl,
        videoId = null,
        browseId = "mood:$id",
    )
}

enum class MoodSection(val title: String) {
    MADE_FOR_YOU("Made For You"),
    FOCUS_AND_CHILL("Focus & Chill"),
    ENERGY_AND_DRIVE("Energy & Drive"),
    LATE_NIGHT_AND_SOUL("Late Night & Soul"),
    DISCOVERY_AND_EMOTIONS("Discovery & Emotions"),
}

object MoodGenreRepository {
    private val songCache = ConcurrentHashMap<String, List<Song>>()
    private val PERSONALIZED_MOODS = setOf("your_daily_mix", "on_repeat", "back_in_rotation")

    private val BAD_TITLE_KEYWORDS = listOf(
        "1 hour", "2 hour", "3 hour", "4 hour", "8 hour", "10 hour", "hour", "hours",
        "sound bath", "singing bowl", "singing bowls", "full album", "loop", "432hz", "528hz",
        "continuous mix", "mega mix", "mashup", "compilation", "nightcore",
        "live stream", "full concert", "bass boosted", "8d audio", "slowed + reverb"
    )

    fun Song.isValidStudioTrack(): Boolean {
        if (isVideo) return false
        val lower = title.lowercase()
        if (BAD_TITLE_KEYWORDS.any { lower.contains(it) }) return false
        val millis = durationMillis()
        if (millis > 0) {
            val sec = millis / 1000
            if (sec < 70 || sec > 540) return false
        }
        return true
    }

    fun Song.toTrackShelfItem(): ShelfItem = ShelfItem(
        title = title,
        subtitle = artist,
        thumbnailUrl = thumbnailUrl,
        videoId = videoId,
        browseId = null,
    )

    val allMoods: List<MoodBox> = listOf(
        // Section: Made For You (Top 6 Quick Grid)
        MoodBox(
            id = "your_daily_mix",
            title = "Your Daily Mix",
            subtitle = "Made for you",
            assetFileName = "your_daily_mix.webp",
            query = "Today's Top Hits 2024",
            section = MoodSection.MADE_FOR_YOU,
        ),
        MoodBox(
            id = "on_repeat",
            title = "On Repeat",
            subtitle = "Top tracks on loop",
            assetFileName = "on_repeat.webp",
            query = "Top Pop Hits",
            section = MoodSection.MADE_FOR_YOU,
        ),
        MoodBox(
            id = "back_in_rotation",
            title = "Back in Rotation",
            subtitle = "Rediscover favorites",
            assetFileName = "back_in_rotation.webp",
            query = "Coldplay OneRepublic Maroon 5 greatest hits",
            section = MoodSection.MADE_FOR_YOU,
        ),
        MoodBox(
            id = "fresh_finds",
            title = "Fresh Finds",
            subtitle = "Unheard music connected to your taste",
            assetFileName = "fresh_finds.webp",
            query = "Sabrina Carpenter Chappell Roan Benson Boone hits",
            section = MoodSection.MADE_FOR_YOU,
        ),
        MoodBox(
            id = "comfort_zone",
            title = "Comfort Zone",
            subtitle = "Familiar favorites, your way",
            assetFileName = "comfort_zone.webp",
            query = "Noah Kahan Vance Joy Lumineers acoustic",
            section = MoodSection.MADE_FOR_YOU,
        ),
        MoodBox(
            id = "time_capsule",
            title = "Time Capsule",
            subtitle = "Nostalgic throwbacks",
            assetFileName = "time_capsule.webp",
            query = "Linkin Park Green Day Killers rock hits",
            section = MoodSection.MADE_FOR_YOU,
        ),

        // Section: Focus & Chill
        MoodBox(
            id = "quiet_focus",
            title = "Quiet Focus",
            subtitle = "Deep work & study",
            assetFileName = "quiet_focus.webp",
            query = "Lofi Fruits Music study chill beats",
            section = MoodSection.FOCUS_AND_CHILL,
        ),
        MoodBox(
            id = "headspace",
            title = "Headspace",
            subtitle = "Clear your mind & unwind",
            assetFileName = "headspace.webp",
            query = "Ludovico Einaudi peaceful piano instrumental",
            section = MoodSection.FOCUS_AND_CHILL,
        ),
        MoodBox(
            id = "dreamspaces",
            title = "Dreamspaces",
            subtitle = "Ethereal ambient soundscapes",
            assetFileName = "dreamspaces.webp",
            query = "Tycho Bonobo ambient electronic",
            section = MoodSection.FOCUS_AND_CHILL,
        ),
        MoodBox(
            id = "drift_to_sleep",
            title = "Drift to Sleep",
            subtitle = "Soft sounds for restful sleep",
            assetFileName = "drift_to_sleep.webp",
            query = "Max Richter Brian Eno sleep ambient piano",
            section = MoodSection.FOCUS_AND_CHILL,
        ),
        MoodBox(
            id = "rainy_window",
            title = "Rainy Window",
            subtitle = "Cozy vibes & gentle beats",
            assetFileName = "rainy_window.webp",
            query = "Kupla Jinsang potsu chillhop",
            section = MoodSection.FOCUS_AND_CHILL,
        ),
        MoodBox(
            id = "healing_space",
            title = "Healing Space",
            subtitle = "Meditation & restorative calm",
            assetFileName = "healing_space.webp",
            query = "Ludovico Einaudi Yiruma calm piano",
            section = MoodSection.FOCUS_AND_CHILL,
        ),

        // Section: Energy & Drive
        MoodBox(
            id = "neon_drive",
            title = "Neon Drive",
            subtitle = "Synthwave & retro electronic",
            assetFileName = "neon_drive.webp",
            query = "The Midnight Kavinsky Gunship synthwave",
            section = MoodSection.ENERGY_AND_DRIVE,
        ),
        MoodBox(
            id = "full_throttle",
            title = "Full Throttle",
            subtitle = "High energy & workout pump",
            assetFileName = "full_throttle.webp",
            query = "Travis Scott Kendrick Lamar gym workout rap",
            section = MoodSection.ENERGY_AND_DRIVE,
        ),
        MoodBox(
            id = "open_road",
            title = "Open Road",
            subtitle = "Anthems for the journey",
            assetFileName = "open_road.webp",
            query = "Fleetwood Mac Bruce Springsteen road trip rock",
            section = MoodSection.ENERGY_AND_DRIVE,
        ),
        MoodBox(
            id = "main_character",
            title = "Main Character",
            subtitle = "Feel like you're in a movie",
            assetFileName = "main_character.webp",
            query = "Billie Eilish Lana Del Rey Lorde cinematic pop",
            section = MoodSection.ENERGY_AND_DRIVE,
        ),
        MoodBox(
            id = "cloud_nine",
            title = "Cloud Nine",
            subtitle = "Euphoric & feel-good vibes",
            assetFileName = "cloud_nine.webp",
            query = "Dua Lipa Harry Styles Bruno Mars upbeat pop",
            section = MoodSection.ENERGY_AND_DRIVE,
        ),
        MoodBox(
            id = "weekend_glow",
            title = "Weekend Glow",
            subtitle = "Party starters & upbeat tunes",
            assetFileName = "weekend_glow.webp",
            query = "Calvin Harris Fred again Peggy Gou dance hits",
            section = MoodSection.ENERGY_AND_DRIVE,
        ),

        // Section: Late Night & Soul
        MoodBox(
            id = "midnight_flow",
            title = "Midnight Flow",
            subtitle = "Smooth late-night R&B",
            assetFileName = "midnight_flow.webp",
            query = "SZA Frank Ocean The Weeknd R&B hits",
            section = MoodSection.LATE_NIGHT_AND_SOUL,
        ),
        MoodBox(
            id = "golden_hours",
            title = "Golden Hours",
            subtitle = "Warm acoustic sunset glow",
            assetFileName = "golden_hours.webp",
            query = "Leon Bridges Norah Jones acoustic soul",
            section = MoodSection.LATE_NIGHT_AND_SOUL,
        ),
        MoodBox(
            id = "slow_burn",
            title = "Slow Burn",
            subtitle = "Moody indie & mellow soul",
            assetFileName = "slow_burn.webp",
            query = "Cigarettes After Sex Joji Arctic Monkeys indie",
            section = MoodSection.LATE_NIGHT_AND_SOUL,
        ),
        MoodBox(
            id = "slow_sunday",
            title = "Slow Sunday",
            subtitle = "Lazy mornings & warm coffee",
            assetFileName = "slow_sunday.webp",
            query = "Jack Johnson John Mayer acoustic",
            section = MoodSection.LATE_NIGHT_AND_SOUL,
        ),
        MoodBox(
            id = "velvet_room",
            title = "Velvet Room",
            subtitle = "Intimate jazz & lounge classics",
            assetFileName = "velvet_room.webp",
            query = "Miles Davis Chet Baker Bill Evans jazz classics",
            section = MoodSection.LATE_NIGHT_AND_SOUL,
        ),
        MoodBox(
            id = "unplugged",
            title = "Unplugged",
            subtitle = "Raw acoustics & live sessions",
            assetFileName = "unplugged.webp",
            query = "MTV Unplugged acoustic rock classics",
            section = MoodSection.LATE_NIGHT_AND_SOUL,
        ),

        // Section: Discovery & Emotions
        MoodBox(
            id = "artist_orbit",
            title = "Artist Orbit",
            subtitle = "Favorite artists and deeper cuts",
            assetFileName = "artist_orbit.webp",
            query = "Dominic Fike Remi Wolf Still Woozy indie",
            section = MoodSection.DISCOVERY_AND_EMOTIONS,
        ),
        MoodBox(
            id = "beyond_the_usual",
            title = "Beyond the Usual",
            subtitle = "Explore beyond your main artists",
            assetFileName = "beyond_the_usual.webp",
            query = "Tame Impala Glass Animals Khruangbin psychedelic",
            section = MoodSection.DISCOVERY_AND_EMOTIONS,
        ),
        MoodBox(
            id = "day_break",
            title = "Daybreak",
            subtitle = "Fresh energy for the morning",
            assetFileName = "day_break.webp",
            query = "George Ezra Surfaces Jason Mraz uplifting pop",
            section = MoodSection.DISCOVERY_AND_EMOTIONS,
        ),
        MoodBox(
            id = "heavy_heart",
            title = "Heavy Heart",
            subtitle = "Sad songs & emotional ballads",
            assetFileName = "heavy_heart.webp",
            query = "Lewis Capaldi Adele Olivia Rodrigo emotional ballads",
            section = MoodSection.DISCOVERY_AND_EMOTIONS,
        ),
        MoodBox(
            id = "let_it_out",
            title = "Let It Out",
            subtitle = "Cathartic releases & anthems",
            assetFileName = "let_it_out.webp",
            query = "Paramore Fall Out Boy My Chemical Romance anthems",
            section = MoodSection.DISCOVERY_AND_EMOTIONS,
        ),
    )

    fun findMood(id: String): MoodBox? = allMoods.firstOrNull { it.id.equals(id, ignoreCase = true) }

    fun madeForYouShelf(): HomeShelf {
        val items = allMoods.filter { it.section == MoodSection.MADE_FOR_YOU }.map { it.toShelfItem() }
        return HomeShelf(title = MoodSection.MADE_FOR_YOU.title, items = items)
    }

    fun categoryShelves(): List<HomeShelf> {
        val grouped = allMoods.filterNot { it.section == MoodSection.MADE_FOR_YOU }.groupBy { it.section }
        return MoodSection.entries.filterNot { it == MoodSection.MADE_FOR_YOU }.mapNotNull { section ->
            val items = grouped[section]?.map { it.toShelfItem() }.orEmpty()
            if (items.isEmpty()) null else HomeShelf(title = section.title, items = items)
        }
    }

    fun initialQuickPicksShelf(): HomeShelf {
        val starterSongs = listOf(
            Song(videoId = "V9PVRfjEBTI", title = "BIRDS OF A FEATHER", artist = "Billie Eilish", thumbnailUrl = "https://i.ytimg.com/vi/V9PVRfjEBTI/sddefault.jpg"),
            Song(videoId = "ev4b8E54XyU", title = "Espresso", artist = "Sabrina Carpenter", thumbnailUrl = "https://i.ytimg.com/vi/ev4b8E54XyU/sddefault.jpg"),
            Song(videoId = "kPa7bsKwL-8", title = "Die With A Smile", artist = "Lady Gaga & Bruno Mars", thumbnailUrl = "https://i.ytimg.com/vi/kPa7bsKwL-8/sddefault.jpg"),
            Song(videoId = "4NRXx6U8ABQ", title = "Blinding Lights", artist = "The Weeknd", thumbnailUrl = "https://i.ytimg.com/vi/4NRXx6U8ABQ/sddefault.jpg"),
            Song(videoId = "1KI_wP_6-yY", title = "Good Luck, Babe!", artist = "Chappell Roan", thumbnailUrl = "https://i.ytimg.com/vi/1KI_wP_6-yY/sddefault.jpg"),
            Song(videoId = "t7bQwEgyptU", title = "A Bar Song (Tipsy)", artist = "Shaboozey", thumbnailUrl = "https://i.ytimg.com/vi/t7bQwEgyptU/sddefault.jpg"),
            Song(videoId = "H5v3kku4y6Q", title = "As It Was", artist = "Harry Styles", thumbnailUrl = "https://i.ytimg.com/vi/H5v3kku4y6Q/sddefault.jpg"),
            Song(videoId = "ic8j13piAhQ", title = "Cruel Summer", artist = "Taylor Swift", thumbnailUrl = "https://i.ytimg.com/vi/ic8j13piAhQ/sddefault.jpg"),
            Song(videoId = "34Na4j8AVgA", title = "Starboy", artist = "The Weeknd ft. Daft Punk", thumbnailUrl = "https://i.ytimg.com/vi/34Na4j8AVgA/sddefault.jpg"),
            Song(videoId = "G7KNmW9a75Y", title = "Flowers", artist = "Miley Cyrus", thumbnailUrl = "https://i.ytimg.com/vi/G7KNmW9a75Y/sddefault.jpg"),
            Song(videoId = "TUVcZfQe-Kw", title = "Levitating", artist = "Dua Lipa", thumbnailUrl = "https://i.ytimg.com/vi/TUVcZfQe-Kw/sddefault.jpg"),
            Song(videoId = "XXYlFuWEuKI", title = "Save Your Tears", artist = "The Weeknd", thumbnailUrl = "https://i.ytimg.com/vi/XXYlFuWEuKI/sddefault.jpg"),
        )
        return HomeShelf(
            title = "Quick picks",
            subtitle = "Songs you might like",
            items = starterSongs.map { it.toTrackShelfItem() },
        )
    }

    fun initialTrendingSongsShelf(): HomeShelf {
        val starterTrending = listOf(
            Song(videoId = "DI-v_19u6xQ", title = "Taste", artist = "Sabrina Carpenter", thumbnailUrl = "https://i.ytimg.com/vi/DI-v_19u6xQ/sddefault.jpg"),
            Song(videoId = "Oa_RSwwpPaA", title = "Beautiful Things", artist = "Benson Boone", thumbnailUrl = "https://i.ytimg.com/vi/Oa_RSwwpPaA/sddefault.jpg"),
            Song(videoId = "9PvgqBCbmbA", title = "Lose Control", artist = "Teddy Swims", thumbnailUrl = "https://i.ytimg.com/vi/9PvgqBCbmbA/sddefault.jpg"),
            Song(videoId = "cfnY_rL9eCY", title = "Please Please Please", artist = "Sabrina Carpenter", thumbnailUrl = "https://i.ytimg.com/vi/cfnY_rL9eCY/sddefault.jpg"),
            Song(videoId = "toBb_b38o-w", title = "greedy", artist = "Tate McRae", thumbnailUrl = "https://i.ytimg.com/vi/toBb_b38o-w/sddefault.jpg"),
            Song(videoId = "suAR1PYFNYA", title = "Houdini", artist = "Dua Lipa", thumbnailUrl = "https://i.ytimg.com/vi/suAR1PYFNYA/sddefault.jpg"),
            Song(videoId = "f1r0RMQuTqE", title = "One Of The Girls", artist = "The Weeknd, JENNIE, Lily-Rose Depp", thumbnailUrl = "https://i.ytimg.com/vi/f1r0RMQuTqE/sddefault.jpg"),
            Song(videoId = "XoiOOiuH8iI", title = "Water", artist = "Tyla", thumbnailUrl = "https://i.ytimg.com/vi/XoiOOiuH8iI/sddefault.jpg"),
            Song(videoId = "m4_9TFeMfJE", title = "Paint The Town Red", artist = "Doja Cat", thumbnailUrl = "https://i.ytimg.com/vi/m4_9TFeMfJE/sddefault.jpg"),
            Song(videoId = "hYkX8N2V3E8", title = "vampire", artist = "Olivia Rodrigo", thumbnailUrl = "https://i.ytimg.com/vi/hYkX8N2V3E8/sddefault.jpg"),
            Song(videoId = "aezstCBbvh8", title = "Too Sweet", artist = "Hozier", thumbnailUrl = "https://i.ytimg.com/vi/aezstCBbvh8/sddefault.jpg"),
            Song(videoId = "H58vbez_m4E", title = "Not Like Us", artist = "Kendrick Lamar", thumbnailUrl = "https://i.ytimg.com/vi/H58vbez_m4E/sddefault.jpg"),
        )
        return HomeShelf(
            title = "Trending songs",
            subtitle = "Today's top tunes",
            items = starterTrending.map { it.toTrackShelfItem() },
        )
    }

    private fun Song.withHighResArtwork(): Song {
        val art = thumbnailUrl ?: return this
        val enhanced = when {
            art.contains("googleusercontent.com") || art.contains("ggpht.com") -> {
                art.replace(Regex("""w\d+-h\d+"""), "w544-h544")
                    .replace(Regex("""=s\d+"""), "=s544")
            }
            art.contains("i.ytimg.com") -> {
                art.replace("hqdefault.jpg", "sddefault.jpg")
                    .replace("mqdefault.jpg", "sddefault.jpg")
                    .replace("default.jpg", "sddefault.jpg")
            }
            else -> art
        }
        return copy(thumbnailUrl = enhanced)
    }

    suspend fun loadQuickPicksShelf(): Result<HomeShelf> {
        val recents = YtMusicRepository.recents().getOrNull().orEmpty().filter { it.isValidStudioTrack() }
        val collected = mutableListOf<Song>()
        val seen = mutableSetOf<String>()

        if (recents.isNotEmpty()) {
            recents.take(6).forEach {
                if (seen.add(it.videoId)) collected.add(it.withHighResArtwork())
            }
            val seed = recents.first()
            val radioSongs = YtMusicRepository.radio(seed.videoId).getOrNull().orEmpty()
                .filter { it.isValidStudioTrack() }
            radioSongs.forEach {
                if (seen.add(it.videoId)) collected.add(it.withHighResArtwork())
            }
        }

        if (collected.size < 12) {
            val hitsResult = YtMusicRepository.searchPage("Top Hits 2024", SearchFilter.SONGS)
            hitsResult.getOrNull()?.rows?.mapNotNull { r ->
                when (r) {
                    is SearchResult.Track -> r.song
                    is SearchResult.TopTrack -> r.song
                    else -> null
                }
            }?.filter { it.isValidStudioTrack() }?.forEach {
                if (seen.add(it.videoId)) collected.add(it.withHighResArtwork())
            }
        }

        if (collected.isEmpty()) {
            return Result.success(initialQuickPicksShelf())
        }

        val items = collected.map { it.toTrackShelfItem() }
        return Result.success(HomeShelf(title = "Quick picks", items = items, subtitle = "Songs you might like"))
    }

    suspend fun loadTrendingSongsShelf(): Result<HomeShelf> {
        val trendingResult = YtMusicRepository.searchPage("Trending songs", SearchFilter.SONGS)
        val collected = mutableListOf<Song>()
        val seen = mutableSetOf<String>()

        trendingResult.getOrNull()?.rows?.mapNotNull { r ->
            when (r) {
                is SearchResult.Track -> r.song
                is SearchResult.TopTrack -> r.song
                else -> null
            }
        }?.filter { it.isValidStudioTrack() }?.forEach {
            if (seen.add(it.videoId)) collected.add(it.withHighResArtwork())
        }

        if (collected.isEmpty()) {
            return Result.success(initialTrendingSongsShelf())
        }

        val items = collected.map { it.toTrackShelfItem() }
        return Result.success(HomeShelf(title = "Trending songs", items = items, subtitle = "Today's top tunes"))
    }

    private suspend fun loadPersonalizedSongs(moodId: String): List<Song>? {
        val recents = YtMusicRepository.recents().getOrNull().orEmpty()
            .filter { it.isValidStudioTrack() }
        if (recents.isEmpty()) return null

        return when (moodId) {
            "your_daily_mix" -> {
                val seed = recents.firstOrNull() ?: return null
                val radioSongs = YtMusicRepository.radio(seed.videoId).getOrNull().orEmpty()
                    .filter { it.isValidStudioTrack() }
                (recents.take(8) + radioSongs).distinctBy { it.videoId }
            }
            "on_repeat" -> {
                recents.take(25)
            }
            "back_in_rotation" -> {
                val dropped = recents.drop(4)
                if (dropped.isNotEmpty()) dropped.take(25) else recents.take(25)
            }
            else -> null
        }
    }

    suspend fun loadSongsForMood(moodIdOrQuery: String): Result<List<Song>> {
        val mood = findMood(moodIdOrQuery)
        val moodId = mood?.id ?: moodIdOrQuery
        val query = mood?.query ?: moodIdOrQuery

        // Local personalized snapshots must bypass the legacy global song cache and search fallback.
        val localKind = LocalMixKind.fromId(moodId)
        val localRepository = LocalMixPlatform.repository
        if (localKind != null && localRepository != null) {
            val loaded = localRepository.load(LocalMixPlatform.listenerScope(), localKind)
            return loaded.mix?.let { Result.success(it.songs) }
                ?: Result.failure(IllegalStateException(when (loaded.failure) {
                    com.music.vikify.data.personalization.MixFailure.NO_DISCOVERIES -> "No suitable unheard tracks were found. Try refreshing later."
                    com.music.vikify.data.personalization.MixFailure.HIDDEN_BY_FEEDBACK -> "These songs are hidden from your mixes. Change your preferences or refresh."
                    com.music.vikify.data.personalization.MixFailure.REFRESH_FAILED -> "Could not refresh this mix. Please try again."
                    else -> "Listen to more music to build this mix."
                }))
        }

        songCache[moodId]?.let { return Result.success(it) }

        // 1. Personalized mixes based on user listening
        if (moodId in PERSONALIZED_MOODS) {
            val personalSongs = runCatching { loadPersonalizedSongs(moodId) }.getOrNull()
            if (!personalSongs.isNullOrEmpty()) {
                val enhanced = personalSongs.filter { it.isValidStudioTrack() }.map { it.withHighResArtwork() }
                if (enhanced.isNotEmpty()) {
                    songCache[moodId] = enhanced
                    return Result.success(enhanced)
                }
            }
        }

        // 2. Fetch official studio songs matching the curated mood query
        val songsResult = YtMusicRepository.searchPage(query, SearchFilter.SONGS)
        return songsResult.map { page ->
            val collected = mutableListOf<Song>()
            val seenIds = mutableSetOf<String>()

            fun addPageSongs(pageRows: List<SearchResult>) {
                pageRows.mapNotNull { result ->
                    when (result) {
                        is SearchResult.Track -> result.song
                        is SearchResult.TopTrack -> result.song
                        else -> null
                    }
                }.filter { it.isValidStudioTrack() }.forEach { song ->
                    if (seenIds.add(song.videoId)) {
                        collected.add(song.withHighResArtwork())
                    }
                }
            }

            addPageSongs(page.rows)

            // If we have fewer than 15 songs and continuation is available, fetch next page
            val continuationToken = page.continuation
            if (collected.size < 15 && continuationToken != null) {
                runCatching {
                    val next = YtMusicRepository.searchContinuation(continuationToken, SearchFilter.SONGS).getOrNull()
                    if (next != null) {
                        addPageSongs(next.rows)
                    }
                }
            }

            if (collected.isNotEmpty()) {
                songCache[moodId] = collected
            }
            collected
        }
    }
}
