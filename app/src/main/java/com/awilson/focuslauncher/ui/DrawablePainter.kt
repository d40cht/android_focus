package com.awilson.focuslauncher.ui

import android.content.Context
import android.content.pm.PackageManager
import android.graphics.drawable.Drawable
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
internal fun rememberAppIcon(packageName: String): Painter? {
    val context = LocalContext.current
    return remember(packageName) { loadAppIcon(context, packageName) }
}

internal fun loadAppIcon(context: Context, packageName: String): Painter? = try {
    DrawablePainter(context.packageManager.getApplicationIcon(packageName))
} catch (_: PackageManager.NameNotFoundException) {
    null
}
