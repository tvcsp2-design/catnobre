package com.th.thtestes.ui.viewmodel

import android.content.Context
import android.net.Uri
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.th.thtestes.domain.AppCloner
import com.th.thtestes.domain.BoxUserProfile
import com.th.thtestes.domain.ClonedApp
import com.th.thtestes.domain.FingerprintProfileData
import com.th.thtestes.domain.HostApp
import com.th.thtestes.domain.ShortcutHelper
import com.th.thtestes.domain.UserProfileManager
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch

data class ClonerUiState(
    val activeUserId: Int = 0,
    val availableProfiles: List<BoxUserProfile> = emptyList(),
    val clonedApps: List<ClonedApp> = emptyList(),
    val hostApps: List<HostApp> = emptyList(),
    val isLoading: Boolean = false,
    val isLoadingHostApps: Boolean = false,
    val isCloning: Boolean = false,
    val busyMessage: String? = null,
    val userMessage: String? = null
)

class ClonerViewModel : ViewModel() {

    private val _uiState = MutableStateFlow(ClonerUiState())
    val uiState: StateFlow<ClonerUiState> = _uiState.asStateFlow()
    private var cachedHostApps: List<HostApp> = emptyList()

    fun loadInitialData(context: Context) {
        viewModelScope.launch {
            _uiState.update { it.copy(isLoading = true) }
            val activeUser = UserProfileManager.getActiveUserId(context)
            val profiles = UserProfileManager.getProfiles(context)
            val allClones = AppCloner.getClonedApps(context, activeUser)

            _uiState.update {
                it.copy(
                    activeUserId = activeUser,
                    availableProfiles = profiles,
                    clonedApps = allClones,
                    isLoading = false
                )
            }
            loadHostApps(context)
            try {
                com.th.thtestes.domain.logs.LogStorageManager.syncLogsToPublic(context)
            } catch (_: Throwable) {
            }
        }
    }

    fun switchActiveProfile(context: Context, userId: Int) {
        if (_uiState.value.activeUserId == userId) return
        viewModelScope.launch {
            _uiState.update { it.copy(isLoading = true, activeUserId = userId) }
            UserProfileManager.setActiveUserId(context, userId)
            val profiles = UserProfileManager.getProfiles(context)
            val allClones = AppCloner.getClonedApps(context, userId)
            cachedHostApps = emptyList()
            val hostApps = AppCloner.getHostApps(context, userId)
            cachedHostApps = hostApps

            _uiState.update {
                it.copy(
                    availableProfiles = profiles,
                    clonedApps = allClones,
                    hostApps = hostApps,
                    isLoading = false
                )
            }
        }
    }

    fun createProfile(context: Context, customName: String? = null) {
        viewModelScope.launch {
            _uiState.update { it.copy(isCloning = true, busyMessage = "Criando novo perfil...") }
            val created = UserProfileManager.createProfile(context, customName)
            val profiles = UserProfileManager.getProfiles(context)
            val targetUser = created?.userId ?: _uiState.value.activeUserId
            UserProfileManager.setActiveUserId(context, targetUser)
            val allClones = AppCloner.getClonedApps(context, targetUser)

            _uiState.update {
                it.copy(
                    activeUserId = targetUser,
                    availableProfiles = profiles,
                    clonedApps = allClones,
                    isCloning = false,
                    busyMessage = null,
                    userMessage = if (created != null) "Perfil '${created.name}' criado com sucesso!" else "Falha ao criar perfil."
                )
            }
        }
    }

    fun deleteProfile(context: Context, userId: Int) {
        viewModelScope.launch {
            _uiState.update { it.copy(isCloning = true, busyMessage = "Removendo perfil...") }
            val success = UserProfileManager.deleteProfile(context, userId)
            val activeUser = UserProfileManager.getActiveUserId(context)
            val profiles = UserProfileManager.getProfiles(context)
            val allClones = AppCloner.getClonedApps(context, activeUser)

            _uiState.update {
                it.copy(
                    activeUserId = activeUser,
                    availableProfiles = profiles,
                    clonedApps = allClones,
                    isCloning = false,
                    busyMessage = null,
                    userMessage = if (success) "Perfil removido com sucesso." else "Não foi possível remover o perfil."
                )
            }
        }
    }

    fun renameProfile(context: Context, userId: Int, newName: String) {
        viewModelScope.launch {
            val success = UserProfileManager.renameProfile(context, userId, newName)
            if (success) {
                val profiles = UserProfileManager.getProfiles(context)
                _uiState.update { it.copy(availableProfiles = profiles) }
            }
        }
    }

    fun loadHostApps(context: Context, forceRefresh: Boolean = false) {
        viewModelScope.launch {
            val activeUser = _uiState.value.activeUserId
            if (cachedHostApps.isNotEmpty() && !forceRefresh) {
                _uiState.update { it.copy(hostApps = cachedHostApps, isLoadingHostApps = false) }
                return@launch
            }
            _uiState.update { it.copy(isLoadingHostApps = true) }
            val hostApps = AppCloner.getHostApps(context, activeUser)
            cachedHostApps = hostApps
            _uiState.update { it.copy(hostApps = hostApps, isLoadingHostApps = false) }
        }
    }

    suspend fun getPreviewFingerprint(packageName: String, personaId: String? = null): FingerprintProfileData {
        return AppCloner.getPreviewFingerprint(packageName, _uiState.value.activeUserId, personaId)
    }

    suspend fun getStoredFingerprint(packageName: String): FingerprintProfileData {
        return AppCloner.getStoredFingerprint(packageName, _uiState.value.activeUserId)
    }

    fun updateFingerprint(packageName: String, customOverrides: Map<String, String>) {
        viewModelScope.launch {
            val activeUser = _uiState.value.activeUserId
            val success = AppCloner.updateFingerprint(packageName, activeUser, customOverrides)
            _uiState.update {
                it.copy(userMessage = if (success) "Fingerprint atualizado com sucesso!" else "Falha ao atualizar fingerprint.")
            }
        }
    }

    fun resetIdentitySeed(context: Context, packageName: String) {
        viewModelScope.launch {
            val activeUser = _uiState.value.activeUserId
            val success = AppCloner.resetIdentitySeed(packageName, activeUser)
            val allClones = AppCloner.getClonedApps(context, activeUser)
            _uiState.update {
                it.copy(
                    clonedApps = allClones,
                    userMessage = if (success) "Nova semente de identidade gerada!" else "Falha ao renovar semente."
                )
            }
        }
    }

    private fun runCloningTask(
        context: Context,
        busyMessage: String,
        successMessage: String,
        refreshHostApps: Boolean = false,
        action: suspend () -> Result<String>
    ) {
        viewModelScope.launch {
            _uiState.update { it.copy(isCloning = true, busyMessage = busyMessage) }
            val activeUser = _uiState.value.activeUserId
            action().onSuccess {
                val allClones = AppCloner.getClonedApps(context, activeUser)
                val profiles = UserProfileManager.getProfiles(context)
                if (refreshHostApps) {
                    cachedHostApps = emptyList()
                    loadHostApps(context, forceRefresh = true)
                }
                _uiState.update { state ->
                    state.copy(
                        clonedApps = allClones,
                        availableProfiles = profiles,
                        isCloning = false,
                        busyMessage = null,
                        userMessage = successMessage
                    )
                }
            }.onFailure { err ->
                _uiState.update { state -> state.copy(isCloning = false, busyMessage = null, userMessage = "Erro: ${err.message}") }
            }
        }
    }

    private fun runBusyAction(
        context: Context,
        busyMessage: String,
        action: suspend () -> String?
    ) {
        viewModelScope.launch {
            _uiState.update { it.copy(isCloning = true, busyMessage = busyMessage) }
            val message = action()
            val activeUser = _uiState.value.activeUserId
            val allClones = AppCloner.getClonedApps(context, activeUser)
            val profiles = UserProfileManager.getProfiles(context)
            _uiState.update {
                it.copy(
                    clonedApps = allClones,
                    availableProfiles = profiles,
                    isCloning = false,
                    busyMessage = null,
                    userMessage = message
                )
            }
        }
    }

    fun cloneApp(
        context: Context,
        packageName: String,
        personaId: String? = null,
        customOverrides: Map<String, String>? = null
    ) {
        val activeUser = _uiState.value.activeUserId
        runCloningTask(context, "Clonando aplicativo...", "Aplicativo clonado com sucesso!", refreshHostApps = true) {
            AppCloner.cloneApp(context, packageName, activeUser, personaId, customOverrides)
        }
    }

    fun cloneApp(
        context: Context,
        packageName: String,
        customOverrides: Map<String, String>?
    ) {
        cloneApp(context, packageName, null, customOverrides)
    }

    fun recloneApp(
        context: Context,
        packageName: String,
        personaId: String? = null,
        customOverrides: Map<String, String>? = null
    ) {
        val activeUser = _uiState.value.activeUserId
        runCloningTask(context, "Renovando identidade e conta...", "Nova identidade e conta renovada com sucesso!") {
            AppCloner.recloneApp(context, packageName, activeUser, personaId, customOverrides)
        }
    }

    fun installApk(
        context: Context,
        uri: Uri,
        personaId: String? = null,
        customOverrides: Map<String, String>? = null
    ) {
        val activeUser = _uiState.value.activeUserId
        runCloningTask(context, "Instalando APK...", "APK instalado com sucesso!") {
            AppCloner.installApk(context, uri, activeUser, personaId, customOverrides)
        }
    }

    fun duplicateToProfile(
        context: Context,
        packageName: String,
        targetUserId: Int,
        personaId: String? = null
    ) {
        runCloningTask(context, "Duplicando no perfil de destino...", "Aplicativo duplicado no outro perfil!") {
            AppCloner.duplicateToProfile(context, packageName, targetUserId, personaId)
        }
    }

    fun launchApp(context: Context, packageName: String) {
        viewModelScope.launch {
            val activeUser = _uiState.value.activeUserId
            val success = AppCloner.launchApp(context, packageName, activeUser)
            val allClones = AppCloner.getClonedApps(context, activeUser)
            _uiState.update {
                it.copy(
                    clonedApps = allClones,
                    userMessage = if (!success) "Falha ao iniciar o aplicativo." else null
                )
            }
        }
    }

    fun stopApp(context: Context, packageName: String) {
        viewModelScope.launch {
            val activeUser = _uiState.value.activeUserId
            val success = AppCloner.stopApp(packageName, activeUser)
            val allClones = AppCloner.getClonedApps(context, activeUser)
            _uiState.update {
                it.copy(
                    clonedApps = allClones,
                    userMessage = if (success) "Aplicativo fechado." else "Falha ao fechar aplicativo."
                )
            }
        }
    }

    fun deepCleanApp(context: Context, packageName: String) {
        val activeUser = _uiState.value.activeUserId
        runBusyAction(context, "Limpando dados do aplicativo...") {
            val success = AppCloner.deepCleanAppData(packageName, activeUser)
            if (success) "Dados limpos com sucesso!" else "Falha na limpeza de dados."
        }
    }

    fun clearCache(context: Context, packageName: String) {
        viewModelScope.launch {
            val activeUser = _uiState.value.activeUserId
            val count = AppCloner.clearPackageCache(packageName, activeUser)
            _uiState.update {
                it.copy(userMessage = if (count >= 0) "Cache limpo ($count itens)." else "Falha ao limpar cache.")
            }
        }
    }

    fun uninstallApp(context: Context, packageName: String) {
        val activeUser = _uiState.value.activeUserId
        runBusyAction(context, "Removendo aplicativo clonado...") {
            AppCloner.uninstallApp(packageName, activeUser)
            "Aplicativo removido."
        }
    }

    fun runHomeCleanup(context: Context) {
        val activeUser = _uiState.value.activeUserId
        runBusyAction(context, "Limpando caches e processos...") {
            val res = AppCloner.runHomeCleanup(context, activeUser)
            "Limpeza concluída! ${res.stoppedPackages} apps finalizados."
        }
    }

    fun createShortcut(context: Context, app: ClonedApp) {
        viewModelScope.launch {
            val success = ShortcutHelper.createShortcut(context, app)
            _uiState.update {
                it.copy(
                    userMessage = if (success) "Atalho solicitado para a tela inicial!" else "Falha ao solicitar criação de atalho."
                )
            }
        }
    }

    fun clearUserMessage() {
        _uiState.update { it.copy(userMessage = null) }
    }
}
