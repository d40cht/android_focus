package com.awilson.focuslauncher

import android.app.NotificationManager
import android.content.Intent
import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ColumnScope
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material3.Button
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.RadioButton
import androidx.compose.material3.Surface
import androidx.compose.material3.Switch
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateListOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.lifecycle.lifecycleScope
import com.awilson.focuslauncher.data.AppCatalog
import com.awilson.focuslauncher.data.AppEntry
import com.awilson.focuslauncher.data.FocusPrefs
import com.awilson.focuslauncher.data.LaunchableApp
import com.awilson.focuslauncher.data.WorkProfile
import com.awilson.focuslauncher.ui.AppPickerRow
import com.awilson.focuslauncher.ui.AppPickerSearch
import com.awilson.focuslauncher.ui.SortMode
import com.awilson.focuslauncher.ui.SortToggle
import com.awilson.focuslauncher.ui.filterAndSort
import com.awilson.focuslauncher.ui.theme.FocusTheme
import kotlinx.coroutines.launch

private const val GRID_MAX = 28

private sealed class Screen {
    data object Menu : Screen()
    data object Grid : Screen()
    data object WorkGrid : Screen()
    data object Fallback : Screen()
    data object DndMode : Screen()
}

class SettingsActivity : ComponentActivity() {

    private lateinit var prefs: FocusPrefs

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        prefs = FocusPrefs(applicationContext)

        setContent {
            FocusTheme {
                Surface(
                    modifier = Modifier.fillMaxSize(),
                    color = MaterialTheme.colorScheme.background,
                ) {
                    SettingsRoot(
                        prefs = prefs,
                        onClose = { finish() },
                        onRerunOnboarding = {
                            lifecycleScope.launch {
                                prefs.setOnboardingComplete(false)
                                startActivity(Intent(this@SettingsActivity, OnboardingActivity::class.java))
                                finish()
                            }
                        },
                    )
                }
            }
        }
    }
}

@Composable
private fun SettingsRoot(
    prefs: FocusPrefs,
    onClose: () -> Unit,
    onRerunOnboarding: () -> Unit,
) {
    val state by prefs.state.collectAsState(initial = null)
    var screen by remember { mutableStateOf<Screen>(Screen.Menu) }
    val s = state ?: return
    val context = androidx.compose.ui.platform.LocalContext.current

    Column(
        modifier = Modifier
            .fillMaxSize()
            .padding(horizontal = 24.dp, vertical = 48.dp),
        verticalArrangement = Arrangement.spacedBy(12.dp),
    ) {
        when (screen) {
            Screen.Menu -> MenuScreen(
                state = s,
                onConfigureGrid = { screen = Screen.Grid },
                onConfigureWorkGrid = { screen = Screen.WorkGrid },
                onPickFallback = { screen = Screen.Fallback },
                onDndMode = { screen = Screen.DndMode },
                onRerunOnboarding = onRerunOnboarding,
                onClose = onClose,
            )
            Screen.Grid -> GridSubscreen(
                prefs = prefs,
                existing = s.gridApps,
                listApps = { AppCatalog.launchableApps(it) },
                listByUsage = { AppCatalog.launchableAppsByUsage(it) },
                onSave = { picked -> prefs.setGridApps(picked) },
                onBack = { screen = Screen.Menu },
                title = "Configure personal grid",
            )
            Screen.WorkGrid -> {
                val wp = remember { WorkProfile(context.applicationContext) }
                GridSubscreen(
                    prefs = prefs,
                    existing = s.workGridApps,
                    listApps = { wp.launchableApps() },
                    listByUsage = { wp.launchableApps() }, // Usage stats are personal-profile-only
                    onSave = { picked -> prefs.setWorkGridApps(picked) },
                    onBack = { screen = Screen.Menu },
                    title = "Configure work grid",
                    forceAlphabetical = true,
                    userHandle = wp.handle,
                )
            }
            Screen.Fallback -> FallbackSubscreen(
                prefs = prefs,
                currentFallback = s.fallbackLauncherPackage,
                onBack = { screen = Screen.Menu },
            )
            Screen.DndMode -> DndModeSubscreen(
                prefs = prefs,
                currentFilter = s.dndFilter,
                onBack = { screen = Screen.Menu },
            )
        }
    }
}

@Composable
private fun MenuScreen(
    state: com.awilson.focuslauncher.data.FocusState,
    onConfigureGrid: () -> Unit,
    onConfigureWorkGrid: () -> Unit,
    onPickFallback: () -> Unit,
    onDndMode: () -> Unit,
    onRerunOnboarding: () -> Unit,
    onClose: () -> Unit,
) {
    val context = androidx.compose.ui.platform.LocalContext.current
    val prefs = remember { FocusPrefs(context.applicationContext) }

    SettingsHeader("Settings")

    SettingsRow(
        title = "Configure personal grid",
        subtitle = "${state.gridApps.size} app${if (state.gridApps.size == 1) "" else "s"} on the personal page",
        onClick = onConfigureGrid,
    )

    val workProfile = remember { WorkProfile(context.applicationContext) }
    if (workProfile.handle != null) {
        SettingsRow(
            title = "Configure work grid",
            subtitle = "${state.workGridApps.size} app${if (state.workGridApps.size == 1) "" else "s"} on the work page",
            onClick = onConfigureWorkGrid,
        )
    }

    SettingsRow(
        title = "Fallback launcher",
        subtitle = state.fallbackLauncherPackage ?: "(none chosen)",
        onClick = onPickFallback,
    )

    SettingsRow(
        title = "Do Not Disturb mode",
        subtitle = dndFilterLabel(state.dndFilter),
        onClick = onDndMode,
    )

    HideNotificationsRow(
        hideEnabled = state.autoDismissNotifications,
        onToggleHide = { enabled ->
            (context as ComponentActivity).lifecycleScope.launch {
                prefs.setAutoDismissNotifications(enabled)
            }
        },
    )

    SettingsRow(
        title = "Allow apps through Focus",
        subtitle = "Opens Android's DND app exceptions — add apps you still want to see (e.g. WhatsApp from family)",
        onClick = {
            context.startActivity(
                android.content.Intent(android.provider.Settings.ACTION_ZEN_MODE_PRIORITY_SETTINGS)
                    .addFlags(android.content.Intent.FLAG_ACTIVITY_NEW_TASK),
            )
        },
    )

    SettingsRow(
        title = "Re-run onboarding",
        subtitle = "Start the setup flow again",
        onClick = onRerunOnboarding,
    )

    Spacer(Modifier.height(16.dp))
    TextButton(onClick = onClose) { Text("Close") }
}

@Composable
private fun HideNotificationsRow(
    hideEnabled: Boolean,
    onToggleHide: (Boolean) -> Unit,
) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .padding(vertical = 12.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Column(modifier = Modifier.weight(1f)) {
            Text(
                "Hide notifications in focus mode",
                color = MaterialTheme.colorScheme.onBackground,
                fontSize = 16.sp,
            )
            Text(
                "While DND is active, notifications it intercepts are hidden from the shade, status bar, and lock screen. Tap \"Unlock full phone\" and they're all back instantly.",
                color = MaterialTheme.colorScheme.onBackground.copy(alpha = 0.6f),
                fontSize = 12.sp,
            )
        }
        Switch(checked = hideEnabled, onCheckedChange = onToggleHide)
    }
}

@Composable
private fun ColumnScope.GridSubscreen(
    prefs: FocusPrefs,
    existing: List<AppEntry>,
    listApps: (android.content.Context) -> List<LaunchableApp>,
    listByUsage: (android.content.Context) -> List<LaunchableApp>,
    onSave: suspend (List<AppEntry>) -> Unit,
    onBack: () -> Unit,
    title: String,
    forceAlphabetical: Boolean = false,
    userHandle: android.os.UserHandle? = null,
) {
    val context = androidx.compose.ui.platform.LocalContext.current
    var usageGranted by remember {
        mutableStateOf(!forceAlphabetical && AppCatalog.hasUsageStatsPermission(context))
    }
    val alphabetical = remember { listApps(context) }
    var byUsage by remember { mutableStateOf(listByUsage(context)) }
    var sortMode by remember {
        mutableStateOf(if (usageGranted) SortMode.Usage else SortMode.Alphabetical)
    }
    var query by remember { mutableStateOf("") }

    val lifecycleOwner = androidx.lifecycle.compose.LocalLifecycleOwner.current
    androidx.compose.runtime.DisposableEffect(lifecycleOwner) {
        val observer = androidx.lifecycle.LifecycleEventObserver { _, event ->
            if (!forceAlphabetical && event == androidx.lifecycle.Lifecycle.Event.ON_RESUME) {
                val granted = AppCatalog.hasUsageStatsPermission(context)
                if (granted != usageGranted) {
                    usageGranted = granted
                    byUsage = listByUsage(context)
                    if (granted) sortMode = SortMode.Usage
                }
            }
        }
        lifecycleOwner.lifecycle.addObserver(observer)
        onDispose { lifecycleOwner.lifecycle.removeObserver(observer) }
    }

    val selected = remember {
        mutableStateListOf<String>().apply { addAll(existing.map { it.packageName }) }
    }
    val displayed = remember(query, sortMode, alphabetical, byUsage) {
        filterAndSort(query, sortMode, alphabetical, byUsage)
    }

    SettingsHeader(title)
    Text(
        text = "Pick apps to show on this page. Up to $GRID_MAX.",
        color = MaterialTheme.colorScheme.onBackground,
        fontSize = 13.sp,
    )

    AppPickerSearch(query = query, onQueryChange = { query = it })

    if (!forceAlphabetical) {
        SortToggle(
            current = sortMode,
            usageAvailable = usageGranted,
            onChange = { sortMode = it },
            onGrantUsage = { AppCatalog.openUsageAccessSettings(context) },
        )
    }

    LazyColumn(modifier = Modifier.fillMaxWidth().weight(1f)) {
        items(displayed, key = { it.packageName }) { app ->
            AppPickerRow(
                packageName = app.packageName,
                label = app.label,
                checked = selected.contains(app.packageName),
                onCheckedChange = { checked ->
                    if (checked) {
                        if (selected.size < GRID_MAX) selected.add(app.packageName)
                    } else {
                        selected.remove(app.packageName)
                    }
                },
                userHandle = userHandle,
            )
        }
    }
    Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
        TextButton(onClick = onBack, modifier = Modifier.weight(1f)) { Text("Cancel") }
        Button(
            onClick = {
                val pickedPkgs = selected.toList()
                val labelByPkg = (alphabetical + byUsage).associateBy({ it.packageName }, { it.label })
                val picked = pickedPkgs.map { pkg ->
                    AppEntry(packageName = pkg, label = labelByPkg[pkg] ?: pkg)
                }
                (context as ComponentActivity).lifecycleScope.launch {
                    onSave(picked)
                    onBack()
                }
            },
            enabled = selected.isNotEmpty(),
            modifier = Modifier.weight(1f),
        ) { Text("Save (${selected.size})") }
    }
}

@Composable
private fun ColumnScope.FallbackSubscreen(
    prefs: FocusPrefs,
    currentFallback: String?,
    onBack: () -> Unit,
) {
    val context = androidx.compose.ui.platform.LocalContext.current
    val homeApps = remember { AppCatalog.homeApps(context) }
    var picked by remember { mutableStateOf(currentFallback) }

    SettingsHeader("Fallback launcher")
    Text(
        text = "When you tap \"Unlock full phone\", we'll launch this one.",
        color = MaterialTheme.colorScheme.onBackground,
        fontSize = 13.sp,
    )
    LazyColumn(modifier = Modifier.fillMaxWidth().weight(1f)) {
        items(homeApps, key = { it.packageName }) { app ->
            AppPickerRow(
                packageName = app.packageName,
                label = app.label,
                checked = picked == app.packageName,
                onCheckedChange = { picked = app.packageName },
            )
        }
    }
    Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
        TextButton(onClick = onBack, modifier = Modifier.weight(1f)) { Text("Cancel") }
        Button(
            onClick = {
                (context as ComponentActivity).lifecycleScope.launch {
                    prefs.setFallbackLauncher(picked)
                    onBack()
                }
            },
            enabled = picked != null,
            modifier = Modifier.weight(1f),
        ) { Text("Save") }
    }
}

@Composable
private fun DndModeSubscreen(
    prefs: FocusPrefs,
    currentFilter: Int,
    onBack: () -> Unit,
) {
    val context = androidx.compose.ui.platform.LocalContext.current
    var selected by remember { mutableStateOf(currentFilter) }

    val options = listOf(
        NotificationManager.INTERRUPTION_FILTER_PRIORITY to
            ("Priority" to "Honour your system DND priority list (starred contacts, alarms, etc.)."),
        NotificationManager.INTERRUPTION_FILTER_ALARMS to
            ("Alarms only" to "Silence everything except alarms."),
        NotificationManager.INTERRUPTION_FILTER_NONE to
            ("Total silence" to "Silence everything, including alarms."),
    )

    SettingsHeader("Do Not Disturb mode")
    options.forEach { (value, labels) ->
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .clickable { selected = value }
                .padding(vertical = 8.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            RadioButton(selected = selected == value, onClick = { selected = value })
            Spacer(Modifier.height(0.dp))
            Column(modifier = Modifier.padding(start = 4.dp)) {
                Text(labels.first, color = MaterialTheme.colorScheme.onBackground)
                Text(
                    labels.second,
                    color = MaterialTheme.colorScheme.onBackground.copy(alpha = 0.6f),
                    fontSize = 12.sp,
                )
            }
        }
    }

    Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
        TextButton(onClick = onBack, modifier = Modifier.weight(1f)) { Text("Cancel") }
        Button(
            onClick = {
                (context as ComponentActivity).lifecycleScope.launch {
                    prefs.setDndFilter(selected)
                    onBack()
                }
            },
            modifier = Modifier.weight(1f),
        ) { Text("Save") }
    }
}

private fun dndFilterLabel(filter: Int): String = when (filter) {
    NotificationManager.INTERRUPTION_FILTER_PRIORITY -> "Priority"
    NotificationManager.INTERRUPTION_FILTER_ALARMS -> "Alarms only"
    NotificationManager.INTERRUPTION_FILTER_NONE -> "Total silence"
    else -> "Priority"
}

@Composable
private fun SettingsHeader(text: String) {
    Text(
        text = text,
        color = MaterialTheme.colorScheme.onBackground,
        fontSize = 24.sp,
        fontWeight = FontWeight.Light,
        modifier = Modifier.padding(bottom = 8.dp),
    )
}

@Composable
private fun SettingsRow(title: String, subtitle: String, onClick: () -> Unit) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .clickable(onClick = onClick)
            .padding(vertical = 12.dp),
    ) {
        Column {
            Text(title, color = MaterialTheme.colorScheme.onBackground, fontSize = 16.sp)
            Text(
                subtitle,
                color = MaterialTheme.colorScheme.onBackground.copy(alpha = 0.6f),
                fontSize = 12.sp,
            )
        }
    }
}
