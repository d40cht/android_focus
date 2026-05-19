package com.awilson.focuslauncher

import android.app.Application

/**
 * Application class. Intentionally minimal now: DND is owned by LauncherActivity (resume/pause) and
 * by FocusPausedService (for the duration of an Unlock-full-phone pause). No app-scope receivers
 * or Flow observers run here — that prevented bugs where DND was re-engaged while the user was
 * inside a launched app or inside a timed pause.
 */
class FocusApplication : Application()
