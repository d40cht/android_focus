package com.awilson.focuslauncher

import android.app.Application
import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.content.IntentFilter
import android.util.Log
import com.awilson.focuslauncher.data.FocusPrefs
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.launch

/**
 * Owns the SCREEN_OFF receiver that brings Focus to the front whenever the screen turns off, so the
 * next unlock lands on the launcher rather than whatever app the user last had open — "every unlock
 * is a fresh intent declaration".
 *
 * It stays out of the way during an active timed/indefinite pause: if the user explicitly paused
 * until 8pm, locking and unlocking should leave them on the full phone, not snap them back to Focus.
 *
 * Caveat: a dynamically-registered receiver only fires while our process is alive. After Focus has
 * been foreground recently the process is warm, which covers the common "lock right after using
 * Focus" case. If the user spent a long time in another app and the system reclaimed our process,
 * the receiver won't fire and the unlock returns to that app — acceptable for v1.
 */
class FocusApplication : Application() {

    private val appScope = CoroutineScope(SupervisorJob() + Dispatchers.IO)
    private val prefs by lazy { FocusPrefs(applicationContext) }

    private val screenOffReceiver = object : BroadcastReceiver() {
        override fun onReceive(context: Context, intent: Intent) {
            if (intent.action != Intent.ACTION_SCREEN_OFF) return
            appScope.launch { bringFocusForwardUnlessPaused() }
        }
    }

    override fun onCreate() {
        super.onCreate()
        registerReceiver(screenOffReceiver, IntentFilter(Intent.ACTION_SCREEN_OFF))
    }

    private suspend fun bringFocusForwardUnlessPaused() {
        val state = runCatching { prefs.state.first() }.getOrNull() ?: return
        if (!state.onboardingComplete) return

        // Active timed/indefinite pause: leave the user on the full phone.
        if (state.pausedUntilEpochMs != 0L) {
            val stillPaused = state.pausedUntilEpochMs == Long.MAX_VALUE ||
                System.currentTimeMillis() < state.pausedUntilEpochMs
            if (stillPaused) return
        }

        try {
            startActivity(
                Intent(applicationContext, LauncherActivity::class.java)
                    .addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
                    .addFlags(Intent.FLAG_ACTIVITY_REORDER_TO_FRONT)
                    .putExtra(LauncherActivity.EXTRA_FORCE_RESUME, true),
            )
        } catch (t: Throwable) {
            Log.w(TAG, "Couldn't bring launcher to front on screen off", t)
        }
    }

    companion object {
        private const val TAG = "FocusApp"
    }
}
