package com.music.bitchord.data.sources

/**
 * The kinds of source this build knows how to talk to.
 *
 * Fixed and small on purpose. **[rank] is the order sources are tried** — see
 * `SourceRegistry.active()`, which sorts on it — so the sources a user added
 * come first, then the legacy built-in module kind, then JioSaavn, then
 * YouTube Music. Adding a source means adding a [MusicSource]
 * implementation and an entry here, which is the point — every protocol the
 * app speaks is one someone can read in this repo, and a source can't teach
 * the app a new way to behave after it ships.
 *
 * What varies per *instance* — which index, whose module — is [SourceConfig].
 */
enum class SourceKind(
    val label: String,
    val detail: String,
    /** The chips under the name on the sources screen. */
    val labels: List<String>,
    /** Whether an instance needs a URL before it can do anything — and so needs an editor. */
    val needsServer: Boolean,
    /** Whether this kind can serve bit-exact audio when asked. */
    val canServeLossless: Boolean,
    /**
     * Whether this kind answers quickly enough to be worth asking *before* a
     * track is played, so its copy can be pinned and cached ahead of time.
     *
     * Measured on this device, the gap is not close: JioSaavn answers a search
     * and hands back a stream URL in about 0.4s, while a module index takes
     * 7-13s to walk its backends — and read-ahead runs on the track *after* the
     * one playing, so a lookup that slow is usually still going when the
     * listener arrives. A wasted JioSaavn resolve costs one HTTP round trip; a
     * wasted module resolve costs a QuickJS engine, an index fetch and several
     * backend searches. The first is worth spending speculatively and the
     * second is not.
     *
     * False for [YOUTUBE] as well, though it *is* warmed ahead of time — that
     * happens through its own read-ahead in
     * [AudioCache][com.music.bitchord.playback.AudioCache], which speaks video
     * ids directly and needs no cross-source match to find the track.
     */
    val worthPrefetching: Boolean = false,
    /**
     * Where this kind sits in the walk, low first.
     *
     * Separate from [ordinal] because two kinds share a position: an addon and
     * a module index are both sources the user added on purpose, and which of
     * *those* is asked first is theirs to set by dragging — see
     * [SourceRegistry.reorderAddons][com.music.bitchord.data.sources.SourceRegistry.reorderAddons].
     * Sorting on `ordinal` made that impossible to express: a stable sort would
     * always put every addon ahead of every module index whatever order the
     * list was in, so the drag would have moved a row on screen and changed
     * nothing about what was actually asked first.
     */
    val rank: Int,
) {
    JIOSAAVN(
        label = "JioSaavn",
        detail = "Optional JioSaavn streams up to 320kbps AAC/MP4. Catalogue matching can select the wrong song.",
        labels = listOf("High Quality", "320kbps"),
        needsServer = false,
        canServeLossless = false,
        worthPrefetching = true,
        rank = 2,
    ),

    /**
     * The source the app was built on, listed here so it always has a fixed
     * place: second, behind the module source. It cannot be removed — see
     * [SourceRegistry]. Nothing else in the app can supply a home feed, a
     * radio station or a related-tracks queue.
     */
    YOUTUBE(
        label = "YouTube Music",
        detail = "The full catalogue, at Opus up to about 171 kbps. Lossy — there is no " +
            "lossless rendition to ask for.",
        labels = listOf("Lossy", "Full catalogue", "Radio"),
        needsServer = false,
        canServeLossless = false,
        rank = 3,
    ),
    ;

    /** Whether this is a source the user configured, rather than one that ships. */
    val isUserAdded: Boolean get() = rank == USER_ADDED

    companion object {
        /**
         * The shared rank of every source the user added. Their order among
         * themselves is the stored list order, which is what a drag rewrites.
         */
        const val USER_ADDED = 0
    }
}
