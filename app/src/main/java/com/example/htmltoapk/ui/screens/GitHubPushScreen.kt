package com.example.htmltoapk.ui.screens

import androidx.compose.foundation.layout.*
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.input.PasswordVisualTransformation
import androidx.compose.ui.text.input.VisualTransformation
import androidx.compose.ui.unit.dp
import com.example.htmltoapk.data.model.GitHubPushResult

@Composable
fun GitHubPushScreen(
    isBusy: Boolean,
    busyMessage: String,
    pushProgress: Float,
    lastResult: GitHubPushResult?,
    onPush: (owner: String, repo: String, token: String, private_: Boolean) -> Unit,
    onOpenUrl: (String) -> Unit
) {
    var token by remember { mutableStateOf("") }
    var owner by remember { mutableStateOf("") }
    var repo by remember { mutableStateOf("") }
    var makePrivate by remember { mutableStateOf(true) }
    var showToken by remember { mutableStateOf(false) }

    Column(
        modifier = Modifier.fillMaxSize().padding(20.dp),
        verticalArrangement = Arrangement.spacedBy(14.dp)
    ) {
        Text("Push to GitHub", style = MaterialTheme.typography.headlineLarge)
        Text(
            "Creates/updates a repo and pushes the full generated project, including a manual-trigger " +
                "GitHub Actions workflow that builds a debug APK on demand.",
            style = MaterialTheme.typography.bodyMedium,
            color = MaterialTheme.colorScheme.onSurfaceVariant
        )

        OutlinedTextField(
            value = token,
            onValueChange = { token = it },
            label = { Text("Personal Access Token (repo scope)") },
            visualTransformation = if (showToken) VisualTransformation.None else PasswordVisualTransformation(),
            trailingIcon = {
                IconButton(onClick = { showToken = !showToken }) {
                    Icon(if (showToken) Icons.Filled.VisibilityOff else Icons.Filled.Visibility, null)
                }
            },
            modifier = Modifier.fillMaxWidth()
        )
        OutlinedTextField(
            value = owner,
            onValueChange = { owner = it },
            label = { Text("Repository owner (user or org)") },
            modifier = Modifier.fillMaxWidth()
        )
        OutlinedTextField(
            value = repo,
            onValueChange = { repo = it },
            label = { Text("Repository name") },
            modifier = Modifier.fillMaxWidth()
        )

        Row(verticalAlignment = Alignment.CenterVertically) {
            Switch(checked = makePrivate, onCheckedChange = { makePrivate = it })
            Spacer(Modifier.width(8.dp))
            Text("Create as private repository")
        }

        Button(
            onClick = { onPush(owner.trim(), repo.trim(), token.trim(), makePrivate) },
            enabled = !isBusy && owner.isNotBlank() && repo.isNotBlank() && token.isNotBlank(),
            modifier = Modifier.fillMaxWidth()
        ) {
            Icon(Icons.Filled.CloudUpload, contentDescription = null)
            Spacer(Modifier.width(8.dp))
            Text("Push project to GitHub")
        }

        if (isBusy) {
            Column {
                LinearProgressIndicator(progress = { pushProgress }, modifier = Modifier.fillMaxWidth())
                Spacer(Modifier.height(4.dp))
                Text(busyMessage, style = MaterialTheme.typography.labelSmall)
            }
        }

        lastResult?.let { result ->
            Card(
                colors = CardDefaults.cardColors(
                    containerColor = if (result.success)
                        MaterialTheme.colorScheme.secondary.copy(alpha = 0.15f)
                    else
                        MaterialTheme.colorScheme.error.copy(alpha = 0.15f)
                )
            ) {
                Column(Modifier.padding(14.dp)) {
                    Text(
                        if (result.success) "Push succeeded" else "Push failed",
                        style = MaterialTheme.typography.titleMedium
                    )
                    Text(result.message, style = MaterialTheme.typography.bodyMedium)
                    if (result.success && result.repoUrl != null) {
                        Spacer(Modifier.height(8.dp))
                        Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                            TextButton(onClick = { onOpenUrl(result.repoUrl) }) { Text("Open repo") }
                            result.actionsUrl?.let { url ->
                                TextButton(onClick = { onOpenUrl(url) }) { Text("Open Actions tab") }
                            }
                        }
                    }
                }
            }
        }
    }
}
