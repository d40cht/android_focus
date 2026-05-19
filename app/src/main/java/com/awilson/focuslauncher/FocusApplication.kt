package com.awilson.focuslauncher

import android.app.Application
import android.app.NotificationManager
import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.content.IntentFilter
import android.util.Log
import com.awilson.focuslauncher.data.FocusDndController
import com.awilson.focuslauncher.data.FocusPrefs
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.flow.distinctUntilChanged
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.launch

/**
 * Lives as long as the process. Owns three pieces of cross-cutting state:
 *
 *  - A SCREEN_OFF receiver that re-arms focus mode whenever the device locks. This is the
 *    "warm-process" path; while the user is in unlock-full-phone mode, FocusPausedService also
 *    has its own SCREEN_OFF receiver that keeps the process alive.
 *  - A Flow observer on FocusPrefs.state that re-applies the DND policy whenever the relevant
 *    settings change (focusModeActive, dndFilter, autoDismissNotifications), so toggling a
 *    setting in SettingsActivity takes effect immediately.
 *  - A receiver for NotificationManager.ACTION_INTERRUPTION_FILTER_CHANGED that re-applies our
 *    policy if the system's DND state drifted from what we configured (e.g. user toggled DND
 *    off via quick settings while focus mode is active).
 */
class FocusApplication : Application() {

    private val appScope = CoroutineScope(SupervisorJob() + Dispatchers.IO)
    private val prefs by lazy { FocusPrefs(applicationContext) }

    private val screenOffReceiver = object : BroadcastReceiver() {
        override fun onReceive(context: Context, intent: Intent) {
            if (intent.action != Intent.ACTION_SCREEN_OFF) return
            appScope.launch { reArmFocus() }
        }
    }

    private val dndFilterChangedReceiver = object : BroadcastReceiver() {
        override fun onReceive(context: Context, intent: Intent) {
            if (intent.action != NotificationManager.ACTION_INTERRUPTION_FILTER_CHANGED) return
            appScope.launch {
                val state = runCatching { prefs.state.first() }.getOrNull() ?: return@launch
                if (!state.onboardingComplete || !state.focusModeActive) return@launch
                val nm = getSystemService(NotificationManager::class.java) ?: return@launch
                // Avoid infinite loops: only re-apply if the live filter is different from ours.
                if (nm.currentInterruptionFilter != state.dndFilter) {
                    FocusDndController.applyFocus(applicationContext, prefs, state)
                }
            }
        }
    }

    override fun onCreate() {
        super.onCreate()
        registerReceiver(screenOffReceiver, IntentFilter(Intent.ACTION_SCREEN_OFF))
        registerReceiver(
            dndFilterChangedReceiver,
            IntentFilter(NotificationManager.ACTION_INTERRUPTION_FILTER_CHANGED),
        )

        // Re-apply focus DND on every relevant settings change so changes in Settings take effect
        // without waiting for the next onResume.
        appScope.launch {
            prefs.state
                .map { Quad(it.onboardingComplete, it.focusModeActive, it.dndFilter, it.autoDismissNotifications) }
                .distinctUntilChanged()
                .collect { (onboarded, active, _, _) ->
                    if (!onboarded || !active) return@collect
                    val full = prefs.state.first()
                    FocusDndController.applyFocus(applicationContext, prefs, full)
                }
        }
    }

    private suspend fun reArmFocus() {
        val state = runCatching { prefs.state.first() }.getOrNull() ?: return
        if (!state.onboardingComplete) return

        prefs.setFocusModeActive(true)
        FocusDndController.applyFocus(applicationContext, prefs, prefs.state.first())

        // Bring LauncherActivity to the front so that when the user unlocks, they see Focus
        // rather than whichever app they had open when they locked. We're the HOME role holder,
        // which the system generally allows to start activities from the background.
        try {
            startActivity(
                Intent(applicationContext, LauncherActivity::class.java)
                    .addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
                    .addFlags(Intent.FLAG_ACTIVITY_REORDER_TO_FRONT),
            )
        } catch (t: Throwable) {
            Log.w(TAG, "Couldn't bring LauncherActivity to front on screen off", t)
        }
    }

    /** Tiny 4-tuple so we can distinctUntilChanged on the bits we care about. */
    private data class Quad(val a: Boolean, val b: Boolean, val c: Int, val d: Boolean)

    companion object {
        private const val TAG = "FocusApp"
    }
}
