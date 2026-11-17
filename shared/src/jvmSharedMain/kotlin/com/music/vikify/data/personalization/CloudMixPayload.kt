package com.music.vikify.data.personalization

/** The cloud library describes catalog tracks, never another device's files or player queue. */
internal object CloudMixPayload {
    private val youtubeId = Regex("[A-Za-z0-9_-]{11}")
    fun trackId(id: String) = youtubeId.matches(id)
    private fun remoteArt(art: String) = art.startsWith("https://") || art.startsWith("http://")
    fun portable(track: StoredMixTrack): StoredMixTrack = track.copy(localUri = null, localPath = null,
        art = track.art?.takeIf(::remoteArt))
    fun retainDeviceLocation(track: StoredMixTrack, previous: StoredMixTrack?): StoredMixTrack =
        track.copy(localUri = previous?.localUri, localPath = previous?.localPath,
            art = track.art ?: previous?.art?.takeUnless(::remoteArt))
    fun valid(track: StoredMixTrack): Boolean = trackId(track.id) && track.title.isNotBlank() &&
        track.title.length <= 1_000 && track.artist.length <= 1_000 &&
        track.localUri == null && track.localPath == null &&
        (track.art == null || remoteArt(track.art)) &&
        (track.art?.length ?: 0) <= 8_000 && (track.duration?.length ?: 0) <= 100 &&
        (track.artistId?.length ?: 0) <= 200 && (track.albumId?.length ?: 0) <= 200 &&
        (track.album?.length ?: 0) <= 1_000 && track.artists.size <= 30 &&
        track.artists.all { it.name.length <= 1_000 && (it.browseId?.length ?: 0) <= 200 }
    fun sessionId(id: String) = id.isNotBlank() && id.length <= 100
    fun bounded(entries: Map<String, String>, maxEntries: Int, maxValueBytes: Int) {
        require(entries.size <= maxEntries) { "Cloud collection exceeds its limit" }
        require(entries.values.sumOf { it.toByteArray(Charsets.UTF_8).size.toLong() } <= 7_000_000L) {
            "Cloud collection exceeds its storage limit"
        }
        require(entries.all { (key, value) -> key.length <= 200 && value.toByteArray(Charsets.UTF_8).size <= maxValueBytes }) {
            "Cloud item exceeds its limit"
        }
    }
}
