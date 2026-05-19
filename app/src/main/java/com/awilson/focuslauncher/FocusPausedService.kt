package com.awilson.focuslauncher

import android.app.Notification
import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.PendingIntent
import android.app.Service
import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.content.IntentFilter
import android.content.pm.ServiceInfo
import android.os.Build
import android.os.IBinder
import android.util.Log
import com.awilson.focuslauncher.data.FocusDndController
import com.awilson.focuslauncher.data.FocusPrefs
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.cancel
import kotlinx.coroutines.flow.distinctUntilChanged
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.launch
import java.text.DateFormat
import java.util.Date

/**
 * Foreground service that runs while focus is paused.
 *
 * Three pause modes:
 *  - snap-back (pausedUntilEpochMs == 0): re-arm on next screen off
 *  - timed (pausedUntilEpochMs == future ms): AlarmManager re-arms us at that time; screen-off
 *    during the pause is ignored so the user can lock and unlock without snapping back
 *  - indefinite (pausedUntilEpochMs == Long.MAX_VALUE): never auto re-arms; user must tap the
 *    notification or open the app to return
 */
class FocusPausedService : Service() {

    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.Main.immediate)
    private val prefs by lazy { FocusPrefs(applicationContext) }

    private var receiverRegistered = false
    private val screenOffReceiver = object : BroadcastReceiver() {
        override fun onReceive(context: Context, intent: Intent) {
            if (intent.action != Intent.ACTION_SCREEN_OFF) return
            scope.launch {
                val state = runCatching { prefs.state.first() }.getOrNull() ?: return@launch
                // Snap-back only: scheduled and indefinite pauses ignore the lock.
                if (state.pausedUntilEpochMs != 0L) return@launch
                reArmFocusAndStop()
            }
        }
    }

    override fun onBind(intent: Intent?): IBinder? = null

    override fun onCreate() {
        super.onCreate()
        ensureNotificationChannel()
        // Re-render the notification when paused-until changes (e.g. user picked a different
        // duration without leaving the launcher).
        scope.launch {
            prefs.state
                .map { it.pausedUntilEpochMs }
                .distinctUntilChanged()
                .collect { _ ->
                    val nm = getSystemService(NotificationManager::class.java)
                    nm?.notify(NOTIFICATION_ID, buildNotificationFromCurrentState())
                }
        }
    }

    override fun onStartCommand(intent: Intent?, flags: Int, startId: Int): Int {
        val notification = buildNotificationFromCurrentState()
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.UPSIDE_DOWN_CAKE) {
            startForeground(
                NOTIFICATION_ID,
                notification,
                ServiceInfo.FOREGROUND_SERVICE_TYPE_SPECIAL_USE,
            )
        } else {
            startForeground(NOTIFICATION_ID, notification)
        }
        if (!receiverRegistered) {
            registerReceiver(screenOffReceiver, IntentFilter(Intent.ACTION_SCREEN_OFF))
            receiverRegistered = true
        }
        return START_STICKY
    }

    override fun onDestroy() {
        if (receiverRegistered) {
            runCatching { unregisterReceiver(screenOffReceiver) }
            receiverRegistered = false
        }
        scope.cancel()
        super.onDestroy()
    }

    private suspend fun reArmFocusAndStop() {
        val state = runCatching { prefs.state.first() }.getOrNull()
        if (state != null && state.onboardingComplete) {
            prefs.setFocusModeActive(true)
            prefs.setPausedUntil(0L)
            FocusDndController.applyFocus(applicationContext, prefs, prefs.state.first())
            try {
                startActivity(
                    Intent(applicationContext, LauncherActivity::class.java)
                        .addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
                        .addFlags(Intent.FLAG_ACTIVITY_REORDER_TO_FRONT)
                        .putExtra(LauncherActivity.EXTRA_FORCE_RESUME, true),
                )
            } catch (t: Throwable) {
                Log.w(TAG, "Couldn't bring LauncherActivity to front from paused service", t)
            }
        }
        stopSelf()
    }

    private fun buildNotificationFromCurrentState(): Notification {
        // Read prefs synchronously off the cached snapshot — runBlocking would block the main thread.
        // We re-render from the Flow observer when prefs change.
        val until = runCatching {
            kotlinx.coroutines.runBlocking { prefs.state.first().pausedUntilEpochMs }
        }.getOrNull() ?: 0L
        val (title, body) = when {
            until == 0L -> "Focus is paused" to "Tap to return now, or lock + unlock to snap back."
            until == Long.MAX_VALUE -> "Focus is paused indefinitely" to "Tap to return whenever you're ready."
            else -> {
                val timeStr = DateFormat.getTimeInstance(DateFormat.SHORT).format(Date(until))
                "Focus paused until $timeStr" to "Tap to return now."
            }
        }
        return buildNotification(title, body)
    }

    private fun buildNotification(title: String, body: String): Notification {
        val tapIntent = PendingIntent.getActivity(
            this,
            0,
            Intent(this, LauncherActivity::class.java)
                .addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
                .addFlags(Intent.FLAG_ACTIVITY_REORDER_TO_FRONT)
                .putExtra(LauncherActivity.EXTRA_FORCE_RESUME, true),
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE,
        )
        return Notification.Builder(this, CHANNEL_ID)
            .setSmallIcon(R.drawable.ic_launcher_foreground)
            .setContentTitle(title)
            .setContentText(body)
            .setOngoing(true)
            .setShowWhen(false)
            .setContentIntent(tapIntent)
            .build()
    }

    private fun ensureNotificationChannel() {
        val nm = getSystemService(NotificationManager::class.java) ?: return
        if (nm.getNotificationChannel(CHANNEL_ID) != null) return
        val channel = NotificationChannel(
            CHANNEL_ID,
            "Focus paused",
            NotificationManager.IMPORTANCE_MIN,
        ).apply {
            description = "Visible while you've temporarily unlocked the full phone"
            setShowBadge(false)
        }
        nm.createNotificationChannel(channel)
    }

    companion object {
        private const val TAG = "FocusPaused"
        private const val CHANNEL_ID = "focus_paused"
        private const val NOTIFICATION_ID = 1001

        fun start(context: Context) {
            val intent = Intent(context, FocusPausedService::class.java)
            context.startForegroundService(intent)
        }

        fun stop(context: Context) {
            context.stopService(Intent(context, FocusPausedService::class.java))
        }
    }
}
