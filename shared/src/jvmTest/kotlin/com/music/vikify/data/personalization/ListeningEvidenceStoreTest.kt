package com.music.vikify.data.personalization

import kotlinx.coroutines.runBlocking
import java.nio.file.Files
import kotlinx.serialization.json.Json
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertNotNull
import kotlin.test.assertTrue

class ListeningEvidenceStoreTest {
    private val now = 1_800_000_000_000L
    private fun session(id: String = "session", revision: Long = 1, updated: Long = now, ended: SessionEnd? = null) =
        ListeningSessionRecord(id, "abcdefghijk", "queue-entry", "mood:on_repeat", updated - 20_000,
            updated, revision, 20_000, 20_000, 120_000, false, ended)

    @Test fun preferencesPersistAcrossRestartAndCanBeReversed(): Unit = runBlocking {
        val directory = Files.createTempDirectory("vikify-evidence").toFile()
        try {
            val first = ListeningEvidenceStore(directory) { now }
            first.setPreference("a", "abcdefghijk", TrackPreference.HIDE)
            val reopened = ListeningEvidenceStore(directory) { now }
            assertEquals(TrackPreference.HIDE, reopened.snapshot("a").preferences["abcdefghijk"])
            reopened.setPreference("a", "abcdefghijk", null)
            assertTrue(ListeningEvidenceStore(directory) { now }.snapshot("a").preferences.isEmpty())
        } finally { directory.deleteRecursively() }
    }

    @Test fun repeatedPreferenceDoesNotInflateTheRevision(): Unit = runBlocking {
        val directory = Files.createTempDirectory("vikify-evidence").toFile()
        try {
            val store = ListeningEvidenceStore(directory) { now }
            val first = store.setPreference("a", "track", TrackPreference.MORE)
            assertEquals(first, store.setPreference("a", "track", TrackPreference.MORE))
            assertEquals(first.revision + 1, store.setPreference("a", "track", TrackPreference.HIDE).revision)
        } finally { directory.deleteRecursively() }
    }

    @Test fun checkpointsAreUpsertsAndOlderWritesCannotReopenACompletedSession(): Unit = runBlocking {
        val directory = Files.createTempDirectory("vikify-evidence").toFile()
        try {
            val store = ListeningEvidenceStore(directory) { now }
            store.record("a", session(revision = 2, ended = SessionEnd.MANUAL_SKIP))
            store.record("a", session(revision = 1))
            store.record("a", session(revision = 3))
            assertEquals(1, store.snapshot("a").sessions.size)
            assertEquals(SessionEnd.MANUAL_SKIP, store.snapshot("a").sessions.single().ended)
            assertEquals(2, store.snapshot("a").sessions.single().revision)
        } finally { directory.deleteRecursively() }
    }

    @Test fun accountEvidenceIsIsolated(): Unit = runBlocking {
        val directory = Files.createTempDirectory("vikify-evidence").toFile()
        try {
            val store = ListeningEvidenceStore(directory) { now }
            store.record("a", session())
            store.setPreference("a", "track", TrackPreference.HIDE)
            assertTrue(store.snapshot("b").sessions.isEmpty())
            assertTrue(store.snapshot("b").preferences.isEmpty())
            assertFalse(directory.listFiles()!!.any { it.name.contains("account") })
        } finally { directory.deleteRecursively() }
    }

    @Test fun expiredSessionsArePrunedWithoutRemovingExplicitPreferences(): Unit = runBlocking {
        val directory = Files.createTempDirectory("vikify-evidence").toFile()
        try {
            val store = ListeningEvidenceStore(directory) { now }
            store.setPreference("a", "track", TrackPreference.MORE)
            store.record("a", session("expired", updated = now - ListeningEvidenceStore.RETENTION_MS - 1))
            store.record("a", session("current"))
            assertEquals(listOf("current"), store.snapshot("a").sessions.map { it.id })
            assertNotNull(store.snapshot("a").preferences["track"])
        } finally { directory.deleteRecursively() }
    }

    @Test fun recordCountIsBoundedAndNewestEvidenceSurvives(): Unit = runBlocking {
        val directory = Files.createTempDirectory("vikify-evidence").toFile()
        try {
            val initial = ListeningEvidenceStore(directory) { now }
            initial.setPreference("a", "track", TrackPreference.MORE)
            val seed = initial.snapshot("a").copy(sessions = (0 until ListeningEvidenceStore.MAX_SESSIONS).map {
                session("old-$it", updated = now - it - 1)
            })
            directory.listFiles()!!.single().writeText(Json.encodeToString(ListeningEvidenceSnapshot.serializer(), seed))
            val store = ListeningEvidenceStore(directory) { now }
            store.record("a", session("newest"))
            val result = store.snapshot("a")
            assertEquals(ListeningEvidenceStore.MAX_SESSIONS, result.sessions.size)
            assertEquals("newest", result.sessions.first().id)
            assertFalse(result.sessions.any { it.id == "old-${ListeningEvidenceStore.MAX_SESSIONS - 1}" })
        } finally { directory.deleteRecursively() }
    }
}
