package com.awilson.focuslauncher

import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import com.awilson.focuslauncher.data.FocusPrefs
import com.awilson.focuslauncher.data.PauseAlarm
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.launch

/**
 * On reboot, end any active pause and return to focus mode.
 *
 * AlarmManager alarms don't survive a reboot, so a timed pause would otherwise lose its scheduled
 * end and leave the user permanently bounced to the fallback launcher. Treating a reboot as a fresh
 * focus start is both simpler and matches the expectation that powering the phone back on lands you
 * in Focus.
 */
class BootReceiver : BroadcastReceiver() {

    override fun onReceive(context: Context, intent: Intent) {
        if (intent.action != Intent.ACTION_BOOT_COMPLETED) return
        val pendingResult = goAsync()
        CoroutineScope(Dispatchers.IO).launch {
            try {
                val prefs = FocusPrefs(context.applicationContext)
                val state = runCatching { prefs.state.first() }.getOrNull()
                if (state != null && state.onboardingComplete) {
                    prefs.setPausedUntil(0L)
                    prefs.setFocusModeActive(true)
                    PauseAlarm.cancel(context.applicationContext)
                    FocusPausedService.stop(context.applicationContext)
                }
            } finally {
                pendingResult.finish()
            }
        }
    }
}
