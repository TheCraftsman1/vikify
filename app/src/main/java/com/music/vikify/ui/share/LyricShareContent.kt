package com.music.vikify.ui.share

/** The card uses real nonempty lyric lines, with a bounded excerpt for export. */
internal fun lyricShareLines(text: String?): List<String> = text.orEmpty().lineSequence()
    .map(String::trim).filter(String::isNotBlank).toList()

internal fun lyricShareExcerpt(lines: List<String>, start: Int): String =
    lines.drop(start.coerceIn(0, (lines.size - 1).coerceAtLeast(0)))
        .take(4).joinToString("\n").take(480)
