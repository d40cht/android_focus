package com.awilson.focuslauncher.data

import android.app.NotificationManager
import android.content.Context
import androidx.datastore.core.DataStore
import androidx.datastore.preferences.core.Preferences
import androidx.datastore.preferences.core.booleanPreferencesKey
import androidx.datastore.preferences.core.edit
import androidx.datastore.preferences.core.intPreferencesKey
import androidx.datastore.preferences.core.stringPreferencesKey
import androidx.datastore.preferences.preferencesDataStore
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.map

val Context.focusDataStore: DataStore<Preferences> by preferencesDataStore(name = "focus_prefs")

data class FocusState(
    val onboardingComplete: Boolean,
    val fallbackLauncherPackage: String?,
    val gridApps: List<AppEntry>,
    val workGridApps: List<AppEntry>,
    val dndFilter: Int,
    val focusModeActive: Boolean,
    val autoDismissNotifications: Boolean,
)

class FocusPrefs(private val context: Context) {

    private object Keys {
        val onboardingComplete = booleanPreferencesKey("onboarding_complete")
        val fallbackLauncher = stringPreferencesKey("fallback_launcher")
        val gridApps = stringPreferencesKey("grid_apps")
        val workGridApps = stringPreferencesKey("work_grid_apps")
        val dndFilter = intPreferencesKey("dnd_filter")
        val focusModeActive = booleanPreferencesKey("focus_mode_active")
        val autoDismiss = booleanPreferencesKey("auto_dismiss_notifications")
    }

    val state: Flow<FocusState> = context.focusDataStore.data.map { prefs ->
        FocusState(
            onboardingComplete = prefs[Keys.onboardingComplete] == true,
            fallbackLauncherPackage = prefs[Keys.fallbackLauncher],
            gridApps = decodeGrid(prefs[Keys.gridApps].orEmpty()),
            workGridApps = decodeGrid(prefs[Keys.workGridApps].orEmpty()),
            dndFilter = prefs[Keys.dndFilter] ?: NotificationManager.INTERRUPTION_FILTER_PRIORITY,
            focusModeActive = prefs[Keys.focusModeActive] != false,
            autoDismissNotifications = prefs[Keys.autoDismiss] == true,
        )
    }

    suspend fun setOnboardingComplete(value: Boolean) {
        context.focusDataStore.edit { it[Keys.onboardingComplete] = value }
    }

    suspend fun setFallbackLauncher(pkg: String?) {
        context.focusDataStore.edit { prefs ->
            if (pkg == null) prefs.remove(Keys.fallbackLauncher) else prefs[Keys.fallbackLauncher] = pkg
        }
    }

    suspend fun setGridApps(apps: List<AppEntry>) {
        context.focusDataStore.edit { prefs ->
            prefs[Keys.gridApps] = encodeGrid(apps)
        }
    }

    suspend fun setWorkGridApps(apps: List<AppEntry>) {
        context.focusDataStore.edit { prefs ->
            prefs[Keys.workGridApps] = encodeGrid(apps)
        }
    }

    suspend fun setDndFilter(filter: Int) {
        context.focusDataStore.edit { it[Keys.dndFilter] = filter }
    }

    suspend fun setFocusModeActive(active: Boolean) {
        context.focusDataStore.edit { it[Keys.focusModeActive] = active }
    }

    suspend fun setAutoDismissNotifications(value: Boolean) {
        context.focusDataStore.edit { it[Keys.autoDismiss] = value }
    }

    companion object {
        // Each entry serialised as "pkg\tlabel"; entries separated by newlines.
        // Package names cannot contain tab or newline; labels are sanitised on write.
        private fun encodeGrid(apps: List<AppEntry>): String = apps.joinToString("\n") { entry ->
            val safeLabel = entry.label.replace('\t', ' ').replace('\n', ' ')
            "${entry.packageName}\t$safeLabel"
        }

        private fun decodeGrid(raw: String): List<AppEntry> {
            if (raw.isEmpty()) return emptyList()
            return raw.split('\n').mapNotNull { line ->
                val tab = line.indexOf('\t')
                if (tab <= 0) return@mapNotNull null
                AppEntry(
                    packageName = line.substring(0, tab),
                    label = line.substring(tab + 1),
                )
            }
        }
    }
}
