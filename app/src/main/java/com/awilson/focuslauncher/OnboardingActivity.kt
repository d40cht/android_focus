package com.awilson.focuslauncher

import android.app.NotificationManager
import android.app.role.RoleManager
import android.content.Intent
import android.os.Bundle
import android.provider.Settings
import androidx.activity.ComponentActivity
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.compose.setContent
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ColumnScope
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material3.Button
import androidx.compose.material3.Checkbox
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateListOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.lifecycle.lifecycleScope
import com.awilson.focuslauncher.data.AppCatalog
import com.awilson.focuslauncher.data.AppEntry
import com.awilson.focuslauncher.data.FocusPrefs
import com.awilson.focuslauncher.data.LaunchableApp
import com.awilson.focuslauncher.ui.AppPickerRow
import com.awilson.focuslauncher.ui.AppPickerSearch
import com.awilson.focuslauncher.ui.SortMode
import com.awilson.focuslauncher.ui.SortToggle
import com.awilson.focuslauncher.ui.filterAndSort
import com.awilson.focuslauncher.ui.theme.FocusTheme
import kotlinx.coroutines.launch

private const val GRID_MIN = 1
private const val GRID_MAX = 28

class OnboardingActivity : ComponentActivity() {

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
                    OnboardingFlow(
                        prefs = prefs,
                        onCaptureCurrentLauncher = { AppCatalog.currentHomePackage(this) },
                        onListHomeApps = { AppCatalog.homeApps(this) },
                        onListLaunchableApps = { AppCatalog.launchableAppsByUsage(this) },
                        hasUsageStats = { AppCatalog.hasUsageStatsPermission(this) },
                        openUsageStatsSettings = { AppCatalog.openUsageAccessSettings(this) },
                        isDndGranted = ::isDndGranted,
                        openDndSettings = ::openDndSettings,
                        requestHomeRoleIntent = ::homeRoleIntent,
                        openHomeSettingsFallback = ::openHomeSettings,
                        isHomeRoleHeld = ::isHomeRoleHeld,
                        onFinish = { finish() },
                    )
                }
            }
        }
    }

    private fun isDndGranted(): Boolean =
        getSystemService(NotificationManager::class.java)?.isNotificationPolicyAccessGranted == true

    private fun openDndSettings() {
        startActivity(Intent(Settings.ACTION_NOTIFICATION_POLICY_ACCESS_SETTINGS))
    }

    private fun homeRoleIntent(): Intent? {
        val rm = getSystemService(RoleManager::class.java) ?: return null
        if (!rm.isRoleAvailable(RoleManager.ROLE_HOME)) return null
        if (rm.isRoleHeld(RoleManager.ROLE_HOME)) return null
        return rm.createRequestRoleIntent(RoleManager.ROLE_HOME)
    }

    private fun openHomeSettings() {
        startActivity(Intent(Settings.ACTION_HOME_SETTINGS))
    }

    private fun isHomeRoleHeld(): Boolean {
        val rm = getSystemService(RoleManager::class.java) ?: return false
        return rm.isRoleHeld(RoleManager.ROLE_HOME)
    }
}

private enum class Step { Welcome, CaptureFallback, Dnd, HomeRole, ConfigureGrid, Done }

@Composable
private fun OnboardingFlow(
    prefs: FocusPrefs,
    onCaptureCurrentLauncher: () -> String?,
    onListHomeApps: () -> List<LaunchableApp>,
    onListLaunchableApps: () -> List<LaunchableApp>,
    hasUsageStats: () -> Boolean,
    openUsageStatsSettings: () -> Unit,
    isDndGranted: () -> Boolean,
    openDndSettings: () -> Unit,
    requestHomeRoleIntent: () -> Intent?,
    openHomeSettingsFallback: () -> Unit,
    isHomeRoleHeld: () -> Boolean,
    onFinish: () -> Unit,
) {
    val context = LocalContext.current
    val state by prefs.state.collectAsState(initial = null)

    var step by remember { mutableStateOf(Step.Welcome) }

    Column(
        modifier = Modifier
            .fillMaxSize()
            .padding(horizontal = 24.dp, vertical = 48.dp),
        verticalArrangement = Arrangement.spacedBy(16.dp),
    ) {
        when (step) {
            Step.Welcome -> WelcomeStep(onNext = { step = Step.CaptureFallback })

            Step.CaptureFallback -> CaptureFallbackStep(
                currentDefault = onCaptureCurrentLauncher,
                listHomeApps = onListHomeApps,
                savedFallback = state?.fallbackLauncherPackage,
                onSelect = { pkg ->
                    (context as ComponentActivity).lifecycleScope.launch {
                        prefs.setFallbackLauncher(pkg)
                        step = Step.Dnd
                    }
                },
            )

            Step.Dnd -> DndStep(
                isGranted = isDndGranted,
                openSettings = openDndSettings,
                onNext = { step = Step.HomeRole },
            )

            Step.HomeRole -> HomeRoleStep(
                buildIntent = requestHomeRoleIntent,
                openFallback = openHomeSettingsFallback,
                isHeld = isHomeRoleHeld,
                onNext = { step = Step.ConfigureGrid },
            )

            Step.ConfigureGrid -> ConfigureGridStep(
                listApps = onListLaunchableApps,
                hasUsageStats = hasUsageStats,
                openUsageStatsSettings = openUsageStatsSettings,
                existing = state?.gridApps.orEmpty(),
                onSave = { picked ->
                    (context as ComponentActivity).lifecycleScope.launch {
                        prefs.setGridApps(picked)
                        step = Step.Done
                    }
                },
            )

            Step.Done -> DoneStep(
                onFinish = {
                    (context as ComponentActivity).lifecycleScope.launch {
                        prefs.setOnboardingComplete(true)
                        onFinish()
                    }
                },
            )
        }
    }
}

@Composable
private fun WelcomeStep(onNext: () -> Unit) {
    StepHeader(title = "Focus")
    Text(
        text = "A home screen that asks what you came here to do, instead of showing you what you didn't.",
        color = MaterialTheme.colorScheme.onBackground,
        fontSize = 16.sp,
    )
    Spacer(Modifier.height(16.dp))
    PrimaryButton(text = "Continue", onClick = onNext)
}

@Composable
private fun CaptureFallbackStep(
    currentDefault: () -> String?,
    listHomeApps: () -> List<LaunchableApp>,
    savedFallback: String?,
    onSelect: (String) -> Unit,
) {
    val detected = remember { currentDefault() }
    val homeApps = remember { listHomeApps() }
    var picked by remember { mutableStateOf(savedFallback ?: detected) }

    StepHeader(title = "Pick your fallback launcher")
    Text(
        text = "When you tap \"Unlock full phone\", we'll return you to this launcher.",
        color = MaterialTheme.colorScheme.onBackground,
        fontSize = 14.sp,
    )

    if (detected != null) {
        Text(
            text = "Detected current default: $detected",
            color = MaterialTheme.colorScheme.onBackground.copy(alpha = 0.7f),
            fontSize = 13.sp,
        )
    }

    LazyColumn(modifier = Modifier.fillMaxWidth().padding(top = 8.dp)) {
        items(homeApps, key = { it.packageName }) { app ->
            AppPickerRow(
                packageName = app.packageName,
                label = app.label,
                checked = picked == app.packageName,
                onCheckedChange = { picked = app.packageName },
            )
        }
    }

    PrimaryButton(
        text = "Continue",
        enabled = picked != null,
        onClick = { picked?.let(onSelect) },
    )
}

@Composable
private fun DndStep(
    isGranted: () -> Boolean,
    openSettings: () -> Unit,
    onNext: () -> Unit,
) {
    var granted by remember { mutableStateOf(isGranted()) }
    // Re-check when the activity resumes (after returning from settings).
    val lifecycleOwner = androidx.lifecycle.compose.LocalLifecycleOwner.current
    DisposableEffect(lifecycleOwner) {
        val observer = androidx.lifecycle.LifecycleEventObserver { _, event ->
            if (event == androidx.lifecycle.Lifecycle.Event.ON_RESUME) {
                granted = isGranted()
            }
        }
        lifecycleOwner.lifecycle.addObserver(observer)
        onDispose { lifecycleOwner.lifecycle.removeObserver(observer) }
    }

    StepHeader(title = "Allow Do Not Disturb")
    Text(
        text = "Focus turns on Do Not Disturb when you open it, so notifications can't pull you out of what you came here to do.",
        color = MaterialTheme.colorScheme.onBackground,
        fontSize = 14.sp,
    )
    if (granted) {
        Text(
            text = "Granted ✓",
            color = MaterialTheme.colorScheme.onBackground,
            fontSize = 14.sp,
            fontWeight = FontWeight.Medium,
        )
        PrimaryButton(text = "Continue", onClick = onNext)
    } else {
        PrimaryButton(text = "Open notification access settings", onClick = openSettings)
    }
}

@Composable
private fun HomeRoleStep(
    buildIntent: () -> Intent?,
    openFallback: () -> Unit,
    isHeld: () -> Boolean,
    onNext: () -> Unit,
) {
    var held by remember { mutableStateOf(isHeld()) }
    val launcher = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.StartActivityForResult(),
    ) {
        held = isHeld()
    }

    StepHeader(title = "Set Focus as your home screen")
    Text(
        text = "Pressing home should bring you here. We'll ask Android for the home role next.",
        color = MaterialTheme.colorScheme.onBackground,
        fontSize = 14.sp,
    )

    if (held) {
        Text(
            text = "Focus is the home app ✓",
            color = MaterialTheme.colorScheme.onBackground,
            fontSize = 14.sp,
            fontWeight = FontWeight.Medium,
        )
        PrimaryButton(text = "Continue", onClick = onNext)
    } else {
        PrimaryButton(
            text = "Set as home",
            onClick = {
                val intent = buildIntent()
                if (intent != null) launcher.launch(intent) else openFallback()
            },
        )
        TextButton(onClick = openFallback) { Text("Open home app settings instead") }
    }
}

@Composable
private fun ColumnScope.ConfigureGridStep(
    listApps: () -> List<LaunchableApp>,
    hasUsageStats: () -> Boolean,
    openUsageStatsSettings: () -> Unit,
    existing: List<AppEntry>,
    onSave: (List<AppEntry>) -> Unit,
) {
    val context = LocalContext.current
    var usageGranted by remember { mutableStateOf(hasUsageStats()) }
    var alphabetical by remember { mutableStateOf(AppCatalog.launchableApps(context)) }
    var byUsage by remember { mutableStateOf(listApps()) }
    var sortMode by remember { mutableStateOf(if (usageGranted) SortMode.Usage else SortMode.Alphabetical) }
    var query by remember { mutableStateOf("") }

    val lifecycleOwner = androidx.lifecycle.compose.LocalLifecycleOwner.current
    DisposableEffect(lifecycleOwner) {
        val observer = androidx.lifecycle.LifecycleEventObserver { _, event ->
            if (event == androidx.lifecycle.Lifecycle.Event.ON_RESUME) {
                val granted = hasUsageStats()
                if (granted != usageGranted) {
                    usageGranted = granted
                    byUsage = listApps()
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

    StepHeader(title = "Choose your apps")
    Text(
        text = "Pick the apps that should appear on your focus screen. Up to $GRID_MAX.",
        color = MaterialTheme.colorScheme.onBackground,
        fontSize = 14.sp,
    )

    AppPickerSearch(query = query, onQueryChange = { query = it })

    SortToggle(
        current = sortMode,
        usageAvailable = usageGranted,
        onChange = { sortMode = it },
        onGrantUsage = openUsageStatsSettings,
    )

    LazyColumn(modifier = Modifier.fillMaxWidth().weight(1f, fill = true)) {
        items(displayed, key = { it.packageName }) { app ->
            val isChecked = selected.contains(app.packageName)
            AppPickerRow(
                packageName = app.packageName,
                label = app.label,
                checked = isChecked,
                onCheckedChange = { checked ->
                    if (checked) {
                        if (selected.size < GRID_MAX) selected.add(app.packageName)
                    } else {
                        selected.remove(app.packageName)
                    }
                },
            )
        }
    }

    PrimaryButton(
        text = "Save (${selected.size} selected)",
        enabled = selected.size in GRID_MIN..GRID_MAX,
        onClick = {
            val pickedPkgs = selected.toList()
            val labelByPkg = (alphabetical + byUsage).associateBy({ it.packageName }, { it.label })
            val picked = pickedPkgs.map { pkg ->
                AppEntry(packageName = pkg, label = labelByPkg[pkg] ?: pkg)
            }
            onSave(picked)
        },
    )
}

@Composable
private fun DoneStep(onFinish: () -> Unit) {
    StepHeader(title = "Ready")
    Text(
        text = "Lock and unlock your phone, or press home, to start using Focus.",
        color = MaterialTheme.colorScheme.onBackground,
        fontSize = 14.sp,
    )
    PrimaryButton(text = "Finish", onClick = onFinish)
}

@Composable
private fun StepHeader(title: String) {
    Text(
        text = title,
        color = MaterialTheme.colorScheme.onBackground,
        fontSize = 28.sp,
        fontWeight = FontWeight.Light,
    )
}

@Composable
private fun PrimaryButton(text: String, enabled: Boolean = true, onClick: () -> Unit) {
    Button(
        onClick = onClick,
        enabled = enabled,
        modifier = Modifier.fillMaxWidth(),
    ) { Text(text) }
}
