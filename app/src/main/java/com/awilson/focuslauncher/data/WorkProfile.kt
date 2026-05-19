package com.awilson.focuslauncher.data

import android.content.Context
import android.content.pm.LauncherApps
import android.os.Process
import android.os.UserHandle
import android.os.UserManager

/**
 * Helpers for the device's work profile, accessed via LauncherApps (which the HOME role holder is
 * allowed to use without QUERY_ALL_PACKAGES). Treats "work profile" as "the first non-personal
 * profile returned by LauncherApps.profiles". Returns null/empty when there is no work profile.
 */
class WorkProfile(private val context: Context) {

    private val launcherApps: LauncherApps? = context.getSystemService(LauncherApps::class.java)
    private val userManager: UserManager? = context.getSystemService(UserManager::class.java)

    private val personalHandle: UserHandle = Process.myUserHandle()

    /** The work profile's UserHandle, or null if the device has no work profile. */
    val handle: UserHandle?
        get() = launcherApps?.profiles?.firstOrNull { it != personalHandle }

    /** True if a work profile exists AND is not currently in quiet mode (Android 14+ pause toggle). */
    val isAvailable: Boolean
        get() {
            val h = handle ?: return false
            val quiet = userManager?.isQuietModeEnabled(h) == true
            return !quiet
        }

    /** All launchable apps in the work profile, sorted alphabetically. Empty if no work profile. */
    fun launchableApps(): List<LaunchableApp> {
        val h = handle ?: return emptyList()
        val la = launcherApps ?: return emptyList()
        return la.getActivityList(null, h).asSequence()
            .map { LaunchableApp(packageName = it.applicationInfo.packageName, label = it.label.toString()) }
            .distinctBy { it.packageName }
            .sortedBy { it.label.lowercase() }
            .toList()
    }

    /**
     * Launch the work-profile app with the given package name. Returns true on success.
     */
    fun launch(packageName: String): Boolean {
        val h = handle ?: return false
        val la = launcherApps ?: return false
        val activity = la.getActivityList(packageName, h).firstOrNull() ?: return false
        return runCatching {
            la.startMainActivity(activity.componentName, h, null, null)
        }.isSuccess
    }
}
