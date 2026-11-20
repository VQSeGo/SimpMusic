package com.maxrave.simpmusic.utils

import com.maxrave.domain.manager.DataStoreManager
import kotlinx.coroutines.flow.first

/**
 * The lyrics picked with Other lyrics for one track: the [source] and which of its results
 * ([variant] 0 is the automatic pick, 1, 2, … the next ones of the same search).
 */
data class LyricsChoice(
    val source: LyricsSource,
    val variant: Int,
)

/**
 * The Other lyrics picks, by videoId, kept so a track plays with the lyrics chosen for it rather than
 * the automatic pick again (that one was wrong, or there would be no pick). One DataStore string under
 * [KEY]: `videoId=lrclib:1;videoId=spotify:0`, the latest pick last; only the [LIMIT] latest are kept.
 * The text itself is the track's saved lyrics, which the pick replaces.
 */
object LyricsChoices {
    const val KEY = "lyrics_choice"
    const val LIMIT = 500

    fun decode(raw: String?): Map<String, LyricsChoice> {
        if (raw.isNullOrBlank()) return emptyMap()
        val out = LinkedHashMap<String, LyricsChoice>()
        raw.split(';').forEach { token ->
            val videoId = token.substringBefore('=', "").trim()
            val value = token.substringAfter('=', "")
            val source = LyricsSource.fromId(value.substringBefore(':').trim()) ?: return@forEach
            val variant = value.substringAfter(':', "").trim().toIntOrNull()?.takeIf { it >= 0 } ?: return@forEach
            if (videoId.isEmpty()) return@forEach
            out.remove(videoId)
            out[videoId] = LyricsChoice(source, variant)
        }
        return out
    }

    fun encode(choices: Map<String, LyricsChoice>): String =
        choices.entries.joinToString(";") { (videoId, choice) -> "$videoId=${choice.source.id}:${choice.variant}" }

    /** [choices] with [videoId] set to [choice] as the latest pick, the oldest dropped past [LIMIT]. */
    fun with(
        choices: Map<String, LyricsChoice>,
        videoId: String,
        choice: LyricsChoice,
    ): Map<String, LyricsChoice> {
        val out = LinkedHashMap(choices)
        out.remove(videoId)
        out[videoId] = choice
        while (out.size > LIMIT) out.remove(out.keys.first())
        return out
    }

    suspend fun get(
        dataStoreManager: DataStoreManager,
        videoId: String,
    ): LyricsChoice? = decode(dataStoreManager.getString(KEY).first())[videoId]

    suspend fun put(
        dataStoreManager: DataStoreManager,
        videoId: String,
        choice: LyricsChoice,
    ) {
        val current = decode(dataStoreManager.getString(KEY).first())
        dataStoreManager.putString(KEY, encode(with(current, videoId, choice)))
    }
}

/** Sources whose search gives several results to pick from; SimpMusic has one per contributor. */
val LyricsSource.hasVariants: Boolean
    get() = this == LyricsSource.SIMPMUSIC || this == LyricsSource.LRCLIB || this == LyricsSource.SPOTIFY

/** One source's turn in an Other lyrics walk: its results [from] up to [until] (exclusive). */
data class LyricsWalkStep(
    val source: LyricsSource,
    val from: Int,
    val until: Int,
)

/**
 * Where Other lyrics looks, in order, after [current] (the lyrics on screen; null when not known, as for
 * lyrics typed in or saved by an older build): the next results of the same source, then the other
 * [sources] (the switched-on ones, in the queue's order) from the one after it round to it, then the
 * results of [current]'s source before it. A source with one answer per track (BetterLyrics, YouTube)
 * has one turn; the others at most [variantsPerSource] results.
 */
fun otherLyricsWalk(
    sources: List<LyricsSource>,
    current: LyricsChoice?,
    variantsPerSource: Int,
): List<LyricsWalkStep> {
    fun count(source: LyricsSource) = if (source.hasVariants) variantsPerSource else 1
    if (current == null || current.source !in sources) return sources.map { LyricsWalkStep(it, 0, count(it)) }
    val at = sources.indexOf(current.source)
    return buildList {
        if (current.variant + 1 < count(current.source)) add(LyricsWalkStep(current.source, current.variant + 1, count(current.source)))
        (sources.drop(at + 1) + sources.take(at)).forEach { add(LyricsWalkStep(it, 0, count(it))) }
        if (current.variant > 0) add(LyricsWalkStep(current.source, 0, minOf(current.variant, count(current.source))))
    }
}
