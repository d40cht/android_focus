package com.awilson.focuslauncher.ui

import android.content.Context
import android.content.pm.LauncherApps
import android.content.pm.PackageManager
import android.graphics.drawable.Drawable
import android.os.Process
import android.os.UserHandle
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.drawscope.DrawScope
import androidx.compose.ui.graphics.drawscope.drawIntoCanvas
import androidx.compose.ui.graphics.nativeCanvas
import androidx.compose.ui.graphics.painter.Painter
import androidx.compose.ui.platform.LocalContext

internal class DrawablePainter(private val drawable: Drawable) : Painter() {
    override val intrinsicSize: Size = run {
        val w = drawable.intrinsicWidth.takeIf { it > 0 }?.toFloat() ?: 48f
        val h = drawable.intrinsicHeight.takeIf { it > 0 }?.toFloat() ?: 48f
        Size(w, h)
    }

    override fun DrawScope.onDraw() {
        drawIntoCanvas { canvas ->
            val w = size.width.toInt().coerceAtLeast(1)
            val h = size.height.toInt().coerceAtLeast(1)
            drawable.setBounds(0, 0, w, h)
            drawable.draw(canvas.nativeCanvas)
        }
    }
}

@Composable
internal fun rememberAppIcon(packageName: String, userHandle: UserHandle? = null): Painter? {
    val context = LocalContext.current
    return remember(packageName, userHandle) { loadAppIcon(context, packageName, userHandle) }
}

internal fun loadAppIcon(
    context: Context,
    packageName: String,
    userHandle: UserHandle? = null,
): Painter? {
    val targetHandle = userHandle ?: Process.myUserHandle()
    if (targetHandle != Process.myUserHandle()) {
        // Work or other-profile package — load via LauncherApps so we don't depend on the package
        // being installed in our personal profile.
        val la = context.getSystemService(LauncherApps::class.java) ?: return null
        val activity = la.getActivityList(packageName, targetHandle).firstOrNull() ?: return null
        val drawable = runCatching { activity.getBadgedIcon(0) }.getOrNull() ?: return null
        return DrawablePainter(drawable)
    }
    return try {
        DrawablePainter(context.packageManager.getApplicationIcon(packageName))
    } catch (_: PackageManager.NameNotFoundException) {
        null
    }
}
