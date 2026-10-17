package com.th.thtestes.ui.components

import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowForward
import androidx.compose.material.icons.filled.Info
import androidx.compose.material.icons.filled.PlayArrow
import androidx.compose.material3.*
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.window.DialogProperties
import com.th.thtestes.domain.HostApp

@Composable
fun PreCloneRecommendationDialog(
    app: HostApp,
    onOpenOriginal: () -> Unit,
    onContinue: () -> Unit,
    onDismiss: () -> Unit
) {
    AlertDialog(
        onDismissRequest = onDismiss,
        properties = DialogProperties(usePlatformDefaultWidth = false),
        modifier = Modifier
            .fillMaxWidth(0.92f)
            .widthIn(max = 520.dp)
            .padding(vertical = 16.dp),
        title = {
            Row(
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(12.dp)
            ) {
                AppIconImage(
                    drawable = app.icon,
                    contentDescription = app.name,
                    size = 44.dp
                )
                Column {
                    Text(
                        text = app.name,
                        style = MaterialTheme.typography.titleMedium,
                        fontWeight = FontWeight.Bold,
                        color = MaterialTheme.colorScheme.onSurface
                    )
                    Text(
                        text = "Recomendação Pré-Clonagem",
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.primary,
                        fontWeight = FontWeight.Medium
                    )
                }
            }
        },
        text = {
            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .verticalScroll(rememberScrollState()),
                verticalArrangement = Arrangement.spacedBy(14.dp)
            ) {
                Surface(
                    shape = RoundedCornerShape(12.dp),
                    color = MaterialTheme.colorScheme.primaryContainer.copy(alpha = 0.5f),
                    border = CardDefaults.outlinedCardBorder()
                ) {
                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(14.dp),
                        verticalAlignment = Alignment.Top,
                        horizontalArrangement = Arrangement.spacedBy(12.dp)
                    ) {
                        Icon(
                            imageVector = Icons.Default.Info,
                            contentDescription = null,
                            tint = MaterialTheme.colorScheme.primary,
                            modifier = Modifier.size(24.dp)
                        )
                        Column(verticalArrangement = Arrangement.spacedBy(4.dp)) {
                            Text(
                                text = "Abra o app original pelo menos uma vez",
                                style = MaterialTheme.typography.bodyMedium,
                                fontWeight = FontWeight.SemiBold,
                                color = MaterialTheme.colorScheme.onPrimaryContainer
                            )
                            Text(
                                text = "Se este aplicativo foi instalado recentemente na sua TV/Box e nunca foi aberto, é altamente recomendável abri-lo uma vez no sistema original antes de clonar.",
                                style = MaterialTheme.typography.bodySmall,
                                color = MaterialTheme.colorScheme.onPrimaryContainer.copy(alpha = 0.85f)
                            )
                        }
                    }
                }
            }
        },
        confirmButton = {
            Button(
                onClick = onContinue,
                shape = RoundedCornerShape(8.dp)
            ) {
                Text("Continuar")
                Spacer(modifier = Modifier.width(6.dp))
                Icon(
                    imageVector = Icons.AutoMirrored.Filled.ArrowForward,
                    contentDescription = null,
                    modifier = Modifier.size(16.dp)
                )
            }
        },
        dismissButton = {
            Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                TextButton(onClick = onDismiss) {
                    Text("Cancelar")
                }
                OutlinedButton(
                    onClick = onOpenOriginal,
                    shape = RoundedCornerShape(8.dp)
                ) {
                    Icon(
                        imageVector = Icons.Default.PlayArrow,
                        contentDescription = null,
                        modifier = Modifier.size(16.dp)
                    )
                    Spacer(modifier = Modifier.width(4.dp))
                    Text("Abrir Original")
                }
            }
        }
    )
}

// ==========================================
// PREVIEWS PARA O ANDROID STUDIO
// ==========================================

@androidx.compose.ui.tooling.preview.Preview(name = "Recomendação Pré-Clonagem (Light)", showBackground = true)
@Composable
private fun PreCloneRecommendationDialogLightPreview() {
    com.th.thtestes.ui.theme.ClonaTelaTheme(darkTheme = false, dynamicColor = false) {
        PreCloneRecommendationDialog(
            app = HostApp(
                packageName = "com.unitv.app",
                name = "UniTV",
                icon = null,
                sourceDir = "",
                isCloned = false,
                isKnown = true
            ),
            onOpenOriginal = {},
            onContinue = {},
            onDismiss = {}
        )
    }
}

@androidx.compose.ui.tooling.preview.Preview(name = "Recomendação Pré-Clonagem (Dark)", showBackground = true)
@Composable
private fun PreCloneRecommendationDialogDarkPreview() {
    com.th.thtestes.ui.theme.ClonaTelaTheme(darkTheme = true, dynamicColor = false) {
        PreCloneRecommendationDialog(
            app = HostApp(
                packageName = "com.unitv.app",
                name = "UniTV",
                icon = null,
                sourceDir = "",
                isCloned = false,
                isKnown = true
            ),
            onOpenOriginal = {},
            onContinue = {},
            onDismiss = {}
        )
    }
}
