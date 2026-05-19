package com.awilson.focuslauncher

import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import com.awilson.focuslauncher.data.FocusDndController
import com.awilson.focuslauncher.data.FocusPrefs
import com.awilson.focuslauncher.data.PauseAlarm
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.launch

/**
 * Fires when a timed pause ends. Re-arms focus mode and brings the launcher back to the front.
 */
class PauseEndReceiver : BroadcastReceiver() {

    override fun onReceive(context: Context, intent: Intent) {
        if (intent.action != PauseAlarm.ACTION_PAUSE_END) return
        val pendingResult = goAsync()
        CoroutineScope(Dispatchers.IO).launch {
            try {
                val prefs = FocusPrefs(context.applicationContext)
                val state = runCatching { prefs.state.first() }.getOrNull()
                if (state != null && state.onboardingComplete) {
                    prefs.setPausedUntil(0L)
                    prefs.setFocusModeActive(true)
                    FocusDndController.applyFocus(context.applicationContext, prefs, prefs.state.first())
                }
                FocusPausedService.stop(context.applicationContext)
                try {
                    context.startActivity(
                        Intent(context.applicationContext, LauncherActivity::class.java)
                            .addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
                            .addFlags(Intent.FLAG_ACTIVITY_REORDER_TO_FRONT)
                            .putExtra(LauncherActivity.EXTRA_FORCE_RESUME, true),
                    )
                } catch (_: Throwable) {
                    // OK if we can't bring it to front; the next lock/unlock will land on us anyway.
                }
            } finally {
                pendingResult.finish()
            }
        }
    }
}
