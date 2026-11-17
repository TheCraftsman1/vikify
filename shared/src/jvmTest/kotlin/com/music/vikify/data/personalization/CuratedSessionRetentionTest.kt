package com.music.vikify.data.personalization

import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertTrue

class CuratedSessionRetentionTest {
    private val sessionKey = "evidence/session:session-1"
    private val eventAt = 1_800_000_000_000L
    private val expiry get() = eventAt + CuratedSessionRetention.WINDOW_MS
    private fun payload(at: Long = eventAt) = "{\"updatedAt\":$at}"

    @Test fun sessionLifetimeComesFromTheEventRatherThanTheLatestUpload() {
        val replica = CuratedCloudReplica().observe(mapOf(sessionKey to payload()), 0, "a", eventAt + 1_000)
        assertEquals(expiry, replica.manifest.entries.getValue(sessionKey).expiresAt)
        val resent = replica.observe(mapOf(sessionKey to payload()), 0, "a", eventAt + 5_000)
        assertEquals(expiry, resent.manifest.entries.getValue(sessionKey).expiresAt)
        assertTrue(replica.manifest.mergeReplica(resent.manifest, expiry).entries.isEmpty())
    }

    @Test fun deletingASessionPreservesItsExpiryAndDoesNotExpireDurableUserChoices() {
        val initial = CuratedCloudReplica().observe(mapOf(sessionKey to payload(),
            "copies/deleted" to "copy", "evidence/preference:song" to "MORE"), 0, "a", eventAt)
        val deleted = initial.observe(emptyMap(), 0, "a", eventAt + 10_000)
        assertEquals(expiry, deleted.manifest.entries.getValue(sessionKey).expiresAt)
        val retained = deleted.manifest.mergeReplica(CuratedCloudManifest(), expiry)
        assertFalse(sessionKey in retained.entries)
        assertEquals(setOf("copies/deleted", "evidence/preference:song"), retained.entries.keys)
        assertTrue(retained.entries.values.all { it.value == null && it.expiresAt == 0L })
    }

    @Test fun anOfflineDeviceCannotResurrectExpiredSessionsAfterTombstoneCollection() {
        val old = CuratedCloudReplica().observe(mapOf(sessionKey to payload()), 0, "offline", eventAt)
        val removed = old.observe(emptyMap(), 0, "other", eventAt + 1_000).manifest
            .mergeReplica(CuratedCloudManifest(), expiry + 1)
        assertTrue(removed.entries.isEmpty())
        // Even an old client that stamps a stale checkpoint as a new edit cannot extend its event lifetime.
        val stale = CuratedCloudManifest(entries = mapOf(sessionKey to CuratedCloudValue(
            expiry + 10_000, "old-client", payload())))
        assertTrue(removed.mergeReplica(stale, expiry + 20_000).entries.isEmpty())
        assertTrue(stale.mergeReplica(removed, expiry + 20_000).entries.isEmpty())
        assertTrue(CuratedCloudReplica.bootstrap(mapOf(sessionKey to payload()), 0, "new", expiry + 1).manifest.entries.isEmpty())
    }

    @Test fun legacySessionPayloadsAndDeletionMarkersReceiveBoundedRetention() {
        val legacy = CuratedCloudManifest(entries = mapOf(
            "evidence/session:active" to CuratedCloudValue(eventAt + 500, "legacy", payload()),
            "evidence/session:deleted" to CuratedCloudValue(eventAt, "legacy", null),
            "copies/deleted" to CuratedCloudValue(eventAt, "legacy", null)))
        val upgraded = legacy.mergeReplica(CuratedCloudManifest(), eventAt + 1_000)
        assertEquals(expiry, upgraded.entries.getValue("evidence/session:active").expiresAt)
        assertEquals(expiry, upgraded.entries.getValue("evidence/session:deleted").expiresAt)
        assertEquals(setOf("copies/deleted"), upgraded.mergeReplica(CuratedCloudManifest(), expiry).entries.keys)
    }

    @Test fun onlyNewerResetEpochsMayPromoteInitialLearningSeedsOverAnExistingCloudKey() {
        val remote = CuratedCloudManifest(entries = mapOf(
            "reset" to CuratedCloudValue(10, "remote", "{\"cutoff\":100}", 100),
            "evidence/preference:song" to CuratedCloudValue(20, "remote", "OLD", 100),
            "copies/copy" to CuratedCloudValue(20, "remote", "CLOUD")))
        val newer = CuratedCloudReplica.bootstrap(mapOf("reset" to "{\"cutoff\":200}",
            "evidence/preference:song" to "NEW", "copies/copy" to "LOCAL"), 200, "local", 300)
        val merged = remote.mergeReplica(newer.manifest, 400)
        assertEquals("NEW", merged.values()["evidence/preference:song"])
        assertEquals("CLOUD", merged.values()["copies/copy"])
        assertEquals(200L, merged.epoch)
    }

    @Test fun twoPhonesUnionKeepsTheNewestTwoThousandSessionsWithoutBlockingRestore() {
        fun sessions(range: IntRange) = range.associate { number ->
            "evidence/session:${number.toString().padStart(4, '0')}" to payload(eventAt + number * 1_000L)
        }
        val now = eventAt + 3_000_000
        val oldPhone = CuratedCloudReplica().observe(sessions(0..1499), 0, "old", now).manifest
        val newPhone = CuratedCloudReplica().observe(sessions(1500..2999), 0, "new", now).manifest
        val merged = oldPhone.mergeReplica(newPhone, now)
        assertEquals(2_000, merged.values().size)
        assertEquals(sessions(1000..2999).keys, merged.values().keys)
        assertEquals(merged, merged.mergeReplica(oldPhone, now + 1_000))
    }
}
