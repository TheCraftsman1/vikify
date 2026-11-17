package com.music.vikify.data.mood

import com.music.vikify.data.model.Song
import com.music.vikify.data.personalization.LocalMixKind
import com.music.vikify.data.personalization.LocalMixPlatform
import kotlinx.coroutines.runBlocking
import kotlin.test.Test
import kotlin.test.assertFalse
import kotlin.test.assertTrue

class MoodGenreRepositoryTest {
    private fun song(title: String) = Song("aaaaaaaaaaa", title, "Artist", null, durationText = "3:30")

    @Test fun ordinarySongTitlesDoNotFailBecauseTheyContainHourOrLoop() = with(MoodGenreRepository) {
        listOf("Golden Hour", "After Hours", "Hourglass", "24 Hours", "Loop", "In the Loop").forEach {
            assertTrue(song(it).isValidStudioTrack(), it)
        }
    }

    @Test fun longMixesAndUnplayableIdentifiersStayOutOfCollections() = with(MoodGenreRepository) {
        assertFalse(song("1 hour relaxing piano").copy(durationText = null).isValidStudioTrack())
        assertFalse(song("12 HOURS of music").copy(durationText = null).isValidStudioTrack())
        assertFalse(song("Full album compilation").isValidStudioTrack())
        assertFalse(song("A song").copy(videoId = "invalid-id").isValidStudioTrack())
        assertFalse(song("A song").copy(videoId = "local:missing", localUri = "").isValidStudioTrack())
        assertFalse(song("A song").copy(isVideo = true).isValidStudioTrack())
        assertFalse(song("A song").copy(durationText = "30:00").isValidStudioTrack())
    }

    @Test fun initialHomeViewsContainNoInventedOrStaleStarterTracks() {
        assertTrue(MoodGenreRepository.initialQuickPicksShelf().items.isEmpty())
        assertTrue(MoodGenreRepository.initialTrendingSongsShelf().items.isEmpty())
    }

    @Test fun unavailablePersonalMixEngineNeverFallsThroughToGenericSongSearch() = runBlocking {
        val previous = LocalMixPlatform.repository
        try {
            LocalMixPlatform.repository = null
            LocalMixKind.entries.forEach { kind ->
                val loaded = MoodGenreRepository.loadSongsForMood(kind.id)
                assertTrue(loaded.isFailure, kind.id)
                assertTrue(loaded.exceptionOrNull()?.message?.contains("not available") == true)
            }
        } finally { LocalMixPlatform.repository = previous }
    }
}
