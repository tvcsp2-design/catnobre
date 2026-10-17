package com.th.thtestes.ui.components

import android.content.ClipData
import android.content.ClipboardManager
import android.content.Context
import android.content.Intent
import android.content.res.Configuration
import android.widget.Toast
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.dp
import androidx.compose.ui.window.Dialog
import androidx.compose.ui.window.DialogProperties
import androidx.core.content.FileProvider
import com.th.thtestes.domain.logs.CrashReportManager
import com.th.thtestes.domain.logs.LogStorageManager
import com.th.thtestes.domain.logs.LogcatRecorder
import com.th.thtestes.ui.theme.ClonaTelaTheme
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import java.io.File
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

/**
 * Modelo imutável representando um arquivo de log na UI.
 */
data class LogItemUiState(
    val name: String,
    val formattedSize: String,
    val formattedDate: String,
    val isCrashReport: Boolean,
    val isLogcat: Boolean,
    val absolutePath: String
)

/**
 * Stateful Composable para gerenciamento e visualização de diagnósticos e logs.
 */
@Composable
fun DiagnosticsDialog(
    onDismissRequest: () -> Unit,
    modifier: Modifier = Modifier
) {
    val context = LocalContext.current
    val coroutineScope = rememberCoroutineScope()

    var isWorking by remember { mutableStateOf(false) }
    var statusMessage by remember { mutableStateOf<String?>(null) }
    var logItems by remember { mutableStateOf<List<LogItemUiState>>(emptyList()) }

    val publicLogDir = remember { LogStorageManager.getPublicLogDirectory().absolutePath }

    fun refreshLogs() {
        coroutineScope.launch(Dispatchers.IO) {
            val files = LogStorageManager.listAllLogFiles(context)
            val items = files.map { file ->
                val isCrash = file.name.startsWith("crash_")
                val isLogcat = file.name.startsWith("logcat_")
                val sizeKb = (file.length() / 1024.0)
                val formattedSize = if (sizeKb < 1024) {
                    String.format(Locale.US, "%.1f KB", sizeKb)
                } else {
                    String.format(Locale.US, "%.2f MB", sizeKb / 1024.0)
                }
                val dateStr = SimpleDateFormat("dd/MM HH:mm:ss", Locale.getDefault()).format(Date(file.lastModified()))
                LogItemUiState(
                    name = file.name,
                    formattedSize = formattedSize,
                    formattedDate = dateStr,
                    isCrashReport = isCrash,
                    isLogcat = isLogcat,
                    absolutePath = file.absolutePath
                )
            }
            withContext(Dispatchers.Main) {
                logItems = items
            }
        }
    }

    LaunchedEffect(Unit) {
        refreshLogs()
    }

    val onCopyPath: () -> Unit = {
        try {
            val clipboard = context.getSystemService(Context.CLIPBOARD_SERVICE) as? ClipboardManager
            val clip = ClipData.newPlainText("Caminho Logs ClonaTela", publicLogDir)
            clipboard?.setPrimaryClip(clip)
            Toast.makeText(context, "Caminho copiado para a área de transferência!", Toast.LENGTH_SHORT).show()
        } catch (_: Throwable) {
        }
    }

    val onCaptureLogcat: () -> Unit = {
        isWorking = true
        statusMessage = "Capturando Logcat do sistema..."
        coroutineScope.launch(Dispatchers.IO) {
            val file = LogcatRecorder.captureAndSaveLogcat(context, 1500)
            withContext(Dispatchers.Main) {
                isWorking = false
                statusMessage = if (file != null) "Logcat salvo com sucesso!" else "Falha ao capturar Logcat"
                refreshLogs()
            }
        }
    }

    val onExportZip: () -> Unit = {
        isWorking = true
        statusMessage = "Gerando pacote ZIP de logs..."
        coroutineScope.launch(Dispatchers.IO) {
            val zipFile = LogStorageManager.createConsolidatedZip(context)
            withContext(Dispatchers.Main) {
                isWorking = false
                if (zipFile != null && zipFile.exists()) {
                    statusMessage = "ZIP gerado com sucesso!"
                    try {
                        val uri = FileProvider.getUriForFile(
                            context,
                            "${context.packageName}.fileprovider",
                            zipFile
                        )
                        val sendIntent = Intent(Intent.ACTION_SEND).apply {
                            type = "application/zip"
                            putExtra(Intent.EXTRA_STREAM, uri)
                            putExtra(Intent.EXTRA_SUBJECT, "Relatórios de Diagnóstico ClonaTela")
                            addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION)
                        }
                        context.startActivity(Intent.createChooser(sendIntent, "Exportar Logs ClonaTela"))
                    } catch (t: Throwable) {
                        Toast.makeText(context, "Erro ao compartilhar: ${t.message}", Toast.LENGTH_LONG).show()
                    }
                } else {
                    statusMessage = "Nenhum arquivo de log para exportar."
                }
                refreshLogs()
            }
        }
    }

    val onClearLogs: () -> Unit = {
        isWorking = true
        coroutineScope.launch(Dispatchers.IO) {
            LogStorageManager.clearAllLogs(context)
            CrashReportManager.clearRecentCrashAlert(context)
            withContext(Dispatchers.Main) {
                isWorking = false
                statusMessage = "Todos os logs foram apagados."
                refreshLogs()
            }
        }
    }

    DiagnosticsDialogContent(
        publicLogPath = publicLogDir,
        logFiles = logItems,
        isWorking = isWorking,
        statusMessage = statusMessage,
        onCopyPath = onCopyPath,
        onCaptureLogcat = onCaptureLogcat,
        onExportZip = onExportZip,
        onClearLogs = onClearLogs,
        onDismiss = onDismissRequest,
        modifier = modifier
    )
}

/**
 * Stateless Composable desacoplado para renderização de UI e Previews.
 */
@Composable
fun DiagnosticsDialogContent(
    publicLogPath: String,
    logFiles: List<LogItemUiState>,
    isWorking: Boolean,
    statusMessage: String?,
    onCopyPath: () -> Unit,
    onCaptureLogcat: () -> Unit,
    onExportZip: () -> Unit,
    onClearLogs: () -> Unit,
    onDismiss: () -> Unit,
    modifier: Modifier = Modifier
) {
    Dialog(
        onDismissRequest = onDismiss,
        properties = DialogProperties(usePlatformDefaultWidth = false)
    ) {
        Surface(
            modifier = modifier
                .fillMaxWidth(0.92f)
                .fillMaxHeight(0.85f),
            shape = RoundedCornerShape(24.dp),
            color = MaterialTheme.colorScheme.surface,
            tonalElevation = 6.dp
        ) {
            Column(
                modifier = Modifier
                    .fillMaxSize()
                    .padding(20.dp)
            ) {
                // Cabeçalho
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.SpaceBetween
                ) {
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Icon(
                            imageVector = Icons.Default.BugReport,
                            contentDescription = null,
                            tint = MaterialTheme.colorScheme.primary,
                            modifier = Modifier.size(28.dp)
                        )
                        Spacer(modifier = Modifier.width(10.dp))
                        Text(
                            text = "Diagnóstico & Logs",
                            style = MaterialTheme.typography.titleLarge,
                            fontWeight = FontWeight.Bold
                        )
                    }
                    IconButton(onClick = onDismiss) {
                        Icon(Icons.Default.Close, contentDescription = "Fechar")
                    }
                }

                Spacer(modifier = Modifier.height(12.dp))

                // Card com Caminho da Pasta Pública
                Card(
                    modifier = Modifier.fillMaxWidth(),
                    colors = CardDefaults.cardColors(
                        containerColor = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.5f)
                    ),
                    shape = RoundedCornerShape(12.dp)
                ) {
                    Column(modifier = Modifier.padding(12.dp)) {
                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            verticalAlignment = Alignment.CenterVertically,
                            horizontalArrangement = Arrangement.SpaceBetween
                        ) {
                            Text(
                                text = "Pasta Pública de Logs:",
                                style = MaterialTheme.typography.labelMedium,
                                color = MaterialTheme.colorScheme.primary,
                                fontWeight = FontWeight.SemiBold
                            )
                            IconButton(
                                onClick = onCopyPath,
                                modifier = Modifier.size(28.dp)
                            ) {
                                Icon(
                                    imageVector = Icons.Default.ContentCopy,
                                    contentDescription = "Copiar Caminho",
                                    tint = MaterialTheme.colorScheme.primary,
                                    modifier = Modifier.size(18.dp)
                                )
                            }
                        }
                        Text(
                            text = publicLogPath,
                            style = MaterialTheme.typography.bodySmall,
                            fontFamily = FontFamily.Monospace,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                            maxLines = 2,
                            overflow = TextOverflow.Ellipsis
                        )
                    }
                }

                Spacer(modifier = Modifier.height(12.dp))

                // Barra de Ações Rápidas
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.spacedBy(8.dp)
                ) {
                    Button(
                        onClick = onCaptureLogcat,
                        enabled = !isWorking,
                        modifier = Modifier.weight(1f),
                        contentPadding = PaddingValues(horizontal = 8.dp, vertical = 8.dp),
                        shape = RoundedCornerShape(10.dp)
                    ) {
                        Icon(Icons.Default.Terminal, contentDescription = null, modifier = Modifier.size(16.dp))
                        Spacer(modifier = Modifier.width(6.dp))
                        Text(text = "Logcat", style = MaterialTheme.typography.labelMedium)
                    }

                    FilledTonalButton(
                        onClick = onExportZip,
                        enabled = !isWorking && logFiles.isNotEmpty(),
                        modifier = Modifier.weight(1f),
                        contentPadding = PaddingValues(horizontal = 8.dp, vertical = 8.dp),
                        shape = RoundedCornerShape(10.dp)
                    ) {
                        Icon(Icons.Default.Share, contentDescription = null, modifier = Modifier.size(16.dp))
                        Spacer(modifier = Modifier.width(6.dp))
                        Text(text = "Exportar ZIP", style = MaterialTheme.typography.labelMedium)
                    }

                    OutlinedButton(
                        onClick = onClearLogs,
                        enabled = !isWorking && logFiles.isNotEmpty(),
                        contentPadding = PaddingValues(horizontal = 10.dp, vertical = 8.dp),
                        shape = RoundedCornerShape(10.dp)
                    ) {
                        Icon(
                            Icons.Default.DeleteOutline,
                            contentDescription = "Limpar",
                            tint = MaterialTheme.colorScheme.error,
                            modifier = Modifier.size(16.dp)
                        )
                    }
                }

                if (isWorking || !statusMessage.isNullOrBlank()) {
                    Spacer(modifier = Modifier.height(8.dp))
                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .clip(RoundedCornerShape(8.dp))
                            .background(MaterialTheme.colorScheme.primaryContainer.copy(alpha = 0.4f))
                            .padding(horizontal = 12.dp, vertical = 6.dp),
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        if (isWorking) {
                            CircularProgressIndicator(
                                modifier = Modifier.size(14.dp),
                                strokeWidth = 2.dp
                            )
                            Spacer(modifier = Modifier.width(8.dp))
                        }
                        Text(
                            text = statusMessage ?: "",
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onPrimaryContainer
                        )
                    }
                }

                Spacer(modifier = Modifier.height(12.dp))

                // Título da Lista
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Text(
                        text = "Arquivos Registrados (${logFiles.size}):",
                        style = MaterialTheme.typography.titleSmall,
                        fontWeight = FontWeight.Bold
                    )
                }

                Spacer(modifier = Modifier.height(6.dp))

                // Lista de Arquivos com Scroll
                if (logFiles.isEmpty()) {
                    Box(
                        modifier = Modifier
                            .weight(1f)
                            .fillMaxWidth(),
                        contentAlignment = Alignment.Center
                    ) {
                        Column(horizontalAlignment = Alignment.CenterHorizontally) {
                            Icon(
                                imageVector = Icons.Default.CheckCircleOutline,
                                contentDescription = null,
                                tint = MaterialTheme.colorScheme.primary.copy(alpha = 0.5f),
                                modifier = Modifier.size(48.dp)
                            )
                            Spacer(modifier = Modifier.height(8.dp))
                            Text(
                                text = "Nenhum relatório de crash ou log gravado ainda.",
                                style = MaterialTheme.typography.bodyMedium,
                                color = MaterialTheme.colorScheme.onSurfaceVariant
                            )
                        }
                    }
                } else {
                    LazyColumn(
                        modifier = Modifier
                            .weight(1f)
                            .fillMaxWidth(),
                        verticalArrangement = Arrangement.spacedBy(6.dp)
                    ) {
                        items(logFiles, key = { it.absolutePath }) { item ->
                            LogItemRow(item = item)
                        }
                    }
                }

                Spacer(modifier = Modifier.height(10.dp))

                // Rodapé com botão de fechar
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.End
                ) {
                    TextButton(onClick = onDismiss) {
                        Text("Fechar")
                    }
                }
            }
        }
    }
}

/**
 * Componente individual de linha para cada arquivo de log.
 */
@Composable
fun LogItemRow(
    item: LogItemUiState,
    modifier: Modifier = Modifier
) {
    Card(
        modifier = modifier.fillMaxWidth(),
        shape = RoundedCornerShape(10.dp),
        colors = CardDefaults.cardColors(
            containerColor = if (item.isCrashReport) {
                MaterialTheme.colorScheme.errorContainer.copy(alpha = 0.25f)
            } else {
                MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.35f)
            }
        )
    ) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(10.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            val icon = when {
                item.isCrashReport -> Icons.Default.WarningAmber
                item.isLogcat -> Icons.Default.Terminal
                else -> Icons.Default.Description
            }
            val iconTint = when {
                item.isCrashReport -> MaterialTheme.colorScheme.error
                item.isLogcat -> MaterialTheme.colorScheme.secondary
                else -> MaterialTheme.colorScheme.primary
            }

            Icon(
                imageVector = icon,
                contentDescription = null,
                tint = iconTint,
                modifier = Modifier.size(22.dp)
            )

            Spacer(modifier = Modifier.width(10.dp))

            Column(modifier = Modifier.weight(1f)) {
                Text(
                    text = item.name,
                    style = MaterialTheme.typography.bodySmall,
                    fontWeight = FontWeight.SemiBold,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis
                )
                Spacer(modifier = Modifier.height(2.dp))
                Row(
                    horizontalArrangement = Arrangement.spacedBy(8.dp),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Text(
                        text = item.formattedDate,
                        style = MaterialTheme.typography.labelSmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                    Text(
                        text = "•",
                        style = MaterialTheme.typography.labelSmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                    Text(
                        text = item.formattedSize,
                        style = MaterialTheme.typography.labelSmall,
                        fontWeight = FontWeight.Medium,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                }
            }
        }
    }
}

// ==========================================
// PREVIEWS OBRIGATÓRIOS (Material 3 & Theme)
// ==========================================

@Preview(name = "Light Mode - Com Logs", showBackground = true)
@Composable
private fun DiagnosticsDialogContentPreview() {
    ClonaTelaTheme(dynamicColor = false, darkTheme = false) {
        DiagnosticsDialogContent(
            publicLogPath = "/sdcard/Download/ClonaTela_Logs",
            logFiles = listOf(
                LogItemUiState(
                    name = "crash_main_20260902_214000.txt",
                    formattedSize = "45.2 KB",
                    formattedDate = "02/09 21:40:00",
                    isCrashReport = true,
                    isLogcat = false,
                    absolutePath = "/sdcard/Download/ClonaTela_Logs/crash_main_20260902_214000.txt"
                ),
                LogItemUiState(
                    name = "crash_p0_20260902_214210.txt",
                    formattedSize = "32.1 KB",
                    formattedDate = "02/09 21:42:10",
                    isCrashReport = true,
                    isLogcat = false,
                    absolutePath = "/sdcard/Download/ClonaTela_Logs/crash_p0_20260902_214210.txt"
                ),
                LogItemUiState(
                    name = "logcat_20260902_214300.txt",
                    formattedSize = "180.5 KB",
                    formattedDate = "02/09 21:43:00",
                    isCrashReport = false,
                    isLogcat = true,
                    absolutePath = "/sdcard/Download/ClonaTela_Logs/logcat_20260902_214300.txt"
                ),
                LogItemUiState(
                    name = "clonatela_2026-09-02.log",
                    formattedSize = "14.8 KB",
                    formattedDate = "02/09 21:44:30",
                    isCrashReport = false,
                    isLogcat = false,
                    absolutePath = "/sdcard/Download/ClonaTela_Logs/clonatela_2026-09-02.log"
                )
            ),
            isWorking = false,
            statusMessage = null,
            onCopyPath = {},
            onCaptureLogcat = {},
            onExportZip = {},
            onClearLogs = {},
            onDismiss = {}
        )
    }
}

@Preview(name = "Dark Mode - Com Logs", showBackground = true, uiMode = Configuration.UI_MODE_NIGHT_YES)
@Composable
private fun DiagnosticsDialogContentDarkPreview() {
    ClonaTelaTheme(dynamicColor = false, darkTheme = true) {
        DiagnosticsDialogContent(
            publicLogPath = "/sdcard/Download/ClonaTela_Logs",
            logFiles = listOf(
                LogItemUiState(
                    name = "crash_main_20260902_214000.txt",
                    formattedSize = "45.2 KB",
                    formattedDate = "02/09 21:40:00",
                    isCrashReport = true,
                    isLogcat = false,
                    absolutePath = "/sdcard/Download/ClonaTela_Logs/crash_main_20260902_214000.txt"
                )
            ),
            isWorking = false,
            statusMessage = null,
            onCopyPath = {},
            onCaptureLogcat = {},
            onExportZip = {},
            onClearLogs = {},
            onDismiss = {}
        )
    }
}

@Preview(name = "Empty State", showBackground = true)
@Composable
private fun DiagnosticsDialogContentEmptyPreview() {
    ClonaTelaTheme(dynamicColor = false, darkTheme = false) {
        DiagnosticsDialogContent(
            publicLogPath = "/sdcard/Download/ClonaTela_Logs",
            logFiles = emptyList(),
            isWorking = false,
            statusMessage = null,
            onCopyPath = {},
            onCaptureLogcat = {},
            onExportZip = {},
            onClearLogs = {},
            onDismiss = {}
        )
    }
}
