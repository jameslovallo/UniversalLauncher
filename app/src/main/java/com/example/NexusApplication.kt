package com.example

import android.app.Application
import android.content.Context
import coil.ImageLoader
import coil.ImageLoaderFactory
import coil.disk.DiskCache
import coil.memory.MemoryCache
import java.io.File

/**
 * Configures a shared Coil [ImageLoader] with larger memory and disk caches so
 * scrolling through app icons and game posters keeps bitmaps hot in RAM and avoids
 * HTTP cache revalidation overhead.
 */
class NexusApplication : Application(), ImageLoaderFactory {

    override fun newImageLoader(): ImageLoader = buildAppImageLoader(this)

    companion object {
        internal const val MEMORY_CACHE_PERCENT = 0.30
        internal const val DISK_CACHE_MAX_BYTES = 150L * 1024L * 1024L
        internal const val DISK_CACHE_DIR = "image_cache"

        fun buildAppImageLoader(context: Context): ImageLoader {
            val appContext = context.applicationContext
            return ImageLoader.Builder(appContext)
                .memoryCache {
                    MemoryCache.Builder(appContext)
                        .maxSizePercent(MEMORY_CACHE_PERCENT)
                        .build()
                }
                .diskCache {
                    DiskCache.Builder()
                        .directory(File(appContext.cacheDir, DISK_CACHE_DIR))
                        .maxSizeBytes(DISK_CACHE_MAX_BYTES)
                        .build()
                }
                .respectCacheHeaders(false)
                .crossfade(false)
                .build()
        }
    }
}
