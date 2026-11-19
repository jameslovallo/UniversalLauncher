package com.example

import android.app.Activity
import android.app.Application
import android.content.Context
import android.os.Bundle
import coil.ImageLoader
import coil.ImageLoaderFactory
import coil.disk.DiskCache
import coil.memory.MemoryCache
import java.io.File
import java.lang.ref.WeakReference

/**
 * Configures a shared Coil [ImageLoader] with larger memory and disk caches so
 * scrolling through app icons and game posters keeps bitmaps hot in RAM and avoids
 * HTTP cache revalidation overhead, and tracks the active foreground [Activity] for
 * Google Play Services authorization prompts.
 */
class NexusApplication : Application(), ImageLoaderFactory {

    override fun onCreate() {
        super.onCreate()
        registerActivityLifecycleCallbacks(object : ActivityLifecycleCallbacks {
            override fun onActivityCreated(activity: Activity, savedInstanceState: Bundle?) {
                activeActivityRef = WeakReference(activity)
            }

            override fun onActivityStarted(activity: Activity) {
                activeActivityRef = WeakReference(activity)
            }

            override fun onActivityResumed(activity: Activity) {
                activeActivityRef = WeakReference(activity)
            }

            override fun onActivityPaused(activity: Activity) = Unit

            override fun onActivityStopped(activity: Activity) = Unit

            override fun onActivitySaveInstanceState(activity: Activity, outState: Bundle) = Unit

            override fun onActivityDestroyed(activity: Activity) {
                if (activeActivityRef.get() === activity) {
                    activeActivityRef.clear()
                }
            }
        })
    }

    override fun newImageLoader(): ImageLoader = buildAppImageLoader(this)

    companion object {
        // Bitmaps are requested at their display size, so a large cache only pins memory
        // the launcher cannot spare on low-RAM TV boxes. 12% keeps a screenful or two hot
        // without competing with the Compose UI heap.
        internal const val MEMORY_CACHE_PERCENT = 0.12
        internal const val DISK_CACHE_MAX_BYTES = 150L * 1024L * 1024L
        internal const val DISK_CACHE_DIR = "image_cache"

        @Volatile
        private var activeActivityRef: WeakReference<Activity> = WeakReference(null)

        fun currentForegroundActivity(): Activity? =
            activeActivityRef.get()?.takeIf { !it.isFinishing && !it.isDestroyed }

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
