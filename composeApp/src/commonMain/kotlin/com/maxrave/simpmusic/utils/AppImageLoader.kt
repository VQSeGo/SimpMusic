package com.maxrave.simpmusic.utils

import coil3.EventListener
import coil3.ImageLoader
import coil3.PlatformContext
import coil3.annotation.ExperimentalCoilApi
import coil3.compose.preferEndFirstIntrinsicSize
import coil3.disk.DiskCache
import coil3.network.okhttp.OkHttpNetworkFetcherFactory
import coil3.request.CachePolicy
import coil3.request.ErrorResult
import coil3.request.ImageRequest
import coil3.request.SuccessResult
import coil3.request.crossfade
import com.maxrave.logger.Logger
import okhttp3.Dispatcher
import okhttp3.OkHttpClient
import okio.FileSystem
import okio.Path.Companion.toPath
import java.io.File
import java.util.concurrent.TimeUnit
import kotlin.time.TimeSource

/**
 * The one image loader both platforms build: artwork cache in a persistent directory (not the
 * system temp directory, which a desktop empties on reboot) and an HTTP client that gives up
 * quickly on a network that accepts nothing, so a stale cover falls back to disk within seconds.
 */
object AppImageLoader {
    private const val TAG = "Offline"
    private const val CACHE_DIR_NAME = "image_cache"
    private const val MAX_CACHE_BYTES = 512L * 1024 * 1024

    // A cover is tens of kilobytes: 3 s without a connection or 8 s of silence means the network is not usable.
    private const val CONNECT_TIMEOUT_SECONDS = 3L
    private const val READ_TIMEOUT_SECONDS = 8L
    private const val MAX_REQUESTS = 32

    // Only what helps to tell "came from disk" from "waited for the network" goes to the log.
    private const val SLOW_LOAD_MILLIS = 1000L

    /** [parentDir] is `~/.simpmusic` on desktop and the app's cache directory on Android. */
    fun cacheDirectory(parentDir: File): File = File(parentDir, CACHE_DIR_NAME)

    @OptIn(ExperimentalCoilApi::class)
    fun build(
        context: PlatformContext,
        cacheDir: File,
    ): ImageLoader {
        removeLegacyCache(cacheDir)
        Logger.i(TAG, "image cache: ${cacheDir.absolutePath}")
        lateinit var imageLoader: ImageLoader
        imageLoader =
            ImageLoader
                .Builder(context)
                .components {
                    add(NetworkFirstInterceptor { imageLoader })
                    add(
                        OkHttpNetworkFetcherFactory(
                            callFactory = {
                                OkHttpClient
                                    .Builder()
                                    .dispatcher(
                                        // OkHttp runs 5 calls per host by default: on a hanging network a
                                        // screenful of covers then waits for the timeout in turns of five.
                                        Dispatcher().apply {
                                            maxRequests = MAX_REQUESTS
                                            maxRequestsPerHost = MAX_REQUESTS
                                        },
                                    )
                                    .connectTimeout(CONNECT_TIMEOUT_SECONDS, TimeUnit.SECONDS)
                                    .readTimeout(READ_TIMEOUT_SECONDS, TimeUnit.SECONDS)
                                    .build()
                            },
                        ),
                    )
                }.diskCachePolicy(CachePolicy.ENABLED)
                .networkCachePolicy(CachePolicy.ENABLED)
                .diskCache {
                    DiskCache
                        .Builder()
                        .directory(cacheDir.absolutePath.toPath())
                        .maxSizeBytes(MAX_CACHE_BYTES)
                        .build()
                }.eventListenerFactory { SlowImageLogger() }
                .crossfade(true)
                // CrossfadePainter otherwise sizes itself as max(placeholder, image) once and keeps it,
                // so a square holder over a wide banner leaves the banner zoomed after the fade.
                .preferEndFirstIntrinsicSize(true)
                .build()
        return imageLoader
    }

    /**
     * Older builds kept the cache in okio's temporary directory (`/tmp/image_cache` on desktop).
     * Nothing reads it any more, so it goes once, off the calling thread. On Android that directory
     * already was `cacheDir/image_cache`, the same as the new one, and stays.
     */
    private fun removeLegacyCache(cacheDir: File) {
        val legacy = File((FileSystem.SYSTEM_TEMPORARY_DIRECTORY / CACHE_DIR_NAME).toString())
        val same =
            runCatching { legacy.canonicalPath == cacheDir.canonicalPath }.getOrDefault(true)
        if (same || !legacy.exists()) return
        Thread({
            try {
                val bytes = legacy.walkBottomUp().filter { it.isFile }.sumOf { it.length() }
                val deleted = legacy.deleteRecursively()
                Logger.i(TAG, "image cache: legacy ${legacy.absolutePath} removed=$deleted (${bytes / 1024 / 1024} MB)")
            } catch (error: Exception) {
                Logger.w(TAG, "image cache: legacy ${legacy.absolutePath} not removed (${error::class.simpleName})")
            }
        }, "image-cache-cleanup").apply {
            isDaemon = true
            start()
        }
    }

    private class SlowImageLogger : EventListener() {
        private var started: TimeSource.Monotonic.ValueTimeMark? = null

        override fun onStart(request: ImageRequest) {
            started = TimeSource.Monotonic.markNow()
        }

        override fun onSuccess(
            request: ImageRequest,
            result: SuccessResult,
        ) {
            val millis = started?.elapsedNow()?.inWholeMilliseconds ?: return
            if (millis >= SLOW_LOAD_MILLIS) {
                Logger.d(TAG) { "image ${result.dataSource} $millis ms ${request.data.toString().take(60)}" }
            }
        }

        override fun onError(
            request: ImageRequest,
            result: ErrorResult,
        ) {
            val millis = started?.elapsedNow()?.inWholeMilliseconds ?: return
            Logger.d(TAG) {
                "image failed $millis ms ${result.throwable::class.simpleName} ${request.data.toString().take(60)}"
            }
        }
    }
}
