package com.music.vikify.data.personalization

import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertTrue

class CuratedCloudManifestTest {
    @Test fun staleInitialCacheCannotReplaceExistingCloudOrResurrectDeletedCopy() {
        val initial = CuratedCloudReplica().observe(mapOf("copies/a" to "new", "copies/deleted" to "old"), 0, "old", 100)
        val deleted = initial.observe(mapOf("copies/a" to "new"), 0, "old", 200).manifest
        val stale = CuratedCloudReplica.bootstrap(mapOf("copies/a" to "stale", "copies/deleted" to "old", "copies/local" to "unique"), 0, "fresh")
        val result = deleted.mergeReplica(stale.manifest, 300)
        assertEquals(mapOf("copies/a" to "new", "copies/local" to "unique"), result.values())
        assertTrue(result.entries.getValue("copies/local").time > 0)
        val edit = stale.observe(stale.observed + ("copies/a" to "edited offline"), 0, "fresh", 400)
        assertEquals("edited offline", deleted.mergeReplica(edit.manifest, 500).values()["copies/a"])
    }

    @Test fun concurrentEditsMergeAndDeletionSurvivesStaleDevice() {
        val a = CuratedCloudReplica().observe(mapOf("copies/a" to "A"), 0, "a", 100)
        val b = CuratedCloudReplica().observe(mapOf("copies/b" to "B"), 0, "b", 100)
        val both = a.manifest.merge(b.manifest)
        assertEquals(both, b.manifest.merge(a.manifest))
        val removed = a.copy(manifest = both).observe(emptyMap(), 0, "a", 200)
        assertEquals(mapOf("copies/b" to "B"), removed.manifest.merge(both).values())
    }

    @Test fun resetHidesOldLearningButRetainsSavedCopies() {
        val old = CuratedCloudReplica().observe(mapOf("evidence/preference:song" to "HIDE", "copies/a" to "A"), 0, "a", 100)
        val marker = "{\"cutoff\":150}"
        val reset = old.observe(mapOf("reset" to marker, "copies/a" to "A"), 150, "a", 200)
        val stale = old.observe(mapOf("evidence/preference:song" to "MORE", "copies/a" to "A"), 0, "b", 300)
        assertEquals(mapOf("reset" to marker, "copies/a" to "A"), reset.manifest.merge(stale.manifest).values())
        assertEquals(150, reset.manifest.merge(stale.manifest).epoch)
        val fresh = reset.observe(mapOf("reset" to marker, "copies/a" to "A", "evidence/preference:song" to "HIDE"), 150, "a", 250)
        assertEquals("HIDE", fresh.manifest.merge(stale.manifest).values()["evidence/preference:song"])
    }

    @Test fun emptyInstallDoesNotEraseCloudAndOfflineDeletionIsRetained() {
        val cloud = CuratedCloudReplica().observe(mapOf("copies/a" to "A"), 0, "first", 10).manifest
        val fresh = CuratedCloudReplica().observe(emptyMap(), 0, "new", 20)
        assertEquals(cloud.values(), fresh.manifest.merge(cloud).values())
        val restored = fresh.copy(manifest = cloud, observed = cloud.values())
        val deleted = restored.observe(emptyMap(), 0, "new", 30)
        assertTrue(deleted.manifest.entries.containsKey("copies/a"))
        assertFalse(deleted.manifest.merge(cloud).values().containsKey("copies/a"))
    }

    @Test fun interruptedImportDoesNotRestampCloudValuesButKeepsConcurrentLocalEdit() {
        val cloud = CuratedCloudReplica().observe(mapOf("copies/a" to "A"), 0, "first", 10).manifest
        val intent = CuratedCloudReplica(manifest = cloud, restoreTarget = cloud.values())
        assertEquals(cloud, intent.observe(cloud.values(), 0, "new", 100).manifest)
        assertEquals("edited", intent.observe(mapOf("copies/a" to "edited"), 0, "new", 100).manifest.values()["copies/a"])
    }
}
