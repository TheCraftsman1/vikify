package com.music.bitchord.data.personalization

import com.music.bitchord.data.model.HomeShelf
import com.music.bitchord.data.model.ShelfItem
import com.music.bitchord.data.mood.MoodBox
import com.music.bitchord.data.mood.MoodGenreRepository
import com.music.bitchord.data.mood.MoodSection

/** View data only: no catalog work occurs during feed rebuilds or scrolling. */
object HomeCollectionShelves {
    fun build(snapshot: HomeCollectionSnapshot): Pair<List<HomeShelf>, List<HomeShelf>> {
        val prefs = snapshot.preferences
        val summaries = snapshot.summaries.associateBy { it.id }
        fun item(mood: MoodBox): ShelfItem = mood.toShelfItem().copy(subtitle = summaries[mood.id]?.let(::reason) ?: "Curated collection")
        val definitions = MoodGenreRepository.allMoods.associateBy { it.id }
        val pinned = prefs.pinned.filterNot { it in prefs.hidden }.mapNotNull(definitions::get)
        val personal = snapshot.readyOrder.filterNot { it in prefs.hidden || it in prefs.pinned }
            .filter { summaries[it]?.ready == true }.mapNotNull(definitions::get)
        val learning = LocalMixKind.entries.mapNotNull { kind -> definitions[kind.id]?.takeIf {
            kind.id !in prefs.hidden && kind.id !in prefs.pinned && summaries[kind.id]?.ready != true
        } }
        val leading = buildList {
            if (pinned.isNotEmpty()) add(HomeShelf("Pinned collections", pinned.map(::item),
                "Your choices stay in the order you pinned them", collectionGrid = true))
            if (personal.isNotEmpty()) add(HomeShelf("Made For You", personal.map(::item),
                "Shaped by listening on this device", collectionGrid = true))
            if (learning.isNotEmpty()) add(HomeShelf("Your mixes are taking shape", learning.map(::item),
                "Open a card to see what it needs", collectionGrid = true))
        }
        val curated = MoodGenreRepository.allMoods.filter {
            LocalMixKind.fromId(it.id) == null && it.id !in prefs.hidden && it.id !in prefs.pinned
        }.groupBy { it.section }
        val categories = MoodSection.entries.mapNotNull { section ->
            val moods = curated[section].orEmpty()
            if (moods.isEmpty()) null else HomeShelf(
                if (section == MoodSection.MADE_FOR_YOU) "Explore collections" else section.title,
                moods.map(::item), "Curated collections")
        }
        return leading to categories
    }

    fun reason(summary: HomeMixSummary): String = when {
        summary.ready -> when (LocalMixKind.fromId(summary.id)) {
            LocalMixKind.ON_REPEAT -> "Your recent repeat listening"
            LocalMixKind.BACK_IN_ROTATION -> "Favorites you have not played lately"
            LocalMixKind.DAILY_MIX -> "Favorites and connected discoveries"
            LocalMixKind.COMFORT_ZONE -> "Familiar favorites, your way"
            LocalMixKind.ARTIST_ORBIT -> "Artists you return to"
            LocalMixKind.FRESH_FINDS -> "Discoveries connected to your taste"
            LocalMixKind.BEYOND_THE_USUAL -> "Explore beyond your main artists"
            else -> "Shaped by your listening"
        }
        summary.availability == HomeMixAvailability.NO_DISCOVERIES -> "More discoveries needed; try later"
        summary.availability == HomeMixAvailability.UNAVAILABLE -> "Could not prepare this mix; try again"
        summary.availability == HomeMixAvailability.NEEDS_REFRESH -> "Open this mix to refresh its songs"
        summary.availability == HomeMixAvailability.EXCLUDED -> "Review your song preferences"
        summary.id == LocalMixKind.BACK_IN_ROTATION.id -> "Favorites return after two weeks away"
        summary.id == LocalMixKind.ON_REPEAT.id -> "Play a few favorites more than once"
        summary.id == LocalMixKind.BEYOND_THE_USUAL.id -> "Listen across different songs and days"
        else -> "Keep listening to shape this mix"
    }
}
