package com.music.vikify.dj

import android.content.Context
import android.media.AudioFormat
import android.media.AudioAttributes
import android.os.SystemClock
import android.media.MediaPlayer
import android.net.Uri
import kotlinx.coroutines.withContext
import kotlinx.coroutines.Dispatchers
import android.speech.tts.TextToSpeech
import android.speech.tts.UtteranceProgressListener
import androidx.media3.common.Player
import androidx.media3.common.MediaItem
import androidx.media3.common.util.UnstableApi
import androidx.media3.exoplayer.ExoPlayer
import com.music.vikify.data.LikeState
import com.music.vikify.data.listentogether.ListenTogether
import com.music.vikify.data.model.LikeStatus
import com.music.vikify.data.model.Song
import com.music.vikify.data.model.durationMillis
import com.music.vikify.data.personalization.AndroidLocalMixes
import com.music.vikify.data.intelligence.LocalMusicIntent
import com.music.vikify.data.intelligence.MusicAction
import com.music.vikify.data.intelligence.MusicIntent
import com.music.vikify.playback.queueEntryId
import com.music.vikify.playback.toMediaItem
import com.music.vikify.playback.toSong
import com.music.vikify.playback.BoundedContextPersistence
import java.util.Locale
import java.util.UUID
import kotlinx.coroutines.*
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.serialization.encodeToString
import kotlinx.serialization.json.Json

const val ACTION_DJ = "com.music.vikify.action.DJ"
data class DjMessage(val text: String, val fromUser: Boolean = false, val id: String = UUID.randomUUID().toString())
enum class DjLoadingStage { IDLE, ANALYZING, FINDING, BUILDING, READY }
enum class DjPhase { IDLE, THINKING, DUCKING, SPEAKING, LISTENING }
data class DjStatus(
    val active: Boolean = false, val loading: Boolean = false, val speaking: Boolean = false,
    val loadingStage: DjLoadingStage = DjLoadingStage.IDLE, val foregroundLoading: Boolean = false,
    val requestDescription: String? = null,
    val playing: Boolean = false,
    val current: DjTrack? = null, val song: Song? = null, val upcoming: List<Song> = emptyList(),
    val messages: List<DjMessage> = emptyList(), val chapter: String = "Picked for you",
    val message: String = "Your next favorite is out there.", val error: String? = null,
    val events: Int = 0, val listenedMs: Long = 0, val voiceEnabled: Boolean = true, val liked: Boolean = false,
    val ducking: Boolean = false, val speechText: String? = null,
    val speechRangeStart: Int = 0, val speechRangeEnd: Int = 0,
    val intelligenceLabel: String = "Your music companion",
    val suggestions: List<Song> = emptyList(), val suggestedQuery: String? = null,
    val listenerName: String? = null,
    val replyPending: Boolean = false,
) {
    val phase: DjPhase get() = when {
        speaking -> DjPhase.SPEAKING
        ducking -> DjPhase.DUCKING
        !active -> DjPhase.IDLE
        loading -> DjPhase.THINKING
        else -> DjPhase.LISTENING
    }
}
object DjPlayback {
    internal val mutable = MutableStateFlow(DjStatus())
    val state = mutable.asStateFlow()
    // High-rate voice samples are read only by drawing code, never by the studio's state collector.
    internal val mutableVoiceLevel = MutableStateFlow(0f)
    val voiceLevel = mutableVoiceLevel.asStateFlow()
    internal val openPlayer = MutableStateFlow(false)
}

/** The service owns music, learning, conversations and voice while the sheet is closed. */
@androidx.annotation.OptIn(UnstableApi::class)
class AndroidDjSession(private val context: Context, private val scope: CoroutineScope,
    private val player: () -> ExoPlayer?, private val prepareQueue: () -> Unit,
    private val captureBoundedQueue: (ExoPlayer) -> BoundedContextPersistence.State? = { null },
    private val restoreBoundedQueue: (ExoPlayer, BoundedContextPersistence.State) -> Boolean = { _, _ -> false },
) : AutoCloseable {
    private val json = Json { ignoreUnknownKeys = true }
    private val prefs = context.getSharedPreferences("dj_taste_v1", Context.MODE_PRIVATE)
    private var brain = DjBrain()
    private fun newDiscovery() = DjDiscovery { com.music.vikify.download.Downloads.getDownloadedSongs(context) }
    private var discovery = newDiscovery()
    private var inventory = emptyList<DjCandidate>()
    private var familiar = emptySet<String>()
    private var hidden = emptySet<String>()
    private var requested = emptyList<DjCandidate>()
    private val entries = mutableMapOf<String, Pair<Song, Int>>()
    private val played = mutableListOf<String>()
    private var sessionId = ""
    private var profileScope = ""
    private var requestQuery: String? = null
    private val intelligence = DjIntelligenceGateway(context.applicationContext)
    private var selectionIntent = MusicIntent()
    private var libraryContext = DjIntelligenceGateway.LibraryContext(emptyList(), emptySet(), emptySet())
    private var plannedIds = emptySet<String>()
    private var operation: Job? = null
    private var ticker: Job? = null
    private var observedEntry: String? = null
    private var current: Song? = null
    private var anchor: Song? = null
    private var dwellMs = 0L
    private var currentDuration = 0L
    private var chaptersPlanned = 0
    private var observedChapter = -1
    private var retryAt = 0L
    private var pendingSpeech: String? = null
    private var speechDeadline = 0L
    private var pendingReplySpeech: Pair<String, Long>? = null
    private var speechRequiresMusic = true
    private var tts: TextToSpeech? = null
    private var ttsReady = false
    private var speechTimeout: Job? = null
    private var duckJob: Job? = null
    private var amplitudeJob: Job? = null
    private var voicePlayer: ExoPlayer? = null
    private var gnaniSpeechPlayer: MediaPlayer? = null
    private var duckGain: DjDuckingGain? = null
    private val ownedVolumeWrites = ArrayDeque<Float>()
    @Volatile private var utterance = ""
    @Volatile private var speechEnvelope: DjSpeechEnvelope? = null
    private var queueSnapshot: QueueSnapshot? = null
    private data class QueueSnapshot(val player: ExoPlayer, val items: List<MediaItem>, val index: Int, val position: Long,
        val playing: Boolean, val repeat: Int, val shuffled: Boolean, val bounded: BoundedContextPersistence.State?)
    private val volumeListener = object : Player.Listener {
        override fun onVolumeChanged(volume: Float) {
            // Media3 may deliver a nested setter's event after the setter has returned.
            if (ownedVolumeWrites.firstOrNull() == volume) { ownedVolumeWrites.removeFirst(); return }
            duckGain?.userVolume(volume)
            applyVoiceVolume()
        }
    }
    private var learnedLike = false
    private var queuedRequest: String? = null
    private var conversationOperation: Job? = null
    private var suggestionOperation: Job? = null

    private fun listenerName(): String? = prefs.getString("name:${AndroidLocalMixes.scope()}", null)

    init {
        tts = TextToSpeech(context.applicationContext) { status -> scope.launch {
            if (status == TextToSpeech.SUCCESS) {
                val supported = tts?.setLanguage(Locale.getDefault()) ?: TextToSpeech.LANG_NOT_SUPPORTED
                ttsReady = supported >= 0 || (tts?.setLanguage(Locale.US) ?: -1) >= 0
                val reply = pendingReplySpeech
                if (ttsReady && reply != null && SystemClock.elapsedRealtime() <= reply.second) speakReply(reply.first)
                else pendingReplySpeech = null
            }
        } }
        tts?.setOnUtteranceProgressListener(object : UtteranceProgressListener() {
            override fun onBeginSynthesis(id: String?, sampleRateInHz: Int, audioFormat: Int, channelCount: Int) {
                if (id == utterance) speechEnvelope = when (audioFormat) {
                    AudioFormat.ENCODING_PCM_16BIT -> DjSpeechEnvelope(sampleRateInHz, channelCount)
                    AudioFormat.ENCODING_PCM_8BIT -> DjSpeechEnvelope(sampleRateInHz, channelCount, pcm8 = true)
                    else -> null
                }
            }
            override fun onAudioAvailable(id: String?, audio: ByteArray?) {
                if (id == utterance && audio != null) speechEnvelope?.append(audio)
            }
            override fun onStart(id: String?) { scope.launch { if (id == utterance) {
                DjPlayback.mutable.update { it.copy(speaking = true, ducking = false) }
                amplitudeJob?.cancel()
                amplitudeJob = scope.launch {
                    val started = SystemClock.elapsedRealtime()
                    while (isActive && id == utterance) {
                        DjPlayback.mutableVoiceLevel.value = speechEnvelope?.levelAt(SystemClock.elapsedRealtime() - started) ?: 0f
                        delay(1_000L / DjSpeechEnvelope.FRAMES_PER_SECOND)
                    }
                }
            } } }
            override fun onRangeStart(id: String?, start: Int, end: Int, frame: Int) { scope.launch {
                if (id == utterance) DjPlayback.mutable.update { it.copy(speechRangeStart = start, speechRangeEnd = end) }
            } }
            override fun onDone(id: String?) { scope.launch { if (id == utterance) finishSpeech() } }
            @Deprecated("Platform callback") override fun onError(id: String?) { scope.launch { if (id == utterance) finishSpeech() } }
        })
    }

    fun open(name: String) {
        DjPlayback.mutable.update { it.copy(listenerName = listenerName()) }
    }

    fun start(genres: String = "", voice: Boolean = DjPlayback.state.value.voiceEnabled, name: String = "",
        intent: MusicIntent = MusicIntent(), intelligenceLabel: String = "Local recommendations", preferredIds: Set<String> = emptySet(),
        requestText: String? = null, preserveConversation: Boolean = false, openingSong: Song? = null) {
        if (remotePlayback()) { DjPlayback.mutable.update { it.copy(error = "Switch playback to this device and leave Listen Together to start your DJ.") }; return }
        val conversation = if (preserveConversation) DjPlayback.state.value.messages else emptyList()
        val waitingRequest = if (preserveConversation) queuedRequest else null
        stop(announce = false)
        queuedRequest = waitingRequest
        sessionId = UUID.randomUUID().toString()
        val ticket = sessionId
        profileScope = AndroidLocalMixes.scope()
        selectionIntent = intent
        plannedIds = preferredIds
        requestQuery = genres.takeIf { it.isNotBlank() }?.take(250)
        val firstName = listenerName() ?: name.trim().substringBefore(' ').take(32).takeIf { it.isNotBlank() && it != "Listener" }
        val greeting = if (requestQuery == null) "Let's dive in${firstName?.let { ", $it" }.orEmpty()}. Recent favorites, songs connected to them, and a few discoveries. I'll find a new direction as we go."
            else "Let's listen to $requestQuery${firstName?.let { ", $it" }.orEmpty()}. I'll keep the next songs connected."
        DjPlayback.mutable.value = DjStatus(active = true, loading = true, voiceEnabled = voice,
            listenerName = firstName,
            intelligenceLabel = intelligenceLabel,
            loadingStage = DjLoadingStage.ANALYZING, foregroundLoading = true,
            requestDescription = requestQuery,
            messages = (if (preserveConversation) conversation + DjMessage(greeting) else
                listOfNotNull((requestText ?: requestQuery)?.let { DjMessage(it, true) }, DjMessage(greeting))).takeLast(40),
            message = "Finding your opening songs…")
        operation = scope.launch {
            try {
                brain = withContext(Dispatchers.IO) {
                    DjBrain(prefs.getString(profileScope, null)?.let { runCatching { json.decodeFromString<DjTaste>(it) }.getOrNull() } ?: DjTaste())
                }
                brain.excludedArtists += selectionIntent.excludedArtists.map(DjBrain::normalize)
                libraryContext = intelligence.libraryContext()
                val p = requireNotNull(player()) { "The player is still connecting. Tap Let DJ pick to retry." }
                val startingQueue = (0 until p.mediaItemCount).map(p::getMediaItemAt)
                val preferredArtists = brain.taste.global.filter { it.key.startsWith("1:") && it.value.alpha > it.value.beta + .5 }
                    .entries.sortedByDescending { it.value.alpha / (it.value.alpha + it.value.beta) }
                    .take(2).map { it.key.removePrefix("1:") }
                DjPlayback.mutable.update { it.copy(loadingStage = DjLoadingStage.FINDING) }
                val saved = libraryCandidates(requestQuery)
                val result = (if (selectionIntent.libraryOnly) DjDiscoveryResult(saved, hidden = libraryContext.blocked)
                    else discovery.opening(p.currentMediaItem?.toSong(), requestQuery, preferredArtists).let {
                        it.copy(candidates = it.candidates + saved)
                    }).let { result -> if (openingSong == null) result else result.copy(
                        candidates = listOf(DjCandidate(openingSong, DjOrigin.REQUEST, 0)) + result.candidates) }
                ensureActive(); checkScope(ticket); remember(result)
                DjPlayback.mutable.update { it.copy(loadingStage = DjLoadingStage.BUILDING) }
                val ready = select(emptySet(), 0).sortedBy { openingSong != null && it.song.videoId != openingSong.videoId }
                check(ready.isNotEmpty()) { "I couldn't find songs right now. Check your connection, then tap Let DJ pick." }
                check(startingQueue == (0 until p.mediaItemCount).map(p::getMediaItemAt)) { "Your queue changed while I was finding songs. Tap Let DJ pick when you're ready." }
                queueSnapshot = QueueSnapshot(p, startingQueue, p.currentMediaItemIndex, p.currentPosition.coerceAtLeast(0),
                    p.playWhenReady, p.repeatMode, p.shuffleModeEnabled, captureBoundedQueue(p))
                prepareQueue()
                p.repeatMode = Player.REPEAT_MODE_OFF; p.shuffleModeEnabled = false
                p.setMediaItems(ready.map { item(it.song, 0) }, true)
                chaptersPlanned = 1; p.prepare(); p.play()
                DjPlayback.openPlayer.value = true
                pendingSpeech = greeting; speechDeadline = SystemClock.elapsedRealtime() + 15_000
                DjPlayback.mutable.update { it.copy(loading = false, foregroundLoading = false, loadingStage = DjLoadingStage.READY, message = "Your DJ is here") }
                startTicker()
            } catch (c: CancellationException) { throw c }
            catch (failure: Exception) { if (ticket == sessionId) {
                stop(announce = false)
                DjPlayback.mutable.update { it.copy(error = failure.message ?: "Couldn't start. Let's try again.") }
            } }
        }
    }
    private fun remember(result: DjDiscoveryResult) {
        android.util.Log.d("VikifyDJ", "Live inventory: ${result.candidates.size} choices; ${result.hidden.size} hidden")
        requested = result.candidates.filter { it.origin == DjOrigin.REQUEST }
        if (requestQuery != null) check(requested.isNotEmpty()) { "I couldn't find $requestQuery. Try a song, artist, language or a different mood." }
        inventory = (result.candidates + inventory).distinctBy { it.song.videoId to it.origin }.take(240)
        familiar = result.familiar; hidden = result.hidden
    }
    private fun select(committed: Set<String>, chapter: Int): List<DjCandidate> {
        val libraryIds = libraryContext.songs.mapTo(HashSet()) { it.videoId }
        val available = (if (requestQuery != null) requested else inventory.filter { it.origin != DjOrigin.REQUEST })
            .filter { !selectionIntent.libraryOnly || it.song.videoId in libraryIds }
        return DjLivePlanner.choose(available, brain.taste, committed, played, familiar, chapter, brain.excludedArtists,
            hidden + libraryContext.blocked + LikeState.overrides.value.filterValues { it == LikeStatus.DISLIKE }.keys +
                (if (selectionIntent.excludePlayedToday) libraryContext.playedToday else emptySet()),
            explicitRequest = requestQuery != null, preferredIds = plannedIds)
    }

    private fun libraryCandidates(query: String?): List<DjCandidate> {
        val words = query?.let(DjBrain::normalize)?.split(' ')?.filter { it !in setOf("", "songs", "music", "song", "by", "the", "some") }.orEmpty()
        return libraryContext.songs.filter { song ->
            val metadata = DjBrain.normalize("${song.title} ${song.artist} ${song.albumName.orEmpty()}")
            words.isEmpty() || words.all { it in metadata }
        }.mapIndexed { index, song -> DjCandidate(song, if (query == null) DjOrigin.LIKED else DjOrigin.REQUEST, index) }
    }
    private fun item(song: Song, chapter: Int): androidx.media3.common.MediaItem {
        val id = "dj:$sessionId:${UUID.randomUUID()}"; entries[id] = song to chapter
        return song.copy(queueEntryId = id, radioName = "DJ · ${DjLivePlanner.chapter(chapter)}").toMediaItem()
    }

    private fun startTicker() {
        ticker?.cancel()
        ticker = scope.launch {
            var last = SystemClock.elapsedRealtime(); var wasPlaying = false
            while (isActive && DjPlayback.state.value.active) {
                val p = player() ?: break
                val now = SystemClock.elapsedRealtime()
                if (remotePlayback() || profileScope != AndroidLocalMixes.scope()) { stop(restoreQueue = false); break }
                val entryId = p.currentMediaItem?.queueEntryId
                if (entryId != observedEntry) {
                    finishTrack(); if (speechRequiresMusic) cancelSpeech(clearPending = false)
                    val entry = entries[entryId]
                    if (entry == null) { stop(); break }
                    observedEntry = entryId; current = entry.first
                    learnedLike = LikeState.overrides.value[entry.first.videoId] == LikeStatus.LIKE
                    played += entry.first.videoId
                    if (played.size > 120) played.removeAt(0)
                    brain.remember(entry.first.videoId)
                    if (observedChapter != entry.second) {
                        observedChapter = entry.second
                        DjPlayback.mutable.update { it.copy(chapter = DjLivePlanner.chapter(entry.second)) }
                        if (entry.second > 0) say(when (entry.second % 3) {
                            1 -> "Let's follow an artist connection. Here's ${entry.first.title}, by ${entry.first.artist}."
                            2 -> "Time for a fresh direction. Give ${entry.first.artist} a listen."
                            else -> "Back to a few picks for you. Starting with ${entry.first.title}."
                        })
                    }
                    DjPlayback.mutable.update { it.copy(current = metadata(entry.first), song = entry.first, liked = learnedLike) }
                } else if (p.isPlaying && wasPlaying) dwellMs += (now - last).coerceIn(0, 1500)
                if (p.duration > 0) currentDuration = p.duration
                if (!p.isPlaying && utterance.isNotEmpty() && speechRequiresMusic) cancelSpeech()
                if (pendingSpeech != null && now > speechDeadline) pendingSpeech = null
                if (p.isPlaying && utterance.isEmpty() && (ttsReady || GnaniTtsEngine.isConfigured()) && pendingSpeech != null) { speak(pendingSpeech!!); pendingSpeech = null }
                if (current?.videoId?.let { LikeState.overrides.value[it] } == LikeStatus.LIKE && !learnedLike) like()
                val upcoming = (p.currentMediaItemIndex + 1 until p.mediaItemCount).mapNotNull { entries[p.getMediaItemAt(it).queueEntryId]?.first }
                DjPlayback.mutable.update { it.copy(upcoming = upcoming, playing = p.isPlaying, events = brain.taste.events, listenedMs = brain.taste.listenedMs + dwellMs) }
                if (operation?.isActive != true && queuedRequest != null) {
                    val text = queuedRequest!!; queuedRequest = null; request(text, record = false)
                } else if (upcoming.size <= 2 && operation?.isActive != true && now >= retryAt) fillQueue()
                if (p.currentMediaItemIndex > 8) p.removeMediaItems(0, p.currentMediaItemIndex - 4)
                val live = (0 until p.mediaItemCount).mapNotNull { p.getMediaItemAt(it).queueEntryId }.toSet()
                entries.keys.retainAll(live)
                wasPlaying = p.isPlaying; last = now; delay(400)
            }
        }
    }
    private fun finishTrack(event: String = "skip") {
        current?.let { song ->
            val duration = currentDuration.takeIf { it > 0 } ?: song.durationMillis().takeIf { it > 0 } ?: 240_000
            val reward = DjBrain.reward(event, dwellMs, duration)
            brain.learn(metadata(song), event, dwellMs, duration); discovery.observe(song, reward)
            if (reward > 0) anchor = song
            saveTaste()
        }
        current = null; dwellMs = 0; currentDuration = 0
    }
    private fun fillQueue(replace: Boolean = false, advance: Boolean = false) {
        if (operation?.isActive == true) return
        val p = player() ?: return
        val ticket = sessionId; val expected = p.currentMediaItem?.queueEntryId ?: return
        val chapter = chaptersPlanned
        // A requested opening is one chapter; later chapters follow its musical connections.
        if (!replace && chapter > 0) { requestQuery = null; plannedIds = emptySet() }
        operation = scope.launch {
            try {
                DjPlayback.mutable.update { it.copy(loading = true, error = null, foregroundLoading = replace,
                    loadingStage = DjLoadingStage.FINDING, requestDescription = requestQuery) }
                libraryContext = intelligence.libraryContext()
                val saved = libraryCandidates(requestQuery)
                remember(if (selectionIntent.libraryOnly) DjDiscoveryResult(saved, hidden = libraryContext.blocked)
                    else discovery.next(anchor ?: current, chapter, requestQuery).let { it.copy(candidates = it.candidates + saved) })
                ensureActive(); checkScope(ticket)
                if (p.currentMediaItem?.queueEntryId != expected) return@launch
                DjPlayback.mutable.update { it.copy(loadingStage = DjLoadingStage.BUILDING) }
                val committed = if (replace) setOfNotNull(current?.videoId) else
                    (p.currentMediaItemIndex until p.mediaItemCount).map { p.getMediaItemAt(it).mediaId }.toSet()
                val ready = select(committed, chapter)
                check(ready.isNotEmpty()) { "I'm having trouble finding the next songs. Your queued music can keep playing; try again in a moment." }
                if (replace) p.removeMediaItems(p.currentMediaItemIndex + 1, p.mediaItemCount)
                p.addMediaItems(ready.map { item(it.song, chapter) }); chaptersPlanned++
                if (advance || p.playbackState == Player.STATE_ENDED) { p.seekToNextMediaItem(); p.prepare(); p.play() }
                if (replace && advance) DjPlayback.openPlayer.value = true
                retryAt = 0; DjPlayback.mutable.update { it.copy(message = "I've got your next songs", loadingStage = DjLoadingStage.READY) }
            } catch (c: CancellationException) { throw c }
            catch (failure: Exception) { if (ticket == sessionId) {
                retryAt = SystemClock.elapsedRealtime() + 30_000
                DjPlayback.mutable.update { it.copy(error = failure.message ?: "Couldn't get the next songs.") }
            } } finally { if (ticket == sessionId) DjPlayback.mutable.update { it.copy(loading = false, foregroundLoading = false) } }
        }
    }
    fun like() {
        val song = current ?: return
        if (learnedLike) return
        brain.learn(metadata(song), "like", 0, currentDuration.coerceAtLeast(1))
        learnedLike = true; anchor = song; discovery.observe(song, 1.5); saveTaste()
        DjPlayback.mutable.update { it.copy(liked = true, events = brain.taste.events) }
    }
    fun playTrack(trackId: String) {
        DjPlayback.state.value.suggestions.firstOrNull { it.videoId == trackId }?.let { song ->
            start(genres = "${song.title} ${song.artist}", intent = MusicIntent(query = "${song.title} ${song.artist}"),
                preferredIds = setOf(trackId), requestText = "Play ${song.title}", preserveConversation = true, openingSong = song)
            return
        }
        val p = player() ?: return
        val index = (0 until p.mediaItemCount).firstOrNull {
            entries[p.getMediaItemAt(it).queueEntryId]?.first?.videoId == trackId
        } ?: return
        interrupt()
        if (index == p.currentMediaItemIndex && p.isPlaying) p.pause()
        else {
            if (index != p.currentMediaItemIndex) p.seekTo(index, 0L)
            if (p.playbackState == Player.STATE_IDLE || p.playbackState == Player.STATE_ENDED) p.prepare()
            p.play()
            DjPlayback.openPlayer.value = true
        }
    }
    fun pick() {
        if (!DjPlayback.state.value.active) { start(preserveConversation = true); return }
        if (operation?.isActive == true) { queuedRequest = "let dj pick"; return }
        requestQuery = null; plannedIds = emptySet(); say("Leave it with me. Let's try a different direction.")
        fillQueue(replace = true, advance = true)
    }
    fun request(text: String, record: Boolean = true) {
        val original = text.trim().take(300)
        if (original.isBlank()) return
        conversationOperation?.cancel(); conversationOperation = null
        interrupt()
        if (record) addMessage(original, true)
        val turn = DjConversation.route(original, DjPlayback.state.value.suggestedQuery, listenerName())
        turn.name?.let { prefs.edit().putString("name:${AndroidLocalMixes.scope()}", it).apply() }
        DjPlayback.mutable.update { it.copy(listenerName = listenerName(), error = null, replyPending = false) }
        if (turn.waitForPlayback) applyControl(MusicAction.PAUSE)
        if (turn.kind == DjConversation.Kind.CHAT) { converse(original, turn.reply); return }
        if (turn.kind == DjConversation.Kind.SUGGEST) { converse(original, turn.reply, turn.query); return }
        val musicRequest = if (turn.kind == DjConversation.Kind.PLAY) "play ${turn.query}".trim() else original
        val command = DjBrain.normalize(musicRequest)
        val local = LocalMusicIntent.parse(musicRequest)
        if (turn.kind == DjConversation.Kind.PLAY && turn.query.isBlank()) { pick(); return }
        if (local.action != MusicAction.PLAY) { applyControl(local.action); return }
        conversationOperation?.cancel()
        suggestionOperation?.cancel()
        if (operation?.isActive == true) {
            queuedRequest = original
            if (record) addMessage("Got it. I'll use that as soon as these songs are ready.")
            return
        }
        val listener = AndroidLocalMixes.scope()
        val ticket = sessionId
        val actual = (inventory.map { it.song } + listOfNotNull(current, player()?.currentMediaItem?.toSong())).distinctBy { it.videoId }
        val preferred = brain.taste.global.filter { it.key.startsWith("1:") && it.value.alpha > it.value.beta }
            .entries.sortedByDescending { it.value.alpha / (it.value.alpha + it.value.beta) }.take(8).map { it.key.removePrefix("1:") }
        operation = scope.launch {
            val job = currentCoroutineContext()[Job]
            try {
                DjPlayback.mutable.update { it.copy(loading = true, foregroundLoading = true,
                    loadingStage = DjLoadingStage.ANALYZING, requestDescription = original, error = null) }
                val saved = intelligence.libraryContext()
                val plan = intelligence.plan(musicRequest, saved, actual, preferred)
                ensureActive()
                if (listener != AndroidLocalMixes.scope() || ticket != sessionId || remotePlayback()) return@launch
                libraryContext = saved
                val label = if (plan.source == "model") "Connected AI · music companion" else "Your music companion"
                DjPlayback.mutable.update { it.copy(intelligenceLabel = label) }
                operation = null
                if (plan.intent.action != MusicAction.PLAY) {
                    DjPlayback.mutable.update { it.copy(loading = false, foregroundLoading = false) }
                    applyControl(plan.intent.action)
                    return@launch
                }
                var intent = plan.intent
                if (plan.source == "rules") intent = intent.copy(query = when {
                    command.contains("more energy") || command == "upbeat" -> "upbeat energetic songs"
                    command.contains("calmer") || command == "chill" || command.contains("less energy") -> "chill mellow songs"
                    else -> intent.query
                })
                if (!DjPlayback.state.value.active) {
                    start(genres = intent.query, intent = intent, intelligenceLabel = label,
                        preferredIds = plan.trackIds.toSet(), requestText = original, preserveConversation = true)
                    return@launch
                }
                interrupt()
                selectionIntent = intent.copy(
                    libraryOnly = selectionIntent.libraryOnly || intent.libraryOnly,
                    excludePlayedToday = selectionIntent.excludePlayedToday || intent.excludePlayedToday,
                    excludedArtists = (selectionIntent.excludedArtists + intent.excludedArtists).distinct(),
                )
                brain.excludedArtists += selectionIntent.excludedArtists.map(DjBrain::normalize)
                requestQuery = intent.query.takeIf { it.isNotBlank() }
                plannedIds = plan.trackIds.toSet()
                DjPlayback.mutable.update { it.copy(loading = false, foregroundLoading = false) }
                say(if (requestQuery != null) "I'm finding $requestQuery for your next songs."
                    else "I'll use your listening and saved music, respecting those exclusions.")
                fillQueue(replace = true, advance = true)
            } catch (cancelled: CancellationException) { throw cancelled }
            catch (_: Exception) {
                DjPlayback.mutable.update { it.copy(error = "I couldn't interpret that request. Your queued music is unchanged; try a song or artist name.") }
            } finally {
                if (operation === job) {
                    operation = null
                    DjPlayback.mutable.update { it.copy(loading = false, foregroundLoading = false) }
                }
            }
        }
    }
    private fun converse(original: String, fallback: String, fallbackQuery: String? = null) {
        conversationOperation?.cancel()
        val profile = AndroidLocalMixes.scope()
        val history = DjPlayback.state.value.messages.takeLast(12)
        // Give local replies immediately; a configured model can replace this one bubble.
        val message = DjMessage(fallback)
        DjPlayback.mutable.update { it.copy(messages = (it.messages + message).takeLast(40), replyPending = true) }
        conversationOperation = scope.launch {
            val thisJob = currentCoroutineContext()[Job]
            try {
            val response = intelligence.converse(original, history, listenerName(),
                (current ?: player()?.currentMediaItem?.toSong())?.let { "${it.title} by ${it.artist}" },
                fallbackQuery ?: DjPlayback.state.value.suggestedQuery)
            ensureActive()
            if (profile != AndroidLocalMixes.scope()) return@launch
            if (response == null || response.source != "model") {
                speakReply(fallback); fallbackQuery?.let { suggest(it, "") }; return@launch
            }
            DjPlayback.mutable.update { state -> state.copy(
                messages = state.messages.map { if (it.id == message.id) it.copy(text = response.reply) else it },
                intelligenceLabel = "Connected AI · music companion",
            ) }
            speakReply(response.reply)
            (response.query ?: fallbackQuery)?.takeIf { it.isNotBlank() }?.let { suggest(it, "") }
            } finally {
                if (conversationOperation === thisJob) DjPlayback.mutable.update { it.copy(replyPending = false) }
            }
        }
    }
    private fun suggest(query: String, reply: String) {
        suggestionOperation?.cancel()
        if (reply.isNotBlank()) { addMessage(reply); speakReply(reply) }
        val profile = AndroidLocalMixes.scope()
        DjPlayback.mutable.update { it.copy(suggestedQuery = query, suggestions = emptyList(), error = null) }
        suggestionOperation = scope.launch {
            val songs = discovery.search(query).filter { it.videoId !in LikeState.overrides.value.filterValues { value -> value == LikeStatus.DISLIKE }.keys }.take(5)
            ensureActive()
            if (profile != AndroidLocalMixes.scope()) return@launch
            DjPlayback.mutable.update { it.copy(suggestions = songs) }
            if (songs.isEmpty()) addMessage("I couldn't find a match just now. Try another artist or a more specific mood. Your music hasn't changed.")
        }
    }
    private fun applyControl(action: MusicAction) {
        if (action == MusicAction.PAUSE) {
            operation?.cancel(); operation = null; queuedRequest = null
            DjPlayback.mutable.update { it.copy(loading = false, foregroundLoading = false,
                active = it.active && ticker?.isActive == true, loadingStage = DjLoadingStage.IDLE) }
        }
        if (action == MusicAction.EXPLAIN) {
            val song = current
            if (song == null) { say("Start a DJ set first and I can explain the music I choose."); return }
            val origin = inventory.firstOrNull { it.song.videoId == song.videoId }?.origin
            val reason = when (origin) {
                DjOrigin.REQUEST -> "It came from the current music search."
                DjOrigin.LIKED -> "It is in your saved music or likes."
                DjOrigin.HISTORY -> "It came from your listening history."
                DjOrigin.RADIO -> "Your music provider connected it to an earlier song."
                DjOrigin.DOWNLOAD -> "It is an available downloaded song."
                DjOrigin.FOR_YOU -> "Your music provider recommended it."
                else -> "It came from the available discovery inventory."
            }
            say("$reason I also consider artist feedback, freshness and variety; I have no measured audio features for this track.")
            return
        }
        val p = player() ?: return
        interrupt()
        when (action) {
            MusicAction.PAUSE -> { p.pause(); addMessage("Paused. I'm right here when you're ready.") }
            MusicAction.RESUME -> { p.play(); addMessage("Let's keep going.") }
            MusicAction.NEXT -> if (p.hasNextMediaItem()) p.seekToNextMediaItem() else if (DjPlayback.state.value.active) fillQueue(advance = true)
            MusicAction.PREVIOUS -> p.seekToPrevious()
            MusicAction.REPLAY -> {
                current?.let { brain.learn(metadata(it), "replay", 0, currentDuration.coerceAtLeast(1)); saveTaste() }
                p.seekTo(0); p.play()
            }
            else -> Unit
        }
    }
    private fun searchQuery(text: String) = text.replace(Regex("^(please\\s+)?(play|put on|give me|i want|find me|more)\\s+", RegexOption.IGNORE_CASE), "").trim()
    fun setVoice(enabled: Boolean) { DjPlayback.mutable.update { it.copy(voiceEnabled = enabled) }; if (!enabled) interrupt() }
    fun interrupt() = cancelSpeech()
    private fun addMessage(text: String, user: Boolean = false) { DjPlayback.mutable.update { it.copy(messages = (it.messages + DjMessage(text, user)).takeLast(40)) } }
    private fun say(text: String) { addMessage(text); pendingSpeech = text; speechDeadline = SystemClock.elapsedRealtime() + 12_000 }
    /** Conversation voice never resumes music or creates a queue. */
    private fun speakReply(text: String) {
        pendingSpeech = null
        pendingReplySpeech = null
        if (!DjPlayback.state.value.voiceEnabled) return
        if (!ttsReady && !GnaniTtsEngine.isConfigured()) {
            pendingReplySpeech = text to (SystemClock.elapsedRealtime() + 12_000)
            return
        }
        speak(text, requiresMusic = false)
    }
    private fun speak(text: String, requiresMusic: Boolean = true) {
        if (!DjPlayback.state.value.voiceEnabled) return
        if (!ttsReady && !GnaniTtsEngine.isConfigured()) return
        val p = player()
        if (requiresMusic && p?.isPlaying != true) return
        cancelSpeech(clearPending = false)
        duckJob?.cancel()
        releaseVoiceVolume()
        if (p != null && p.isPlaying) {
            voicePlayer = p; duckGain = DjDuckingGain(p.volume); p.addListener(volumeListener)
        }
        speechRequiresMusic = requiresMusic
        utterance = UUID.randomUUID().toString()
        val id = utterance
        speechEnvelope = null
        DjPlayback.mutable.update { it.copy(ducking = true, speechText = text, speechRangeStart = 0, speechRangeEnd = 0) }
        duckJob = scope.launch {
            // Keep the music at normal volume while the network prepares the voice.
            val wavFile = if (GnaniTtsEngine.isConfigured()) GnaniTtsEngine.synthesize(context, text) else null
            ensureActive()
            if (id != utterance) return@launch
            if (requiresMusic && player()?.isPlaying != true) { cancelSpeech(); return@launch }
            rampVoiceGain(.22f, 250)
            if (id != utterance) return@launch
            if (wavFile != null && wavFile.exists()) playGnaniAudio(wavFile, id, text)
            else playDeviceSpeech(text, id)
        }
    }

    private fun playDeviceSpeech(text: String, id: String) {
        if (id != utterance) return
        if (!ttsReady) { finishSpeech(); return }
        tts?.setAudioAttributes(AudioAttributes.Builder().setUsage(AudioAttributes.USAGE_MEDIA)
            .setContentType(AudioAttributes.CONTENT_TYPE_SPEECH).build())
        tts?.setSpeechRate(1f)
        if (tts?.speak(text, TextToSpeech.QUEUE_FLUSH, null, id) != TextToSpeech.SUCCESS) finishSpeech()
        else speechTimeout = scope.launch { delay(12_000); if (id == utterance) cancelSpeech() }
    }

    private suspend fun playGnaniAudio(wavFile: java.io.File, id: String, fallbackText: String) {
        try {
            val envelope = withContext(Dispatchers.IO) { GnaniTtsEngine.extractEnvelope(wavFile) }
            if (id != utterance) return
            gnaniSpeechPlayer?.release()
            val mp = MediaPlayer()
            gnaniSpeechPlayer = mp
            speechEnvelope = envelope
            mp.setAudioAttributes(AudioAttributes.Builder().setUsage(AudioAttributes.USAGE_MEDIA)
                .setContentType(AudioAttributes.CONTENT_TYPE_SPEECH).build())
            mp.setOnPreparedListener {
                if (id != utterance || gnaniSpeechPlayer !== mp) { runCatching { mp.release() }; return@setOnPreparedListener }
                mp.start()
                DjPlayback.mutable.update { it.copy(speaking = true, ducking = false) }
                amplitudeJob?.cancel()
                amplitudeJob = scope.launch {
                    while (isActive && id == utterance && gnaniSpeechPlayer === mp && mp.isPlaying) {
                        DjPlayback.mutableVoiceLevel.value = speechEnvelope?.levelAt(mp.currentPosition.toLong()) ?: 0f
                        delay(1_000L / DjSpeechEnvelope.FRAMES_PER_SECOND)
                    }
                }
                speechTimeout?.cancel()
                speechTimeout = scope.launch {
                    delay((mp.duration + 1500).toLong().coerceIn(3000L, 30_000L))
                    if (id == utterance) cancelSpeech()
                }
            }
            mp.setOnCompletionListener {
                scope.launch { if (id == utterance) finishSpeech() }
            }
            mp.setOnErrorListener { _, _, _ ->
                scope.launch { if (id == utterance && gnaniSpeechPlayer === mp) {
                    gnaniSpeechPlayer = null; mp.release(); playDeviceSpeech(fallbackText, id)
                } }
                true
            }
            mp.setDataSource(context, Uri.fromFile(wavFile))
            mp.prepareAsync()
            speechTimeout = scope.launch {
                delay(8_000)
                if (id == utterance) cancelSpeech()
            }
        } catch (cancelled: CancellationException) {
            throw cancelled
        } catch (e: Exception) {
            android.util.Log.w("VikifyDJ", "Gnani audio playback failed: ${e.javaClass.simpleName}")
            if (id == utterance) {
                gnaniSpeechPlayer?.let { runCatching { it.release() } }; gnaniSpeechPlayer = null
                playDeviceSpeech(fallbackText, id)
            }
        }
    }
    private fun finishSpeech() {
        gnaniSpeechPlayer?.let { runCatching { if (it.isPlaying) it.stop(); it.release() }; gnaniSpeechPlayer = null }
        speechTimeout?.cancel(); speechTimeout = null
        amplitudeJob?.cancel(); amplitudeJob = null
        DjPlayback.mutableVoiceLevel.value = 0f
        utterance = ""; speechEnvelope = null
        DjPlayback.mutable.update { it.copy(speaking = false, ducking = false, speechText = null, speechRangeStart = 0, speechRangeEnd = 0) }
        duckJob?.cancel()
        duckJob = if (voicePlayer != null) scope.launch {
            rampVoiceGain(1f, 450)
            releaseVoiceVolume()
        } else null
    }
    private fun applyVoiceVolume() {
        val gain = duckGain ?: return
        val p = voicePlayer ?: return
        if (p.volume == gain.volume) return
        ownedVolumeWrites.addLast(gain.volume)
        p.volume = gain.volume
    }
    private suspend fun rampVoiceGain(target: Float, durationMs: Long) {
        val gain = duckGain ?: return
        val from = gain.gain
        val started = SystemClock.elapsedRealtime()
        while (true) {
            val progress = ((SystemClock.elapsedRealtime() - started).toFloat() / durationMs).coerceIn(0f, 1f)
            gain.setGain(from + (target - from) * progress); applyVoiceVolume()
            if (progress >= 1f) return
            delay(16)
        }
    }
    private fun releaseVoiceVolume() {
        val p = voicePlayer
        p?.removeListener(volumeListener)
        ownedVolumeWrites.clear()
        duckGain?.let { p?.volume = it.baseVolume }
        voicePlayer = null; duckGain = null
    }
    private fun cancelSpeech(clearPending: Boolean = true) { if (clearPending) { pendingSpeech = null; pendingReplySpeech = null }; gnaniSpeechPlayer?.let { runCatching { if (it.isPlaying) it.stop(); it.release() }; gnaniSpeechPlayer = null }; tts?.stop(); finishSpeech() }
    private fun saveTaste() { if (profileScope.isNotBlank()) prefs.edit().putString(profileScope, json.encodeToString(brain.taste)).apply() }
    private fun checkScope(ticket: String) { check(sessionId == ticket && profileScope == AndroidLocalMixes.scope() && !remotePlayback()) { "Your playback or profile changed. Start your DJ again." } }
    private fun remotePlayback() = ListenTogether.state.value.inParty || com.music.vikify.playback.cast.CastController.state.value.casting
    fun stop(announce: Boolean = true, restoreQueue: Boolean = true) {
        DjPlayback.openPlayer.value = false
        operation?.cancel(); operation = null; conversationOperation?.cancel(); conversationOperation = null
        suggestionOperation?.cancel(); suggestionOperation = null
        ticker?.cancel(); ticker = null; interrupt(); finishTrack("progress")
        val p = player(); val snapshot = queueSnapshot; queueSnapshot = null
        if (restoreQueue && p != null && snapshot != null && snapshot.player === p && ownsDjQueue(sessionId, p.currentMediaItem?.queueEntryId,
                (0 until p.mediaItemCount).map { p.getMediaItemAt(it).queueEntryId })) {
            // The bounded projection depends on repeat mode; restore it before rebuilding the context.
            p.repeatMode = snapshot.repeat; p.shuffleModeEnabled = snapshot.shuffled
            val boundedRestored = snapshot.bounded?.let { restoreBoundedQueue(p, it) } == true
            if (!boundedRestored) {
                if (snapshot.items.isEmpty()) p.clearMediaItems()
                else p.setMediaItems(snapshot.items, snapshot.index.coerceIn(snapshot.items.indices), snapshot.position)
            }
            if (p.mediaItemCount > 0) {
                p.prepare(); p.playWhenReady = snapshot.playing
            }
        }
        observedEntry = null; entries.clear(); played.clear(); current = null; anchor = null; queuedRequest = null
        inventory = emptyList(); requested = emptyList(); discovery = newDiscovery(); chaptersPlanned = 0; observedChapter = -1; retryAt = 0
        DjPlayback.mutable.update { it.copy(active = false, loading = false, speaking = false, foregroundLoading = false,
            replyPending = false,
            loadingStage = DjLoadingStage.IDLE, current = null, song = null, playing = false,
            upcoming = emptyList(), message = "DJ paused. You're back in control.") }
        if (announce) addMessage("You're back in control. Tap Let DJ pick whenever you want me back.")
    }
    override fun close() {
        stop(announce = false); duckJob?.cancel(); releaseVoiceVolume()
        tts?.shutdown(); tts = null; ttsReady = false
    }
    private fun metadata(song: Song) = DjTrack(song.videoId, song.title, song.artist, "unknown", era = "unknown",
        bpm = 0.0, key = "", durationMs = song.durationMillis().takeIf { it > 0 } ?: 240_000, vector = DjVector(), hasAcoustics = false)
}
