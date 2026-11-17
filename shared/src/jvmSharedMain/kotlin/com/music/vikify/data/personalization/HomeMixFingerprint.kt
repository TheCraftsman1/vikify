package com.music.vikify.data.personalization

import com.music.vikify.data.model.Song
import java.security.MessageDigest

/** Compare actual playable membership, ignoring order and duplicate rows. */
internal fun homeTrackSetFingerprint(songs: List<Song>): String {
    val identity = songs.filter(LocalMixGenerator::eligible).map { it.videoId }.distinct().sorted()
        .joinToString("") { "${it.length}:$it" }
    return MessageDigest.getInstance("SHA-256").digest(identity.toByteArray(Charsets.UTF_8))
        .joinToString("") { "%02x".format(it) }
}
