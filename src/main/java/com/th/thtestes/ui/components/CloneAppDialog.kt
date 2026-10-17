package com.th.thtestes.ui.components

import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Check
import androidx.compose.material.icons.filled.Clear
import androidx.compose.material.icons.filled.Search
import androidx.compose.material.icons.filled.Verified
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.window.DialogProperties
import com.th.thtestes.domain.HostApp

@Composable
fun CloneAppDialog(
    hostApps: List<HostApp>,
    isLoading: Boolean = false,
    isCloning: Boolean = false,
    onCloneApp: (HostApp) -> Unit,
    onDismiss: () -> Unit
) {
    var selectedTab by remember { mutableIntStateOf(0) } // 0: Todos, 1: Conhecidos
    var searchQuery by remember { mutableStateOf("") }
    
    val knownCount = remember(hostApps) { hostApps.count { it.isKnown } }
    val displayedApps = remember(hostApps, selectedTab, searchQuery) {
        val q = searchQuery.trim()
        hostApps.filter {
            (selectedTab == 0 || it.isKnown) &&
            (q.isEmpty() || it.name.contains(q, ignoreCase = true) || it.packageName.contains(q, ignoreCase = true))
        }
    }

    AlertDialog(
        onDismissRequest = { if (!isCloning) onDismiss() },
        properties = DialogProperties(usePlatformDefaultWidth = false),
        modifier = Modifier
            .fillMaxWidth(0.92f)
            .widthIn(max = 680.dp)
            .padding(vertical = 16.dp),
        title = {
            Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                Text(
                    text = "Clonar Aplicativo",
                    style = MaterialTheme.typography.titleLarge,
                    fontWeight = FontWeight.Bold
                )
                TabRow(
                    selectedTabIndex = selectedTab,
                    modifier = Modifier.fillMaxWidth()
                ) {
                    Tab(
                        selected = selectedTab == 0,
                        onClick = { selectedTab = 0 },
                        text = { Text("Todos (${hostApps.size})") }
                    )
                    Tab(
                        selected = selectedTab == 1,
                        onClick = { selectedTab = 1 },
                        text = {
                            Row(
                                verticalAlignment = Alignment.CenterVertically,
                                horizontalArrangement = Arrangement.spacedBy(4.dp)
                            ) {
                                Icon(
                                    imageVector = Icons.Default.Verified,
                                    contentDescription = null,
                                    modifier = Modifier.size(16.dp),
                                    tint = if (selectedTab == 1) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.onSurfaceVariant
                                )
                                Text("Conhecidos ($knownCount)")
                            }
                        }
                    )
                }
            }
        },
        text = {
            Box(
                modifier = Modifier
                    .fillMaxWidth()
                    .heightIn(min = 360.dp, max = 520.dp)
            ) {
                if (isCloning) {
                    Column(
                        modifier = Modifier.fillMaxSize(),
                        horizontalAlignment = Alignment.CenterHorizontally,
                        verticalArrangement = Arrangement.Center
                    ) {
                        CircularProgressIndicator(strokeWidth = 3.dp)
                        Spacer(modifier = Modifier.height(16.dp))
                        Text(
                            text = "Clonando e isolando no sandbox...",
                            style = MaterialTheme.typography.bodyMedium
                        )
                    }
                } else if (isLoading) {
                    Column(
                        modifier = Modifier.fillMaxSize(),
                        horizontalAlignment = Alignment.CenterHorizontally,
                        verticalArrangement = Arrangement.Center
                    ) {
                        CircularProgressIndicator(strokeWidth = 3.dp)
                        Spacer(modifier = Modifier.height(16.dp))
                        Text(
                            text = "Carregando aplicativos do dispositivo...",
                            style = MaterialTheme.typography.bodyMedium,
                            color = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                    }
                } else {
                    Column(modifier = Modifier.fillMaxSize()) {
                        OutlinedTextField(
                            value = searchQuery,
                            onValueChange = { searchQuery = it },
                            placeholder = {
                                val label = if (selectedTab == 1) "apps conhecidos" else "aplicativo"
                                Text("Pesquisar $label (${displayedApps.size})...")
                            },
                            leadingIcon = { Icon(Icons.Default.Search, contentDescription = null) },
                            trailingIcon = {
                                if (searchQuery.isNotEmpty()) {
                                    IconButton(onClick = { searchQuery = "" }) {
                                        Icon(Icons.Default.Clear, contentDescription = "Limpar")
                                    }
                                }
                            },
                            modifier = Modifier
                                .fillMaxWidth()
                                .padding(bottom = 8.dp),
                            shape = RoundedCornerShape(12.dp),
                            singleLine = true
                        )

                        if (displayedApps.isEmpty()) {
                            Box(
                                modifier = Modifier.fillMaxSize(),
                                contentAlignment = Alignment.Center
                            ) {
                                Text(
                                    text = if (searchQuery.isNotBlank()) "Nenhum aplicativo corresponde a \"$searchQuery\"."
                                           else if (selectedTab == 1) "Nenhum aplicativo com perfil conhecido encontrado no aparelho."
                                           else "Nenhum aplicativo encontrado.",
                                    style = MaterialTheme.typography.bodyMedium,
                                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                                    textAlign = androidx.compose.ui.text.style.TextAlign.Center,
                                    modifier = Modifier.padding(16.dp)
                                )
                            }
                        } else {
                            LazyColumn(
                                modifier = Modifier.fillMaxSize(),
                                verticalArrangement = Arrangement.spacedBy(4.dp)
                            ) {
                                items(displayedApps, key = { it.packageName }) { app ->
                                    HostAppItemRow(
                                        app = app,
                                        onCloneClick = { onCloneApp(app) }
                                    )
                                }
                            }
                        }
                    }
                }
            }
        },
        confirmButton = {
            if (!isCloning) {
                TextButton(onClick = onDismiss) {
                    Text("Fechar")
                }
            }
        }
    )
}

// ==========================================
// PREVIEWS PARA O ANDROID STUDIO
// ==========================================

@androidx.compose.ui.tooling.preview.Preview(name = "Diálogo Escolher App", showBackground = true)
@Composable
private fun CloneAppDialogPreview() {
    com.th.thtestes.ui.theme.ClonaTelaTheme(dynamicColor = false) {
        CloneAppDialog(
            hostApps = listOf(
                HostApp(packageName = "com.whatsapp", name = "WhatsApp", icon = null, sourceDir = "", isCloned = true, isKnown = true),
                HostApp(packageName = "com.instagram.android", name = "Instagram", icon = null, sourceDir = "", isCloned = false, isKnown = true),
                HostApp(packageName = "com.unitv.app", name = "UniTV", icon = null, sourceDir = "", isCloned = false, isKnown = true),
                HostApp(packageName = "com.example.other", name = "Outro App Qualquer", icon = null, sourceDir = "", isCloned = false, isKnown = false)
            ),
            isLoading = false,
            isCloning = false,
            onCloneApp = {},
            onDismiss = {}
        )
    }
}

@androidx.compose.ui.tooling.preview.Preview(name = "Diálogo Carregando", showBackground = true)
@Composable
private fun CloneAppDialogLoadingPreview() {
    com.th.thtestes.ui.theme.ClonaTelaTheme(dynamicColor = false) {
        CloneAppDialog(
            hostApps = emptyList(),
            isLoading = true,
            isCloning = false,
            onCloneApp = {},
            onDismiss = {}
        )
    }
}

@Composable
private fun HostAppItemRow(
    app: HostApp,
    onCloneClick: () -> Unit
) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(8.dp))
            .padding(vertical = 6.dp, horizontal = 4.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        AppIconImage(
            drawable = app.icon,
            contentDescription = app.name,
            size = 40.dp
        )
        Spacer(modifier = Modifier.width(12.dp))
        Column(modifier = Modifier.weight(1f)) {
            Text(
                text = app.name,
                style = MaterialTheme.typography.bodyMedium,
                fontWeight = FontWeight.Medium,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis
            )
            Text(
                text = app.packageName,
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis
            )
        }
        Spacer(modifier = Modifier.width(8.dp))
        if (app.isCloned) {
            FilledTonalButton(
                onClick = onCloneClick,
                contentPadding = PaddingValues(horizontal = 12.dp, vertical = 6.dp),
                shape = RoundedCornerShape(8.dp)
            ) {
                Icon(Icons.Default.Check, contentDescription = null, modifier = Modifier.size(16.dp))
                Spacer(modifier = Modifier.width(4.dp))
                Text("Clonar +", style = MaterialTheme.typography.labelSmall)
            }
        } else {
            Button(
                onClick = onCloneClick,
                contentPadding = PaddingValues(horizontal = 12.dp, vertical = 6.dp),
                shape = RoundedCornerShape(8.dp)
            ) {
                Text("Clonar", style = MaterialTheme.typography.labelSmall)
            }
        }
    }
}
