package com.music.vikify.ui.screens

import com.music.vikify.data.model.HomeShelf
import com.music.vikify.data.model.ShelfItem

/**
 * Occurrences disambiguate repeated server entries without tying every key to
 * its position. Inserting a different shelf or card leaves existing keys alone.
 * Shelves without a server browse target use their heading and layout type.
 */
internal fun homeShelfKeys(shelves: List<HomeShelf>): List<String> = occurrenceKeys(shelves) { shelf ->
    identityParts("shelf", shelf.moreBrowseId ?: shelf.title, shelf.moreParams.orEmpty(), shelf.collectionGrid.toString())
}

internal fun homeItemKeys(items: List<ShelfItem>): List<String> = occurrenceKeys(items) { item ->
    val videoId = item.videoId
    val browseId = item.browseId
    when {
        !videoId.isNullOrBlank() -> identityParts("video", videoId)
        !browseId.isNullOrBlank() -> identityParts("browse", browseId)
        else -> identityParts("text", item.title, item.subtitle)
    }
}

// Length-prefix fields so server text containing separators cannot collide.
private fun identityParts(vararg parts: String): String = parts.joinToString("") { "${it.length}:$it" }

private fun <T> occurrenceKeys(items: List<T>, identity: (T) -> String): List<String> {
    val seen = mutableMapOf<String, Int>()
    return items.map { item ->
        val base = identity(item)
        val occurrence = seen.getOrDefault(base, 0)
        seen[base] = occurrence + 1
        "$base#$occurrence"
    }
}
