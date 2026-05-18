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
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.launch

/**
 * Foreground service that runs while focus is paused (i.e. user is in "Unlock full phone" mode).
 *
 * Why a service: a dynamically-registered SCREEN_OFF receiver dies with the process. Android will
 * kill an idle background process within minutes; without a foreground service we can't reliably
 * notice the lock event. A foreground service keeps our process at foreground priority so the
 * receiver stays attached.
 *
 * The notification doubles as a manual escape hatch: tap it to return to Focus immediately.
 */
class FocusPausedService : Service() {

    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.IO)
    private val prefs by lazy { FocusPrefs(applicationContext) }

    private var receiverRegistered = false
    private val screenOffReceiver = object : BroadcastReceiver() {
        override fun onReceive(context: Context, intent: Intent) {
            if (intent.action != Intent.ACTION_SCREEN_OFF) return
            scope.launch { reArmFocusAndStop() }
        }
    }

    override fun onBind(intent: Intent?): IBinder? = null

    override fun onCreate() {
        super.onCreate()
        ensureNotificationChannel()
    }

    override fun onStartCommand(intent: Intent?, flags: Int, startId: Int): Int {
        val notification = buildNotification()
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
            FocusDndController.applyFocus(
                applicationContext,
                state.dndFilter,
                state.autoDismissNotifications,
            )
            try {
                startActivity(
                    Intent(applicationContext, LauncherActivity::class.java)
                        .addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
                        .addFlags(Intent.FLAG_ACTIVITY_REORDER_TO_FRONT),
                )
            } catch (t: Throwable) {
                Log.w(TAG, "Couldn't bring LauncherActivity to front from paused service", t)
            }
        }
        stopSelf()
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

    private fun buildNotification(): Notification {
        val tapIntent = PendingIntent.getActivity(
            this,
            0,
            Intent(this, LauncherActivity::class.java)
                .addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
                .addFlags(Intent.FLAG_ACTIVITY_REORDER_TO_FRONT),
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE,
        )
        return Notification.Builder(this, CHANNEL_ID)
            .setSmallIcon(R.drawable.ic_launcher_foreground)
            .setContentTitle("Focus is paused")
            .setContentText("Tap to return to Focus, or lock + unlock for the same effect.")
            .setOngoing(true)
            .setShowWhen(false)
            .setContentIntent(tapIntent)
            .build()
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
