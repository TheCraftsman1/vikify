package com.music.vikify.dj

import android.app.Activity
import android.content.Intent
import android.graphics.drawable.ColorDrawable
import android.os.Bundle
import android.speech.RecognizerIntent
import androidx.activity.compose.BackHandler
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.BasicTextField
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.text.input.InputTransformation
import androidx.compose.foundation.text.input.TextFieldLineLimits
import androidx.compose.foundation.text.input.TextFieldState
import androidx.compose.foundation.text.input.clearText
import androidx.compose.foundation.text.input.maxLength
import androidx.compose.foundation.text.input.rememberTextFieldState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.rounded.ArrowBack
import androidx.compose.material.icons.rounded.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.drawBehind
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.focus.focusRequester
import androidx.compose.ui.geometry.CornerRadius
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.SolidColor
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.graphics.toArgb
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.hapticfeedback.HapticFeedbackType
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.platform.LocalHapticFeedback
import androidx.compose.ui.platform.LocalSoftwareKeyboardController
import androidx.compose.ui.platform.LocalView
import androidx.compose.ui.text.SpanStyle
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.buildAnnotatedString
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.window.Dialog
import androidx.compose.ui.window.DialogProperties
import androidx.compose.ui.window.DialogWindowProvider
import androidx.core.view.WindowCompat
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.media3.session.MediaController
import androidx.media3.session.SessionCommand
import androidx.media3.session.SessionResult
import coil3.compose.AsyncImage
import com.music.vikify.data.listentogether.ListenTogether
import com.music.vikify.data.model.Song
import com.music.vikify.data.model.artworkAt
import com.music.vikify.data.settings.AppSettings
import dev.chrisbanes.haze.HazeState
import dev.chrisbanes.haze.hazeSource
import kotlinx.coroutines.flow.distinctUntilChanged
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.guava.await
import kotlinx.coroutines.launch

private enum class DjPage { LANDING, CHAT }

/** One request shortcut or session action in the DJ options menu. */
private class DjShortcut(val label: String, val icon: ImageVector, val action: () -> Unit)

/** Full-screen studio: intentional navigation, never a layer intercepting Home scroll. */
@Composable
fun DjConversationSheet(controller: MediaController?, name: String, onDismiss: () -> Unit,
    bottomBar: (@Composable (HazeState) -> Unit)? = null) {
    Dialog(onDismissRequest = onDismiss, properties = DialogProperties(
        usePlatformDefaultWidth = false, decorFitsSystemWindows = false,
    )) {
        val view = LocalView.current
        DisposableEffect(view) {
            val window = (view.parent as? DialogWindowProvider)?.window
            window?.setBackgroundDrawable(ColorDrawable(DjStyle.background.toArgb()))
            if (window != null) WindowCompat.getInsetsController(window, view).apply {
                isAppearanceLightStatusBars = false; isAppearanceLightNavigationBars = false
            }
            onDispose { }
        }
        DjStudio(controller, name, Modifier.fillMaxSize().background(DjStyle.backdrop)
            .statusBarsPadding(), onDismiss, bottomBar)
    }
}

@Composable
fun DjScreen(controller: MediaController?, contentPadding: PaddingValues) {
    DjStudio(controller, ListenTogether.nickname(), Modifier.fillMaxSize().background(DjStyle.backdrop)
        .padding(contentPadding).consumeWindowInsets(contentPadding))
}

@Composable
private fun DjStudio(controller: MediaController?, name: String, modifier: Modifier,
    onDismiss: (() -> Unit)? = null, bottomBar: (@Composable (HazeState) -> Unit)? = null) {
    val state by remember { DjPlayback.state.map { it.copy(listenedMs = 0, events = 0) }.distinctUntilChanged() }
        .collectAsStateWithLifecycle(initialValue = DjPlayback.state.value)
    val reduced by AppSettings.reduceAnimation.collectAsStateWithLifecycle()
    val scope = rememberCoroutineScope()
    var page by rememberSaveable { mutableStateOf(DjPage.LANDING) }
    val draft = rememberTextFieldState()
    var prompt by rememberSaveable { mutableStateOf<String?>(null) }
    var focusComposer by remember { mutableStateOf(false) }
    var listening by remember { mutableStateOf(false) }
    var error by remember { mutableStateOf<String?>(null) }
    var menu by remember { mutableStateOf(false) }
    var activity by remember { mutableStateOf(false) }
    var preferences by remember { mutableStateOf(false) }
    val focus = remember { FocusRequester() }
    val keyboard = LocalSoftwareKeyboardController.current
    val haptic = LocalHapticFeedback.current
    val navigationColors = MaterialTheme.colorScheme
    val navigationHazeState = remember { HazeState() }
    val ime = WindowInsets.ime
    val density = LocalDensity.current
    val imeVisible by remember(ime, density) { derivedStateOf { ime.getBottom(density) > 0 } }
    val crafting = page == DjPage.CHAT && state.loading && state.foregroundLoading

    fun send(operation: String, text: String = "", enabled: Boolean = true) {
        val c = controller ?: run { error = "The player is connecting. Try again in a moment."; return }
        error = null
        scope.launch {
            try {
                val result = c.sendCustomCommand(SessionCommand(ACTION_DJ, Bundle.EMPTY), Bundle().apply {
                    putString("operation", operation); putString("text", text); putString("name", name)
                    putBoolean("enabled", enabled)
                    putBoolean("voice", state.voiceEnabled)
                }).await()
                if (result.resultCode != SessionResult.RESULT_SUCCESS) error = "I couldn't accept that just now. Try again."
            } catch (c: kotlinx.coroutines.CancellationException) { throw c }
            catch (_: Exception) { error = "The player disconnected. Reopen DJ AI and try again." }
        }
    }
    fun chat(message: String? = null, focusInput: Boolean = false) {
        page = DjPage.CHAT; prompt = message; focusComposer = focusInput
    }
    LaunchedEffect(draft) {
        snapshotFlow { draft.text.isNotBlank() }.collect { hasText ->
            if (hasText && page == DjPage.LANDING) chat()
        }
    }
    fun pick() { haptic.performHapticFeedback(HapticFeedbackType.LongPress); chat(); keyboard?.hide(); send("pick") }
    LaunchedEffect(controller) { if (controller != null) send("open") }
    fun submit() {
        val request = draft.text.toString().trim()
        if (request.isBlank()) return
        draft.clearText(); prompt = null; focusComposer = false; keyboard?.hide()
        page = DjPage.CHAT // Requests typed on the landing open the conversation.
        send("request", request)
    }
    BackHandler(page != DjPage.LANDING) { page = DjPage.LANDING; keyboard?.hide() }
    LaunchedEffect(page, crafting, focusComposer) {
        if (page == DjPage.CHAT && !crafting && focusComposer) { focus.requestFocus(); keyboard?.show(); focusComposer = false }
    }
    val recognition = rememberLauncherForActivityResult(ActivityResultContracts.StartActivityForResult()) { result ->
        listening = false
        if (result.resultCode == Activity.RESULT_OK) result.data?.getStringArrayListExtra(RecognizerIntent.EXTRA_RESULTS)
            ?.firstOrNull()?.takeIf { it.isNotBlank() }?.let { prompt = null; page = DjPage.CHAT; send("request", it) }
    }
    fun listen() {
        send("interrupt"); listening = true
        try {
            recognition.launch(Intent(RecognizerIntent.ACTION_RECOGNIZE_SPEECH).apply {
                putExtra(RecognizerIntent.EXTRA_LANGUAGE_MODEL, RecognizerIntent.LANGUAGE_MODEL_FREE_FORM)
                putExtra(RecognizerIntent.EXTRA_PROMPT, "What would you like to hear?")
            })
        } catch (_: Exception) { listening = false; error = "Voice input isn't available. Type your request here." }
    }
    val emotion = when {
        listening -> DjEmotion.LISTENING
        state.phase == DjPhase.SPEAKING -> DjEmotion.SPEAKING
        state.phase == DjPhase.DUCKING -> DjEmotion.LISTENING
        state.phase == DjPhase.THINKING -> DjEmotion.THINKING
        else -> DjEmotion.HAPPY
    }
    // Request shortcuts share the DJ options menu, keeping the composer focused on input.
    val landingShortcuts = listOf(
        DjShortcut("Make a playlist", Icons.Rounded.AutoAwesome) { chat("What mood should I build your playlist around?", true) },
        DjShortcut("Music discovery", Icons.Rounded.GraphicEq) { pick() },
        DjShortcut("Based on your taste", Icons.Rounded.Album) { pick() },
        DjShortcut("Talk to DJ", Icons.Rounded.Headphones) { chat(focusInput = true) },
    )
    val chatShortcuts = listOf(
        DjShortcut("Let DJ pick", Icons.Rounded.AutoAwesome) { pick() },
        DjShortcut("More energy", Icons.Rounded.Bolt) { send("request", "More energy") },
        DjShortcut("Calmer", Icons.Rounded.Spa) { send("request", "Calmer") },
        DjShortcut("Something new", Icons.Rounded.Explore) { pick() },
    )
    // Activity and preferences moved from header icons into the DJ AI pill / overflow menu.
    val options = buildList {
        addAll(if (page == DjPage.LANDING) landingShortcuts else chatShortcuts)
        add(DjShortcut(if (state.voiceEnabled) "Mute spoken replies" else "Enable spoken replies",
            if (state.voiceEnabled) Icons.Rounded.VoiceOverOff else Icons.Rounded.RecordVoiceOver) { send("voice", enabled = !state.voiceEnabled) })
        add(DjShortcut("DJ activity", Icons.Rounded.NotificationsNone) { activity = true })
        add(DjShortcut("Preferences", Icons.Rounded.Tune) { preferences = true })
        if (state.active) add(DjShortcut("End DJ session", Icons.Rounded.StopCircle) { send("stop") })
    }
    val theme = darkColorScheme(
        primary = DjStyle.lavender, onPrimary = DjStyle.background,
        background = DjStyle.background, onBackground = DjStyle.text,
        surface = DjStyle.surface, onSurface = DjStyle.text, onSurfaceVariant = Color(0xFFB3BFE3),
        surfaceContainer = DjStyle.surface, surfaceContainerHigh = DjStyle.raised,
        surfaceContainerHighest = Color(0xFF1F2E5C), outline = Color(0xFF5A6A99), outlineVariant = DjStyle.glassBorder,
    )
    MaterialTheme(colorScheme = theme) {
        // The app bar owns its bottom inset; chat and crafting own theirs.
        Column(modifier.then(if (page != DjPage.LANDING || bottomBar == null) Modifier.navigationBarsPadding() else Modifier)) {
            if (page == DjPage.LANDING) {
                Row(Modifier.fillMaxWidth().height(64.dp).padding(start = 28.dp, end = 20.dp), verticalAlignment = Alignment.CenterVertically) {
                    Text("VIKIFY", Modifier.weight(1f), color = DjStyle.brand,
                        fontSize = 22.sp, fontWeight = FontWeight.ExtraBold, letterSpacing = 1.2.sp)
                    Box {
                        DjBetaTitle(onClick = { menu = true })
                        DjMenu(menu, { menu = false }, options)
                    }
                }
            } else {
                Row(Modifier.fillMaxWidth().height(64.dp).padding(horizontal = 6.dp), verticalAlignment = Alignment.CenterVertically) {
                    IconButton(onClick = { page = DjPage.LANDING; keyboard?.hide() }) {
                        Icon(Icons.AutoMirrored.Rounded.ArrowBack, "Back to DJ AI", tint = DjStyle.text)
                    }
                    Box(Modifier.weight(1f), contentAlignment = Alignment.Center) { DjBetaTitle() }
                    Box {
                        IconButton(onClick = { menu = true }) { Icon(Icons.Rounded.MoreHoriz, "DJ options", tint = DjStyle.text) }
                        DjMenu(menu, { menu = false }, options)
                    }
                }
            }
            if (crafting) {
                DjCrafting(Modifier.weight(1f), state, reduced)
            } else {
                Column(Modifier.weight(1f).fillMaxWidth().imePadding()) {
                    if (page == DjPage.LANDING) {
                        DjLanding(Modifier.weight(1f).hazeSource(navigationHazeState), emotion, state, reduced,
                            onMascot = { chat(focusInput = true) })
                    } else {
                        DjChatContent(Modifier.weight(1f), state, prompt, emotion, reduced,
                            onPlay = { send("play-track", it) }, onPick = { pick() },
                            onPlayMix = { send("request", "play them") })
                    }
                    DjNotice(error ?: state.error, listening)
                    if (page == DjPage.LANDING) {
                        Button(onClick = { pick() }, enabled = controller != null && !state.loading,
                            modifier = Modifier.fillMaxWidth().padding(horizontal = 28.dp, vertical = 8.dp).heightIn(min = 52.dp),
                            colors = ButtonDefaults.buttonColors(containerColor = DjStyle.cyan, contentColor = DjStyle.ink),
                            shape = RoundedCornerShape(18.dp)) {
                            Icon(Icons.Rounded.AutoAwesome, null, Modifier.size(19.dp))
                            Spacer(Modifier.width(10.dp))
                            Text(if (state.active) "Explore a new vibe" else "Dive in", fontWeight = FontWeight.SemiBold)
                        }
                        Text("Recent favorites + songs you'll connect with", color = DjStyle.muted,
                            modifier = Modifier.fillMaxWidth().padding(bottom = 14.dp, start = 20.dp, end = 20.dp),
                            textAlign = TextAlign.Center, fontSize = 12.sp)
                    }
                    // Keep the same field mounted when the first character opens
                    // chat, preserving the IME connection, selection and composition.
                    DjComposer(draft, focus, listening, enabled = controller != null,
                        onSend = { submit() }, onMic = { listen() })
                    Spacer(Modifier.height(if (page == DjPage.LANDING) {
                        if (bottomBar != null && !imeVisible) 12.dp else 20.dp
                    } else 16.dp))
                }
                if (page == DjPage.LANDING && !imeVisible) {
                    if (bottomBar != null) MaterialTheme(colorScheme = navigationColors) { bottomBar(navigationHazeState) }
                    else if (onDismiss != null) TextButton(onClick = onDismiss, modifier = Modifier.align(Alignment.CenterHorizontally)) {
                        Text("Back to music", color = DjStyle.muted)
                    }
                }
            }
        }
        if (activity) AlertDialog(onDismissRequest = { activity = false }, title = { Text("DJ activity") },
            text = { Column(verticalArrangement = Arrangement.spacedBy(12.dp)) {
                val messages = state.messages.filterNot { it.fromUser }.takeLast(4)
                if (messages.isEmpty()) Text("Your DJ activity will appear here after you start a conversation.")
                else messages.forEach { Text(it.text, style = MaterialTheme.typography.bodyMedium) }
            } }, confirmButton = { TextButton(onClick = { activity = false }) { Text("Done") } })
        if (preferences) AlertDialog(onDismissRequest = { preferences = false }, title = { Text(name.takeIf { it.isNotBlank() } ?: "Your DJ") },
            text = { Column(Modifier.heightIn(max = 480.dp).verticalScroll(rememberScrollState()), verticalArrangement = Arrangement.spacedBy(16.dp)) {
                Text("Music that gets to know you as you listen.", color = DjStyle.muted)
                Text(state.intelligenceLabel, color = DjStyle.cyan, style = MaterialTheme.typography.bodySmall)
                NemotronPreferencesContent()
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Text("Spoken replies", Modifier.weight(1f))
                    Switch(state.voiceEnabled, { send("voice", enabled = it) })
                }
                androidx.compose.material3.HorizontalDivider(color = DjStyle.muted.copy(alpha = 0.2f))
                Text("AI voice · Gnani Timbre (${AppSettings.gnaniTtsVoice.value})",
                    style = MaterialTheme.typography.labelMedium, color = DjStyle.lavender)
                var testing by remember { mutableStateOf(false) }
                var previewPlayer by remember { mutableStateOf<android.media.MediaPlayer?>(null) }
                var previewError by remember { mutableStateOf<String?>(null) }
                val voiceStatus by GnaniTtsEngine.status.collectAsStateWithLifecycle()
                val previewScope = rememberCoroutineScope()
                val ctx = androidx.compose.ui.platform.LocalContext.current
                DisposableEffect(Unit) { onDispose { previewPlayer?.release() } }
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Text("Hear your DJ", Modifier.weight(1f), style = MaterialTheme.typography.bodySmall, color = DjStyle.muted)
                    TextButton(enabled = !testing && GnaniTtsEngine.isConfigured(), onClick = {
                        testing = true; previewError = null
                        previewScope.launch {
                            val audio = GnaniTtsEngine.synthesize(ctx, "Hello! I am your Vikify DJ.")
                            if (audio == null) { testing = false; return@launch }
                            try {
                                previewPlayer?.release()
                                val player = android.media.MediaPlayer()
                                previewPlayer = player
                                player.setAudioAttributes(android.media.AudioAttributes.Builder()
                                    .setUsage(android.media.AudioAttributes.USAGE_MEDIA)
                                    .setContentType(android.media.AudioAttributes.CONTENT_TYPE_SPEECH).build())
                                player.setOnPreparedListener { it.start() }
                                player.setOnCompletionListener { it.release(); previewPlayer = null; testing = false }
                                player.setOnErrorListener { failed, _, _ ->
                                    failed.release(); previewPlayer = null; testing = false
                                    previewError = "Your device could not play the voice preview. Try again."
                                    true
                                }
                                player.setDataSource(ctx, android.net.Uri.fromFile(audio))
                                player.prepareAsync()
                            } catch (_: Exception) {
                                previewPlayer?.release(); previewPlayer = null; testing = false
                                previewError = "Your device could not play the voice preview. Try again."
                            }
                        }
                    }) { Text(if (testing) "Preparing…" else "Preview voice") }
                }
                val voiceMessage = previewError ?: voiceStatus ?: if (!GnaniTtsEngine.isConfigured())
                    "Gnani voice needs an API key and voice access. Device voice is available as a fallback." else null
                voiceMessage?.let { Text(it, style = MaterialTheme.typography.bodySmall, color = DjStyle.muted) }
            } }, confirmButton = { TextButton(onClick = { preferences = false }) { Text("Done") } })
    }
}

@Composable
private fun DjMenu(expanded: Boolean, onDismiss: () -> Unit, items: List<DjShortcut>) {
    DropdownMenu(expanded = expanded, onDismissRequest = onDismiss) {
        items.forEach { item ->
            DropdownMenuItem(text = { Text(item.label) }, onClick = { onDismiss(); item.action() },
                leadingIcon = { Icon(item.icon, contentDescription = null, tint = DjStyle.lavender, modifier = Modifier.size(20.dp)) })
        }
    }
}

@Composable
private fun DjNotice(message: String?, listening: Boolean) {
    if (message != null) Text(message, Modifier.fillMaxWidth().padding(horizontal = 26.dp, vertical = 6.dp),
        color = DjStyle.error, style = MaterialTheme.typography.bodySmall)
    if (listening) Text("I'm listening…", Modifier.fillMaxWidth().padding(horizontal = 26.dp, vertical = 4.dp),
        color = DjStyle.cyan, fontSize = 12.sp)
}

/** Full-width request field with microphone and send controls. */
@Composable
private fun DjComposer(
    draft: TextFieldState, focus: FocusRequester, listening: Boolean, enabled: Boolean,
    onSend: () -> Unit, onMic: () -> Unit,
) {
    val canSend = enabled && draft.text.isNotBlank()
    val fieldShape = RoundedCornerShape(20.dp)
    Row(Modifier.fillMaxWidth().padding(horizontal = 16.dp), verticalAlignment = Alignment.CenterVertically) {
        Box(Modifier.weight(1f).djGlass(RoundedCornerShape(26.dp)).padding(6.dp)) {
            Row(
                Modifier.fillMaxWidth()
                    .drawBehind {
                        drawRoundRect(DjStyle.cyan.copy(alpha = .16f), cornerRadius = CornerRadius(20.dp.toPx()), style = Stroke(5.dp.toPx()))
                    }
                    .clip(fieldShape)
                    .background(DjStyle.field.copy(alpha = .92f))
                    .border(1.dp, Brush.horizontalGradient(listOf(DjStyle.cyan.copy(alpha = .95f),
                        Color(0xFF6F9BE9).copy(alpha = .7f), DjStyle.cyan.copy(alpha = .6f))), fieldShape)
                    .padding(start = 16.dp, end = 5.dp, top = 5.dp, bottom = 5.dp),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                BasicTextField(state = draft, modifier = Modifier.weight(1f).padding(vertical = 9.dp).focusRequester(focus),
                    textStyle = TextStyle(color = DjStyle.text, fontSize = 16.sp),
                    lineLimits = TextFieldLineLimits.MultiLine(maxHeightInLines = 3),
                    inputTransformation = InputTransformation.maxLength(300),
                    cursorBrush = SolidColor(DjStyle.cyan),
                    keyboardOptions = KeyboardOptions(imeAction = ImeAction.Send),
                    onKeyboardAction = { if (canSend) onSend() },
                    decorator = { field ->
                        Box(contentAlignment = Alignment.CenterStart) {
                            if (draft.text.isEmpty()) Text(if (listening) "Listening…" else "Talk, explore, or ask me to play…",
                                color = DjStyle.muted, fontSize = 16.sp, maxLines = 1)
                            field()
                        }
                    })
                Spacer(Modifier.width(8.dp))
                DjIconTile(Icons.Rounded.Mic, "Talk to DJ", onClick = onMic, enabled = enabled,
                    fill = SolidColor(if (listening) DjStyle.cyan.copy(alpha = .24f) else Color(0xFF283157)),
                    tint = if (listening) DjStyle.sky else DjStyle.periwinkle)
                Spacer(Modifier.width(6.dp))
                DjIconTile(Icons.Rounded.NearMe, "Send music request", onClick = onSend, enabled = canSend,
                    fill = DjStyle.accent, tint = DjStyle.ink)
            }
        }
    }
}

@Composable
private fun DjLanding(modifier: Modifier, emotion: DjEmotion, state: DjStatus, reduced: Boolean, onMascot: () -> Unit) {
    val caption = when {
        state.loading -> "Finding your next songs…"
        state.active -> state.song?.let { "On air · ${it.title}" } ?: "DJ AI · On air"
        else -> null
    }
    BoxWithConstraints(modifier.fillMaxWidth(), contentAlignment = Alignment.Center) {
        val reserve = if (caption != null) 30.dp else 8.dp
        val hero = minOf(maxWidth * .84f, maxHeight - reserve, 340.dp).coerceAtLeast(0.dp)
        Column(horizontalAlignment = Alignment.CenterHorizontally) {
            DjOrb(hero, emotion, state.song?.videoId, reduced, onClick = onMascot)
            if (caption != null) Text(caption, Modifier.padding(horizontal = 32.dp), color = DjStyle.muted,
                fontSize = 13.sp, maxLines = 1, overflow = TextOverflow.Ellipsis)
        }
    }
}

@Composable
private fun DjChatContent(modifier: Modifier, state: DjStatus, prompt: String?, emotion: DjEmotion, reduced: Boolean,
    onPlay: (String) -> Unit, onPick: () -> Unit, onPlayMix: () -> Unit) {
    val list = rememberLazyListState()
    val songs = state.suggestions
    LaunchedEffect(state.messages.lastOrNull()?.id, state.song?.videoId, prompt) {
        val count = list.layoutInfo.totalItemsCount
        if (count > 0) list.animateScrollToItem(count - 1)
    }
    BoxWithConstraints(modifier.fillMaxWidth()) {
        val compact = maxHeight < 320.dp
        val heroSize = if (compact) 64.dp else 156.dp
        val showHero = maxHeight >= 160.dp
        Column(Modifier.fillMaxSize()) {
            // Reserve the small keyboard viewport for the conversation; a fixed
            // hero here would squeeze both the mascot and the message list away.
            if (showHero) Box(Modifier.fillMaxWidth().height(heroSize), contentAlignment = Alignment.Center) {
                DjOrb(heroSize, emotion, state.song?.videoId, reduced)
            }
            state.speechText?.let { speech ->
                val caption = buildAnnotatedString {
                    append(speech)
                    val start = state.speechRangeStart.coerceIn(0, speech.length)
                    val end = state.speechRangeEnd.coerceIn(start, speech.length)
                    if (end > start) addStyle(SpanStyle(color = DjStyle.text, fontWeight = FontWeight.SemiBold,
                        background = DjStyle.cyan.copy(alpha = .18f)), start, end)
                }
                Text(caption, Modifier.fillMaxWidth().padding(horizontal = 28.dp, vertical = 8.dp),
                    color = DjStyle.muted, fontSize = 13.sp, lineHeight = 19.sp, textAlign = TextAlign.Center,
                    maxLines = if (compact) 2 else 3, overflow = TextOverflow.Ellipsis)
            }
            LazyColumn(state = list, modifier = Modifier.weight(1f).fillMaxWidth(),
                contentPadding = PaddingValues(start = 20.dp, end = 20.dp, bottom = 18.dp),
                verticalArrangement = Arrangement.spacedBy(12.dp)) {
                item("intelligence") {
                    Text(state.intelligenceLabel, Modifier.fillMaxWidth(), color = DjStyle.muted,
                        fontSize = 11.sp, textAlign = TextAlign.Center)
                }
                if (state.messages.isEmpty() && prompt == null) item { DjChatBubble("Hey! How's your day? We can talk or explore music together. When you're ready to listen, tap Dive in.", false) }
                items(state.messages, key = { it.id }) { message -> DjChatBubble(message.text, message.fromUser) }
                if (state.replyPending) item("thinking") {
                    Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                        CircularProgressIndicator(Modifier.size(14.dp), color = DjStyle.cyan, strokeWidth = 2.dp)
                        Text("Thinking…", color = DjStyle.muted, fontSize = 12.sp)
                    }
                }
                if (prompt != null) item { DjChatBubble(prompt, false) }
                if (songs.isNotEmpty()) item {
                    Column(verticalArrangement = Arrangement.spacedBy(8.dp), modifier = Modifier.padding(top = 4.dp)) {
                        songs.forEach { song -> DjSongRow(song, false) { onPlay(song.videoId) } }
                    }
                }
                if (state.suggestedQuery != null) item {
                    Button(onClick = onPlayMix, enabled = !state.loading, modifier = Modifier.fillMaxWidth(),
                        colors = ButtonDefaults.buttonColors(containerColor = DjStyle.cyan, contentColor = DjStyle.ink)) {
                        Icon(Icons.Rounded.PlayArrow, null, Modifier.size(18.dp)); Spacer(Modifier.width(8.dp))
                        Text("Play this mix")
                    }
                }
                item {
                    TextButton(onClick = onPick, contentPadding = PaddingValues(horizontal = 12.dp, vertical = 0.dp)) {
                        Icon(Icons.Rounded.AutoAwesome, null, Modifier.size(16.dp), tint = DjStyle.cyan)
                        Spacer(Modifier.width(8.dp)); Text("Dive in · pick for me", color = DjStyle.cyan, fontSize = 13.sp)
                    }
                }
            }
        }
    }
}

@Composable
private fun DjChatBubble(text: String, user: Boolean) {
    Row(Modifier.fillMaxWidth(), horizontalArrangement = if (user) Arrangement.End else Arrangement.Start,
        verticalAlignment = Alignment.Bottom) {
        if (!user) {
            Box(Modifier.size(30.dp).clip(CircleShape)
                .background(Brush.radialGradient(listOf(DjStyle.glow.copy(alpha = .55f), DjStyle.surface)))
                .border(1.dp, DjStyle.cyan.copy(alpha = .28f), CircleShape), contentAlignment = Alignment.Center) {
                VikifyMascot(size = 30.dp, reduceMotion = true)
            }
            Spacer(Modifier.width(8.dp))
        }
        val shape = RoundedCornerShape(20.dp, 20.dp, if (user) 6.dp else 20.dp, if (user) 20.dp else 6.dp)
        Box(Modifier.widthIn(max = 296.dp).weight(1f, fill = false)
            .then(if (user) Modifier.clip(shape).background(DjStyle.userBubbleFill) else Modifier.djGlass(shape, .6f))) {
            Text(text, Modifier.padding(horizontal = 15.dp, vertical = 12.dp), color = DjStyle.text, fontSize = 14.sp, lineHeight = 20.sp)
        }
    }
}

@Composable
private fun DjSongRow(song: Song, playing: Boolean, onPlay: () -> Unit) {
    Row(Modifier.fillMaxWidth().djGlass(RoundedCornerShape(18.dp), .5f).padding(8.dp), verticalAlignment = Alignment.CenterVertically) {
        Box(Modifier.size(50.dp).clip(RoundedCornerShape(12.dp)).background(DjStyle.raised), contentAlignment = Alignment.Center) {
            Icon(Icons.Rounded.MusicNote, null, tint = DjStyle.muted, modifier = Modifier.size(22.dp))
            AsyncImage(song.thumbnailUrl.artworkAt(160), contentDescription = null, modifier = Modifier.fillMaxSize(),
                contentScale = ContentScale.Crop)
        }
        Column(Modifier.weight(1f).padding(horizontal = 12.dp)) {
            Text(song.title, color = DjStyle.text, fontSize = 14.sp, fontWeight = FontWeight.Medium, maxLines = 1, overflow = TextOverflow.Ellipsis)
            Text(song.artist, Modifier.padding(top = 4.dp), color = DjStyle.muted, fontSize = 12.sp, maxLines = 1, overflow = TextOverflow.Ellipsis)
        }
        DjIconTile(if (playing) Icons.Rounded.Pause else Icons.Rounded.PlayArrow,
            if (playing) "Pause ${song.title}" else "Play ${song.title}", onClick = onPlay,
            fill = if (playing) DjStyle.accent else SolidColor(DjStyle.raised),
            tint = if (playing) DjStyle.ink else DjStyle.text, iconSize = 21.dp)
    }
}

@Composable
private fun DjCrafting(modifier: Modifier, state: DjStatus, reduced: Boolean) {
    val stage = when (state.loadingStage) {
        DjLoadingStage.IDLE, DjLoadingStage.ANALYZING -> 0
        DjLoadingStage.FINDING -> 1; DjLoadingStage.BUILDING -> 2; DjLoadingStage.READY -> 3
    }
    val request = state.requestDescription
    Column(modifier.fillMaxWidth().verticalScroll(rememberScrollState()).padding(horizontal = 26.dp),
        horizontalAlignment = Alignment.CenterHorizontally) {
        Spacer(Modifier.height(24.dp))
        DjOrb(280.dp, DjEmotion.THINKING, state.song?.videoId, reduced, crafting = true)
        Spacer(Modifier.height(20.dp))
        Text("Crafting your playlist…", color = DjStyle.text, fontSize = 22.sp, fontWeight = FontWeight.SemiBold, textAlign = TextAlign.Center)
        Text(if (request != null) "Finding a mix for “${request.take(110)}”" else "A few familiar sounds. A little something new.",
            Modifier.padding(top = 12.dp, start = 8.dp, end = 8.dp), color = DjStyle.muted, fontSize = 14.sp,
            lineHeight = 21.sp, textAlign = TextAlign.Center)
        Spacer(Modifier.height(28.dp))
        Column(Modifier.fillMaxWidth().djGlass(RoundedCornerShape(24.dp)).padding(22.dp),
            verticalArrangement = Arrangement.spacedBy(20.dp)) {
            listOf("Analyzing your taste", "Selecting the right tracks", "Building the playlist", "Getting ready to play")
                .forEachIndexed { index, label -> DjProgressStep(label, index, stage) }
        }
        Spacer(Modifier.height(24.dp))
    }
}
