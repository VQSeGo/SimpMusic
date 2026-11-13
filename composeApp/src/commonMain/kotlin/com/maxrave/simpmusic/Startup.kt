package com.maxrave.simpmusic

import com.maxrave.logger.Logger

/**
 * Startup timeline marks. Info level, so they reach logcat and `app.log` in release builds.
 * the cold-start scripts in `scripts/perf` read them: the line is `Startup: <name> <ms> ms`, with the
 * milliseconds counted from process start.
 */
object Startup {
    private const val TAG = "Startup"
    private val seen = mutableSetOf<String>()

    fun mark(name: String) {
        Logger.i(TAG, "$name ${millisSinceProcessStart()} ms")
    }

    /** Marks [name] on its first call in this process; returns false on every later call. Main thread only. */
    fun markOnce(name: String): Boolean {
        val first = seen.add(name)
        if (first) mark(name)
        return first
    }
}
