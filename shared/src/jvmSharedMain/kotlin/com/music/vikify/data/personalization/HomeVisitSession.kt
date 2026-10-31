package com.music.vikify.data.personalization

import java.util.UUID

data class HomeVisitTicket(val scope: String, val id: String, val controlsRevision: Long)

/** The active feed is frozen until a visit boundary or an explicit listener action. */
class HomeVisitSession {
    var ticket: HomeVisitTicket? = null
        private set
    var snapshot: HomeCollectionSnapshot? = null
        private set
    private var pending: HomeCollectionSnapshot? = null
    private var controlsRevision = 0L

    fun begin(scope: String): HomeVisitTicket {
        if (ticket?.scope != scope) reset()
        snapshot = pending ?: snapshot
        pending = null
        return HomeVisitTicket(scope, UUID.randomUUID().toString(), controlsRevision).also { ticket = it }
    }

    fun accept(request: HomeVisitTicket, value: HomeCollectionSnapshot): Boolean {
        if (!current(request)) return false
        snapshot = if (request.controlsRevision != controlsRevision && snapshot != null)
            value.copy(preferences = snapshot!!.preferences) else value
        return true
    }

    fun stage(scope: String, value: HomeCollectionSnapshot) {
        if (ticket?.scope == scope) pending = value.copy(preferences = snapshot?.preferences ?: value.preferences)
    }

    fun preferences(scope: String, value: HomeCollectionPreferences) {
        if (ticket?.scope != scope) return
        controlsRevision++
        snapshot = snapshot?.copy(preferences = value)
        pending = pending?.copy(preferences = value)
    }

    fun current(request: HomeVisitTicket) = ticket?.id == request.id && ticket?.scope == request.scope
    fun reset() { ticket = null; snapshot = null; pending = null; controlsRevision = 0 }
}
