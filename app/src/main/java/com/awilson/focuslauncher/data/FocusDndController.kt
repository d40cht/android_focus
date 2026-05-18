package com.awilson.focuslauncher.data

import android.app.NotificationManager
import android.content.Context

/**
 * Applies and clears the focus-mode DND state.
 *
 * When focus mode is active with notification hiding enabled, we:
 *  - Set the notification policy's suppressedVisualEffects so that any notification DND intercepts
 *    is hidden from the shade, status bar, peek, ambient display, badge, lights, and full-screen
 *    intents — i.e. it's silently filed away rather than rendered.
 *  - Apply the user's chosen interruption filter (Priority / Alarms / None) so the system DND
 *    machinery actually intercepts the right notifications.
 *
 * We preserve the user's priority categories and sender lists (their existing DND config),
 * touching only the visual-effects bits.
 *
 * On exit, we just turn DND off (INTERRUPTION_FILTER_ALL). At that point, suppressedVisualEffects
 * no longer apply because no notification is being intercepted by DND.
 */
object FocusDndController {

    fun applyFocus(context: Context, filter: Int, hideNotifications: Boolean): Boolean {
        val nm = context.getSystemService(NotificationManager::class.java) ?: return false
        if (!nm.isNotificationPolicyAccessGranted) return false

        if (hideNotifications) {
            applyHidingPolicy(nm)
        }
        return runCatching { nm.setInterruptionFilter(filter) }.isSuccess
    }

    fun releaseFocus(context: Context): Boolean {
        val nm = context.getSystemService(NotificationManager::class.java) ?: return false
        if (!nm.isNotificationPolicyAccessGranted) return false
        return runCatching {
            nm.setInterruptionFilter(NotificationManager.INTERRUPTION_FILTER_ALL)
        }.isSuccess
    }

    private fun applyHidingPolicy(nm: NotificationManager) {
        val current = nm.notificationPolicy
        val newPolicy = NotificationManager.Policy(
            current.priorityCategories,
            current.priorityCallSenders,
            current.priorityMessageSenders,
            HIDE_ALL_VISUAL_EFFECTS,
        )
        runCatching { nm.notificationPolicy = newPolicy }
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
