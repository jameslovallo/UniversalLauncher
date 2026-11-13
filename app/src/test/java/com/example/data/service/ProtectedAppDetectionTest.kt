package com.example.data.service

import android.app.Application
import android.app.admin.DevicePolicyManager
import android.content.ComponentName
import android.content.Context
import android.content.pm.ApplicationInfo
import android.content.pm.PackageInfo
import android.os.UserManager
import androidx.test.core.app.ApplicationProvider
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.Shadows.shadowOf
import org.robolectric.annotation.Config
import org.robolectric.shadows.ShadowDevicePolicyManager
import org.robolectric.shadows.ShadowPackageManager
import org.robolectric.shadows.ShadowUserManager

@RunWith(RobolectricTestRunner::class)
@Config(sdk = [34])
class ProtectedAppDetectionTest {

    private lateinit var context: Application
    private lateinit var manager: InstalledAppsManager
    private lateinit var shadowPackageManager: ShadowPackageManager
    private lateinit var shadowUserManager: ShadowUserManager
    private lateinit var shadowDpm: ShadowDevicePolicyManager

    @Before
    fun setUp() {
        context = ApplicationProvider.getApplicationContext()
        manager = InstalledAppsManager(context)
        shadowPackageManager = shadowOf(context.packageManager)
        val um = context.getSystemService(Context.USER_SERVICE) as UserManager
        shadowUserManager = shadowOf(um)
        val dpm = context.getSystemService(Context.DEVICE_POLICY_SERVICE) as DevicePolicyManager
        shadowDpm = shadowOf(dpm)
    }

    private fun installFakePackage(pkg: String, flags: Int) {
        val appInfo = ApplicationInfo().apply {
            packageName = pkg
            this.flags = flags
        }
        val packageInfo = PackageInfo().apply {
            packageName = pkg
            applicationInfo = appInfo
        }
        shadowPackageManager.installPackage(packageInfo)
    }

    @Test
    fun `user installed package without restrictions is not protected`() {
        installFakePackage("com.example.userapp", flags = 0)

        assertFalse(manager.isProtectedApp("com.example.userapp"))
    }

    @Test
    fun `system app flag marks package as protected`() {
        installFakePackage("com.amazon.venezia", flags = ApplicationInfo.FLAG_SYSTEM)

        assertTrue(manager.isProtectedApp("com.amazon.venezia"))
    }

    @Test
    fun `updated system app flag marks package as protected`() {
        installFakePackage(
            "com.amazon.cloud9",
            flags = ApplicationInfo.FLAG_SYSTEM or ApplicationInfo.FLAG_UPDATED_SYSTEM_APP
        )

        assertTrue(manager.isProtectedApp("com.amazon.cloud9"))
    }

    @Test
    fun `persistent app flag marks package as protected`() {
        installFakePackage("com.amazon.core", flags = ApplicationInfo.FLAG_PERSISTENT)

        assertTrue(manager.isProtectedApp("com.amazon.core"))
    }

    @Test
    fun `disallow uninstall user restriction marks apps as protected`() {
        installFakePackage("com.example.userapp", flags = 0)
        shadowUserManager.setUserRestriction(
            android.os.Process.myUserHandle(),
            UserManager.DISALLOW_UNINSTALL_APPS,
            true
        )

        assertTrue(manager.isProtectedApp("com.example.userapp"))
    }

    @Test
    fun `disallow apps control restriction marks apps as protected`() {
        installFakePackage("com.example.userapp", flags = 0)
        shadowUserManager.setUserRestriction(
            android.os.Process.myUserHandle(),
            UserManager.DISALLOW_APPS_CONTROL,
            true
        )

        assertTrue(manager.isProtectedApp("com.example.userapp"))
    }

    @Test
    fun `active device admin package is protected`() {
        installFakePackage("com.example.adminapp", flags = 0)
        shadowDpm.setActiveAdmin(ComponentName("com.example.adminapp", "AdminReceiver"))

        assertTrue(manager.isProtectedApp("com.example.adminapp"))
    }

    @Test
    fun `missing package is not treated as protected so stale tiles can be removed`() {
        assertFalse(manager.isProtectedApp("com.example.uninstalled"))
    }
}
