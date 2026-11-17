package com.music.vikify.data.personalization

import kotlinx.coroutines.CompletableDeferred
import kotlinx.coroutines.async
import kotlinx.coroutines.runBlocking
import kotlinx.coroutines.withTimeout
import kotlinx.coroutines.yield
import java.nio.file.Files
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFailsWith
import kotlin.test.assertFalse
import kotlin.test.assertNull
import kotlin.test.assertTrue

class PersonalizationResetCoordinatorTest {
    private val f = CoreMixFixture()
    private val ids = LocalMixKind.entries.mapTo(HashSet()) { it.id }
    private fun session(revision: Long = 1) = ListeningSessionRecord("session", f.song(1).videoId, "entry", null,
        f.now - 60_000, f.now, revision, 60_000, 60_000, 180_000, false, SessionEnd.STOPPED)

    @Test fun resetClearsOneProfilesLearningAndControlsButPreservesSavedCopiesAndOtherProfiles() = runBlocking {
        val dir = Files.createTempDirectory("reset-coordinator-test").toFile()
        try {
            val boundaries = PersonalizationResetStore(dir.resolve("boundary"))
            val evidence = ListeningEvidenceStore(dir.resolve("evidence"), { f.now })
            val home = HomeCollectionStore(dir.resolve("home"), ids, { f.now })
            val mixes = GeneratedMixRepository(dir.resolve("mixes"), { boundaries.learningHistory("a", f.history) }, { emptyList() },
                isCurrentScope = { it == boundaries.scope("a") }, clock = { f.now }, zone = { f.zone })
            val copies = MixCopyStore(dir.resolve("copies"))
            evidence.record("a", session())
            evidence.setPreference("a", f.song(1).videoId, TrackPreference.MORE)
            evidence.setPreference("b", f.song(2).videoId, TrackPreference.HIDE)
            home.pin("a", "on_repeat", true)
            home.follow("a", "your_daily_mix", true)
            home.record("a", "visit", "on_repeat", opened = true)
            home.hide("b", "comfort_zone", true)
            val before = mixes.load("a", LocalMixKind.ON_REPEAT).mix!!
            val saved = copies.save("a", "Keep this", before.kind, before.version, before.songs)
            val reset = PersonalizationResetCoordinator(boundaries, evidence, mixes, home).reset("a", { f.history }, { f.now }, f.zone)
            assertTrue(reset.cleanupComplete)
            assertEquals("a:reset:1", reset.scope)
            assertEquals(HomeCollectionPreferences(), home.snapshot(reset.scope).preferences)
            assertTrue(home.snapshot(reset.scope).usage.isEmpty())
            assertTrue(evidence.snapshot(reset.scope).sessions.isEmpty())
            assertTrue(evidence.snapshot(reset.scope).preferences.isEmpty())
            assertNull(mixes.cached(reset.scope, LocalMixKind.ON_REPEAT))
            assertEquals(MixFailure.INSUFFICIENT_EVIDENCE, mixes.load(reset.scope, LocalMixKind.ON_REPEAT).failure)
            assertEquals(saved, copies.get("a", saved.browseId))
            assertEquals(TrackPreference.HIDE, evidence.snapshot("b").preferences[f.song(2).videoId])
            assertEquals(setOf("comfort_zone"), home.snapshot("b").preferences.hidden)
            assertEquals(f.history, boundaries.learningHistory("b", f.history))
            assertTrue(boundaries.pending("a").isEmpty())
        } finally { dir.deleteRecursively() }
    }

    @Test fun lateCheckpointsFeedbackAndHomeUpdatesCannotRecreateRetiredFiles() = runBlocking {
        val dir = Files.createTempDirectory("reset-coordinator-test").toFile()
        try {
            val boundaries = PersonalizationResetStore(dir.resolve("boundary"))
            val evidenceDir = dir.resolve("evidence")
            val homeDir = dir.resolve("home")
            val evidence = ListeningEvidenceStore(evidenceDir, { f.now })
            val home = HomeCollectionStore(homeDir, ids)
            val mixes = GeneratedMixRepository(dir.resolve("mixes"), { f.history }, { emptyList() }, clock = { f.now }, zone = { f.zone })
            evidence.record("a", session())
            home.follow("a", "on_repeat", true)
            PersonalizationResetCoordinator(boundaries, evidence, mixes, home).reset("a", { f.history }, { f.now }, f.zone)
            evidence.record("a", session(2))
            assertFailsWith<IllegalStateException> { evidence.setPreference("a", f.song(1).videoId, TrackPreference.MORE) }
            assertFailsWith<IllegalStateException> { home.record("a", "late", "on_repeat") }
            assertEquals(MixFailure.SCOPE_CHANGED, mixes.load("a", LocalMixKind.ON_REPEAT).failure)
            assertTrue(evidenceDir.listFiles().isNullOrEmpty())
            assertTrue(homeDir.listFiles().isNullOrEmpty())
            assertTrue(dir.resolve("mixes").listFiles().isNullOrEmpty())
        } finally { dir.deleteRecursively() }
    }

    @Test fun inFlightGenerationCannotPublishIntoTheResetScope() = runBlocking {
        val dir = Files.createTempDirectory("reset-coordinator-test").toFile()
        val entered = CompletableDeferred<Unit>()
        val release = CompletableDeferred<Unit>()
        try {
            val boundaries = PersonalizationResetStore(dir.resolve("boundary"))
            val evidence = ListeningEvidenceStore(dir.resolve("evidence"))
            val home = HomeCollectionStore(dir.resolve("home"), ids)
            val mixes = GeneratedMixRepository(dir.resolve("mixes"), { f.history }, {
                entered.complete(Unit); release.await(); f.candidates.map { it.song }
            }, isCurrentScope = { it == boundaries.scope("a") }, clock = { f.now }, zone = { f.zone })
            val generating = async { mixes.load("a", LocalMixKind.DAILY_MIX, force = true) }
            entered.await()
            val resetting = async { PersonalizationResetCoordinator(boundaries, evidence, mixes, home).reset("a", { f.history }, { f.now }, f.zone) }
            withTimeout(2_000) { while (boundaries.scope("a") == "a") yield() }
            release.complete(Unit)
            assertEquals(MixFailure.SCOPE_CHANGED, generating.await().failure)
            assertTrue(resetting.await().cleanupComplete)
            assertTrue(dir.resolve("mixes").listFiles().isNullOrEmpty())
        } finally { release.complete(Unit); dir.deleteRecursively() }
    }

    @Test fun incompleteCleanupIsJournaledAndResumesAfterRestart() = runBlocking {
        val dir = Files.createTempDirectory("reset-coordinator-test").toFile()
        try {
            val boundaryDir = dir.resolve("boundary")
            val evidenceDir = dir.resolve("evidence")
            val homeDir = dir.resolve("home")
            val mixDir = dir.resolve("mixes")
            val boundaries = PersonalizationResetStore(boundaryDir)
            val evidence = ListeningEvidenceStore(evidenceDir)
            val home = HomeCollectionStore(homeDir, ids)
            val mixes = GeneratedMixRepository(mixDir, { f.history }, { emptyList() })
            evidence.setPreference("a", f.song(1).videoId, TrackPreference.MORE)
            val blocked = evidenceDir.listFiles()!!.single()
            assertTrue(blocked.delete())
            assertTrue(blocked.mkdir())
            val child = blocked.resolve("locked-data").also { it.writeText("pending deletion") }
            val result = PersonalizationResetCoordinator(boundaries, evidence, mixes, home).reset("a", { f.history }, { f.now }, f.zone)
            assertFalse(result.cleanupComplete)
            assertEquals("a:reset:1", boundaries.scope("a"))
            assertEquals(listOf("a"), boundaries.pending("a"))
            assertTrue(child.delete())
            assertTrue(blocked.delete())
            val restored = PersonalizationResetStore(boundaryDir)
            assertTrue(PersonalizationResetCoordinator(restored, ListeningEvidenceStore(evidenceDir),
                GeneratedMixRepository(mixDir, { emptyList() }, { emptyList() }), HomeCollectionStore(homeDir, ids)).resumeCleanup("a"))
            assertTrue(restored.pending("a").isEmpty())
        } finally { dir.deleteRecursively() }
    }

    @Test fun aFailedBoundaryCommitLeavesTheOldProfileIntact() = runBlocking {
        val dir = Files.createTempDirectory("reset-coordinator-test").toFile()
        try {
            val boundaryPath = dir.resolve("boundary").also { it.writeText("directory unavailable") }
            val boundaries = PersonalizationResetStore(boundaryPath)
            val evidence = ListeningEvidenceStore(dir.resolve("evidence"))
            val home = HomeCollectionStore(dir.resolve("home"), ids)
            val mixes = GeneratedMixRepository(dir.resolve("mixes"), { f.history }, { emptyList() })
            evidence.setPreference("a", f.song(1).videoId, TrackPreference.MORE)
            home.follow("a", "on_repeat", true)
            assertFailsWith<IllegalStateException> {
                PersonalizationResetCoordinator(boundaries, evidence, mixes, home).reset("a", { f.history }, { f.now }, f.zone)
            }
            assertEquals("a", boundaries.scope("a"))
            assertEquals(TrackPreference.MORE, evidence.snapshot("a").preferences[f.song(1).videoId])
            assertEquals(setOf("on_repeat"), home.snapshot("a").preferences.followed)
        } finally { dir.deleteRecursively() }
    }
}
