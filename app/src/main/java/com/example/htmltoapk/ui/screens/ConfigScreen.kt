package com.example.htmltoapk.ui.screens

import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import com.example.htmltoapk.data.model.ProjectConfig
import com.example.htmltoapk.data.model.ScreenOrientation

@Composable
fun ConfigScreen(
    config: ProjectConfig,
    onUpdate: ((ProjectConfig) -> Unit) -> Unit
) {
    LazyColumn(
        modifier = Modifier.fillMaxSize().padding(16.dp),
        verticalArrangement = Arrangement.spacedBy(16.dp)
    ) {
        item { SectionTitle("App identity") }
        item {
            OutlinedTextField(
                value = config.appName,
                onValueChange = { v -> onUpdate { it.appName = v } },
                label = { Text("App name") },
                modifier = Modifier.fillMaxWidth()
            )
        }
        item {
            OutlinedTextField(
                value = config.packageName,
                onValueChange = { v -> onUpdate { it.packageName = v } },
                label = { Text("Package name") },
                supportingText = { Text("e.g. com.yourcompany.appname") },
                modifier = Modifier.fillMaxWidth()
            )
        }

        item { SectionTitle("Versioning") }
        item {
            Row(horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                OutlinedTextField(
                    value = config.versionName,
                    onValueChange = { v -> onUpdate { it.versionName = v } },
                    label = { Text("Version name") },
                    modifier = Modifier.weight(1f)
                )
                OutlinedTextField(
                    value = config.versionCode.toString(),
                    onValueChange = { v -> v.toIntOrNull()?.let { code -> onUpdate { it.versionCode = code } } },
                    label = { Text("Version code") },
                    modifier = Modifier.weight(1f)
                )
            }
        }
        item {
            Row(horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                OutlinedTextField(
                    value = config.targetSdk.toString(),
                    onValueChange = { v -> v.toIntOrNull()?.let { sdk -> onUpdate { it.targetSdk = sdk } } },
                    label = { Text("Target SDK") },
                    modifier = Modifier.weight(1f)
                )
                OutlinedTextField(
                    value = config.minSdk.toString(),
                    onValueChange = { v -> v.toIntOrNull()?.let { sdk -> onUpdate { it.minSdk = sdk } } },
                    label = { Text("Min SDK") },
                    modifier = Modifier.weight(1f)
                )
            }
        }

        item { SectionTitle("Orientation") }
        item {
            Column {
                ScreenOrientation.values().forEach { orientation ->
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        verticalAlignment = androidx.compose.ui.Alignment.CenterVertically
                    ) {
                        RadioButton(
                            selected = config.orientation == orientation,
                            onClick = { onUpdate { it.orientation = orientation } }
                        )
                        Text(orientation.label)
                    }
                }
            }
        }

        item { SectionTitle("Theme colors") }
        item {
            Row(horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                OutlinedTextField(
                    value = config.primaryColorHex,
                    onValueChange = { v -> onUpdate { it.primaryColorHex = v } },
                    label = { Text("Primary (#hex)") },
                    modifier = Modifier.weight(1f)
                )
                OutlinedTextField(
                    value = config.accentColorHex,
                    onValueChange = { v -> onUpdate { it.accentColorHex = v } },
                    label = { Text("Accent (#hex)") },
                    modifier = Modifier.weight(1f)
                )
            }
        }

        item { SectionTitle("Permissions") }
        items(config.permissions) { perm ->
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = androidx.compose.ui.Alignment.CenterVertically
            ) {
                Column {
                    Text(perm.displayName)
                    Text(
                        perm.manifestName,
                        style = MaterialTheme.typography.labelSmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                }
                Switch(
                    checked = perm.enabled,
                    onCheckedChange = { checked ->
                        onUpdate { cfg -> cfg.permissions.find { it.manifestName == perm.manifestName }?.enabled = checked }
                    }
                )
            }
        }
        item { Spacer(Modifier.height(24.dp)) }
    }
}

@Composable
private fun SectionTitle(text: String) {
    Text(text, style = MaterialTheme.typography.titleMedium)
}
