package com.music.vikify.data.personalization

import com.music.vikify.data.model.HomeShelf
import com.music.vikify.data.model.ShelfItem
import com.music.vikify.data.mood.MoodBox
import com.music.vikify.data.mood.MoodGenreRepository

/** View data only: no catalog work occurs during feed rebuilds or scrolling. */
object HomeCollectionShelves {
    private val fingerprintPattern = Regex("[0-9a-f]{64}")
    fun build(snapshot: HomeCollectionSnapshot): Pair<List<HomeShelf>, List<HomeShelf>> {
        val prefs = snapshot.preferences
        val summaries = snapshot.summaries.associateBy { it.id }
        fun item(mood: MoodBox): ShelfItem = mood.toShelfItem().copy(subtitle = summaries[mood.id]?.let { summary ->
            if (summary.ready) "${summary.trackCount} songs · ${reason(summary)}" else reason(summary)
        } ?: mood.subtitle)
        val definitions = MoodGenreRepository.allMoods.associateBy { it.id }
        val pinned = prefs.pinned.distinct().filterNot { it in prefs.hidden }.mapNotNull(definitions::get)
        val seenTracks = pinned.mapNotNull { mood -> summaries[mood.id]?.trackSetFingerprint
            ?.takeIf(fingerprintPattern::matches) }.toHashSet()
        val personal = (snapshot.readyOrder + snapshot.summaries.filter { it.ready }.map { it.id }).distinct()
            .filterNot { it in prefs.hidden || it in prefs.pinned }
            .filter { id -> summaries[id]?.let { summary ->
                val fingerprint = summary.trackSetFingerprint
                summary.ready && summary.trackCount >= 5 && fingerprint != null &&
                    fingerprintPattern.matches(fingerprint) && seenTracks.add(fingerprint)
            } == true }.mapNotNull(definitions::get).take(3)
        val leading = buildList {
            if (pinned.isNotEmpty()) add(HomeShelf("Pinned collections", pinned.map(::item),
                "Your choices stay in the order you pinned them", collectionGrid = true))
            if (personal.isNotEmpty()) add(HomeShelf("Made For You", personal.map(::item),
                "Shaped by listening on this device", collectionGrid = true))
        }
        // The full catalog remains available in collection management. Home
        // promotes usable personal mixes and the listener's explicit choices.
        return leading to emptyList()
    }

    fun reason(summary: HomeMixSummary): String = when {
        summary.ready -> when (LocalMixKind.fromId(summary.id)) {
            LocalMixKind.ON_REPEAT -> "Your recent repeat listening"
            LocalMixKind.BACK_IN_ROTATION -> "Favorites you have not played lately"
            LocalMixKind.DAILY_MIX -> "Selected from your listening history"
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
