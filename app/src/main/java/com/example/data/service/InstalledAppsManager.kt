package com.example.data.service

import android.content.Context
import android.content.Intent
import android.content.pm.PackageManager
import android.content.pm.ResolveInfo
import android.net.Uri
import android.os.Build
import android.provider.Settings
import com.example.data.model.GameItem
import com.example.data.model.GameType
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext

class InstalledAppsManager(private val context: Context) {
    private val packageManager: PackageManager = context.packageManager

    /**
     * Query all launchable apps on the device.
     */
    suspend fun getInstalledApps(): List<GameItem> = withContext(Dispatchers.IO) {
        val launcherIntent = Intent(Intent.ACTION_MAIN, null).apply {
            addCategory(Intent.CATEGORY_LAUNCHER)
        }

        val flags = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
            PackageManager.ResolveInfoFlags.of(0L)
        } else {
            0
        }

        val resolveInfos: List<ResolveInfo> = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
            packageManager.queryIntentActivities(launcherIntent, flags as PackageManager.ResolveInfoFlags)
        } else {
            @Suppress("DEPRECATION")
            packageManager.queryIntentActivities(launcherIntent, flags as Int)
        }

        val myPackageName = context.packageName

        resolveInfos.filter { it.activityInfo.packageName != myPackageName }.map { resolveInfo ->
            val pkg = resolveInfo.activityInfo.packageName
            val label = resolveInfo.loadLabel(packageManager).toString()
            val activityName = resolveInfo.activityInfo.name

            // Build android resource URI for app icon
            val iconUri = "android.resource://$pkg/${resolveInfo.activityInfo.iconResource}"

            GameItem(
                id = "app_$pkg",
                title = label,
                type = GameType.INSTALLED_APP,
                packageName = pkg,
                activityName = activityName,
                iconUrl = iconUri,
                genre = "Installed Application",
                publisher = pkg,
                description = "Installed Android app ($label)",
                tags = "Installed,Android,App"
            )
        }.sortedBy { it.title.lowercase() }
    }

    /**
     * Launch an installed app by package name.
     */
    fun launchApp(packageName: String): Boolean {
        return try {
            val intent = packageManager.getLaunchIntentForPackage(packageName)
            if (intent != null) {
                intent.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
                context.startActivity(intent)
                true
            } else {
                false
            }
        } catch (e: Exception) {
            false
        }
    }

    /**
     * Open system settings to configure default Home/Launcher app.
     */
    fun openHomeSettings() {
        try {
            val intent = Intent(Settings.ACTION_HOME_SETTINGS).apply {
                addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
            }
            context.startActivity(intent)
        } catch (e: Exception) {
            try {
                val intent = Intent(Settings.ACTION_SETTINGS).apply {
                    addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
                }
                context.startActivity(intent)
            } catch (_: Exception) {}
        }
    }
}
