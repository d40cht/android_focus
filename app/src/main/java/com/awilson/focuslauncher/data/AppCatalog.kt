package com.awilson.focuslauncher.data

import android.app.AppOpsManager
import android.app.usage.UsageStatsManager
import android.content.Context
import android.content.Intent
import android.content.pm.PackageManager
import android.content.pm.ResolveInfo
import android.os.Process
import android.provider.Settings

data class LaunchableApp(
    val packageName: String,
    val label: String,
)

object AppCatalog {

    /**
     * All apps with a CATEGORY_LAUNCHER activity, sorted by label.
     * Excludes our own package.
     */
    fun launchableApps(context: Context): List<LaunchableApp> {
        val intent = Intent(Intent.ACTION_MAIN).addCategory(Intent.CATEGORY_LAUNCHER)
        val pm = context.packageManager
        val resolved: List<ResolveInfo> = pm.queryIntentActivities(intent, 0)
        return resolved.asSequence()
            .map { info ->
                val pkg = info.activityInfo.packageName
                val label = info.loadLabel(pm).toString().ifBlank { pkg }
                LaunchableApp(packageName = pkg, label = label)
            }
            .filter { it.packageName != context.packageName }
            .distinctBy { it.packageName }
            .sortedBy { it.label.lowercase() }
            .toList()
    }

    /**
     * All apps with a CATEGORY_HOME activity. Used for fallback launcher selection.
     * Excludes our own package and the Android resolver stub.
     */
    fun homeApps(context: Context): List<LaunchableApp> {
        val intent = Intent(Intent.ACTION_MAIN).addCategory(Intent.CATEGORY_HOME)
        val pm = context.packageManager
        val resolved: List<ResolveInfo> = pm.queryIntentActivities(intent, 0)
        return resolved.asSequence()
            .map { info ->
                val pkg = info.activityInfo.packageName
                val label = info.loadLabel(pm).toString().ifBlank { pkg }
                LaunchableApp(packageName = pkg, label = label)
            }
            .filter { it.packageName != context.packageName && it.packageName != "android" }
            .distinctBy { it.packageName }
            .sortedBy { it.label.lowercase() }
            .toList()
    }

    /**
     * Resolve the current default home package. Returns null if there is no clear default
     * (e.g. the Android resolver disambiguator is in the way), or if we are the default.
     */
    fun currentHomePackage(context: Context): String? {
        val intent = Intent(Intent.ACTION_MAIN).addCategory(Intent.CATEGORY_HOME)
        val info = context.packageManager.resolveActivity(intent, PackageManager.MATCH_DEFAULT_ONLY)
        val pkg = info?.activityInfo?.packageName ?: return null
        return when (pkg) {
            "android", context.packageName -> null
            else -> pkg
        }
    }

    fun isInstalled(context: Context, packageName: String): Boolean = try {
        context.packageManager.getApplicationInfo(packageName, 0)
        true
    } catch (_: PackageManager.NameNotFoundException) {
        false
    }

    fun hasUsageStatsPermission(context: Context): Boolean {
        val appOps = context.getSystemService(AppOpsManager::class.java) ?: return false
        val mode = appOps.unsafeCheckOpNoThrow(
            AppOpsManager.OPSTR_GET_USAGE_STATS,
            Process.myUid(),
            context.packageName,
        )
        return mode == AppOpsManager.MODE_ALLOWED
    }

    fun openUsageAccessSettings(context: Context) {
        context.startActivity(
            Intent(Settings.ACTION_USAGE_ACCESS_SETTINGS)
                .addFlags(Intent.FLAG_ACTIVITY_NEW_TASK),
        )
    }

    /**
     * Apps with a launchable intent, sorted by descending foreground-time over the last 30 days.
     * Falls back to alphabetical if usage stats are unavailable or the permission is missing.
     */
    fun launchableAppsByUsage(context: Context): List<LaunchableApp> {
        val base = launchableApps(context)
        if (!hasUsageStatsPermission(context)) return base
        val usm = context.getSystemService(UsageStatsManager::class.java) ?: return base

        val now = System.currentTimeMillis()
        val thirtyDaysAgo = now - 30L * 24 * 60 * 60 * 1000
        val statsList = runCatching {
            usm.queryUsageStats(UsageStatsManager.INTERVAL_BEST, thirtyDaysAgo, now)
        }.getOrNull().orEmpty()

        if (statsList.isEmpty()) return base

        val totals = HashMap<String, Long>(statsList.size)
        for (s in statsList) {
            // Merge multiple entries per package by summing.
            totals[s.packageName] = (totals[s.packageName] ?: 0L) + s.totalTimeInForeground
        }

        return base.sortedWith(
            compareByDescending<LaunchableApp> { totals[it.packageName] ?: 0L }
                .thenBy { it.label.lowercase() },
        )
    }
}
