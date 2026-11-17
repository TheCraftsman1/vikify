package com.music.vikify.data.mood

import com.music.vikify.data.YtMusicRepository
import com.music.vikify.data.model.HomeShelf
import com.music.vikify.data.model.SearchFilter
import com.music.vikify.data.model.SearchResult
import com.music.vikify.data.model.ShelfItem
import com.music.vikify.data.model.Song
import com.music.vikify.data.model.durationMillis
import com.music.vikify.data.personalization.LocalMixKind
import com.music.vikify.data.personalization.LocalMixGenerator
import com.music.vikify.data.personalization.LocalMixPlatform
import com.music.vikify.data.innertube.Innertube
import kotlinx.coroutines.CancellationException
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

    private val BAD_TITLE_KEYWORDS = listOf(
        "sound bath", "singing bowl", "singing bowls", "full album", "432hz", "528hz",
        "continuous mix", "mega mix", "mashup", "compilation", "nightcore",
        "live stream", "full concert", "bass boosted", "8d audio", "slowed + reverb"
    )
    private val longMixTitle = Regex("\\b\\d+\\s*(?:hours?|hrs?)\\b", RegexOption.IGNORE_CASE)

    fun Song.isValidStudioTrack(): Boolean {
        if (!LocalMixGenerator.eligible(this)) return false
        val lower = title.lowercase()
        val millis = durationMillis()
        // A measured short song can legitimately be called "24 Hours".
        // Use the long-form title guard only when duration is unavailable.
        if ((millis <= 0 && longMixTitle.containsMatchIn(title)) || BAD_TITLE_KEYWORDS.any { lower.contains(it) }) return false
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

    /** Empty view state only; starter metadata must never masquerade as loaded recommendations. */
    fun initialQuickPicksShelf(): HomeShelf = HomeShelf("Quick picks", emptyList(), "Songs you might like")

    fun initialTrendingSongsShelf(): HomeShelf = HomeShelf("Trending songs", emptyList(), "Current music")

    private fun Song.withHighResArtwork(): Song {
        val art = thumbnailUrl ?: return this
        val enhanced = when {
            art.contains("googleusercontent.com") || art.contains("ggpht.com") ->
                art.replace(Regex("""w\d+-h\d+"""), "w544-h544")
                    .replace(Regex("""=s\d+"""), "=s544")
            art.contains("i.ytimg.com") ->
                art.replace(Regex("""(?<=/)(?:hq|mq)?default\.jpg(?=$|\?)"""), "sddefault.jpg")
            else -> art
        }
        return copy(thumbnailUrl = enhanced)
    }

    suspend fun loadQuickPicksShelf(): Result<HomeShelf> {
        val collected = linkedMapOf<String, Song>()
        YtMusicRepository.recents().getOrNull().orEmpty().filter { it.isValidStudioTrack() }.take(8)
            .forEach { collected[it.videoId] = it.withHighResArtwork() }
        YtMusicRepository.quickPicks(collected.keys).getOrNull().orEmpty().filter { it.isValidStudioTrack() }
            .forEach { collected.putIfAbsent(it.videoId, it.withHighResArtwork()) }
        if (collected.isEmpty()) return Result.failure(IllegalStateException("No music recommendations are available yet."))
        return Result.success(HomeShelf("Quick picks", collected.values.take(24).map { it.toTrackShelfItem() },
            "From your recent listening and recommendations"))
    }

    suspend fun loadTrendingSongsShelf(): Result<HomeShelf> {
        val result = YtMusicRepository.searchPage("${Innertube.currentRegion} trending songs", SearchFilter.SONGS)
        return result.mapCatching { page ->
            val songs = page.rows.mapNotNull { row -> when (row) {
                is SearchResult.Track -> row.song
                is SearchResult.TopTrack -> row.song
                else -> null
            } }.filter { it.isValidStudioTrack() }.distinctBy { it.videoId }.take(24)
            check(songs.isNotEmpty()) { "No current trending songs are available." }
            HomeShelf("Trending songs", songs.map { it.withHighResArtwork().toTrackShelfItem() }, "Current music")
        }
    }

    suspend fun loadSongsForMood(moodIdOrQuery: String): Result<List<Song>> {
        val mood = findMood(moodIdOrQuery)
        val moodId = mood?.id ?: moodIdOrQuery
        val query = mood?.query ?: moodIdOrQuery

        // Local personalized snapshots must bypass the legacy global song cache and search fallback.
        val localKind = LocalMixKind.fromId(moodId)
        val localRepository = LocalMixPlatform.repository
        if (localKind != null) {
            if (localRepository == null) return Result.failure(IllegalStateException("Personal mixes are not available on this device yet."))
            val loaded = localRepository.load(LocalMixPlatform.listenerScope(), localKind)
            return loaded.mix?.let { Result.success(it.songs) }
                ?: Result.failure(IllegalStateException(when (loaded.failure) {
                    com.music.vikify.data.personalization.MixFailure.NO_DISCOVERIES -> "No suitable unheard tracks were found. Try refreshing later."
                    com.music.vikify.data.personalization.MixFailure.HIDDEN_BY_FEEDBACK -> "These songs are hidden from your mixes. Change your preferences or refresh."
                    com.music.vikify.data.personalization.MixFailure.REFRESH_FAILED -> "Could not refresh this mix. Please try again."
                    else -> "Listen to more music to build this mix."
                }))
        }

        val scope = LocalMixPlatform.listenerScope()
        val cacheKey = "${scope.length}:$scope:${Innertube.currentRegion}:$moodId"
        songCache[cacheKey]?.let { return Result.success(it) }

        // Curated collections search real studio tracks; they are never a
        // substitute for unavailable personal listening evidence.
        val songsResult = YtMusicRepository.searchPage(query, SearchFilter.SONGS)
        return songsResult.mapCatching { page ->
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
                try {
                    val next = YtMusicRepository.searchContinuation(continuationToken, SearchFilter.SONGS).getOrNull()
                    if (next != null) {
                        addPageSongs(next.rows)
                    }
                } catch (cancelled: CancellationException) { throw cancelled }
                catch (_: Exception) { /* Keep the playable first page. */ }
            }

            check(scope == LocalMixPlatform.listenerScope()) { "Listener changed. Reopen this collection." }
            check(collected.isNotEmpty()) { "No playable tracks were found for this collection. Try another collection or refresh later." }
            songCache[cacheKey] = collected
            collected
        }.onFailure { if (it is CancellationException) throw it }
    }
}
