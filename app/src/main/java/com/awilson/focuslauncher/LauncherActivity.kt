package com.awilson.focuslauncher

import android.app.NotificationManager
import android.content.ComponentName
import android.content.Intent
import android.os.Bundle
import android.provider.Settings
import android.util.Log
import androidx.activity.ComponentActivity
import androidx.activity.OnBackPressedCallback
import androidx.activity.compose.setContent
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.lifecycle.lifecycleScope
import com.awilson.focuslauncher.data.AppEntry
import com.awilson.focuslauncher.data.FocusDndController
import com.awilson.focuslauncher.data.FocusPrefs
import com.awilson.focuslauncher.data.FocusState
import com.awilson.focuslauncher.data.PauseAlarm
import com.awilson.focuslauncher.data.WorkProfile
import com.awilson.focuslauncher.ui.LauncherScreen
import com.awilson.focuslauncher.ui.PauseOption
import com.awilson.focuslauncher.ui.theme.FocusTheme
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch

class LauncherActivity : ComponentActivity() {

    private lateinit var prefs: FocusPrefs
    private lateinit var workProfile: WorkProfile
    private lateinit var stateFlow: StateFlow<FocusState>

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        prefs = FocusPrefs(applicationContext)
        workProfile = WorkProfile(applicationContext)

        // Back button on the home screen should do nothing.
        onBackPressedDispatcher.addCallback(this, object : OnBackPressedCallback(true) {
            override fun handleOnBackPressed() = Unit
        })

        stateFlow = prefs.state.stateIn(
            scope = lifecycleScope,
            started = SharingStarted.Eagerly,
            initialValue = FocusState(
                onboardingComplete = false,
                fallbackLauncherPackage = null,
                gridApps = emptyList(),
                workGridApps = emptyList(),
                dndFilter = NotificationManager.INTERRUPTION_FILTER_PRIORITY,
                focusModeActive = true,
                autoDismissNotifications = false,
                originalSuppressedVisualEffects = null,
                originalPriorityCategories = null,
                pausedUntilEpochMs = 0L,
                lastPauseUntilHour = null,
                lastPauseUntilMinute = null,
            ),
        )

        setContent {
            FocusTheme {
                val state by stateFlow.collectAsState()
                if (!state.onboardingComplete) {
                    LaunchOnboardingEffect()
                    return@FocusTheme
                }
                LauncherScreen(
                    personalApps = state.gridApps,
                    workApps = state.workGridApps,
                    workProfileAvailable = workProfile.isAvailable,
                    workUserHandle = workProfile.handle,
                    onPersonalAppClick = ::launchPersonalApp,
                    onWorkAppClick = ::launchWorkApp,
                    onReorderPersonal = { reordered ->
                        lifecycleScope.launch { prefs.setGridApps(reordered) }
                    },
                    onReorderWork = { reordered ->
                        lifecycleScope.launch { prefs.setWorkGridApps(reordered) }
                    },
                    onPauseFocus = { option -> pauseFocus(option, state.fallbackLauncherPackage) },
                    initialPauseHour = state.lastPauseUntilHour,
                    initialPauseMinute = state.lastPauseUntilMinute,
                    onOpenSettings = ::openSettings,
                    dndPermissionGranted = isDndPermissionGranted(),
                    onRequestDndPermission = ::openDndPermissionSettings,
                )
            }
        }
    }

    override fun onResume() {
        super.onResume()
        val s = stateFlow.value
        if (s.onboardingComplete) {
            lifecycleScope.launch {
                prefs.setPausedUntil(0L)
                prefs.setFocusModeActive(true)
                FocusDndController.applyFocus(this@LauncherActivity, prefs, prefs.state.first())
            }
            // We're back in focus, the paused service (if running) is no longer needed.
            PauseAlarm.cancel(this)
            FocusPausedService.stop(this)
        }
    }

    private fun isDndPermissionGranted(): Boolean {
        val nm = getSystemService(NotificationManager::class.java) ?: return false
        return nm.isNotificationPolicyAccessGranted
    }

    private fun openDndPermissionSettings() {
        startActivity(
            Intent(Settings.ACTION_NOTIFICATION_POLICY_ACCESS_SETTINGS)
                .addFlags(Intent.FLAG_ACTIVITY_NEW_TASK),
        )
    }

    private fun openSettings() {
        startActivity(Intent(this, SettingsActivity::class.java))
    }

    private fun launchPersonalApp(entry: AppEntry) {
        val launchIntent = packageManager.getLaunchIntentForPackage(entry.packageName) ?: return
        // DND stays on while the user is inside the launched app.
        startActivity(launchIntent.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK))
    }

    private fun launchWorkApp(entry: AppEntry) {
        workProfile.launch(entry.packageName)
    }

    private fun pauseFocus(option: PauseOption, fallbackPackage: String?) {
        val pausedUntil = when (option) {
            PauseOption.SnapBack -> 0L
            PauseOption.Indefinite -> Long.MAX_VALUE
            is PauseOption.For -> System.currentTimeMillis() + option.durationMs
            is PauseOption.UntilEpoch -> option.epochMs
        }
        lifecycleScope.launch {
            prefs.setFocusModeActive(false)
            prefs.setPausedUntil(pausedUntil)
            if (option is PauseOption.UntilEpoch) {
                prefs.setLastPauseUntilTime(option.hour, option.minute)
            }
            FocusDndController.releaseFocus(this@LauncherActivity, prefs, prefs.state.first())
        }
        FocusPausedService.start(this)
        if (pausedUntil in 1..(Long.MAX_VALUE - 1)) {
            PauseAlarm.schedule(this, pausedUntil)
        } else {
            // 0 (snap-back) or MAX_VALUE (indefinite) — no scheduled alarm
            PauseAlarm.cancel(this)
        }
        val intent = buildFallbackHomeIntent(fallbackPackage)
        try {
            startActivity(intent)
        } catch (t: Throwable) {
            Log.w("FocusLauncher", "Failed to launch fallback launcher; falling back to chooser", t)
            startActivity(
                Intent(Intent.ACTION_MAIN)
                    .addCategory(Intent.CATEGORY_HOME)
                    .addFlags(Intent.FLAG_ACTIVITY_NEW_TASK),
            )
        }
    }

    /**
     * Build an intent that launches the fallback launcher.
     *
     * Most launcher apps register their main activity with CATEGORY_HOME — not CATEGORY_LAUNCHER —
     * which means `getLaunchIntentForPackage` returns null. So we resolve the HOME intent for the
     * fallback package and build an explicit component intent.
     */
    private fun buildFallbackHomeIntent(fallbackPackage: String?): Intent {
        val baseHomeIntent = Intent(Intent.ACTION_MAIN).addCategory(Intent.CATEGORY_HOME)

        if (fallbackPackage != null) {
            val match = packageManager.queryIntentActivities(baseHomeIntent, 0)
                .firstOrNull { it.activityInfo.packageName == fallbackPackage }
            if (match != null) {
                val cn = ComponentName(match.activityInfo.packageName, match.activityInfo.name)
                return Intent(Intent.ACTION_MAIN)
                    .addCategory(Intent.CATEGORY_HOME)
                    .setComponent(cn)
                    .addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
            }
            // Package may have been uninstalled; try its regular launch intent as a backup.
            packageManager.getLaunchIntentForPackage(fallbackPackage)?.let {
                return it.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
            }
        }
        return baseHomeIntent.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
    }

    @androidx.compose.runtime.Composable
    private fun LaunchOnboardingEffect() {
        androidx.compose.runtime.LaunchedEffect(Unit) {
            startActivity(Intent(this@LauncherActivity, OnboardingActivity::class.java))
        }
    }
}
