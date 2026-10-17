package com.th.thtestes.ui.components

import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.Shortcut
import androidx.compose.material.icons.filled.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.window.DialogProperties
import com.th.thtestes.domain.ClonedApp

@Composable
fun CloneQuickActionDialog(
    app: ClonedApp,
    onReclone: () -> Unit,
    onLaunch: () -> Unit,
    onCreateShortcut: () -> Unit,
    onDismiss: () -> Unit
) {
    AlertDialog(
        onDismissRequest = onDismiss,
        properties = DialogProperties(usePlatformDefaultWidth = false),
        modifier = Modifier
            .fillMaxWidth(0.90f)
            .widthIn(max = 560.dp)
            .padding(vertical = 16.dp),
        title = {
            AppDialogHeader(app = app)
        },
        text = {
            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .verticalScroll(rememberScrollState()),
                verticalArrangement = Arrangement.spacedBy(8.dp)
            ) {
                ActionMenuItem(
                    icon = Icons.Default.Autorenew,
                    title = "Renovar Identidade",
                    subtitle = "Cria um novo clone limpo com novos identificadores",
                    onClick = {
                        onDismiss()
                        onReclone()
                    }
                )
                ActionMenuItem(
                    icon = Icons.Default.PlayArrow,
                    title = "Abrir",
                    subtitle = "Inicia o aplicativo normalmente",
                    onClick = {
                        onDismiss()
                        onLaunch()
                    }
                )
                ActionMenuItem(
                    icon = Icons.AutoMirrored.Filled.Shortcut,
                    title = "Criar Atalho",
                    subtitle = "Cria um atalho para abrir o aplicativo diretamente",
                    onClick = {
                        onDismiss()
                        onCreateShortcut()
                    }
                )
            }
        },
        confirmButton = {
            TextButton(onClick = onDismiss) {
                Text("Fechar")
            }
        }
    )
}

@Composable
fun AppActionMenuDialog(
    app: ClonedApp,
    onLaunch: () -> Unit,
    onCreateShortcut: () -> Unit,
    onEditFingerprint: () -> Unit,
    onReclone: () -> Unit,
    onResetSeed: (() -> Unit)? = null,
    onDeepClean: () -> Unit,
    onClearCache: () -> Unit,
    onStopApp: () -> Unit,
    onUninstall: () -> Unit,
    onDismiss: () -> Unit
) {
    AlertDialog(
        onDismissRequest = onDismiss,
        properties = DialogProperties(usePlatformDefaultWidth = false),
        modifier = Modifier
            .fillMaxWidth(0.90f)
            .widthIn(max = 560.dp)
            .padding(vertical = 16.dp),
        title = {
            AppDialogHeader(app = app)
        },
        text = {
            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .verticalScroll(rememberScrollState()),
                verticalArrangement = Arrangement.spacedBy(8.dp)
            ) {
                ActionMenuItem(
                    icon = Icons.Default.PlayArrow,
                    title = "Abrir Aplicativo",
                    subtitle = "Inicia o aplicativo normalmente",
                    onClick = {
                        onDismiss()
                        onLaunch()
                    }
                )
                ActionMenuItem(
                    icon = Icons.AutoMirrored.Filled.Shortcut,
                    title = "Criar Atalho",
                    subtitle = "Cria um atalho para abrir o aplicativo diretamente",
                    onClick = {
                        onDismiss()
                        onCreateShortcut()
                    }
                )
                ActionMenuItem(
                    icon = Icons.Default.Fingerprint,
                    title = "Personalizar Fingerprint",
                    subtitle = "Visualizar ou alterar IDs, MACs e perfil deste clone",
                    onClick = {
                        onDismiss()
                        onEditFingerprint()
                    }
                )
                ActionMenuItem(
                    icon = Icons.Default.Autorenew,
                    title = "Renovar Identidade / Conta",
                    subtitle = "Cria um novo clone limpo com novos identificadores",
                    onClick = {
                        onDismiss()
                        onReclone()
                    }
                )
                if (onResetSeed != null) {
                    ActionMenuItem(
                        icon = Icons.Default.Shuffle,
                        title = "Regenerar IDs (Semente Limpa)",
                        subtitle = "Gera nova semente de hardware sem reinstalar o aplicativo",
                        onClick = {
                            onDismiss()
                            onResetSeed()
                        }
                    )
                }
                ActionMenuItem(
                    icon = Icons.Default.CleaningServices,
                    title = "Limpar Dados",
                    subtitle = "Restaura o aplicativo ao estado inicial",
                    onClick = {
                        onDismiss()
                        onDeepClean()
                    }
                )
                ActionMenuItem(
                    icon = Icons.Default.Cached,
                    title = "Limpar Cache",
                    subtitle = "Libera espaço em disco",
                    onClick = {
                        onDismiss()
                        onClearCache()
                    }
                )
                if (app.isRunning) {
                    ActionMenuItem(
                        icon = Icons.Default.Stop,
                        title = "Fechar Aplicativo",
                        subtitle = "Encerra a execução em segundo plano",
                        onClick = {
                            onDismiss()
                            onStopApp()
                        }
                    )
                }
                ActionMenuItem(
                    icon = Icons.Default.Delete,
                    title = "Desinstalar",
                    subtitle = "Remove o aplicativo clonado",
                    isDestructive = true,
                    onClick = {
                        onDismiss()
                        onUninstall()
                    }
                )
            }
        },
        confirmButton = {
            TextButton(onClick = onDismiss) {
                Text("Fechar")
            }
        }
    )
}

@Composable
private fun AppDialogHeader(app: ClonedApp) {
    Row(
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(12.dp)
    ) {
        AppIconImage(drawable = app.icon, contentDescription = app.name, size = 44.dp)
        Column {
            Text(text = app.name, style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.Bold)
            Text(
                text = app.packageName,
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )
            Row(
                horizontalArrangement = Arrangement.spacedBy(6.dp),
                modifier = Modifier.padding(top = 4.dp)
            ) {
                Surface(
                    shape = RoundedCornerShape(4.dp),
                    color = MaterialTheme.colorScheme.primaryContainer
                ) {
                    Text(
                        text = if (app.userId == 0) "Principal" else "Perfil ${app.userId + 1}",
                        style = MaterialTheme.typography.labelSmall,
                        color = MaterialTheme.colorScheme.onPrimaryContainer,
                        modifier = Modifier.padding(horizontal = 6.dp, vertical = 2.dp)
                    )
                }
                if (!app.personaId.isNullOrEmpty()) {
                    Surface(
                        shape = RoundedCornerShape(4.dp),
                        color = MaterialTheme.colorScheme.secondaryContainer
                    ) {
                        Text(
                            text = app.personaId,
                            style = MaterialTheme.typography.labelSmall,
                            color = MaterialTheme.colorScheme.onSecondaryContainer,
                            modifier = Modifier.padding(horizontal = 6.dp, vertical = 2.dp)
                        )
                    }
                }
                if (app.hasCustomOverrides) {
                    Surface(
                        shape = RoundedCornerShape(4.dp),
                        color = MaterialTheme.colorScheme.tertiaryContainer
                    ) {
                        Text(
                            text = "Customizado",
                            style = MaterialTheme.typography.labelSmall,
                            color = MaterialTheme.colorScheme.onTertiaryContainer,
                            modifier = Modifier.padding(horizontal = 6.dp, vertical = 2.dp)
                        )
                    }
                }
            }
        }
    }
}

@Composable
fun ActionMenuItem(
    icon: ImageVector,
    title: String,
    subtitle: String,
    isDestructive: Boolean = false,
    onClick: () -> Unit
) {
    val color = if (isDestructive) MaterialTheme.colorScheme.error else MaterialTheme.colorScheme.onSurface
    Surface(
        modifier = Modifier
            .fillMaxWidth()
            .clickable(onClick = onClick),
        shape = RoundedCornerShape(8.dp),
        color = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.5f)
    ) {
        Row(
            modifier = Modifier.padding(12.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(12.dp)
        ) {
            Icon(imageVector = icon, contentDescription = null, tint = color, modifier = Modifier.size(24.dp))
            Column {
                Text(text = title, style = MaterialTheme.typography.bodyMedium, fontWeight = FontWeight.SemiBold, color = color)
                Text(text = subtitle, style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
            }
        }
    }
}

// ==========================================
// PREVIEWS PARA O ANDROID STUDIO
// ==========================================

@androidx.compose.ui.tooling.preview.Preview(name = "Ações Rápidas do Clone", showBackground = true)
@Composable
private fun CloneQuickActionDialogPreview() {
    com.th.thtestes.ui.theme.ClonaTelaTheme(dynamicColor = false) {
        CloneQuickActionDialog(
            app = ClonedApp(packageName = "com.whatsapp", name = "WhatsApp", icon = null, isRunning = true),
            onReclone = {},
            onLaunch = {},
            onCreateShortcut = {},
            onDismiss = {}
        )
    }
}

@androidx.compose.ui.tooling.preview.Preview(name = "Menu Completo de Opções", showBackground = true)
@Composable
private fun AppActionMenuDialogPreview() {
    com.th.thtestes.ui.theme.ClonaTelaTheme(dynamicColor = false) {
        AppActionMenuDialog(
            app = ClonedApp(packageName = "com.whatsapp", name = "WhatsApp", icon = null, isRunning = true),
            onLaunch = {},
            onCreateShortcut = {},
            onEditFingerprint = {},
            onReclone = {},
            onDeepClean = {},
            onClearCache = {},
            onStopApp = {},
            onUninstall = {},
            onDismiss = {}
        )
    }
}

