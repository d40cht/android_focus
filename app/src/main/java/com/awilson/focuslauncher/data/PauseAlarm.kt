package com.awilson.focuslauncher.data

import android.app.AlarmManager
import android.app.PendingIntent
import android.content.Context
import android.content.Intent

/**
 * Schedules and cancels the AlarmManager wakeup that ends a timed pause and re-arms focus mode.
 * Uses setAndAllowWhileIdle so the alarm fires even if the device is in Doze (overnight pauses).
 */
object PauseAlarm {

    const val ACTION_PAUSE_END = "com.awilson.focuslauncher.action.PAUSE_END"
    private const val REQUEST_CODE = 1

    fun schedule(context: Context, atEpochMs: Long) {
        val am = context.getSystemService(AlarmManager::class.java) ?: return
        val pi = pendingIntent(context)
        runCatching {
            am.setAndAllowWhileIdle(AlarmManager.RTC_WAKEUP, atEpochMs, pi)
        }
    }

    fun cancel(context: Context) {
        val am = context.getSystemService(AlarmManager::class.java) ?: return
        am.cancel(pendingIntent(context))
    }

    private fun pendingIntent(context: Context): PendingIntent {
        val intent = Intent(context, com.awilson.focuslauncher.PauseEndReceiver::class.java)
            .setAction(ACTION_PAUSE_END)
        return PendingIntent.getBroadcast(
            context,
            REQUEST_CODE,
            intent,
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE,
        )
    }
}
