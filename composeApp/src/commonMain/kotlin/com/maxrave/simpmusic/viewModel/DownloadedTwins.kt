package com.maxrave.simpmusic.viewModel

import com.maxrave.domain.data.entities.SongEntity
import com.maxrave.domain.data.model.browse.album.Track
import kotlin.math.abs

/** How far apart two copies of one recording may run, in seconds, and still count as the same. */
private const val TWIN_DURATION_TOLERANCE_SECONDS = 3

private val whitespace = Regex("\\s+")

private fun String.twinKey(): String = trim().lowercase().replace(whitespace, " ")

/**
 * YouTube Music keeps several editions of one album (a re-release, a deluxe cut, the clip version of a
 * single track), and each edition has its own `videoId` for the same recording. A copy downloaded from
 * one edition therefore does not mark the row of another, and a queue built from the other edition
 * plays from the network although the file is on disk.
 *
 * Returns, for every track in [tracks] that is not downloaded itself, the downloaded song that is the
 * same recording on an album of the same name: same title, a shared artist, and the same length (an
 * unknown length matches any). [albumTitle] names the album the tracks are shown under when a track
 * carries no album of its own. Tracks without such a copy are absent from the result.
 */
fun findDownloadedTwins(
    tracks: List<Track>,
    downloaded: List<SongEntity>,
    albumTitle: String,
): Map<String, SongEntity> {
    if (tracks.isEmpty() || downloaded.isEmpty()) return emptyMap()
    val downloadedIds = downloaded.mapTo(HashSet()) { it.videoId }
    val byTitle = downloaded.groupBy { it.title.twinKey() }
    val result = HashMap<String, SongEntity>()
    for (track in tracks) {
        if (track.videoId in downloadedIds) continue
        val candidates = byTitle[track.title.twinKey()] ?: continue
        val album = (track.album?.name?.takeIf { it.isNotBlank() } ?: albumTitle).twinKey()
        val artists = track.artists.orEmpty().mapTo(HashSet()) { it.name.twinKey() }
        val seconds = track.durationSeconds ?: 0
        val twin =
            candidates
                .filter { song ->
                    song.albumName?.twinKey() == album &&
                        song.artistName.orEmpty().any { it.twinKey() in artists } &&
                        (seconds <= 0 || song.durationSeconds <= 0 || abs(song.durationSeconds - seconds) <= TWIN_DURATION_TOLERANCE_SECONDS)
                }.minByOrNull { abs(it.durationSeconds - seconds) }
        if (twin != null) result[track.videoId] = twin
    }
    return result
}
