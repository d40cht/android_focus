package com.awilson.focuslauncher.ui

import androidx.compose.foundation.Image
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.material3.Checkbox
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.awilson.focuslauncher.data.LaunchableApp

enum class SortMode { Alphabetical, Usage }

@Composable
fun AppPickerRow(
    packageName: String,
    label: String,
    checked: Boolean,
    onCheckedChange: (Boolean) -> Unit,
    showPackageName: Boolean = true,
) {
    val icon = rememberAppIcon(packageName)
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .padding(vertical = 4.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Checkbox(checked = checked, onCheckedChange = onCheckedChange)
        if (icon != null) {
            Image(
                painter = icon,
                contentDescription = null,
                modifier = Modifier.size(32.dp).padding(end = 8.dp),
            )
        } else {
            Spacer(Modifier.size(32.dp))
        }
        Column {
            Text(
                text = label,
                color = MaterialTheme.colorScheme.onBackground,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
            )
            if (showPackageName) {
                Text(
                    text = packageName,
                    color = MaterialTheme.colorScheme.onBackground.copy(alpha = 0.5f),
                    fontSize = 11.sp,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                )
            }
        }
    }
}

@Composable
fun AppPickerSearch(
    query: String,
    onQueryChange: (String) -> Unit,
    modifier: Modifier = Modifier,
) {
    OutlinedTextField(
        value = query,
        onValueChange = onQueryChange,
        placeholder = { Text("Search apps") },
        singleLine = true,
        modifier = modifier.fillMaxWidth(),
    )
}

@Composable
fun SortToggle(
    current: SortMode,
    usageAvailable: Boolean,
    onChange: (SortMode) -> Unit,
    onGrantUsage: () -> Unit,
    modifier: Modifier = Modifier,
) {
    Column(modifier = modifier) {
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.spacedBy(4.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Text(
                "Sort:",
                color = MaterialTheme.colorScheme.onBackground.copy(alpha = 0.6f),
                fontSize = 13.sp,
            )
            SortChip(label = "A → Z", active = current == SortMode.Alphabetical) {
                onChange(SortMode.Alphabetical)
            }
            SortChip(label = "Most used", active = current == SortMode.Usage) {
                onChange(SortMode.Usage)
            }
        }
        if (current == SortMode.Usage && !usageAvailable) {
            TextButton(onClick = onGrantUsage) {
                Text(
                    "Grant Usage Access to rank by foreground time",
                    fontSize = 12.sp,
                    color = MaterialTheme.colorScheme.primary,
                )
            }
        }
    }
}

@Composable
private fun SortChip(label: String, active: Boolean, onClick: () -> Unit) {
    TextButton(onClick = onClick) {
        Text(
            text = label,
            color = if (active) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.onBackground.copy(alpha = 0.6f),
            fontWeight = if (active) FontWeight.Bold else FontWeight.Normal,
            fontSize = 13.sp,
        )
    }
}

/**
 * Apply search query + sort to a list of apps. Caller passes both a pre-alphabetised list and a
 * pre-usage-ranked list so the sort is just a list-swap; filtering then runs on the chosen base.
 */
fun filterAndSort(
    query: String,
    sortMode: SortMode,
    alphabetical: List<LaunchableApp>,
    byUsage: List<LaunchableApp>,
): List<LaunchableApp> {
    val base = if (sortMode == SortMode.Usage) byUsage else alphabetical
    if (query.isBlank()) return base
    val q = query.trim()
    return base.filter {
        it.label.contains(q, ignoreCase = true) || it.packageName.contains(q, ignoreCase = true)
    }
}
