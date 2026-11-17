package com.music.vikify.dj

/** Conversation cannot mutate playback. Only an explicit listening request can start music. */
internal object DjConversation {
    enum class Kind { CHAT, SUGGEST, PLAY, CONTROL }
    data class Turn(val kind: Kind, val reply: String = "", val query: String = "", val name: String? = null,
        val waitForPlayback: Boolean = false)

    private val language = Regex("\\b(telugu|tamil|hindi|english|malayalam|kannada|punjabi|bengali|marathi|korean|japanese|spanish)\\b", RegexOption.IGNORE_CASE)
    private val moods = linkedMapOf(
        "sad|down|heartbroken|lonely" to "gentle comforting songs",
        "happy|joyful|celebrate|celebrating" to "feel good songs",
        "tired|relax|relaxing|calm|calmer|chill|mellow|less energy" to "chill mellow songs",
        "energetic|upbeat|workout|gym|more energy|hype" to "upbeat energetic songs",
        "focus|study|studying|work|working" to "instrumental focus music",
        "sleep|sleepy|sleeping" to "soft sleep music",
        "romantic|love songs|in love" to "romantic songs",
        "drive|driving|road trip" to "road trip songs",
    )
    private val controls = setOf("pause", "pause music", "stop music", "resume", "skip", "next", "next song",
        "previous", "previous song", "back", "replay", "play again", "why", "why this song", "explain my music")

    fun route(message: String, pendingQuery: String? = null, knownName: String? = null): Turn {
        val original = message.trim().take(300)
        val text = original.lowercase(java.util.Locale.ROOT).replace('’', '\'').trim().trimEnd('.', '!', '?')
            .removePrefix("please ")
        val name = Regex("(?i)^(?:(?:hi|hello|hey)[,! ]+)?(?:my name is|i am|i'm|call me)\\s+([\\p{L}][\\p{L} '-]{0,40})[.!]*$")
            .matchEntire(original)?.groupValues?.get(1)?.trim()
        val mood = moods.entries.firstOrNull { Regex("\\b(?:${it.key})\\b").containsMatchIn(text) }?.value
        val lang = language.find(text)?.value?.replaceFirstChar { it.uppercase() }
        val explicitPlay = Regex("^(?:hey dj[, ]+)?(?:play|put on|start playing|start a mix|start the music|let's listen|lets listen)\\b").containsMatchIn(text)
        if (Regex("\\b(?:(?:don't|do not|dont) (?:play|start|autoplay)|without (?:playing|starting)|not yet|just brows(?:e|ing))\\b").containsMatchIn(text))
            return Turn(Kind.CHAT, "Of course. We can talk and explore songs. I'll wait for you to tap Play this mix or ask me to play.", waitForPlayback = true)
        if (text in controls) return Turn(Kind.CONTROL)
        if (text in setOf("let dj pick", "surprise me", "you choose", "change vibe", "dive in", "something new", "start", "let's start", "lets start", "start here", "let's start here", "lets start here", "play them", "play those", "play it", "yes play", "go ahead", "yes", "play")) {
            if (text == "yes") return Turn(Kind.CHAT, "I'm here. Tell me more, or tap Play this mix when you'd like to listen.")
            if (text in setOf("play them", "play those", "play it", "yes play", "go ahead") && pendingQuery == null)
                return Turn(Kind.CHAT, "Which songs should I play? Tell me an artist or mood, or tap Dive in for a personal mix.")
            return Turn(Kind.PLAY, query = if (text in setOf("change vibe", "something new", "surprise me", "you choose", "let dj pick", "dive in")) "" else pendingQuery.orEmpty())
        }
        if (explicitPlay) {
            var query = original.replace(Regex("(?i)^(?:please\\s+)?(?:hey dj[, ]+)?(?:play|put on|start playing|start a mix|start the music|let's listen|lets listen)\\s*"), "").trim()
            if (query.lowercase() in setOf("some music", "music", "songs", "something", "a mix")) query = pendingQuery.orEmpty()
            return Turn(Kind.PLAY, query = query)
        }
        val nameWords = name.orEmpty().lowercase().split(' ')
        val statementWords = setOf("a", "an", "the", "feeling", "having", "doing", "going", "not", "so", "very", "really", "just", "still", "here", "back", "okay", "ok", "fine", "good", "bored", "ready", "looking", "finding", "listening", "from", "in", "at", "on", "with", "your", "sorry", "sure", "confused", "excited", "stressed", "hungry", "well", "sick", "better")
        if (name != null && nameWords.size <= 3 && nameWords.none { it in statementWords } && mood == null && lang == null && !Regex("\\b(songs?|music)\\b").containsMatchIn(text)) {
            return Turn(Kind.CHAT, "Nice to meet you, $name. We can chat, find something for your mood, or dive into a mix made from your recent listening. What are you feeling?", name = name)
        }
        if (text in setOf("hi", "hello", "hey", "hey dj", "hi dj")) return Turn(Kind.CHAT,
            "Hey${knownName?.let { ", $it" }.orEmpty()}! How's your day going? Tell me what's on your mind, or a mood you'd like some music for.")
        if (text in setOf("how are you", "how are you doing", "what's up", "whats up")) return Turn(Kind.CHAT,
            "I'm here and ready to keep you company. How are you feeling today?")
        if (text in setOf("thanks", "thank you", "thank you so much", "nice", "cool")) return Turn(Kind.CHAT,
            "Anytime${knownName?.let { ", $it" }.orEmpty()}. Want to keep this mood or explore something different?")
        if (text in setOf("what is my name", "what's my name", "whats my name", "do you remember my name")) return Turn(Kind.CHAT,
            knownName?.let { "You're $it. I'll remember that for our conversations." } ?: "You haven't told me yet. What should I call you?")
        if (Regex("(?:what can you|help|how does|who are you|can we talk|talk to me|chat with)").containsMatchIn(text)) return Turn(Kind.CHAT,
            "I'm your music companion. Tell me about your day, explore a mood or artist, or ask why I chose a song. Dive in blends your recent listening with related discoveries, then changes direction as the set continues.")
        if (text == "no" || text == "not now") return Turn(Kind.CHAT, "No rush. What would you like to talk about or explore instead?")
        if (mood != null || lang != null) {
            val previousLanguage = language.find(pendingQuery.orEmpty())?.value?.replaceFirstChar { it.uppercase() }
            val query = listOfNotNull(lang ?: previousLanguage, mood ?: pendingQuery?.replace(language, "")?.trim()?.takeIf { it.isNotEmpty() } ?: "songs").joinToString(" ")
            val reply = when {
                Regex("\\b(sad|down|heartbroken|lonely)\\b").containsMatchIn(text) -> "That sounds like a hard day. Would you like to talk about it? I can also find something gentle to sit with you."
                else -> "Let's explore $query. I'll show you a few choices; you decide when we listen."
            }
            return Turn(Kind.SUGGEST, reply, query)
        }
        val discovery = Regex("(?i)^(?:can you |could you )?(?:find(?: me)?|recommend(?: me)?|suggest(?: me)?|show(?: me)?|search(?: for)?|i(?:'m| am) (?:finding|looking for)|i (?:like|love|want|prefer)|make (?:me )?(?:a )?playlist(?: of| with| for)?)\\s+")
        if (discovery.containsMatchIn(original) || Regex("\\b(songs?|music|playlist|artist|album|jazz|rock|pop|lofi|lo fi|hip hop|indie|classical|edm)\\b").containsMatchIn(text)) {
            val query = original.replace(discovery, "").trim(' ', '.', '!', '?').take(250)
            if (query.isBlank() || query.lowercase() in setOf("songs", "music", "a playlist", "playlist", "some songs"))
                return Turn(Kind.CHAT, "What mood, artist or language should we explore? Or tap Dive in for a mix based on your recent listening.")
            return Turn(Kind.SUGGEST, "I'll look for $query. Nothing starts until you choose to play.", query)
        }
        return Turn(Kind.CHAT, "I'm listening. Tell me a little more. If that's a song or artist you'd like to explore, say ‘find $original’ and I'll show you some choices.")
    }
}
