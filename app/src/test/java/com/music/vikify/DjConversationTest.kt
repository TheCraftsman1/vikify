package com.music.vikify

import com.music.vikify.dj.DjConversation
import com.music.vikify.dj.DjConversation.Kind
import org.junit.Assert.*
import org.junit.Test

class DjConversationTest {
    @Test fun `introducing Vishnu is conversation rather than a song search`() {
        for (message in listOf("hi i am vishnu", "Hi, I am Vishnu", "My name is Vishnu", "I'm Vishnu")) {
            val turn = DjConversation.route(message)
            assertEquals(message, Kind.CHAT, turn.kind)
            assertEquals(message, "vishnu", turn.name?.lowercase())
            assertEquals(message, "", turn.query)
        }
    }

    @Test fun `ordinary chat never requests playback`() {
        for (message in listOf("hello", "how are you", "can we talk", "I had a hard day", "not now", "thank you")) {
            val turn = DjConversation.route(message, "Telugu chill songs", "Vishnu")
            assertEquals(message, Kind.CHAT, turn.kind)
            assertTrue(message, turn.reply.isNotBlank())
            assertTrue(message, turn.query.isBlank())
        }
    }

    @Test fun `sentences about the day are not stored as a listener name`() {
        for (message in listOf("I'm having a hard day", "I am a student", "I'm feeling lonely", "I am tired")) {
            assertNull(message, DjConversation.route(message).name)
        }
    }

    @Test fun `explicit song titles retain words that resemble introductions`() {
        val turn = DjConversation.route("Play I Am Vishnu")
        assertEquals(Kind.PLAY, turn.kind)
        assertEquals("I Am Vishnu", turn.query)
        assertNull(turn.name)
        assertEquals("Naatu Naatu", DjConversation.route("please play Naatu Naatu").query)
    }

    @Test fun `finding songs offers choices without starting a queue`() {
        for (message in listOf("find me Arijit Singh", "recommend jazz", "show me songs like Blinding Lights", "I'm finding songs lets start here")) {
            assertEquals(message, Kind.SUGGEST, DjConversation.route(message).kind)
        }
        assertEquals(Kind.CHAT, DjConversation.route("I'm finding songs").kind)
    }

    @Test fun `language then mood followup carries music context without autoplay`() {
        val language = DjConversation.route("Telugu")
        assertEquals(Kind.SUGGEST, language.kind)
        assertEquals("Telugu songs", language.query)
        val mood = DjConversation.route("something calmer", language.query)
        assertEquals(Kind.SUGGEST, mood.kind)
        assertEquals("Telugu chill mellow songs", mood.query)
        val anotherLanguage = DjConversation.route("Hindi instead", mood.query)
        assertEquals(Kind.SUGGEST, anotherLanguage.kind)
        assertEquals("Hindi chill mellow songs", anotherLanguage.query)
    }

    @Test fun `play them uses the proposed mix rather than searching the pronoun`() {
        val turn = DjConversation.route("Play them", "Telugu chill mellow songs")
        assertEquals(Kind.PLAY, turn.kind)
        assertEquals("Telugu chill mellow songs", turn.query)
    }

    @Test fun `dive in explicitly starts a personalized set rather than reusing a search`() {
        val turn = DjConversation.route("Dive in", "An old song search")
        assertEquals(Kind.PLAY, turn.kind)
        assertEquals("", turn.query)
    }

    @Test fun `thanks remembers the name without starting pending suggestions`() {
        val turn = DjConversation.route("thanks", "energetic songs", "Vishnu")
        assertEquals(Kind.CHAT, turn.kind)
        assertTrue(turn.reply.contains("Vishnu"))
        assertTrue(turn.query.isEmpty())
    }

    @Test fun `a yes without a proposed mix asks what to explore`() {
        assertEquals(Kind.CHAT, DjConversation.route("yes").kind)
        assertEquals(Kind.CHAT, DjConversation.route("yes", "Hindi songs").kind)
        assertEquals(Kind.PLAY, DjConversation.route("yes play", "Hindi songs").kind)
    }

    @Test fun `explicit requests to wait never autoplay`() {
        for (message in listOf("don't play music", "do not start songs", "dont autoplay", "play some music but don't start yet")) {
            assertEquals(message, Kind.CHAT, DjConversation.route(message, "Telugu songs").kind)
            assertTrue(message, DjConversation.route(message, "Telugu songs").waitForPlayback)
        }
    }

    @Test fun `transport commands remain controls`() {
        for (message in listOf("pause", "resume", "skip", "next song", "previous song", "replay", "why this song")) {
            assertEquals(message, Kind.CONTROL, DjConversation.route(message).kind)
        }
    }
}
