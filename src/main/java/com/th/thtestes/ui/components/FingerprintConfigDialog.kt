package com.th.thtestes.ui.components

import android.graphics.drawable.Drawable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.window.DialogProperties
import com.th.thtestes.domain.FingerprintProfileData
import kotlinx.coroutines.launch
import top.niunaijun.blackbox.compat.BoxSpaceDevicePersona

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun FingerprintConfigDialog(
    appName: String,
    packageName: String,
    icon: Drawable?,
    initialData: FingerprintProfileData,
    confirmButtonText: String = "Confirmar e Clonar",
    onRegenerateRandom: suspend () -> FingerprintProfileData,
    onConfirm: (isManual: Boolean, customData: FingerprintProfileData?) -> Unit,
    onDismiss: () -> Unit
) {
    var selectedTab by remember { mutableIntStateOf(0) } // 0: Auto, 1: Manual
    var fingerprintData by remember(initialData) { mutableStateOf(initialData) }
    val scope = rememberCoroutineScope()

    AlertDialog(
        onDismissRequest = onDismiss,
        properties = DialogProperties(usePlatformDefaultWidth = false),
        modifier = Modifier
            .fillMaxWidth(0.92f)
            .widthIn(max = 680.dp)
            .padding(vertical = 16.dp),
        title = {
            Column(verticalArrangement = Arrangement.spacedBy(12.dp)) {
                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(12.dp)
                ) {
                    AppIconImage(drawable = icon, contentDescription = appName, size = 44.dp)
                    Column(modifier = Modifier.weight(1f)) {
                        Text(
                            text = appName,
                            style = MaterialTheme.typography.titleMedium,
                            fontWeight = FontWeight.Bold
                        )
                        Text(
                            text = packageName,
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                    }
                }

                TabRow(
                    selectedTabIndex = selectedTab,
                    modifier = Modifier.fillMaxWidth()
                ) {
                    Tab(
                        selected = selectedTab == 0,
                        onClick = { selectedTab = 0 },
                        text = {
                            Row(
                                verticalAlignment = Alignment.CenterVertically,
                                horizontalArrangement = Arrangement.spacedBy(6.dp)
                            ) {
                                Icon(Icons.Default.Bolt, contentDescription = null, modifier = Modifier.size(18.dp))
                                Text("Automático")
                            }
                        }
                    )
                    Tab(
                        selected = selectedTab == 1,
                        onClick = { selectedTab = 1 },
                        text = {
                            Row(
                                verticalAlignment = Alignment.CenterVertically,
                                horizontalArrangement = Arrangement.spacedBy(6.dp)
                            ) {
                                Icon(Icons.Default.Tune, contentDescription = null, modifier = Modifier.size(18.dp))
                                Text("Manual (Avançado)")
                            }
                        }
                    )
                }
            }
        },
        text = {
            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .heightIn(max = 560.dp),
                verticalArrangement = Arrangement.spacedBy(12.dp)
            ) {
                // Card de Destaque: Safe Mode (sempre visível em ambos os modos)
                SafeModeNoticeCard(
                    enabled = fingerprintData.isSafeMode,
                    onToggle = { isChecked ->
                        fingerprintData = fingerprintData.copy(isSafeMode = isChecked)
                    }
                )

                if (selectedTab == 0) {
                    // Modo Automático
                    Box(
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(vertical = 12.dp),
                        contentAlignment = Alignment.Center
                    ) {
                        Text(
                            text = "A identidade e os parâmetros do clone serão gerados automaticamente.",
                            style = MaterialTheme.typography.bodyMedium,
                            color = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                    }
                } else {
                    // Modo Manual (Avançado)
                    Column(
                        modifier = Modifier
                            .fillMaxWidth()
                            .weight(1f, fill = false)
                            .verticalScroll(rememberScrollState()),
                        verticalArrangement = Arrangement.spacedBy(12.dp)
                    ) {
                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            horizontalArrangement = Arrangement.SpaceBetween,
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            Text(
                                text = "Personalizar Parâmetros",
                                style = MaterialTheme.typography.titleSmall,
                                fontWeight = FontWeight.Bold
                            )
                            FilledTonalButton(
                                onClick = {
                                    scope.launch { fingerprintData = onRegenerateRandom().copy(isSafeMode = fingerprintData.isSafeMode) }
                                },
                                contentPadding = PaddingValues(horizontal = 10.dp, vertical = 4.dp),
                                shape = RoundedCornerShape(8.dp)
                            ) {
                                Icon(Icons.Default.Shuffle, contentDescription = null, modifier = Modifier.size(16.dp))
                                Spacer(modifier = Modifier.width(4.dp))
                                Text("Gerar Novos", style = MaterialTheme.typography.labelSmall)
                            }
                        }

                        // 0. Seletor de Persona (Template de Hardware)
                        PersonaSelectorCard(
                            selectedPersonaId = fingerprintData.personaId,
                            onSelectPersona = { persona ->
                                fingerprintData = fingerprintData.copy(
                                    personaId = persona.id,
                                    buildBrand = persona.brand,
                                    buildManufacturer = persona.manufacturer,
                                    buildModel = persona.model,
                                    buildDevice = persona.device,
                                    buildFingerprint = persona.fingerprint("13", "TQ3A.230901.001", "1000000")
                                )
                            }
                        )

                        // 1. Identificadores do Dispositivo
                        ConfigSectionCard(icon = Icons.Default.Badge, title = "Identificadores Básicos") {
                            FingerprintField("Android ID (16 hex)", fingerprintData.androidId) { fingerprintData = fingerprintData.copy(androidId = it) }
                            FingerprintField("Serial do Dispositivo", fingerprintData.serial) { fingerprintData = fingerprintData.copy(serial = it) }
                            FingerprintField("IMEI (15 dígitos)", fingerprintData.imei) { fingerprintData = fingerprintData.copy(imei = it) }
                            FingerprintField("Serial da CPU", fingerprintData.cpuSerial) { fingerprintData = fingerprintData.copy(cpuSerial = it) }
                        }

                        // 2. Rede & Conexão
                        ConfigSectionCard(icon = Icons.Default.Wifi, title = "Rede & Endereços MAC") {
                            FingerprintField("Wi-Fi MAC Address", fingerprintData.wifiMac) { fingerprintData = fingerprintData.copy(wifiMac = it) }
                            FingerprintField("Ethernet MAC Address", fingerprintData.ethernetMac) { fingerprintData = fingerprintData.copy(ethernetMac = it) }
                            FingerprintField("Bluetooth MAC Address", fingerprintData.bluetoothMac) { fingerprintData = fingerprintData.copy(bluetoothMac = it) }
                            FingerprintField("Nome do Wi-Fi (SSID)", fingerprintData.wifiSsid) { fingerprintData = fingerprintData.copy(wifiSsid = it) }
                        }

                        // 3. Hardware & Modelo
                        ConfigSectionCard(icon = Icons.Default.Smartphone, title = "Hardware & Modelo") {
                            FingerprintField("Marca (Brand)", fingerprintData.buildBrand) { fingerprintData = fingerprintData.copy(buildBrand = it) }
                            FingerprintField("Modelo (Model)", fingerprintData.buildModel) { fingerprintData = fingerprintData.copy(buildModel = it) }
                            FingerprintField("Fabricante (Manufacturer)", fingerprintData.buildManufacturer) { fingerprintData = fingerprintData.copy(buildManufacturer = it) }
                            FingerprintField("Fingerprint da Build", fingerprintData.buildFingerprint, singleLine = false) { fingerprintData = fingerprintData.copy(buildFingerprint = it) }
                        }

                        // 4. Publicidade / Tracking
                        ConfigSectionCard(icon = Icons.Default.Campaign, title = "IDs de Publicidade & Tracking") {
                            FingerprintField("Google Advertising ID (GAID)", fingerprintData.advertisingId) { fingerprintData = fingerprintData.copy(advertisingId = it) }
                            FingerprintField("AppSet ID", fingerprintData.appSetId) { fingerprintData = fingerprintData.copy(appSetId = it) }
                        }
                    }
                }
            }
        },
        confirmButton = {
            Button(
                onClick = {
                    if (selectedTab == 0) {
                        onConfirm(false, fingerprintData)
                    } else {
                        onConfirm(true, fingerprintData)
                    }
                },
                shape = RoundedCornerShape(10.dp)
            ) {
                Icon(Icons.Default.Check, contentDescription = null, modifier = Modifier.size(18.dp))
                Spacer(modifier = Modifier.width(6.dp))
                Text(confirmButtonText)
            }
        },
        dismissButton = {
            TextButton(onClick = onDismiss) {
                Text("Cancelar")
            }
        }
    )
}

@Composable
private fun SafeModeNoticeCard(
    enabled: Boolean,
    onToggle: (Boolean) -> Unit
) {
    Card(
        shape = RoundedCornerShape(12.dp),
        colors = CardDefaults.cardColors(
            containerColor = if (enabled) {
                MaterialTheme.colorScheme.primaryContainer.copy(alpha = 0.25f)
            } else {
                MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.35f)
            }
        ),
        border = androidx.compose.foundation.BorderStroke(
            width = 1.dp,
            color = if (enabled) {
                MaterialTheme.colorScheme.primary.copy(alpha = 0.5f)
            } else {
                MaterialTheme.colorScheme.outline.copy(alpha = 0.2f)
            }
        ),
        modifier = Modifier.fillMaxWidth()
    ) {
        Column(
            modifier = Modifier.padding(12.dp),
            verticalArrangement = Arrangement.spacedBy(8.dp)
        ) {
            Row(
                modifier = Modifier.fillMaxWidth(),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.SpaceBetween
            ) {
                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(8.dp),
                    modifier = Modifier.weight(1f)
                ) {
                    Icon(
                        imageVector = Icons.Default.Security,
                        contentDescription = null,
                        tint = if (enabled) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.onSurfaceVariant,
                        modifier = Modifier.size(20.dp)
                    )
                    Column {
                        Text(
                            text = "Safe Mode",
                            style = MaterialTheme.typography.titleSmall,
                            fontWeight = FontWeight.Bold
                        )
                        Text(
                            text = if (enabled) "Ativado" else "Desativado",
                            style = MaterialTheme.typography.labelSmall,
                            color = if (enabled) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.onSurfaceVariant
                        )
                    }
                }
                Switch(
                    checked = enabled,
                    onCheckedChange = onToggle
                )
            }

            Text(
                text = "Recomendado para TV Boxes e aparelhos genéricos caso o aplicativo feche sozinho (crash) ou apresente tela preta ao inicializar.",
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )
        }
    }
}

@Composable
private fun ConfigSectionCard(
    icon: ImageVector,
    title: String,
    content: @Composable ColumnScope.() -> Unit
) {
    Card(
        shape = RoundedCornerShape(12.dp),
        colors = CardDefaults.cardColors(
            containerColor = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.4f)
        ),
        modifier = Modifier.fillMaxWidth()
    ) {
        Column(
            modifier = Modifier.padding(12.dp),
            verticalArrangement = Arrangement.spacedBy(8.dp)
        ) {
            Row(
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(8.dp)
            ) {
                Icon(
                    imageVector = icon,
                    contentDescription = null,
                    tint = MaterialTheme.colorScheme.primary,
                    modifier = Modifier.size(20.dp)
                )
                Text(
                    text = title,
                    style = MaterialTheme.typography.titleSmall,
                    fontWeight = FontWeight.Bold
                )
            }
            content()
        }
    }
}

@Composable
private fun FingerprintField(
    label: String,
    value: String,
    singleLine: Boolean = true,
    onValueChange: (String) -> Unit
) {
    OutlinedTextField(
        value = value,
        onValueChange = onValueChange,
        label = { Text(label) },
        singleLine = singleLine,
        maxLines = if (singleLine) 1 else 2,
        modifier = Modifier.fillMaxWidth()
    )
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun PersonaSelectorCard(
    selectedPersonaId: String,
    onSelectPersona: (BoxSpaceDevicePersona.Persona) -> Unit
) {
    val allPersonas = remember { BoxSpaceDevicePersona.getAllPersonas() }
    var expanded by remember { mutableStateOf(false) }
    val currentPersona = remember(selectedPersonaId) {
        allPersonas.find { it.id == selectedPersonaId } ?: BoxSpaceDevicePersona.productionPersona()
    }

    Card(
        shape = RoundedCornerShape(12.dp),
        colors = CardDefaults.cardColors(
            containerColor = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.4f)
        ),
        modifier = Modifier.fillMaxWidth()
    ) {
        Column(
            modifier = Modifier.padding(12.dp),
            verticalArrangement = Arrangement.spacedBy(8.dp)
        ) {
            Row(
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(8.dp)
            ) {
                Icon(
                    imageVector = Icons.Default.Devices,
                    contentDescription = null,
                    tint = MaterialTheme.colorScheme.primary,
                    modifier = Modifier.size(20.dp)
                )
                Text(
                    text = "Persona de Dispositivo",
                    style = MaterialTheme.typography.titleSmall,
                    fontWeight = FontWeight.Bold
                )
            }

            ExposedDropdownMenuBox(
                expanded = expanded,
                onExpandedChange = { expanded = !expanded },
                modifier = Modifier.fillMaxWidth()
            ) {
                OutlinedTextField(
                    value = "${currentPersona.manufacturer} ${currentPersona.model} (${currentPersona.id})",
                    onValueChange = {},
                    readOnly = true,
                    label = { Text("Modelo Simulado") },
                    trailingIcon = { ExposedDropdownMenuDefaults.TrailingIcon(expanded = expanded) },
                    modifier = Modifier
                        .menuAnchor(MenuAnchorType.PrimaryNotEditable)
                        .fillMaxWidth(),
                    textStyle = MaterialTheme.typography.bodySmall
                )
                ExposedDropdownMenu(
                    expanded = expanded,
                    onDismissRequest = { expanded = false }
                ) {
                    allPersonas.forEach { persona ->
                        val isSelected = persona.id == currentPersona.id
                        DropdownMenuItem(
                            text = {
                                Column {
                                    Text(
                                        text = "${persona.manufacturer} ${persona.model}",
                                        style = MaterialTheme.typography.bodyMedium,
                                        fontWeight = if (isSelected) FontWeight.Bold else FontWeight.Normal
                                    )
                                    Text(
                                        text = "ID: ${persona.id} • Board: ${persona.board}",
                                        style = MaterialTheme.typography.labelSmall,
                                        color = MaterialTheme.colorScheme.onSurfaceVariant
                                    )
                                }
                            },
                            onClick = {
                                onSelectPersona(persona)
                                expanded = false
                            },
                            leadingIcon = {
                                if (persona.id.startsWith("mobile_")) {
                                    Icon(Icons.Default.Smartphone, contentDescription = null, modifier = Modifier.size(18.dp))
                                } else {
                                    Icon(Icons.Default.Tv, contentDescription = null, modifier = Modifier.size(18.dp))
                                }
                            }
                        )
                    }
                }
            }
        }
    }
}

// ==========================================
// PREVIEWS PARA O ANDROID STUDIO
// ==========================================

@androidx.compose.ui.tooling.preview.Preview(name = "Configurar Fingerprint - Safe Mode Desativado", showBackground = true)
@Composable
private fun FingerprintConfigDialogPreview() {
    com.th.thtestes.ui.theme.ClonaTelaTheme(dynamicColor = false) {
        FingerprintConfigDialog(
            appName = "WhatsApp",
            packageName = "com.whatsapp",
            icon = null,
            initialData = FingerprintProfileData(
                androidId = "a1b2c3d4e5f60718",
                serial = "FA6AA0301234",
                imei = "863456041234567",
                cpuSerial = "0000000012345678",
                wifiMac = "02:00:00:12:34:56",
                buildBrand = "Samsung",
                buildModel = "Galaxy S23",
                buildManufacturer = "samsung",
                isSafeMode = false
            ),
            onRegenerateRandom = { FingerprintProfileData() },
            onConfirm = { _, _ -> },
            onDismiss = {}
        )
    }
}

@androidx.compose.ui.tooling.preview.Preview(name = "Configurar Fingerprint - Safe Mode Ativado", showBackground = true)
@Composable
private fun FingerprintConfigDialogSafeModePreview() {
    com.th.thtestes.ui.theme.ClonaTelaTheme(dynamicColor = false) {
        FingerprintConfigDialog(
            appName = "TV Box Player",
            packageName = "com.tvbox.player",
            icon = null,
            initialData = FingerprintProfileData(
                isSafeMode = true
            ),
            onRegenerateRandom = { FingerprintProfileData() },
            onConfirm = { _, _ -> },
            onDismiss = {}
        )
    }
}


