package com.music.bitchord.data.personalization

import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertNull
import kotlin.test.assertTrue

class HomeVisitSessionTest {
    private val first = HomeCollectionSnapshot(scopeKey = "a", readyOrder = listOf("on_repeat", "your_daily_mix"))
    private val next = first.copy(readyOrder = first.readyOrder.reversed())

    @Test fun backgroundPreparationDoesNotMoveAnActiveFeed() {
        val session = HomeVisitSession()
        val request = session.begin("a")
        session.accept(request, first)
        session.stage("a", next)
        assertEquals(first, session.snapshot)
        session.begin("a")
        assertEquals(next, session.snapshot)
    }

    @Test fun explicitRefreshCanAdoptPreparedContent() {
        val session = HomeVisitSession()
        val request = session.begin("a")
        session.accept(request, first)
        assertTrue(session.accept(request, next))
        assertEquals(next, session.snapshot)
    }

    @Test fun resultsFromAnOlderVisitCannotReplaceTheNewerVisit() {
        val session = HomeVisitSession()
        val old = session.begin("a")
        val fresh = session.begin("a")
        assertTrue(session.accept(fresh, next))
        assertFalse(session.accept(old, first))
        assertEquals(next, session.snapshot)
    }

    @Test fun accountSwitchRejectsPreviousSnapshotsAndPreparedResults() {
        val session = HomeVisitSession()
        val a = session.begin("a")
        session.accept(a, first)
        session.stage("a", next)
        session.begin("b")
        assertNull(session.snapshot)
        session.stage("a", next)
        assertFalse(session.accept(a, first))
        session.begin("b")
        assertNull(session.snapshot)
    }

    @Test fun controlsChangedDuringCacheReadCannotBeUndoneByThatRead() {
        val session = HomeVisitSession()
        val request = session.begin("a")
        session.accept(request, first)
        val preferences = HomeCollectionPreferences(hidden = setOf("on_repeat"))
        session.preferences("a", preferences)
        session.accept(request, next)
        assertEquals(preferences, session.snapshot?.preferences)
    }

    @Test fun explicitControlsAlsoApplyToAlreadyPreparedNextVisit() {
        val session = HomeVisitSession()
        val request = session.begin("a")
        session.accept(request, first)
        session.stage("a", next)
        val preferences = HomeCollectionPreferences(pinned = listOf("on_repeat"))
        session.preferences("a", preferences)
        session.begin("a")
        assertEquals(preferences, session.snapshot?.preferences)
        assertEquals(next.readyOrder, session.snapshot?.readyOrder)
    }
}
