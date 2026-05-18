package com.awilson.focuslauncher

import android.app.Application
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
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.launch

/**
 * Lives as long as the process. Owns the SCREEN_OFF receiver that re-arms focus mode whenever the
 * device locks, so the next unlock takes the user back to Focus rather than the fallback launcher.
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

    override fun onCreate() {
        super.onCreate()
        registerReceiver(
            screenOffReceiver,
            IntentFilter(Intent.ACTION_SCREEN_OFF),
        )
    }

    private suspend fun reArmFocus() {
        val state = runCatching { prefs.state.first() }.getOrNull() ?: return
        if (!state.onboardingComplete) return

        prefs.setFocusModeActive(true)
        FocusDndController.applyFocus(applicationContext, state.dndFilter, state.autoDismissNotifications)

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

    companion object {
        private const val TAG = "FocusApp"
    }
}
