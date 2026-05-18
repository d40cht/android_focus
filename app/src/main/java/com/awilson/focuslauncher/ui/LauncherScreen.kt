package com.awilson.focuslauncher.ui

import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.lazy.grid.GridCells
import androidx.compose.foundation.lazy.grid.LazyVerticalGrid
import androidx.compose.foundation.lazy.grid.rememberLazyGridState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateListOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.scale
import androidx.compose.ui.platform.LocalHapticFeedback
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.awilson.focuslauncher.data.AppEntry
import kotlinx.coroutines.delay
import sh.calvin.reorderable.ReorderableItem
import sh.calvin.reorderable.rememberReorderableLazyGridState
import java.time.LocalDateTime
import java.time.format.DateTimeFormatter

@Composable
fun LauncherScreen(
    apps: List<AppEntry>,
    onAppClick: (AppEntry) -> Unit,
    onReorder: (List<AppEntry>) -> Unit,
    onUnlockFullPhone: () -> Unit,
    onOpenSettings: () -> Unit,
    dndPermissionGranted: Boolean,
    onRequestDndPermission: () -> Unit,
) {
    val orderedApps = remember { mutableStateListOf<AppEntry>().apply { addAll(apps) } }

    // Re-sync from prop when the *set* of packages changes (Settings added/removed an app).
    // Ignore reorders we've just persisted, since those round-trip identical sets.
    LaunchedEffect(apps.map { it.packageName }.toSet()) {
        val currentPkgs = orderedApps.map { it.packageName }.toSet()
        val newPkgs = apps.map { it.packageName }.toSet()
        if (currentPkgs != newPkgs) {
            orderedApps.clear()
            orderedApps.addAll(apps)
        }
    }

    val gridState = rememberLazyGridState()
    val haptic = LocalHapticFeedback.current
    val reorderState = rememberReorderableLazyGridState(gridState) { from, to ->
        val movedItem = orderedApps.removeAt(from.index)
        orderedApps.add(to.index, movedItem)
    }

    Surface(
        modifier = Modifier.fillMaxSize(),
        color = MaterialTheme.colorScheme.background,
    ) {
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(horizontal = 24.dp),
            horizontalAlignment = Alignment.CenterHorizontally,
        ) {
            Spacer(Modifier.height(56.dp))

            ClockDisplay()

            Spacer(Modifier.height(24.dp))

            Text(
                text = "What did you come here to do?",
                color = MaterialTheme.colorScheme.onBackground.copy(alpha = 0.7f),
                fontSize = 16.sp,
                fontWeight = FontWeight.Light,
                textAlign = TextAlign.Center,
            )

            Spacer(Modifier.height(32.dp))

            if (!dndPermissionGranted) {
                DndBanner(onClick = onRequestDndPermission)
                Spacer(Modifier.height(24.dp))
            }

            LazyVerticalGrid(
                state = gridState,
                columns = GridCells.Fixed(columnsFor(orderedApps.size)),
                contentPadding = PaddingValues(vertical = 8.dp),
                horizontalArrangement = Arrangement.spacedBy(8.dp),
                verticalArrangement = Arrangement.spacedBy(16.dp),
                modifier = Modifier
                    .weight(1f)
                    .fillMaxWidth(),
            ) {
                items(
                    count = orderedApps.size,
                    key = { i -> orderedApps[i].packageName },
                ) { index ->
                    val entry = orderedApps[index]
                    ReorderableItem(
                        state = reorderState,
                        key = entry.packageName,
                    ) { isDragging ->
                        AppTile(
                            entry = entry,
                            isDragging = isDragging,
                            onClick = { onAppClick(entry) },
                            dragModifier = Modifier.longPressDraggableHandle(
                                onDragStarted = {
                                    haptic.performHapticFeedback(
                                        androidx.compose.ui.hapticfeedback.HapticFeedbackType.LongPress
                                    )
                                },
                                onDragStopped = {
                                    onReorder(orderedApps.toList())
                                },
                            ),
                        )
                    }
                }
            }

            Spacer(Modifier.height(16.dp))

            androidx.compose.foundation.layout.Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically,
            ) {
                TextButton(onClick = onOpenSettings) {
                    Text(
                        text = "Settings",
                        color = MaterialTheme.colorScheme.onBackground.copy(alpha = 0.4f),
                        fontSize = 13.sp,
                    )
                }
                TextButton(onClick = onUnlockFullPhone) {
                    Text(
                        text = "Unlock full phone",
                        color = MaterialTheme.colorScheme.onBackground.copy(alpha = 0.55f),
                        fontSize = 14.sp,
                    )
                }
            }

            Spacer(Modifier.height(24.dp))
        }
    }
}

@Composable
private fun AppTile(
    entry: AppEntry,
    isDragging: Boolean,
    onClick: () -> Unit,
    dragModifier: Modifier,
) {
    val icon = rememberAppIcon(entry.packageName)
    val scale = if (isDragging) 1.1f else 1f

    Column(
        modifier = Modifier
            .scale(scale)
            .clip(RoundedCornerShape(12.dp))
            .then(dragModifier)
            .clickable(onClick = onClick)
            .padding(4.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
    ) {
        Box(
            modifier = Modifier.size(56.dp),
            contentAlignment = Alignment.Center,
        ) {
            if (icon != null) {
                Image(
                    painter = icon,
                    contentDescription = entry.label,
                    modifier = Modifier.size(48.dp),
                )
            } else {
                Box(
                    modifier = Modifier
                        .size(48.dp)
                        .clip(RoundedCornerShape(12.dp))
                        .background(MaterialTheme.colorScheme.surface),
                    contentAlignment = Alignment.Center,
                ) {
                    Text(
                        text = entry.label.take(1).uppercase(),
                        color = MaterialTheme.colorScheme.onSurface,
                        fontSize = 20.sp,
                    )
                }
            }
        }
        Spacer(Modifier.height(4.dp))
        Text(
            text = entry.label,
            color = MaterialTheme.colorScheme.onBackground,
            fontSize = 11.sp,
            maxLines = 1,
            overflow = TextOverflow.Ellipsis,
            textAlign = TextAlign.Center,
        )
    }
}

@Composable
private fun DndBanner(onClick: () -> Unit) {
    Surface(
        color = MaterialTheme.colorScheme.surface,
        shape = RoundedCornerShape(12.dp),
        modifier = Modifier
            .fillMaxWidth()
            .clickable(onClick = onClick),
    ) {
        Text(
            text = "Notification access not granted — tap to fix",
            color = MaterialTheme.colorScheme.onSurface,
            fontSize = 14.sp,
            modifier = Modifier.padding(horizontal = 16.dp, vertical = 12.dp),
        )
    }
}

private fun columnsFor(count: Int): Int = when {
    count <= 4 -> 2
    count <= 9 -> 3
    else -> 4
}

@Composable
private fun ClockDisplay() {
    val context = androidx.compose.ui.platform.LocalContext.current
    val is24Hour = remember { android.text.format.DateFormat.is24HourFormat(context) }
    val timeFormatter = remember(is24Hour) {
        DateTimeFormatter.ofPattern(if (is24Hour) "HH:mm" else "h:mm a")
    }
    val dateFormatter = remember { DateTimeFormatter.ofPattern("EEEE d MMMM") }

    var now by remember { mutableStateOf(LocalDateTime.now()) }
    LaunchedEffect(Unit) {
        while (true) {
            now = LocalDateTime.now()
            // Sleep until just past the next minute boundary so the clock ticks promptly.
            val msToNextMinute = 60_000L - (System.currentTimeMillis() % 60_000L) + 50L
            delay(msToNextMinute)
        }
    }

    Text(
        text = now.format(timeFormatter),
        color = MaterialTheme.colorScheme.onBackground,
        fontSize = 64.sp,
        fontWeight = FontWeight.Light,
        textAlign = TextAlign.Center,
    )
    Text(
        text = now.format(dateFormatter),
        color = MaterialTheme.colorScheme.onBackground.copy(alpha = 0.65f),
        fontSize = 14.sp,
        fontWeight = FontWeight.Light,
        textAlign = TextAlign.Center,
    )
}
