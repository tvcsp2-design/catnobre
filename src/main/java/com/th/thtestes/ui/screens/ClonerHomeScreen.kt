package com.th.thtestes.ui.screens

import android.app.Activity
import android.content.res.Configuration
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.expandVertically
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.shrinkVertically
import androidx.compose.foundation.ExperimentalFoundationApi
import androidx.compose.foundation.clickable
import androidx.compose.foundation.combinedClickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.grid.GridCells
import androidx.compose.foundation.lazy.grid.LazyVerticalGrid
import androidx.compose.foundation.lazy.grid.items
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.compose.LocalLifecycleOwner
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.LifecycleEventObserver
import com.th.thtestes.domain.AppPermissionManager
import com.th.thtestes.domain.BoxUserProfile
import com.th.thtestes.domain.ClonedApp
import com.th.thtestes.domain.FingerprintProfileData
import com.th.thtestes.domain.HostApp
import com.th.thtestes.domain.logs.CrashReportManager
import com.th.thtestes.ui.components.AppActionMenuDialog
import com.th.thtestes.ui.components.AppIconImage
import com.th.thtestes.ui.components.CloneAppDialog
import com.th.thtestes.ui.components.CloneQuickActionDialog
import com.th.thtestes.ui.components.DiagnosticsDialog
import com.th.thtestes.ui.components.FingerprintConfigDialog
import com.th.thtestes.ui.components.LoadingProgressDialog
import com.th.thtestes.ui.components.PreCloneRecommendationDialog
import com.th.thtestes.ui.theme.ClonaTelaTheme
import com.th.thtestes.ui.viewmodel.ClonerUiState
import com.th.thtestes.ui.viewmodel.ClonerViewModel
import kotlinx.coroutines.launch

/**
 * Stateful composable conectado ao [ClonerViewModel].
 */
@Composable
fun ClonerHomeScreen(
    viewModel: ClonerViewModel,
    modifier: Modifier = Modifier
) {
    val context = LocalContext.current
    val lifecycleOwner = LocalLifecycleOwner.current
    val activity = context as? Activity

    var hasAllPermissions by remember {
        mutableStateOf(AppPermissionManager.hasAllRequiredPermissions(context))
    }
    var showPermissionWarningDialog by rememberSaveable {
        mutableStateOf(!AppPermissionManager.hasAllRequiredPermissions(context))
    }
    var hasVerifiedStartupCredits by rememberSaveable {
        mutableStateOf(false)
    }

    lateinit var runtimeLauncherRef: androidx.activity.result.ActivityResultLauncher<Array<String>>

    val settingsLauncher = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.StartActivityForResult()
    ) {
        val granted = AppPermissionManager.hasAllRequiredPermissions(context)
        hasAllPermissions = granted
        if (granted) {
            viewModel.loadInitialData(context)
            if (!hasVerifiedStartupCredits) {
                hasVerifiedStartupCredits = true
                activity?.let { act -> top.niunaijun.blackbox.core.NativeCore.verifyEnvironmentSafe(act, true) }
            }
        } else {
            val runtimeMissing = AppPermissionManager.getRuntimePermissionsToRequest(context)
            if (runtimeMissing.isNotEmpty()) {
                runtimeLauncherRef.launch(runtimeMissing)
            }
        }
    }

    val allFilesLauncher = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.StartActivityForResult()
    ) {
        val granted = AppPermissionManager.hasAllRequiredPermissions(context)
        hasAllPermissions = granted
        if (granted) {
            viewModel.loadInitialData(context)
            if (!hasVerifiedStartupCredits) {
                hasVerifiedStartupCredits = true
                activity?.let { act -> top.niunaijun.blackbox.core.NativeCore.verifyEnvironmentSafe(act, true) }
            }
        } else {
            val runtimeMissing = AppPermissionManager.getRuntimePermissionsToRequest(context)
            if (runtimeMissing.isNotEmpty()) {
                runtimeLauncherRef.launch(runtimeMissing)
            }
        }
    }

    val runtimeLauncher = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.RequestMultiplePermissions()
    ) {
        val granted = AppPermissionManager.hasAllRequiredPermissions(context)
        hasAllPermissions = granted
        if (granted) {
            viewModel.loadInitialData(context)
            if (!hasVerifiedStartupCredits) {
                hasVerifiedStartupCredits = true
                activity?.let { act -> top.niunaijun.blackbox.core.NativeCore.verifyEnvironmentSafe(act, true) }
            }
        } else {
            if (!AppPermissionManager.isAllFilesAccessGranted()) {
                allFilesLauncher.launch(AppPermissionManager.createAllFilesAccessIntent(context))
            }
        }
    }
    runtimeLauncherRef = runtimeLauncher

    fun startPermissionSequence() {
        val granted = AppPermissionManager.hasAllRequiredPermissions(context)
        hasAllPermissions = granted
        if (granted) {
            viewModel.loadInitialData(context)
            if (!hasVerifiedStartupCredits) {
                hasVerifiedStartupCredits = true
                activity?.let { act -> top.niunaijun.blackbox.core.NativeCore.verifyEnvironmentSafe(act, true) }
            }
            return
        }

        val runtimeMissing = AppPermissionManager.getRuntimePermissionsToRequest(context)
        if (runtimeMissing.isNotEmpty()) {
            runtimeLauncher.launch(runtimeMissing)
        } else if (!AppPermissionManager.isAllFilesAccessGranted()) {
            allFilesLauncher.launch(AppPermissionManager.createAllFilesAccessIntent(context))
        } else {
            settingsLauncher.launch(AppPermissionManager.createAppSettingsIntent(context))
        }
    }

    // Monitora retorno do usuário de outras telas/configurações para atualizar estado
    DisposableEffect(lifecycleOwner) {
        val observer = LifecycleEventObserver { _, event ->
            if (event == Lifecycle.Event.ON_RESUME) {
                val granted = AppPermissionManager.hasAllRequiredPermissions(context)
                if (granted != hasAllPermissions) {
                    hasAllPermissions = granted
                }
                if (granted) {
                    viewModel.loadInitialData(context)
                    if (!hasVerifiedStartupCredits) {
                        hasVerifiedStartupCredits = true
                        activity?.let { act -> top.niunaijun.blackbox.core.NativeCore.verifyEnvironmentSafe(act, true) }
                    }
                } else if (!showPermissionWarningDialog) {
                    val runtimeMissing = AppPermissionManager.getRuntimePermissionsToRequest(context)
                    if (runtimeMissing.isNotEmpty() && AppPermissionManager.isAllFilesAccessGranted()) {
                        runtimeLauncher.launch(runtimeMissing)
                    }
                }
            }
        }
        lifecycleOwner.lifecycle.addObserver(observer)
        onDispose {
            lifecycleOwner.lifecycle.removeObserver(observer)
        }
    }

    val uiState by viewModel.uiState.collectAsStateWithLifecycle()
    val snackbarHostState = remember { SnackbarHostState() }

    val coroutineScope = rememberCoroutineScope()
    var showCloneDialog by remember { mutableStateOf(false) }
    var showDiagnosticsDialog by rememberSaveable { mutableStateOf(false) }
    var selectedAppForQuickAction by remember { mutableStateOf<ClonedApp?>(null) }
    var selectedAppForMenu by remember { mutableStateOf<ClonedApp?>(null) }
    var appForRecommendation by remember { mutableStateOf<HostApp?>(null) }
    var activeFingerprintDialog by remember { mutableStateOf<ActiveFingerprintDialog?>(null) }
    var showCreateProfileDialog by remember { mutableStateOf(false) }
    var profileToManage by remember { mutableStateOf<BoxUserProfile?>(null) }

    fun launchWithPermissionCheck(packageName: String) {
        if (!AppPermissionManager.hasAllRequiredPermissions(context)) {
            hasAllPermissions = false
            startPermissionSequence()
            return
        }
        viewModel.launchApp(context, packageName)
    }

    LaunchedEffect(Unit) {
        if (AppPermissionManager.hasAllRequiredPermissions(context)) {
            viewModel.loadInitialData(context)
            if (!hasVerifiedStartupCredits) {
                hasVerifiedStartupCredits = true
                activity?.let { act ->
                    top.niunaijun.blackbox.core.NativeCore.verifyEnvironmentSafe(act, true)
                }
            }
        }
        // Alerta sutil se um crash recente tiver sido registrado
        if (CrashReportManager.hasRecentCrash(context)) {
            val result = snackbarHostState.showSnackbar(
                message = "⚠️ Falha anterior registrada nos logs.",
                actionLabel = "Ver Logs",
                duration = SnackbarDuration.Long
            )
            if (result == SnackbarResult.ActionPerformed) {
                showDiagnosticsDialog = true
            }
        }
    }

    LaunchedEffect(uiState.userMessage) {
        uiState.userMessage?.let { msg ->
            snackbarHostState.showSnackbar(msg)
            viewModel.clearUserMessage()
        }
    }

    // Diálogo direto e simples de aviso de permissão
    if (showPermissionWarningDialog && !hasAllPermissions) {
        AlertDialog(
            onDismissRequest = {
                showPermissionWarningDialog = false
            },
            icon = {
                Icon(
                    imageVector = Icons.Default.Warning,
                    contentDescription = null,
                    tint = MaterialTheme.colorScheme.primary
                )
            },
            title = {
                Text(
                    text = "Aviso de Permissões",
                    fontWeight = FontWeight.Bold
                )
            },
            text = {
                Text(
                    text = "Por favor, permita todas as permissões que forem solicitadas a seguir para o funcionamento do aplicativo."
                )
            },
            confirmButton = {
                Button(
                    onClick = {
                        showPermissionWarningDialog = false
                        startPermissionSequence()
                    }
                ) {
                    Text("Continuar")
                }
            },
            dismissButton = {
                TextButton(
                    onClick = {
                        showPermissionWarningDialog = false
                    }
                ) {
                    Text("Cancelar")
                }
            }
        )
    }

    ClonerHomeScreenContent(
        uiState = uiState,
        snackbarHostState = snackbarHostState,
        onAboutClick = {
            activity?.let { act ->
                top.niunaijun.blackbox.core.NativeCore.verifyEnvironmentSafe(act, true)
            }
        },
        onCleanupClick = {
            if (!AppPermissionManager.hasAllRequiredPermissions(context)) {
                startPermissionSequence()
            } else {
                viewModel.runHomeCleanup(context)
            }
        },
        onAddAppClick = {
            if (!AppPermissionManager.hasAllRequiredPermissions(context)) {
                startPermissionSequence()
            } else {
                viewModel.loadHostApps(context)
                showCloneDialog = true
            }
        },
        onAppClick = { selectedAppForQuickAction = it },
        onAppLongClick = { selectedAppForMenu = it },
        onDiagnosticsClick = { showDiagnosticsDialog = true },
        onSwitchProfile = { userId -> viewModel.switchActiveProfile(context, userId) },
        onCreateProfileClick = { showCreateProfileDialog = true },
        onManageProfileClick = { profile -> profileToManage = profile },
        modifier = modifier
    )

    if (showDiagnosticsDialog) {
        DiagnosticsDialog(
            onDismissRequest = { showDiagnosticsDialog = false }
        )
    }

    // Modal para Escolher Aplicativo da Lista
    if (showCloneDialog) {
        CloneAppDialog(
            hostApps = uiState.hostApps,
            isLoading = uiState.isLoadingHostApps,
            isCloning = uiState.isCloning,
            onCloneApp = { app ->
                showCloneDialog = false
                appForRecommendation = app
            },
            onDismiss = { showCloneDialog = false }
        )
    }

    // Modal de Recomendação Pré-Clonagem (Abre o app original ou continua)
    appForRecommendation?.let { app ->
        PreCloneRecommendationDialog(
            app = app,
            onOpenOriginal = {
                val launchIntent = context.packageManager.getLaunchIntentForPackage(app.packageName)
                if (launchIntent != null) {
                    try {
                        context.startActivity(launchIntent)
                    } catch (e: Exception) {
                        // ignore
                    }
                }
            },
            onContinue = {
                val targetApp = app
                appForRecommendation = null
                coroutineScope.launch {
                    val preview = viewModel.getPreviewFingerprint(targetApp.packageName)
                    activeFingerprintDialog = ActiveFingerprintDialog(
                        appName = targetApp.name,
                        packageName = targetApp.packageName,
                        icon = targetApp.icon,
                        initialData = preview,
                        onConfirm = { overrides -> viewModel.cloneApp(context, targetApp.packageName, customOverrides = overrides) }
                    )
                }
            },
            onDismiss = { appForRecommendation = null }
        )
    }

    // Modal Único de Configuração / Edição de Fingerprint
    activeFingerprintDialog?.let { dialog ->
        FingerprintConfigDialog(
            appName = dialog.appName,
            packageName = dialog.packageName,
            icon = dialog.icon,
            initialData = dialog.initialData,
            confirmButtonText = dialog.confirmButtonText,
            onRegenerateRandom = { viewModel.getPreviewFingerprint(dialog.packageName) },
            onConfirm = { isManual, customData ->
                val overrides = if (customData != null) {
                    if (isManual) {
                        customData.toMap()
                    } else {
                        mapOf("isSafeMode" to customData.isSafeMode.toString())
                    }
                } else null
                dialog.onConfirm(overrides)
                activeFingerprintDialog = null
            },
            onDismiss = { activeFingerprintDialog = null }
        )
    }

    // Dialog Rápido de Ações do Clone (ao clicar no clone)
    selectedAppForQuickAction?.let { app ->
        CloneQuickActionDialog(
            app = app,
            onReclone = { viewModel.recloneApp(context, app.packageName) },
            onLaunch = { launchWithPermissionCheck(app.packageName) },
            onCreateShortcut = { viewModel.createShortcut(context, app) },
            onDismiss = { selectedAppForQuickAction = null }
        )
    }

    // Menu Completo de Ações do Aplicativo (ao pressionar e segurar)
    selectedAppForMenu?.let { app ->
        AppActionMenuDialog(
            app = app,
            onLaunch = { launchWithPermissionCheck(app.packageName) },
            onCreateShortcut = { viewModel.createShortcut(context, app) },
            onEditFingerprint = {
                val targetApp = app
                coroutineScope.launch {
                    val stored = viewModel.getStoredFingerprint(targetApp.packageName)
                    activeFingerprintDialog = ActiveFingerprintDialog(
                        appName = targetApp.name,
                        packageName = targetApp.packageName,
                        icon = targetApp.icon,
                        initialData = stored,
                        confirmButtonText = "Salvar Fingerprint",
                        onConfirm = { overrides -> overrides?.let { viewModel.updateFingerprint(targetApp.packageName, it) } }
                    )
                }
            },
            onReclone = { viewModel.recloneApp(context, app.packageName) },
            onResetSeed = { viewModel.resetIdentitySeed(context, app.packageName) },
            onDeepClean = { viewModel.deepCleanApp(context, app.packageName) },
            onClearCache = { viewModel.clearCache(context, app.packageName) },
            onStopApp = { viewModel.stopApp(context, app.packageName) },
            onUninstall = { viewModel.uninstallApp(context, app.packageName) },
            onDismiss = { selectedAppForMenu = null }
        )
    }

    // Modal para Criação de Novo Perfil Virtual
    if (showCreateProfileDialog) {
        var profileName by remember { mutableStateOf("") }
        AlertDialog(
            onDismissRequest = { showCreateProfileDialog = false },
            title = { Text("Novo Perfil Virtual") },
            text = {
                Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                    Text(
                        "Crie um novo espaço isolado. Aplicativos e identidades serão totalmente independentes.",
                        style = MaterialTheme.typography.bodyMedium
                    )
                    OutlinedTextField(
                        value = profileName,
                        onValueChange = { profileName = it },
                        label = { Text("Nome do Perfil") },
                        placeholder = { Text("Ex: Trabalho, Perfil 2") },
                        singleLine = true,
                        modifier = Modifier.fillMaxWidth()
                    )
                }
            },
            confirmButton = {
                Button(
                    onClick = {
                        val nameToUse = profileName.trim().ifBlank { null }
                        viewModel.createProfile(context, nameToUse)
                        showCreateProfileDialog = false
                    }
                ) {
                    Text("Criar")
                }
            },
            dismissButton = {
                TextButton(onClick = { showCreateProfileDialog = false }) {
                    Text("Cancelar")
                }
            }
        )
    }

    // Modal para Gerenciar / Excluir / Renomear Perfil Virtual
    profileToManage?.let { profile ->
        var newName by remember { mutableStateOf(profile.name) }
        var showConfirmDelete by remember { mutableStateOf(false) }

        if (showConfirmDelete) {
            AlertDialog(
                onDismissRequest = { showConfirmDelete = false },
                title = { Text("Excluir Perfil?") },
                text = {
                    Text("Todos os aplicativos e dados do perfil '${profile.name}' serão removidos permanentemente.")
                },
                confirmButton = {
                    Button(
                        colors = ButtonDefaults.buttonColors(containerColor = MaterialTheme.colorScheme.error),
                        onClick = {
                            showConfirmDelete = false
                            profileToManage = null
                            viewModel.deleteProfile(context, profile.userId)
                        }
                    ) {
                        Text("Excluir Perfil")
                    }
                },
                dismissButton = {
                    TextButton(onClick = { showConfirmDelete = false }) {
                        Text("Cancelar")
                    }
                }
            )
        } else {
            AlertDialog(
                onDismissRequest = { profileToManage = null },
                title = { Text("Gerenciar Perfil: ${profile.name}") },
                text = {
                    Column(verticalArrangement = Arrangement.spacedBy(12.dp)) {
                        OutlinedTextField(
                            value = newName,
                            onValueChange = { newName = it },
                            label = { Text("Nome do Perfil") },
                            singleLine = true,
                            modifier = Modifier.fillMaxWidth()
                        )
                        if (!profile.isDefault) {
                            OutlinedButton(
                                onClick = { showConfirmDelete = true },
                                colors = ButtonDefaults.outlinedButtonColors(
                                    contentColor = MaterialTheme.colorScheme.error
                                ),
                                modifier = Modifier.fillMaxWidth()
                            ) {
                                Icon(Icons.Default.Delete, contentDescription = null, modifier = Modifier.size(18.dp))
                                Spacer(modifier = Modifier.width(8.dp))
                                Text("Excluir Este Perfil")
                            }
                        }
                    }
                },
                confirmButton = {
                    Button(
                        onClick = {
                            if (newName.isNotBlank() && newName != profile.name) {
                                viewModel.renameProfile(context, profile.userId, newName.trim())
                            }
                            profileToManage = null
                        }
                    ) {
                        Text("Salvar")
                    }
                },
                dismissButton = {
                    TextButton(onClick = { profileToManage = null }) {
                        Text("Fechar")
                    }
                }
            )
        }
    }

    // Modal de Carregamento / Progresso para Tarefas em Segundo Plano
    if (uiState.isCloning) {
        LoadingProgressDialog(
            message = uiState.busyMessage ?: "Processando..."
        )
    }
}

/**
 * Stateless composable desacoplado do ViewModel, permitindo Previews no Android Studio.
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun ClonerHomeScreenContent(
    uiState: ClonerUiState,
    modifier: Modifier = Modifier,
    snackbarHostState: SnackbarHostState = remember { SnackbarHostState() },
    onAboutClick: () -> Unit = {},
    onCleanupClick: () -> Unit = {},
    onDiagnosticsClick: () -> Unit = {},
    onAddAppClick: () -> Unit = {},
    onAppClick: (ClonedApp) -> Unit = {},
    onAppLongClick: (ClonedApp) -> Unit = {},
    onSwitchProfile: (Int) -> Unit = {},
    onCreateProfileClick: () -> Unit = {},
    onManageProfileClick: (BoxUserProfile) -> Unit = {}
) {
    Scaffold(
        modifier = modifier.fillMaxSize(),
        snackbarHost = { SnackbarHost(snackbarHostState) },
        topBar = {
            TopAppBar(
                title = {
                    Text(
                        text = "ClonaTela",
                        style = MaterialTheme.typography.titleLarge,
                        fontWeight = FontWeight.Bold
                    )
                },
                actions = {
                    IconButton(onClick = onDiagnosticsClick) {
                        Icon(
                            imageVector = Icons.Default.BugReport,
                            contentDescription = "Diagnóstico e Logs",
                            tint = MaterialTheme.colorScheme.primary
                        )
                    }
                    IconButton(onClick = onAboutClick) {
                        Icon(
                            imageVector = Icons.Default.Info,
                            contentDescription = "Sobre e Créditos",
                            tint = MaterialTheme.colorScheme.primary
                        )
                    }
                    IconButton(onClick = onCleanupClick) {
                        Icon(
                            imageVector = Icons.Default.DeleteSweep,
                            contentDescription = "Limpar Caches",
                            tint = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                    }
                },
                colors = TopAppBarDefaults.topAppBarColors(
                    containerColor = MaterialTheme.colorScheme.surface
                )
            )
        },
        floatingActionButton = {
            FloatingActionButton(
                onClick = onAddAppClick,
                shape = CircleShape,
                containerColor = MaterialTheme.colorScheme.primary,
                contentColor = MaterialTheme.colorScheme.onPrimary
            ) {
                Icon(Icons.Default.Add, contentDescription = "Adicionar Aplicativo")
            }
        }
    ) { innerPadding ->
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(innerPadding)
        ) {
            if (uiState.availableProfiles.isNotEmpty()) {
                Surface(
                    color = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.35f),
                    modifier = Modifier.fillMaxWidth()
                ) {
                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(horizontal = 8.dp, vertical = 2.dp),
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        ScrollableTabRow(
                            selectedTabIndex = uiState.availableProfiles.indexOfFirst { it.userId == uiState.activeUserId }.coerceAtLeast(0),
                            edgePadding = 4.dp,
                            modifier = Modifier.weight(1f),
                            divider = {}
                        ) {
                            uiState.availableProfiles.forEach { profile ->
                                val selected = profile.userId == uiState.activeUserId
                                Tab(
                                    selected = selected,
                                    onClick = {
                                        if (selected) {
                                            onManageProfileClick(profile)
                                        } else {
                                            onSwitchProfile(profile.userId)
                                        }
                                    },
                                    text = {
                                        Row(
                                            verticalAlignment = Alignment.CenterVertically,
                                            horizontalArrangement = Arrangement.spacedBy(4.dp)
                                        ) {
                                            Text(
                                                text = "${profile.name} (${profile.installedAppsCount})",
                                                fontWeight = if (selected) FontWeight.Bold else FontWeight.Normal
                                            )
                                            if (selected) {
                                                Icon(
                                                    imageVector = Icons.Default.Settings,
                                                    contentDescription = "Gerenciar perfil",
                                                    modifier = Modifier.size(14.dp),
                                                    tint = MaterialTheme.colorScheme.primary
                                                )
                                            }
                                        }
                                    }
                                )
                            }
                        }
                        IconButton(onClick = onCreateProfileClick) {
                            Icon(
                                imageVector = Icons.Default.PersonAdd,
                                contentDescription = "Novo Perfil",
                                tint = MaterialTheme.colorScheme.primary
                            )
                        }
                    }
                }
            }

            Box(
                modifier = Modifier
                    .fillMaxSize()
                    .weight(1f)
            ) {
                if (uiState.isLoading) {
                    Box(modifier = Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                        CircularProgressIndicator()
                    }
                } else {
                    Box(
                        modifier = Modifier
                            .fillMaxSize()
                    ) {
                        if (uiState.clonedApps.isEmpty()) {
                            EmptyClonesState(
                                onCloneClick = onAddAppClick,
                                onAboutClick = onAboutClick
                            )
                        } else {
                            LazyVerticalGrid(
                                columns = GridCells.Fixed(4),
                                modifier = Modifier
                                    .fillMaxSize()
                                    .padding(horizontal = 8.dp, vertical = 8.dp),
                                verticalArrangement = Arrangement.spacedBy(16.dp),
                                horizontalArrangement = Arrangement.spacedBy(8.dp)
                            ) {
                                items(uiState.clonedApps, key = { "${it.packageName}_${it.userId}" }) { app ->
                                    ClonedAppGridItem(
                                        app = app,
                                        onLaunch = { onAppClick(app) },
                                        onLongClick = { onAppLongClick(app) }
                                    )
                                }
                            }
                        }
                    }
                }
            }
        }
    }
}

@OptIn(ExperimentalFoundationApi::class)
@Composable
private fun ClonedAppGridItem(
    app: ClonedApp,
    onLaunch: () -> Unit,
    onLongClick: () -> Unit
) {
    Column(
        modifier = Modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(12.dp))
            .combinedClickable(
                onClick = onLaunch,
                onLongClick = onLongClick
            )
            .padding(4.dp),
        horizontalAlignment = Alignment.CenterHorizontally
    ) {
        Box(contentAlignment = Alignment.TopEnd) {
            AppIconImage(
                drawable = app.icon,
                contentDescription = app.name,
                size = 56.dp
            )
            Surface(
                shape = CircleShape,
                color = if (app.isRunning) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.outline.copy(alpha = 0.4f),
                modifier = Modifier.size(12.dp)
            ) {}
        }
        Spacer(modifier = Modifier.height(6.dp))
        Text(
            text = app.name,
            style = MaterialTheme.typography.labelMedium,
            fontWeight = FontWeight.Medium,
            textAlign = TextAlign.Center,
            maxLines = 1,
            overflow = TextOverflow.Ellipsis
        )
    }
}

@Composable
private fun EmptyClonesState(
    onCloneClick: () -> Unit,
    onAboutClick: () -> Unit
) {
    Column(
        modifier = Modifier
            .fillMaxSize()
            .padding(32.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.Center
    ) {
        Icon(
            imageVector = Icons.Default.Layers,
            contentDescription = null,
            tint = MaterialTheme.colorScheme.primary.copy(alpha = 0.6f),
            modifier = Modifier.size(72.dp)
        )
        Spacer(modifier = Modifier.height(16.dp))
        Text(
            text = "Nenhum clone no ClonaTela",
            style = MaterialTheme.typography.titleMedium,
            fontWeight = FontWeight.SemiBold
        )
        Spacer(modifier = Modifier.height(8.dp))
        Text(
            text = "Toque no botão abaixo para clonar e personalizar qualquer aplicativo no ClonaTela.",
            style = MaterialTheme.typography.bodySmall,
            textAlign = TextAlign.Center,
            color = MaterialTheme.colorScheme.onSurfaceVariant
        )
        Spacer(modifier = Modifier.height(24.dp))
        Button(
            onClick = onCloneClick,
            shape = RoundedCornerShape(12.dp)
        ) {
            Icon(Icons.Default.Add, contentDescription = null)
            Spacer(modifier = Modifier.width(8.dp))
            Text("Adicionar Aplicativo")
        }
        Spacer(modifier = Modifier.height(12.dp))
        TextButton(
            onClick = onAboutClick,
            shape = RoundedCornerShape(12.dp)
        ) {
            Icon(Icons.Default.Info, contentDescription = null, modifier = Modifier.size(18.dp))
            Spacer(modifier = Modifier.width(6.dp))
            Text("Sobre o ClonaTela & Créditos")
        }
    }
}

private data class ActiveFingerprintDialog(
    val appName: String,
    val packageName: String,
    val icon: android.graphics.drawable.Drawable? = null,
    val initialData: FingerprintProfileData,
    val confirmButtonText: String = "Confirmar e Clonar",
    val onConfirm: (Map<String, String>?) -> Unit
)

// ==========================================
// PREVIEWS PARA O ANDROID STUDIO
// ==========================================

@Preview(name = "Tela Inicial - Com Clones", showBackground = true)
@Composable
private fun ClonerHomeScreenPopulatedPreview() {
    ClonaTelaTheme(dynamicColor = false) {
        ClonerHomeScreenContent(
            uiState = ClonerUiState(
                clonedApps = listOf(
                    ClonedApp(packageName = "com.whatsapp", name = "WhatsApp", icon = null, isRunning = true),
                    ClonedApp(packageName = "com.instagram.android", name = "Instagram", icon = null, isRunning = false),
                    ClonedApp(packageName = "org.telegram.messenger", name = "Telegram", icon = null, isRunning = false),
                    ClonedApp(packageName = "com.unitv.app", name = "UniTV", icon = null, isRunning = true)
                )
            )
        )
    }
}

@Preview(name = "Tela Inicial - Modo Escuro", uiMode = Configuration.UI_MODE_NIGHT_YES, showBackground = true)
@Composable
private fun ClonerHomeScreenDarkPreview() {
    ClonaTelaTheme(darkTheme = true, dynamicColor = false) {
        ClonerHomeScreenContent(
            uiState = ClonerUiState(
                clonedApps = listOf(
                    ClonedApp(packageName = "com.whatsapp", name = "WhatsApp", icon = null, isRunning = true),
                    ClonedApp(packageName = "com.instagram.android", name = "Instagram", icon = null, isRunning = false)
                )
            )
        )
    }
}

@Preview(name = "Tela Inicial - Totalmente Vazia", showBackground = true)
@Composable
private fun ClonerHomeScreenEmptyPreview() {
    ClonaTelaTheme(dynamicColor = false) {
        ClonerHomeScreenContent(
            uiState = ClonerUiState(clonedApps = emptyList())
        )
    }
}

