package com.music.vikify

import com.music.vikify.data.covers.CoverSpec
import com.music.vikify.data.covers.CoverUri
import com.music.vikify.data.covers.savedPlaylistCover
import com.music.vikify.ui.share.lyricShareExcerpt
import com.music.vikify.ui.share.lyricShareLines
import org.junit.Assert.*
import org.junit.Test

class ShareAndPlaylistCoverTest {
    @Test fun `lyric cards skip empty timings and use the selected real excerpt`() {
        val lines = lyricShareLines("\n First line \n\nSecond line\nThird line\nFourth line\nFifth line\nSixth line ")
        assertEquals("First line\nSecond line\nThird line\nFourth line", lyricShareExcerpt(lines, 0))
        assertEquals("Third line\nFourth line\nFifth line\nSixth line", lyricShareExcerpt(lines, 2))
        assertEquals("Sixth line", lyricShareExcerpt(lines, 99))
        assertEquals("", lyricShareExcerpt(lyricShareLines(null), 0))
    }

    @Test fun `lyric export limits overlong source content`() {
        assertEquals(480, lyricShareExcerpt(listOf("a".repeat(900)), 0).length)
    }

    @Test fun `playlist saves selected design with final name and permanent playlist identity`() {
        val draft = CoverUri.encode(CoverSpec("playlist_draft", "Old draft", "te-IN", "orbit_01", "violet_01", 1))
        val saved = requireNotNull(savedPlaylistCover("VLPLabc123", "  తెలుగు\n మెలోడీస్  ", draft))
        val (spec, size) = requireNotNull(CoverUri.decode(saved))
        assertEquals("playlist:PLabc123", spec.collectionId)
        assertEquals("తెలుగు మెలోడీస్", spec.title)
        assertEquals("orbit_01", spec.templateId)
        assertEquals("violet_01", spec.paletteId)
        assertEquals("te-IN", spec.locale)
        assertEquals(720, size)
        assertNull(savedPlaylistCover("PLabc", "Name", "broken-uri"))
    }
}
