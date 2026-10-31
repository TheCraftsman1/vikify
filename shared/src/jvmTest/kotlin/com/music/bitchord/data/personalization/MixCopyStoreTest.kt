package com.music.bitchord.data.personalization

import com.music.bitchord.data.model.ArtistRef
import kotlinx.coroutines.runBlocking
import java.nio.file.Files
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFailsWith
import kotlin.test.assertFalse
import kotlin.test.assertNotEquals
import kotlin.test.assertNull
import kotlin.test.assertTrue

class MixCopyStoreTest {
    private val f = CoreMixFixture()

    @Test fun savedCopyRetainsChosenOrderAndVersionAcrossRestart() = runBlocking {
        val dir = Files.createTempDirectory("mix-copy-test").toFile()
        try {
            val store = MixCopyStore(dir, { f.now })
            val songs = mutableListOf(f.song(3), f.song(1), f.song(2))
            val saved = store.save("a", " My favorites ", LocalMixKind.ON_REPEAT, 17, songs)
            songs.clear()
            val restored = MixCopyStore(dir).get("a", saved.browseId)!!
            assertEquals(listOf(f.song(3), f.song(1), f.song(2)), restored.songs)
            assertEquals(17, restored.sourceVersion)
            assertEquals("My favorites", restored.title)
            assertEquals(saved, restored)
            assertTrue(store.list("b").isEmpty())
            assertFalse(dir.listFiles().orEmpty().any { it.extension == "tmp" })
        } finally { dir.deleteRecursively() }
    }

    @Test fun savedCopyPreservesCatalogCreditsWithoutPlayerOrQueueState() = runBlocking {
        val dir = Files.createTempDirectory("mix-copy-test").toFile()
        try {
            val song = f.song(1).copy(queueEntryId = "playing-entry", setVideoId = "provider-entry", radioName = "Radio",
                artists = listOf(ArtistRef("Alpha", "UC_Alpha")), isExplicit = true)
            val saved = MixCopyStore(dir).save("a", "Copy", LocalMixKind.DAILY_MIX, 1, listOf(song)).songs.single()
            assertEquals(song.artists, saved.artists)
            assertEquals(true, saved.isExplicit)
            assertNull(saved.queueEntryId)
            assertNull(saved.setVideoId)
            assertNull(saved.radioName)
        } finally { dir.deleteRecursively() }
    }

    @Test fun repeatedSavesAtTheSameTimeProduceIndependentPlaylists() = runBlocking {
        val dir = Files.createTempDirectory("mix-copy-test").toFile()
        try {
            val store = MixCopyStore(dir, { f.now })
            val first = store.save("a", "Copy", LocalMixKind.ON_REPEAT, 1, listOf(f.song(1)))
            val second = store.save("a", "Copy", LocalMixKind.ON_REPEAT, 2, listOf(f.song(2)))
            assertNotEquals(first.id, second.id)
            assertEquals(listOf(f.song(1)), store.get("a", first.browseId)?.songs)
            assertEquals(2, store.list("a").size)
        } finally { dir.deleteRecursively() }
    }

    @Test fun editsAffectOnlyTheSelectedCopyAndAnEmptyPlaylistCanRemainSaved() = runBlocking {
        val dir = Files.createTempDirectory("mix-copy-test").toFile()
        try {
            val source = listOf(f.song(1), f.song(2))
            val store = MixCopyStore(dir)
            val first = store.save("a", "First", LocalMixKind.ON_REPEAT, 1, source)
            val second = store.save("a", "Second", LocalMixKind.ON_REPEAT, 1, source)
            store.rename("a", first.browseId, " Renamed ")
            source.forEach { store.removeTrack("a", first.browseId, it.videoId) }
            assertEquals("Renamed", store.get("a", first.browseId)?.title)
            assertTrue(store.get("a", first.browseId)!!.songs.isEmpty())
            assertEquals(source, store.get("a", second.browseId)?.songs)
            store.delete("a", first.browseId)
            assertNull(store.get("a", first.browseId))
            assertEquals(1, store.list("a").size)
            assertEquals(listOf(f.song(1), f.song(2)), source)
        } finally { dir.deleteRecursively() }
    }

    @Test fun invalidOrEmptySnapshotsAreNotSaved() = runBlocking {
        val dir = Files.createTempDirectory("mix-copy-test").toFile()
        try {
            val store = MixCopyStore(dir)
            assertFailsWith<IllegalArgumentException> { store.save("a", "Copy", LocalMixKind.ON_REPEAT, 1, emptyList()) }
            assertFailsWith<IllegalArgumentException> { store.save("a", " ", LocalMixKind.ON_REPEAT, 1, listOf(f.song(1))) }
            assertFailsWith<IllegalArgumentException> { store.save("a", "Copy", LocalMixKind.ON_REPEAT, 0, listOf(f.song(1))) }
            assertTrue(dir.listFiles().isNullOrEmpty())
        } finally { dir.deleteRecursively() }
    }

    @Test fun unreadableCopyFileIsPreservedInsteadOfPublishingAnEmptyReplacement() = runBlocking {
        val dir = Files.createTempDirectory("mix-copy-test").toFile()
        try {
            MixCopyStore(dir).save("a", "Copy", LocalMixKind.ON_REPEAT, 1, listOf(f.song(1)))
            val file = dir.listFiles()!!.single()
            file.writeText("{broken")
            assertFailsWith<Exception> { MixCopyStore(dir).save("a", "Other", LocalMixKind.ON_REPEAT, 2, listOf(f.song(2))) }
            assertEquals("{broken", file.readText())
        } finally { dir.deleteRecursively() }
    }

    @Test fun failedWriteDoesNotPublishASuccessfulCopyInMemory() = runBlocking {
        val dir = Files.createTempDirectory("mix-copy-test").toFile()
        try {
            val blocker = dir.resolve("copies").also { it.writeText("unavailable directory") }
            val store = MixCopyStore(blocker)
            assertFailsWith<Exception> { store.save("a", "Copy", LocalMixKind.ON_REPEAT, 1, listOf(f.song(1))) }
            assertTrue(store.list("a").isEmpty())
            assertEquals("unavailable directory", blocker.readText())
        } finally { dir.deleteRecursively() }
    }
}
