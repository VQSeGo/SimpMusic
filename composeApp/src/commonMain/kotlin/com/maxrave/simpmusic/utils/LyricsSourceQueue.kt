package com.maxrave.simpmusic.utils

import com.maxrave.domain.data.model.metadata.Lyrics
import com.maxrave.domain.manager.DataStoreManager
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.distinctUntilChanged

/**
 * A place the lyrics of a track can come from. The [id]s are the values the old single "main lyrics
 * provider" setting stored, so that setting can be read into a queue. Declared in the order the old
 * chains fell back in (SimpMusic, Spotify, LRCLIB; BetterLyrics and YouTube were asked only as the
 * main provider): a source missing from a saved queue joins its end in this order.
 *
 * [onByDefault] is false for BetterLyrics: its API answers 401 "API key required" to anything not
 * cached, so it starts switched off (the user can still turn it on).
 */
enum class LyricsSource(
    val id: String,
    val onByDefault: Boolean = true,
) {
    SIMPMUSIC(DataStoreManager.SIMPMUSIC),
    SPOTIFY(DataStoreManager.SPOTIFY),
    LRCLIB(DataStoreManager.LRCLIB),
    BETTER_LYRICS(DataStoreManager.BETTER_LYRICS, onByDefault = false),
    YOUTUBE(DataStoreManager.YOUTUBE),
    ;

    companion object {
        fun fromId(id: String): LyricsSource? = entries.firstOrNull { it.id == id }
    }
}

data class LyricsSourceEntry(
    val source: LyricsSource,
    val enabled: Boolean,
)

/**
 * The lyrics sources in the order the user wants them asked, each one on or off. Stored as one string
 * under [KEY] (`lrclib:1,spotify:0,…`); until the user saves a queue, it is built from the old
 * settings — the main provider first, the rest in the old fallback order.
 */
object LyricsSourceQueue {
    const val KEY = "lyrics_source_queue"

    fun encode(entries: List<LyricsSourceEntry>): String = entries.joinToString(",") { "${it.source.id}:${if (it.enabled) 1 else 0}" }

    /**
     * The saved queue, or null when nothing is saved. An id this build does not know (a removed source)
     * is skipped, so is a repeat of one already read; a source the saved string lacks (added after it
     * was written) joins the end, switched on unless it is off by default.
     */
    fun decode(raw: String?): List<LyricsSourceEntry>? {
        if (raw.isNullOrBlank()) return null
        val seen = mutableSetOf<LyricsSource>()
        val saved =
            raw.split(',').mapNotNull { token ->
                val source = LyricsSource.fromId(token.substringBefore(':').trim()) ?: return@mapNotNull null
                if (!seen.add(source)) return@mapNotNull null
                LyricsSourceEntry(source, enabled = token.substringAfter(':', "1").trim() != "0")
            }
        return saved + LyricsSource.entries.filter { it !in seen }.map { LyricsSourceEntry(it, enabled = it.onByDefault) }
    }

    /**
     * The queue a build with one main provider had in effect: [oldMain] first, then the others in
     * declaration order. Spotify stays on only where the old build asked it — as the main provider, or
     * with its Enable Spotify lyrics switch on; the rest are on, except a source off by default.
     */
    fun migrate(
        oldMain: String?,
        spotifyFallbackOn: Boolean,
    ): List<LyricsSourceEntry> {
        val main = oldMain?.let(LyricsSource::fromId) ?: LyricsSource.SIMPMUSIC
        return (listOf(main) + LyricsSource.entries.filter { it != main }).map { source ->
            LyricsSourceEntry(
                source,
                enabled =
                    source.onByDefault &&
                        (source != LyricsSource.SPOTIFY || main == LyricsSource.SPOTIFY || spotifyFallbackOn),
            )
        }
    }

    fun resolve(
        raw: String?,
        oldMain: String?,
        spotifyFallbackOn: Boolean,
    ): List<LyricsSourceEntry> = decode(raw) ?: migrate(oldMain, spotifyFallbackOn)

    /** The queue in effect, read again whenever it is saved. */
    fun flow(dataStoreManager: DataStoreManager): Flow<List<LyricsSourceEntry>> =
        combine(
            dataStoreManager.getString(KEY),
            dataStoreManager.lyricsProvider,
            dataStoreManager.spotifyLyrics,
        ) { raw, oldMain, spotifyLyrics ->
            resolve(raw, oldMain, spotifyLyrics == DataStoreManager.TRUE)
        }.distinctUntilChanged()

    suspend fun save(
        dataStoreManager: DataStoreManager,
        entries: List<LyricsSourceEntry>,
    ) {
        dataStoreManager.putString(KEY, encode(entries))
    }
}

/** Lines timed to the music: line by line, word by word or syllable by syllable. */
fun Lyrics.isSynced(): Boolean = syncType != null && syncType != "UNSYNCED"

/** A source that answered with no line of text has no lyrics, whatever its status said. */
fun Lyrics.hasText(): Boolean = !error && lines?.any { it.words.isNotBlank() } == true
