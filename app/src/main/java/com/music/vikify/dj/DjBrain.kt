package com.music.vikify.dj

import java.text.Normalizer
import java.util.Locale
import kotlin.math.*
import kotlin.random.Random
import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable

/** Android port of dj-brain/server's metadata-only learner and four-part selector. */
@Serializable
data class DjVector(
    val energy: Double = .5,
    val valence: Double = .5,
    val danceability: Double = .5,
    val acousticness: Double = .5,
    @SerialName("tempo_norm") val tempo: Double = .5,
) {
    fun values() = listOf(energy, valence, danceability, acousticness, tempo)
    fun move(other: DjVector, rate: Double): DjVector = from(values().zip(other.values()) { a, b ->
        (a + rate * (b - a)).coerceIn(0.0, 1.0)
    })
    companion object {
        fun from(v: List<Double>) = DjVector(v[0], v[1], v[2], v[3], v[4])
    }
}

@Serializable
data class DjTrack(
    val id: String,
    val title: String,
    val artist: String,
    val genre: String,
    val subgenres: List<String> = emptyList(),
    val moods: List<String> = emptyList(),
    val era: String,
    val bpm: Double,
    @SerialName("camelot_key") val key: String,
    @SerialName("duration_ms") val durationMs: Long,
    val vector: DjVector,
    val hasAcoustics: Boolean = true,
)

@Serializable
data class DjPrior(var alpha: Double = 1.0, var beta: Double = 1.0)

@Serializable
data class DjTaste(
    val global: MutableMap<String, DjPrior> = mutableMapOf(),
    val contextual: MutableMap<String, DjPrior> = mutableMapOf(),
    val vectors: MutableMap<String, DjVector> = mutableMapOf(),
    val recent: MutableList<String> = mutableListOf(),
    var events: Int = 0,
    var listenedMs: Long = 0,
)

class DjBrain(val taste: DjTaste = DjTaste(), private val random: Random = Random.Default) {
    var bucket: String = bucketForHour(java.time.LocalTime.now().hour)
    var energyShift = 0.0
    val excludedGenres = mutableSetOf<String>()
    val excludedArtists = mutableSetOf<String>()
    private val sessionRecent = mutableListOf<String>()

    fun target(): DjVector {
        val base = taste.vectors[bucket] ?: defaults.getValue(bucket)
        return base.copy(energy = (base.energy + energyShift).coerceIn(0.0, 1.0),
            tempo = (base.tempo + energyShift / 2).coerceIn(0.0, 1.0))
    }

    fun remember(id: String) {
        sessionRecent.remove(id)
        sessionRecent.add(id)
        if (sessionRecent.size > 90) sessionRecent.removeAt(0)
    }

    fun learn(track: DjTrack, event: String, listenedMs: Long, durationMs: Long) {
        val reward = reward(event, listenedMs, durationMs)
        val before = taste.vectors[bucket] ?: defaults.getValue(bucket)
        val rate = .12 * min(1.0, abs(reward)) * if (reward >= 0) 1.0 else -.25
        if (track.hasAcoustics) taste.vectors[bucket] = before.move(track.vector, rate)
        if (reward != 0.0) {
            dimensions(track).forEachIndexed { index, name ->
                if (name.isBlank() || name == "unknown") return@forEachIndexed
                if (reward == -.25 && index != 0) return@forEachIndexed
                val key = "$index:${normalize(name)}"
                listOf(taste.global.getOrPut(key) { DjPrior() },
                    taste.contextual.getOrPut("$bucket:$key") { DjPrior() }).forEach {
                    it.alpha += max(0.0, reward)
                    it.beta += max(0.0, -reward)
                }
            }
        }
        taste.events++
        taste.listenedMs += listenedMs.coerceIn(0, durationMs.coerceAtLeast(0))
        taste.recent.remove(track.id)
        taste.recent.add(track.id)
        while (taste.recent.size > 40) taste.recent.removeAt(0)
    }

    /** Current/queued IDs and explicit exclusions are never relaxed to fill a set. */
    fun select(catalog: List<DjTrack>, committed: Set<String>, size: Int = 4,
               seed: String = "", previous: DjTrack? = null): List<DjTrack> {
        require(size >= 0)
        val pool = catalog.filter { it.id !in committed && normalize(it.genre) !in excludedGenres &&
            normalize(it.artist) !in excludedArtists }.toMutableList()
        val result = mutableListOf<DjTrack>()
        val usedArtists = mutableSetOf<String>()
        val usedSubs = mutableSetOf<String>()
        val seedTags = seed.split(',').map(::normalize).filter { it.isNotBlank() }.toSet()
        var last = previous
        repeat(min(size, pool.size)) { position ->
            fun freshness(t: DjTrack) = when (t.id) {
                in sessionRecent -> 2
                in taste.recent -> 1
                else -> 0
            }
            val tier = pool.minOf(::freshness)
            var candidates = pool.filter { freshness(it) == tier }
            candidates.filter { normalize(it.artist) !in usedArtists }.takeIf { it.isNotEmpty() }
                ?.let { candidates = it }
            val target = arc(target(), position)
            val chosen = candidates.maxBy { t ->
                val priors = dimensions(t).mapIndexed { i, name -> prior(i, name) }
                val sample = priors.zip(weights).sumOf { (prior, weight) -> beta(prior) * weight }
                val uncertainty = priors.zip(weights).sumOf { (p, weight) ->
                    weight * sqrt(12 * p.alpha * p.beta / ((p.alpha + p.beta).pow(2) * (p.alpha + p.beta + 1)))
                }
                val descriptors = (t.subgenres + t.moods + listOf(t.genre, t.artist, t.era)).map(::normalize).toSet()
                val seedBoost = if (seedTags.isEmpty()) 0.0 else .16 * descriptors.intersect(seedTags).size / seedTags.size
                val recency = max(recency(taste.recent, t.id), recency(sessionRecent, t.id))
                .42 * sample + .33 * cosine(target, t.vector) + .17 * harmonic(last, t) + seedBoost -
                    .14 * recency - .08 * t.subgenres.map(::normalize).count { it in usedSubs } / max(1, t.subgenres.size) -
                    .035 * result.count { normalize(it.genre) == normalize(t.genre) } +
                    if (position % 4 == 2) .14 * uncertainty else 0.0
            }
            result += chosen
            pool.remove(chosen)
            usedArtists += normalize(chosen.artist)
            usedSubs += chosen.subgenres.map(::normalize)
            last = chosen
        }
        return result
    }

    private fun prior(index: Int, name: String): DjPrior {
        val key = "$index:${normalize(name)}"
        val global = taste.global[key] ?: DjPrior()
        val local = taste.contextual["$bucket:$key"] ?: DjPrior()
        return DjPrior(1 + .65 * (global.alpha - 1) + .35 * (local.alpha - 1),
            1 + .65 * (global.beta - 1) + .35 * (local.beta - 1))
    }

    private fun beta(p: DjPrior): Double {
        val x = gamma(p.alpha)
        val y = gamma(p.beta)
        return x / (x + y)
    }

    // Marsaglia–Tsang gamma sampling for contextual Beta Thompson draws.
    private fun gamma(shape: Double): Double {
        if (shape < 1) return gamma(shape + 1) * random.nextDouble().coerceAtLeast(1e-12).pow(1 / shape)
        val d = shape - 1.0 / 3
        val c = 1 / sqrt(9 * d)
        while (true) {
            val x = sqrt(-2 * ln(random.nextDouble().coerceAtLeast(1e-12))) * cos(2 * PI * random.nextDouble())
            val v0 = 1 + c * x
            if (v0 <= 0) continue
            val v = v0 * v0 * v0
            val u = random.nextDouble().coerceAtLeast(1e-12)
            if (u < 1 - .0331 * x.pow(4) || ln(u) < .5 * x * x + d * (1 - v + ln(v))) return d * v
        }
    }

    companion object {
        private val weights = listOf(.45, .40, .15)
        private val defaults = mapOf(
            "morning" to DjVector(.42, .60, .38, .62, .42),
            "afternoon" to DjVector(.66, .65, .66, .28, .62),
            "evening" to DjVector(.62, .55, .60, .30, .58),
            "late_night" to DjVector(.30, .42, .35, .65, .30),
        )
        fun normalize(text: String): String = Normalizer.normalize(text.lowercase(Locale.ROOT), Normalizer.Form.NFKD)
            .replace(Regex("\\p{M}"), "").replace(Regex("[^\\p{L}\\p{N}]+"), " ").trim()
        fun bucketForHour(hour: Int): String {
            require(hour in 0..23)
            return when (hour) { in 5..11 -> "morning"; in 12..16 -> "afternoon"; in 17..21 -> "evening"; else -> "late_night" }
        }
        fun reward(event: String, listenedMs: Long, durationMs: Long): Double {
            if (event in listOf("like", "replay")) return 1.5
            val ratio = if (durationMs > 0) listenedMs.coerceAtLeast(0).toDouble() / durationMs else 0.0
            if (event == "skip" && listenedMs < 8000) return -1.0
            if (event == "skip" && ratio in .15.. .65) return -.25
            return if (ratio >= .80) .7 else 0.0
        }
        fun cosine(a: DjVector, b: DjVector): Double {
            val av = a.values(); val bv = b.values()
            val norm = sqrt(av.sumOf { it * it } * bv.sumOf { it * it })
            return if (norm == 0.0) 0.0 else av.zip(bv).sumOf { (x, y) -> x * y } / norm
        }
        fun harmonic(previous: DjTrack?, next: DjTrack): Double {
            if (previous == null) return .5
            val distance = abs(previous.key.dropLast(1).toInt() - next.key.dropLast(1).toInt())
            val wheel = min(distance, 12 - distance)
            val key = when {
                previous.key == next.key -> 1.0
                previous.key.last() == next.key.last() && wheel == 1 -> .9
                wheel == 0 -> .88
                else -> .15
            }
            val delta = minOf(abs(previous.bpm - next.bpm), abs(previous.bpm - next.bpm / 2), abs(previous.bpm / 2 - next.bpm))
            val tempo = if (delta <= 12) 1 - delta / 24 else max(0.0, .5 - (delta - 12) / 36)
            return .65 * key + .35 * tempo
        }
        private fun dimensions(track: DjTrack) = listOf(track.genre, track.artist, track.era)
        private fun recency(ids: List<String>, id: String) = (ids.indexOf(id) + 1).toDouble() / max(1, ids.size)
        fun arc(target: DjVector, position: Int): DjVector {
            require(position >= 0)
            fun clamp(value: Double) = value.coerceIn(0.0, 1.0)
            return when (position % 4) {
                1 -> target.copy(energy = clamp(target.energy + .10), danceability = clamp(target.danceability + .04))
                2 -> target.copy(energy = clamp(target.energy + .04), valence = clamp(target.valence + .03))
                3 -> target.copy(energy = clamp(target.energy - .05), acousticness = clamp(target.acousticness + .05), tempo = clamp(target.tempo - .03))
                else -> target.copy()
            }
        }
    }
}
