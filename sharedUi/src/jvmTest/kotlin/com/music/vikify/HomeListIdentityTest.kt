package com.music.vikify

import com.music.vikify.data.model.HomeShelf
import com.music.vikify.data.model.ShelfItem
import com.music.vikify.ui.screens.homeItemKeys
import com.music.vikify.ui.screens.homeShelfKeys
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotEquals
import org.junit.Test

class HomeListIdentityTest {
    private fun song(id: String) = ShelfItem("Song", "Artist", null, id, null)

    @Test fun insertingAndReorderingDifferentShelvesKeepsExistingIdentity() {
        val recent = HomeShelf("Recents", listOf(song("a")))
        val browse = HomeShelf("Albums", emptyList(), moreBrowseId = "albums")
        val original = homeShelfKeys(listOf(recent, browse))
        val moved = homeShelfKeys(listOf(HomeShelf("New", emptyList()), browse, recent))
        assertEquals(original[0], moved[2])
        assertEquals(original[1], moved[1])
        assertEquals(original[0], homeShelfKeys(listOf(recent.copy(items = listOf(song("new")))))[0])
    }

    @Test fun repeatedCardsAndSameTitleShelvesHaveUniqueKeys() {
        val repeated = listOf(song("a"), song("a"), song("b"))
        val keys = homeItemKeys(repeated)
        assertEquals(3, keys.toSet().size)
        assertEquals(keys, homeItemKeys(listOf(song("new")) + repeated).drop(1))
        val shelves = homeShelfKeys(listOf(HomeShelf("Mix", emptyList()), HomeShelf("Mix", emptyList())))
        assertEquals(2, shelves.toSet().size)
    }

    @Test fun videoAndBrowseTargetsNeverShareIdentity() {
        val track = song("same")
        val album = track.copy(videoId = null, browseId = "same")
        val keys = homeItemKeys(listOf(track, album))
        assertNotEquals(keys[0], keys[1])
    }

    @Test fun separatorsInServerTextCannotCreateCollisions() {
        val first = ShelfItem("a:1#0", "b", null, null, null)
        val second = first.copy(title = "a", subtitle = "1#0:b")
        assertEquals(2, homeItemKeys(listOf(first, second)).toSet().size)
    }
}
