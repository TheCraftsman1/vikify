package com.music.bitchord.data.personalization

import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.currentCoroutineContext
import kotlinx.coroutines.ensureActive

/** Prepares the next visit without publishing changes into the current scrolling feed. */
class AdaptiveHomeRepository(val store: HomeCollectionStore, private val mixes: GeneratedMixRepository,
    private val currentScope: (String) -> Boolean = { true }) {

    suspend fun cached(scope: String): HomeCollectionSnapshot {
        val snapshot = store.snapshot(scope)
        val summaries = LocalMixKind.entries.map { kind ->
            val saved = snapshot.summaries.firstOrNull { it.id == kind.id }
            try { summary(kind, mixes.cached(scope, kind), null, saved) }
            catch (cancelled: CancellationException) { throw cancelled }
            catch (failure: Exception) { HomeMixSummary(kind.id, HomeMixAvailability.UNAVAILABLE) }
        }
        check(currentScope(scope)) { "Listener changed" }
        val ready = summaries.filter { it.ready }.map { it.id }
        return snapshot.copy(summaries = summaries,
            readyOrder = snapshot.readyOrder.filter { it in ready } + ready.filterNot { it in snapshot.readyOrder })
    }

    suspend fun prepare(scope: String): HomeCollectionSnapshot {
        val summaries = LocalMixKind.entries.map { kind ->
            currentCoroutineContext().ensureActive()
            check(currentScope(scope)) { "Listener changed" }
            val loaded = mixes.load(scope, kind)
            summary(kind, loaded.mix, loaded.failure)
        }
        currentCoroutineContext().ensureActive()
        check(currentScope(scope)) { "Listener changed" }
        return store.prepare(scope, summaries)
    }

    private fun summary(kind: LocalMixKind, mix: GeneratedMix?, failure: MixFailure?, saved: HomeMixSummary? = null): HomeMixSummary {
        if (mix != null && mix.songs.isNotEmpty()) return HomeMixSummary(kind.id, HomeMixAvailability.READY,
            mix.songs.size, mix.generatedAt, mix.confidence)
        return HomeMixSummary(kind.id, when (failure) {
            MixFailure.NO_DISCOVERIES -> HomeMixAvailability.NO_DISCOVERIES
            MixFailure.REFRESH_FAILED, MixFailure.SCOPE_CHANGED -> HomeMixAvailability.UNAVAILABLE
            MixFailure.HIDDEN_BY_FEEDBACK -> HomeMixAvailability.EXCLUDED
            else -> if (saved?.ready == true) HomeMixAvailability.NEEDS_REFRESH else saved?.availability ?: HomeMixAvailability.LEARNING
        })
    }
}
