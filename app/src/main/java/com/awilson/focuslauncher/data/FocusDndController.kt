package com.awilson.focuslauncher.data

import android.app.NotificationManager
import android.content.Context

/**
 * Applies and clears the focus-mode DND state.
 *
 * Hide-everything semantics: when focus is active and `autoDismissNotifications` is true, we set
 *   - priorityCategories: PRIORITY_CATEGORY_ALARMS only — overriding the user's system DND priority
 *     list so the only thing allowed through DND is alarms (clock alarms still work). Everything
 *     else is intercepted.
 *   - suppressedVisualEffects: every visual flag (notification list, status bar, peek, ambient,
 *     lights, badge, full-screen intent) — so intercepted notifications are fully hidden, not just
 *     silenced.
 *
 * Hide-off semantics: we restore the snapshotted priorityCategories (so the user's normal DND
 * config is back) and clear suppressedVisualEffects to 0.
 *
 * On first call where there's no snapshot yet, we capture the user's current
 * priorityCategories + suppressedVisualEffects. We restore from that snapshot on releaseFocus and
 * then clear it, so the next focus entry re-captures a fresh snapshot of whatever the user has
 * set externally in the meantime.
 */
object FocusDndController {

    suspend fun applyFocus(context: Context, prefs: FocusPrefs, state: FocusState): Boolean {
        val nm = context.getSystemService(NotificationManager::class.java) ?: return false
        if (!nm.isNotificationPolicyAccessGranted) return false

        val current = nm.notificationPolicy
        if (state.originalSuppressedVisualEffects == null || state.originalPriorityCategories == null) {
            prefs.saveOriginalDndPolicy(
                suppressedVisualEffects = current.suppressedVisualEffects,
                priorityCategories = current.priorityCategories,
            )
        }

        val newPolicy = if (state.autoDismissNotifications) {
            // Hide mode: only alarms allowed; intercepted things are fully hidden.
            NotificationManager.Policy(
                NotificationManager.Policy.PRIORITY_CATEGORY_ALARMS,
                current.priorityCallSenders,
                current.priorityMessageSenders,
                HIDE_ALL_VISUAL_EFFECTS,
            )
        } else {
            // Restore the user's normal priority list, no visual suppression from us.
            val cats = state.originalPriorityCategories ?: current.priorityCategories
            NotificationManager.Policy(
                cats,
                current.priorityCallSenders,
                current.priorityMessageSenders,
                0,
            )
        }
        runCatching { nm.notificationPolicy = newPolicy }
        return runCatching { nm.setInterruptionFilter(state.dndFilter) }.isSuccess
    }

    suspend fun releaseFocus(context: Context, prefs: FocusPrefs, state: FocusState): Boolean {
        val nm = context.getSystemService(NotificationManager::class.java) ?: return false
        if (!nm.isNotificationPolicyAccessGranted) return false

        val origVisual = state.originalSuppressedVisualEffects
        val origCats = state.originalPriorityCategories
        if (origVisual != null && origCats != null) {
            val current = nm.notificationPolicy
            val restored = NotificationManager.Policy(
                origCats,
                current.priorityCallSenders,
                current.priorityMessageSenders,
                origVisual,
            )
            runCatching { nm.notificationPolicy = restored }
            prefs.clearOriginalDndPolicy()
        }
        return runCatching {
            nm.setInterruptionFilter(NotificationManager.INTERRUPTION_FILTER_ALL)
        }.isSuccess
    }

    private const val HIDE_ALL_VISUAL_EFFECTS: Int =
        NotificationManager.Policy.SUPPRESSED_EFFECT_NOTIFICATION_LIST or
            NotificationManager.Policy.SUPPRESSED_EFFECT_STATUS_BAR or
            NotificationManager.Policy.SUPPRESSED_EFFECT_BADGE or
            NotificationManager.Policy.SUPPRESSED_EFFECT_AMBIENT or
            NotificationManager.Policy.SUPPRESSED_EFFECT_LIGHTS or
            NotificationManager.Policy.SUPPRESSED_EFFECT_PEEK or
            NotificationManager.Policy.SUPPRESSED_EFFECT_FULL_SCREEN_INTENT
}
