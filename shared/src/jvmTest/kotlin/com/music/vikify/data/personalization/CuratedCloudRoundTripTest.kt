package com.music.vikify.data.personalization

import java.io.File
import java.nio.file.Files
import kotlinx.coroutines.runBlocking
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.jsonObject
import kotlinx.serialization.json.jsonPrimitive
import kotlinx.serialization.json.long
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertNotNull
import kotlin.test.assertNull
import kotlin.test.assertTrue

/** Exercises real durable stores around the same manifest that a Firestore transaction merges. */
class CuratedCloudRoundTripTest {
    private val fixture = CoreMixFixture()
    private val json = Json { encodeDefaults = true }

    private inner class Device(val directory: File, val base: String) {
        val resets = PersonalizationResetStore(directory.resolve("resets"))
        val home = HomeCollectionStore(directory.resolve("home"), LocalMixKind.entries.mapTo(HashSet()) { it.id },
            clock = { fixture.now })
        val evidence = ListeningEvidenceStore(directory.resolve("evidence"), clock = { fixture.now })
        val copies = MixCopyStore(directory.resolve("copies"), clock = { fixture.now })
        val mixes = GeneratedMixRepository(directory.resolve("mixes"),
            evidence = { resets.learningHistory(base, fixture.history) }, related = { emptyList() },
            isCurrentScope = { it == scope }, clock = { fixture.now }, zone = { fixture.zone },
            feedback = { evidence.snapshot(it).feedback() })
        val resetCoordinator = PersonalizationResetCoordinator(resets, evidence, mixes, home)
        val scope get() = resets.scope(base)
        val epoch get() = resets.cloudMarker(base)?.let {
            json.parseToJsonElement(it).jsonObject.getValue("cutoff").jsonPrimitive.long
        } ?: 0L

        suspend fun capture(): Map<String, String> = buildMap {
            resets.cloudMarker(base)?.let { put("reset", it) }
            home.exportCloud(scope).forEach { (key, value) -> put("home/$key", value) }
            evidence.exportCloud(scope).forEach { (key, value) -> put("evidence/$key", value) }
            copies.exportCloud(base).forEach { (key, value) -> put("copies/$key", value) }
            mixes.exportCloud(scope).forEach { (key, value) -> put("mixes/$key", value) }
        }

        suspend fun applyAll(expected: Map<String, String>, desired: Map<String, String>) {
            assertTrue(evidence.applyCloud(scope, expected.part("evidence/"), desired.part("evidence/")))
            assertTrue(home.applyCloud(scope, expected.part("home/"), desired.part("home/")))
            assertTrue(copies.applyCloud(base, expected.part("copies/"), desired.part("copies/")))
            assertTrue(mixes.applyCloud(scope, expected.part("mixes/"), desired.part("mixes/")))
        }

        suspend fun populate(): SavedMixCopy {
            home.pin(scope, LocalMixKind.ON_REPEAT.id, true)
            home.hide(scope, LocalMixKind.DAILY_MIX.id, true)
            home.follow(scope, LocalMixKind.COMFORT_ZONE.id, true)
            evidence.setPreference(scope, fixture.song(1).videoId, TrackPreference.MORE)
            evidence.setPreference(scope, fixture.song(2).videoId, TrackPreference.HIDE)
            val generated = assertNotNull(mixes.load(scope, LocalMixKind.ON_REPEAT).mix)
            return copies.save(base, "My midnight rotation", LocalMixKind.ON_REPEAT, generated.version, generated.songs)
        }
    }

    private fun Map<String, String>.part(prefix: String) = filterKeys { it.startsWith(prefix) }
        .mapKeys { it.key.removePrefix(prefix) }

    @Test fun freshDeviceRestoresRealStoresAndDeletionSurvivesTheOriginalOfflineDevice() = runBlocking {
        val root = Files.createTempDirectory("curated-round-trip").toFile()
        try {
            val original = Device(root.resolve("original"), "old-cookie-profile")
            val saved = original.populate()
            val originalValues = original.capture()
            val oldReplica = CuratedCloudReplica.bootstrap(originalValues, original.epoch, "old-installation")
            var server = CuratedCloudManifest().mergeReplica(oldReplica.manifest, fixture.now)
            assertTrue(server.entries.values.all { it.time > 0 })

            val fresh = Device(root.resolve("fresh"), "different-cookie-same-profile")
            val empty = fresh.capture()
            assertTrue(empty.isEmpty())
            val freshReplica = CuratedCloudReplica.bootstrap(empty, fresh.epoch, "fresh-installation")
            server = server.mergeReplica(freshReplica.manifest, fixture.now + 1)
            assertEquals(originalValues, server.values())
            fresh.applyAll(empty, server.values())

            // Scope hashes and local feedback revisions differ, while portable content is identical.
            assertEquals(originalValues, fresh.capture())
            assertEquals(original.home.snapshot(original.scope).preferences, fresh.home.snapshot(fresh.scope).preferences)
            assertEquals(original.evidence.snapshot(original.scope).preferences, fresh.evidence.snapshot(fresh.scope).preferences)
            assertEquals(saved, fresh.copies.list(fresh.base).single())
            assertEquals(original.mixes.cached(original.scope, LocalMixKind.ON_REPEAT)?.songs,
                fresh.mixes.cached(fresh.scope, LocalMixKind.ON_REPEAT)?.songs)
            assertTrue(fresh.evidence.snapshot("unrelated-account").preferences.isEmpty())
            assertTrue(fresh.copies.list("unrelated-account").isEmpty())

            val acknowledged = freshReplica.copy(manifest = server, observed = fresh.capture())
            fresh.copies.delete(fresh.base, saved.browseId)
            val deletion = acknowledged.observe(fresh.capture(), fresh.epoch, "fresh-installation", fixture.now + 100)
            server = server.mergeReplica(deletion.manifest, fixture.now + 101)
            assertNull(server.entries["copies/${saved.id}"]?.value)

            // A late upload/reinstall seed from the old phone still contains the removed copy.
            server = server.mergeReplica(oldReplica.manifest, fixture.now + 200)
            assertFalse(server.values().containsKey("copies/${saved.id}"))
            original.applyAll(originalValues, server.values())
            assertTrue(original.copies.list(original.base).isEmpty())
        } finally { root.deleteRecursively() }
    }

    @Test fun resetCrossesDeviceScopesWithoutRestoringOldLearningOrDeletingSavedCopies() = runBlocking {
        val root = Files.createTempDirectory("curated-reset-round-trip").toFile()
        try {
            val source = Device(root.resolve("source"), "source-local-scope")
            val saved = source.populate()
            val before = source.capture()
            val stale = CuratedCloudReplica.bootstrap(before, source.epoch, "source")
            var server = CuratedCloudManifest().mergeReplica(stale.manifest, fixture.now)
            val sourceJournal = stale.copy(manifest = server, observed = before)

            val target = Device(root.resolve("target"), "target-local-scope")
            target.applyAll(target.capture(), server.values())
            val oldTargetScope = target.scope
            val targetJournal = CuratedCloudReplica(manifest = server, observed = target.capture())

            val reset = source.resetCoordinator.reset(source.base, { fixture.history },
                clock = { fixture.now + 1_000 }, zone = fixture.zone)
            assertTrue(reset.cleanupComplete)
            val afterReset = source.capture()
            assertEquals(setOf("reset", "copies/${saved.id}"), afterReset.keys)
            val resetJournal = sourceJournal.observe(afterReset, source.epoch, "source", fixture.now + 1_100)
            server = server.mergeReplica(resetJournal.manifest, fixture.now + 1_200)
            server = server.mergeReplica(stale.manifest, fixture.now + 1_300)
            assertEquals(afterReset, server.values())

            // Downloading a reset rotates the target's own scope and retires its queued writes.
            assertTrue(target.resets.adoptCloudMarker(target.base, null, server.values()["reset"]))
            assertTrue(target.resetCoordinator.resumeCleanup(target.base))
            assertTrue(target.scope != oldTargetScope)
            assertFalse(target.evidence.applyCloud(oldTargetScope, emptyMap(), before.part("evidence/")))
            target.applyAll(target.capture(), server.values())
            assertTrue(target.evidence.snapshot(target.scope).preferences.isEmpty())
            assertEquals(HomeCollectionPreferences(), target.home.snapshot(target.scope).preferences)
            assertNull(target.mixes.cached(target.scope, LocalMixKind.ON_REPEAT))
            assertEquals(saved, target.copies.list(target.base).single())
            assertTrue(target.resets.learningHistory(target.base, fixture.history).isEmpty())

            // A formerly synchronized target's unchanged old state cannot reintroduce its old generation.
            val staleEdit = targetJournal.observe(before + ("evidence/preference:${fixture.song(1).videoId}" to "\"HIDE\""),
                0, "offline-target", fixture.now + 5_000)
            assertEquals(afterReset, server.mergeReplica(staleEdit.manifest, fixture.now + 5_100).values())
        } finally { root.deleteRecursively() }
    }

    @Test fun restartAfterPartialImportKeepsDeletionAndDoesNotRepublishImportedRowsAsNewEdits() = runBlocking {
        val root = Files.createTempDirectory("curated-import-restart").toFile()
        try {
            val source = Device(root.resolve("source"), "source")
            source.populate()
            val cloud = CuratedCloudManifest().mergeReplica(
                CuratedCloudReplica.bootstrap(source.capture(), 0, "source").manifest, fixture.now)
            val targetDirectory = root.resolve("target")
            val target = Device(targetDirectory, "target")
            val empty = target.capture()
            val intent = CuratedCloudReplica.bootstrap(empty, 0, "target").copy(manifest = cloud,
                restoreTarget = cloud.values())
            val journal = root.resolve("journal.json")
            journal.writeText(json.encodeToString(CuratedCloudReplica.serializer(), intent))

            // Power loss occurs after two stores commit and before saved copies/mixes or journal acknowledgement.
            assertTrue(target.evidence.applyCloud(target.scope, emptyMap(), cloud.values().part("evidence/")))
            assertTrue(target.home.applyCloud(target.scope, emptyMap(), cloud.values().part("home/")))
            val restarted = Device(targetDirectory, "target")
            val recovered = json.decodeFromString(CuratedCloudReplica.serializer(), journal.readText())
                .observe(restarted.capture(), 0, "target", fixture.now + 10_000)
            assertEquals(cloud, recovered.manifest)
            restarted.applyAll(restarted.capture(), recovered.manifest.values())
            assertEquals(source.capture(), restarted.capture())

            // Genuine editing after recovery still produces a new durable tombstone.
            val acknowledged = recovered.copy(observed = restarted.capture(), restoreTarget = null)
            val copy = restarted.copies.list(restarted.base).single()
            restarted.copies.delete(restarted.base, copy.browseId)
            val edited = acknowledged.observe(restarted.capture(), 0, "target", fixture.now + 20_000)
            assertNull(edited.manifest.entries["copies/${copy.id}"]?.value)
            assertTrue(edited.manifest.entries.getValue("copies/${copy.id}").time > cloud.entries.getValue("copies/${copy.id}").time)
        } finally { root.deleteRecursively() }
    }
}
