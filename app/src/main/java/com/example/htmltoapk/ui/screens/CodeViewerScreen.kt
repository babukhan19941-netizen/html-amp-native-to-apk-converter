package com.example.htmltoapk.ui.screens

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.example.htmltoapk.data.model.LoadedProject

/** A node in the collapsible file tree: either a folder (children) or a leaf file (relativePath set). */
private sealed class TreeNode(val name: String) {
    class Folder(name: String, val children: MutableMap<String, TreeNode> = linkedMapOf()) : TreeNode(name)
    class Leaf(name: String, val relativePath: String) : TreeNode(name)
}

private fun buildTree(paths: List<String>): TreeNode.Folder {
    val root = TreeNode.Folder("")
    paths.sorted().forEach { path ->
        var current = root
        val parts = path.split("/")
        parts.forEachIndexed { idx, part ->
            if (idx == parts.lastIndex) {
                current.children[part] = TreeNode.Leaf(part, path)
            } else {
                val next = current.children.getOrPut(part) { TreeNode.Folder(part) }
                if (next is TreeNode.Folder) current = next
            }
        }
    }
    return root
}

@Composable
fun CodeViewerScreen(
    project: LoadedProject,
    selectedPath: String?,
    onSelectFile: (String) -> Unit,
    onEditFile: (String, String) -> Unit
) {
    Row(modifier = Modifier.fillMaxSize()) {
        // File tree panel
        Box(
            modifier = Modifier
                .width(160.dp)
                .fillMaxHeight()
                .background(MaterialTheme.colorScheme.surfaceVariant)
        ) {
            val tree = remember(project.files.size) { buildTree(project.files.map { it.relativePath }) }
            LazyColumn(modifier = Modifier.fillMaxSize().padding(vertical = 8.dp)) {
                renderFolder(tree, depth = 0, selectedPath = selectedPath, onSelectFile = onSelectFile, scope = this)
            }
        }

        VerticalDivider()

        // Editor panel
        Box(modifier = Modifier.weight(1f).fillMaxHeight()) {
            val file = project.files.firstOrNull { it.relativePath == selectedPath }
            if (file == null) {
                Box(Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                    Text("Select a file to view or edit", color = MaterialTheme.colorScheme.onSurfaceVariant)
                }
            } else if (file.isBinary) {
                Box(Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                    Column(horizontalAlignment = Alignment.CenterHorizontally) {
                        Icon(Icons.Filled.InsertDriveFile, contentDescription = null)
                        Text("Binary file — ${file.relativePath}")
                    }
                }
            } else {
                var text by remember(file.relativePath) { mutableStateOf(file.textContent ?: "") }
                Column(Modifier.fillMaxSize()) {
                    Surface(
                        color = MaterialTheme.colorScheme.surface,
                        tonalElevation = 2.dp
                    ) {
                        Text(
                            file.relativePath,
                            modifier = Modifier.fillMaxWidth().padding(8.dp),
                            style = MaterialTheme.typography.labelLarge
                        )
                    }
                    OutlinedTextField(
                        value = text,
                        onValueChange = {
                            text = it
                            onEditFile(file.relativePath, it)
                        },
                        modifier = Modifier.fillMaxSize().padding(4.dp),
                        textStyle = TextStyle(fontFamily = FontFamily.Monospace, fontSize = 13.sp),
                        keyboardOptions = KeyboardOptions.Default
                    )
                }
            }
        }
    }
}

private fun androidx.compose.foundation.lazy.LazyListScope.renderFolder(
    folder: TreeNode.Folder,
    depth: Int,
    selectedPath: String?,
    onSelectFile: (String) -> Unit,
    scope: androidx.compose.foundation.lazy.LazyListScope
) {
    folder.children.values.forEach { node ->
        when (node) {
            is TreeNode.Folder -> {
                item {
                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(start = (depth * 12).dp, top = 4.dp, bottom = 4.dp),
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Icon(
                            Icons.Filled.FolderOpen,
                            contentDescription = null,
                            modifier = Modifier.size(16.dp)
                        )
                        Spacer(Modifier.width(4.dp))
                        Text(node.name, style = MaterialTheme.typography.labelLarge, maxLines = 1)
                    }
                }
                // Folders render expanded by default (a simple tree for this scaffold;
                // swap in per-node expand/collapse state for a production editor).
                renderFolder(node, depth + 1, selectedPath, onSelectFile, scope)
            }
            is TreeNode.Leaf -> {
                item {
                    val isSelected = node.relativePath == selectedPath
                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .background(
                                if (isSelected) MaterialTheme.colorScheme.primary.copy(alpha = 0.15f)
                                else androidx.compose.ui.graphics.Color.Transparent
                            )
                            .padding(start = (depth * 12 + 20).dp, top = 4.dp, bottom = 4.dp)
                            .clickableToggle { onSelectFile(node.relativePath) },
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Icon(Icons.Filled.Description, contentDescription = null, modifier = Modifier.size(14.dp))
                        Spacer(Modifier.width(4.dp))
                        Text(node.name, style = MaterialTheme.typography.bodyMedium, maxLines = 1)
                    }
                }
            }
        }
    }
}

@Composable
private fun Modifier.clickableToggle(onClick: () -> Unit): Modifier {
    val interactionSource = remember { androidx.compose.foundation.interaction.MutableInteractionSource() }
    return this.then(
        androidx.compose.foundation.clickable(
            interactionSource = interactionSource,
            indication = null,
            onClick = onClick
        )
    )
}
