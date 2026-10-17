package com.th.thtestes.domain

import android.content.Context
import android.content.pm.ApplicationInfo
import android.content.pm.PackageManager
import android.graphics.drawable.Drawable
import android.net.Uri
import android.util.Log
import com.th.thtestes.domain.logs.AppLogger
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import top.niunaijun.blackbox.BlackBoxCore
import top.niunaijun.blackbox.compat.BoxSpaceDevicePersona
import top.niunaijun.blackbox.compat.BoxSpaceCompatibilityProfile
import top.niunaijun.blackbox.compat.BoxSpaceIdentityProfile
import top.niunaijun.blackbox.core.system.ResidualPackageProcessKiller
import top.niunaijun.blackbox.core.system.pm.VirtualPackageDataCleaner
import top.niunaijun.blackbox.fake.frameworks.BActivityManager
import java.io.File

object AppCloner {
    private const val TAG = "AppCloner"
    const val DEFAULT_USER_ID = 0
    private val core: BlackBoxCore get() = BlackBoxCore.get()

    private fun loadMetadata(pm: PackageManager, appInfo: ApplicationInfo): Pair<String, Drawable?> {
        val name = try { pm.getApplicationLabel(appInfo).toString() } catch (e: Exception) { appInfo.packageName }
        val icon = try { pm.getApplicationIcon(appInfo) } catch (e: Exception) { null }
        return name to icon
    }

    private fun applyProfileAndOverrides(
        packageName: String,
        userId: Int,
        personaId: String? = null,
        customOverrides: Map<String, String>? = null
    ) {
        val requiredProfile = BoxSpaceCompatibilityProfile.requiredProfileForPackage(packageName)
            ?: BoxSpaceCompatibilityProfile.PROFILE_GLOBAL
        BoxSpaceCompatibilityProfile.setProfileForPackage(packageName, userId, requiredProfile)
        if (!personaId.isNullOrEmpty()) {
            BoxSpaceDevicePersona.setLaboratoryPackageOverride(packageName, userId, personaId)
        } else {
            BoxSpaceDevicePersona.clearLaboratoryPackageOverride(packageName, userId)
        }
        if (BoxSpaceCompatibilityProfile.isPrimaryUnitvTv(packageName)) {
            BoxSpaceIdentityProfile.resetForPackage(packageName, userId)
        }
        if (!customOverrides.isNullOrEmpty()) {
            BoxSpaceIdentityProfile.saveCustomOverrides(packageName, userId, customOverrides)
        } else {
            BoxSpaceIdentityProfile.clearCustomOverrides(packageName, userId)
        }
    }

    private fun stopAndReset(packageName: String, userId: Int, cleanData: Boolean = false) {
        try { core.stopPackage(packageName, userId) } catch (e: Exception) { Log.w(TAG, "stop failed: $packageName user=$userId", e) }
        try { BActivityManager.get().resetPackageActivityState(packageName, userId) } catch (e: Exception) { Log.w(TAG, "reset failed: $packageName user=$userId", e) }
        if (cleanData) {
            ResidualPackageProcessKiller.terminate(packageName)
            VirtualPackageDataCleaner.clearPackageData(packageName, userId)
        }
    }

    suspend fun getClonedApps(context: Context, userId: Int = DEFAULT_USER_ID): List<ClonedApp> = withContext(Dispatchers.IO) {
        val pm = context.packageManager
        val apps = core.getInstalledApplications(0, userId) ?: emptyList()
        apps.mapNotNull { appInfo ->
            if (appInfo == null || appInfo.packageName.isNullOrBlank()) return@mapNotNull null
            val (name, icon) = loadMetadata(pm, appInfo)
            val isRunning = try { core.isPackageRunning(appInfo.packageName, userId) } catch (e: Exception) { false }
            val personaId = BoxSpaceDevicePersona.getLaboratoryPackageOverride(appInfo.packageName, userId)
            val profileName = BoxSpaceCompatibilityProfile.profileForPackage(appInfo.packageName, userId) ?: "global"
            val hasCustomOverrides = !BoxSpaceIdentityProfile.getCustomOverrides(appInfo.packageName, userId).isNullOrEmpty()

            ClonedApp(
                packageName = appInfo.packageName,
                name = name,
                icon = icon,
                isRunning = isRunning,
                userId = userId,
                personaId = personaId,
                profileName = profileName,
                hasCustomOverrides = hasCustomOverrides
            )
        }
    }

    suspend fun getHostApps(context: Context, userId: Int = DEFAULT_USER_ID): List<HostApp> = withContext(Dispatchers.IO) {
        val pm = context.packageManager
        val hostPkg = context.packageName
        val flags = if (android.os.Build.VERSION.SDK_INT >= android.os.Build.VERSION_CODES.N) {
            PackageManager.MATCH_UNINSTALLED_PACKAGES
        } else {
            @Suppress("DEPRECATION")
            PackageManager.GET_UNINSTALLED_PACKAGES
        }
        val installedApps = try {
            pm.getInstalledApplications(flags)
        } catch (e: Exception) {
            pm.getInstalledApplications(0)
        }
        val currentClonedApps = (core.getInstalledApplications(0, userId) ?: emptyList())
            .map { it.packageName }.toSet()
        val knownPackages = BoxSpaceCompatibilityProfile.requiredProfilesByPackage().keys
        installedApps
            .filter { it.packageName != hostPkg && !core.isBlackBoxApp(it.packageName) }
            .mapNotNull { app ->
                val (name, icon) = loadMetadata(pm, app)
                HostApp(
                    packageName = app.packageName,
                    name = name,
                    icon = icon,
                    sourceDir = app.sourceDir ?: "",
                    isCloned = currentClonedApps.contains(app.packageName),
                    isKnown = knownPackages.contains(app.packageName)
                )
            }
            .sortedWith(compareByDescending<HostApp> { it.isKnown }.thenBy { it.name.lowercase() })
    }

    suspend fun getPreviewFingerprint(
        packageName: String,
        userId: Int = DEFAULT_USER_ID,
        personaId: String? = null
    ): FingerprintProfileData = withContext(Dispatchers.IO) {
        val finalPersona = personaId ?: BoxSpaceCompatibilityProfile.requiredProfileForPackage(packageName) ?: "generic"
        val identity = BoxSpaceIdentityProfile.generatePreviewIdentity(packageName, userId, finalPersona)
        FingerprintProfileData.fromIdentity(identity)
    }

    suspend fun getStoredFingerprint(
        packageName: String,
        userId: Int = DEFAULT_USER_ID
    ): FingerprintProfileData = withContext(Dispatchers.IO) {
        val identity = BoxSpaceIdentityProfile.forPackage(packageName, userId)
        FingerprintProfileData.fromIdentity(identity)
    }

    suspend fun updateFingerprint(
        packageName: String,
        userId: Int = DEFAULT_USER_ID,
        customOverrides: Map<String, String>
    ): Boolean = withContext(Dispatchers.IO) {
        try {
            BoxSpaceIdentityProfile.saveCustomOverrides(packageName, userId, customOverrides)
            true
        } catch (e: Exception) {
            Log.e(TAG, "Failed to update fingerprint for $packageName user=$userId", e)
            false
        }
    }

    suspend fun resetIdentitySeed(
        packageName: String,
        userId: Int = DEFAULT_USER_ID
    ): Boolean = withContext(Dispatchers.IO) {
        try {
            BoxSpaceIdentityProfile.resetForPackage(packageName, userId)
            true
        } catch (e: Exception) {
            Log.e(TAG, "Failed to reset identity seed for $packageName user=$userId", e)
            false
        }
    }

    suspend fun cloneApp(
        context: Context,
        packageName: String,
        userId: Int = DEFAULT_USER_ID,
        personaId: String? = null,
        customOverrides: Map<String, String>? = null
    ): Result<String> = withContext(Dispatchers.IO) {
        var stagedApk: File? = null
        try {
            applyProfileAndOverrides(packageName, userId, personaId, customOverrides)

            val res = if (BoxSpaceCompatibilityProfile.prefersInternalApk(packageName)) {
                val usesSystemPaths = BoxSpaceCompatibilityProfile.usesSystemCodePaths(packageName) &&
                        !BoxSpaceCompatibilityProfile.usesInternalSelfUpdate(packageName, userId)
                stagedApk = ApkImportStager.stagePackageSource(context, packageName, usesSystemPaths) { Log.i(TAG, it) }
                if (stagedApk != null) {
                    core.installPackageAsUser(stagedApk, userId)
                } else {
                    core.installPackageAsUser(packageName, userId)
                }
            } else {
                core.installPackageAsUser(packageName, userId)
            }

            if (res.success) {
                val installedPkg = res.packageName ?: packageName
                if (!core.isInstalled(installedPkg, userId)) {
                    Log.e(TAG, "Clone reportou sucesso mas nao foi localizado no sistema: $installedPkg user=$userId")
                    Result.failure(Exception("Clone não pôde ser ativado no sistema da Box."))
                } else {
                    val requiredProfile = BoxSpaceCompatibilityProfile.requiredProfileForPackage(installedPkg)
                        ?: BoxSpaceCompatibilityProfile.PROFILE_GLOBAL
                    BoxSpaceCompatibilityProfile.setProfileForPackage(installedPkg, userId, requiredProfile)
                    if (BoxSpaceCompatibilityProfile.isPrimaryUnitvTv(installedPkg)) {
                        BoxSpaceIdentityProfile.resetForPackage(installedPkg, userId)
                    }
                    if (BoxSpaceCompatibilityProfile.prefersInternalApk(installedPkg)) {
                        if (BoxSpaceCompatibilityProfile.usesSystemCodePaths(installedPkg)) {
                            BoxSpaceCompatibilityProfile.cacheSystemPackageInfo(installedPkg)
                        } else {
                            BoxSpaceCompatibilityProfile.clearSystemPackagePaths(installedPkg)
                        }
                    }
                    FreshCloneIdentityFinalizer.finalizeAfterInstall(installedPkg, userId)
                    if (!customOverrides.isNullOrEmpty()) {
                        BoxSpaceIdentityProfile.saveCustomOverrides(installedPkg, userId, customOverrides)
                    }
                    Result.success(installedPkg)
                }
            } else {
                Result.failure(Exception(res.msg ?: "Falha ao clonar aplicativo"))
            }
        } catch (e: Exception) {
            Result.failure(e)
        } finally {
            stagedApk?.let { ApkImportStager.discard(context, it) }
        }
    }

    suspend fun cloneApp(
        packageName: String,
        userId: Int = DEFAULT_USER_ID,
        personaId: String? = null,
        customOverrides: Map<String, String>? = null
    ): Result<String> = cloneApp(
        context = BlackBoxCore.getContext(),
        packageName = packageName,
        userId = userId,
        personaId = personaId,
        customOverrides = customOverrides
    )

    suspend fun recloneApp(
        context: Context,
        packageName: String,
        userId: Int = DEFAULT_USER_ID,
        personaId: String? = null,
        customOverrides: Map<String, String>? = null
    ): Result<String> = withContext(Dispatchers.IO) {
        try {
            applyProfileAndOverrides(packageName, userId, personaId, customOverrides)
            val result = AppRecloneManager.reclone(
                context = context,
                packageName = packageName,
                userId = userId,
                personaId = personaId,
                customOverrides = customOverrides
            )
            if (result.success) {
                Result.success(result.packageName)
            } else {
                Result.failure(Exception("Reclone falhou: ${result.failure} - ${result.detail}"))
            }
        } catch (e: Exception) {
            Result.failure(e)
        }
    }

    suspend fun installApk(
        context: Context,
        uri: Uri,
        userId: Int = DEFAULT_USER_ID,
        personaId: String? = null,
        customOverrides: Map<String, String>? = null
    ): Result<String> = withContext(Dispatchers.IO) {
        try {
            val stagedFile = ApkImportStager.stage(context, uri.toString()) { Log.i(TAG, it) }
            val archiveInfo = context.packageManager.getPackageArchiveInfo(stagedFile.absolutePath, 0)
            val packageName = archiveInfo?.packageName

            if (packageName != null) {
                applyProfileAndOverrides(packageName, userId, personaId, customOverrides)
            }

            val res = core.installPackageAsUser(stagedFile, userId)
            ApkImportStager.discard(context, stagedFile)

            if (res.success) {
                val installedPkg = res.packageName ?: (packageName ?: "Instalado")
                if (!core.isInstalled(installedPkg, userId)) {
                    Log.e(TAG, "APK instalado reportou sucesso mas nao foi localizado no sistema: $installedPkg user=$userId")
                    Result.failure(Exception("Clone do APK não pôde ser ativado no sistema da Box."))
                } else {
                    FreshCloneIdentityFinalizer.finalizeAfterInstall(installedPkg, userId)
                    if (!customOverrides.isNullOrEmpty()) {
                        BoxSpaceIdentityProfile.saveCustomOverrides(installedPkg, userId, customOverrides)
                    }
                    Result.success(installedPkg)
                }
            } else {
                Result.failure(Exception(res.msg ?: "Falha ao instalar APK"))
            }
        } catch (e: Exception) {
            Result.failure(e)
        }
    }

    suspend fun duplicateToProfile(
        context: Context,
        packageName: String,
        targetUserId: Int,
        personaId: String? = null
    ): Result<String> = withContext(Dispatchers.IO) {
        var stagedApk: File? = null
        try {
            applyProfileAndOverrides(packageName, targetUserId, personaId)
            val res = if (BoxSpaceCompatibilityProfile.prefersInternalApk(packageName) && !BoxSpaceCompatibilityProfile.hasInternalApk(packageName)) {
                val usesSystemPaths = BoxSpaceCompatibilityProfile.usesSystemCodePaths(packageName) &&
                        !BoxSpaceCompatibilityProfile.usesInternalSelfUpdate(packageName, targetUserId)
                stagedApk = ApkImportStager.stagePackageSource(context, packageName, usesSystemPaths) { Log.i(TAG, it) }
                if (stagedApk != null) {
                    core.installPackageAsUser(stagedApk, targetUserId)
                } else {
                    core.installPackageAsUser(packageName, targetUserId)
                }
            } else {
                core.installPackageAsUser(packageName, targetUserId)
            }
            if (res.success) {
                val installedPkg = res.packageName ?: packageName
                FreshCloneIdentityFinalizer.finalizeAfterInstall(installedPkg, targetUserId)
                Result.success(installedPkg)
            } else {
                Result.failure(Exception(res.msg ?: "Falha ao duplicar aplicativo"))
            }
        } catch (e: Exception) {
            Result.failure(e)
        } finally {
            stagedApk?.let { ApkImportStager.discard(context, it) }
        }
    }

    suspend fun duplicateToProfile(
        packageName: String,
        targetUserId: Int,
        personaId: String? = null
    ): Result<String> = duplicateToProfile(
        context = BlackBoxCore.getContext(),
        packageName = packageName,
        targetUserId = targetUserId,
        personaId = personaId
    )

    suspend fun launchApp(
        context: Context,
        packageName: String,
        userId: Int = DEFAULT_USER_ID
    ): Boolean = withContext(Dispatchers.IO) {
        try {
            val coherenceResult = RuntimeApkCoherenceManager.prepareForLaunch(context, packageName, userId)
            if (!coherenceResult.canLaunch) {
                AppLogger.w(TAG, "RuntimeApkCoherence impediu lançamento: ${coherenceResult.detail}")
            }

            val success = UnitvLaunchProfile.launch(context, packageName, userId)
            if (!success) {
                AppLogger.w(TAG, "UnitvLaunchProfile.launch retornou false para: $packageName user=$userId")
            } else {
                AppLogger.i(TAG, "App lançado com sucesso: $packageName user=$userId")
            }
            success
        } catch (e: Exception) {
            AppLogger.e(TAG, "Falha crítica ao iniciar app: $packageName user=$userId", e)
            false
        }
    }

    suspend fun stopApp(packageName: String, userId: Int = DEFAULT_USER_ID): Boolean = withContext(Dispatchers.IO) {
        try {
            stopAndReset(packageName, userId)
            true
        } catch (e: Exception) {
            Log.e(TAG, "Falha ao parar app: $packageName user=$userId", e)
            false
        }
    }

    suspend fun deepCleanAppData(packageName: String, userId: Int = DEFAULT_USER_ID): Boolean = withContext(Dispatchers.IO) {
        try {
            stopAndReset(packageName, userId, cleanData = true)
            FreshCloneIdentityFinalizer.finalizeAfterInstall(packageName, userId)
            true
        } catch (e: Exception) {
            Log.e(TAG, "Falha na limpeza profunda: $packageName user=$userId", e)
            false
        }
    }

    suspend fun clearPackageCache(packageName: String, userId: Int = DEFAULT_USER_ID): Int = withContext(Dispatchers.IO) {
        try {
            core.clearPackageCache(packageName, userId)
        } catch (e: Exception) {
            Log.e(TAG, "Falha ao limpar cache: $packageName user=$userId", e)
            -1
        }
    }

    suspend fun uninstallApp(packageName: String, userId: Int = DEFAULT_USER_ID): Boolean = withContext(Dispatchers.IO) {
        try {
            stopAndReset(packageName, userId, cleanData = true)
            core.uninstallPackageAsUser(packageName, userId)
            true
        } catch (e: Exception) {
            Log.e(TAG, "Falha ao desinstalar app: $packageName user=$userId", e)
            false
        }
    }

    suspend fun runHomeCleanup(context: Context, userId: Int = DEFAULT_USER_ID): HomeRuntimeCleaner.Result = withContext(Dispatchers.IO) {
        HomeRuntimeCleaner.stopUserApplications(context, userId)
    }
}
