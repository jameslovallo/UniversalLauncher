package com.example

import android.app.Application
import android.content.pm.PackageManager
import coil.ImageLoader
import coil.ImageLoaderFactory
import org.robolectric.Shadows.shadowOf

/**
 * Reports the leanback system feature that real Android TV devices expose. Compose uses it
 * to pick PivotBringIntoViewSpec, which requests a scroll even when the focused child is
 * already visible. Robolectric does not report the feature by default, so without this the
 * tests would miss the pager bounce that only happens on a TV.
 */
class TvTestApplication : Application(), ImageLoaderFactory {

    override fun newImageLoader(): ImageLoader = NexusApplication.buildAppImageLoader(this)

    override fun onCreate() {
        super.onCreate()
        shadowOf(packageManager)
            .setSystemFeature(PackageManager.FEATURE_LEANBACK, true)
    }
}
