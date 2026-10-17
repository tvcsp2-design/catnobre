package com.th.thtestes.domain

import android.content.Context
import android.util.Log
import top.niunaijun.blackbox.BlackBoxCore
import top.niunaijun.blackbox.compat.BoxSpaceCompatibilityProfile
import top.niunaijun.blackbox.compat.BoxSpaceDevicePersona
import top.niunaijun.blackbox.compat.BoxSpaceIdentityProfile
import top.niunaijun.blackbox.core.system.ResidualPackageProcessKiller
import top.niunaijun.blackbox.core.system.pm.VirtualPackageDataCleaner
import java.io.File

object AppRecloneManager {
    private const val TAG = "AppRecloneManager"
    private const val MAX_INSTALL_ATTEMPTS = 3

    enum class Failure {
        SOURCE_MISSING,
        INVALID_APK,
        UNINSTALL_FAILED,
        INSTALL_FAILED,
        CLEANUP_FAILED,
        INTERNAL_APK_MISSING,
        UNEXPECTED
    }

    data class Result(
        val success: Boolean,
        val packageName: String,
        val profile: String,
        val stagedApk: File? = null,
        val failure: Failure? = null,
        val detail: String = ""
    )

    fun reclone(
        context: Context,
        packageName: String,
        userId: Int = 0,
        preferredSource: File? = null,
        personaId: String? = null,
        customOverrides: Map<String, String>? = null
    ): Result {
        val requiredProfile = BoxSpaceCompatibilityProfile.requiredProfileForPackage(packageName)
            ?: (BoxSpaceCompatibilityProfile.profileForPackage(packageName, userId) ?: BoxSpaceCompatibilityProfile.PROFILE_GLOBAL)

        var stagedApk: File? = null
        var isTemporaryStaged = false
        try {
            val isHostPackage = try {
                context.packageManager.getPackageInfo(packageName, 0) != null
            } catch (t: Throwable) {
                false
            }

            val prefersInternal = BoxSpaceCompatibilityProfile.prefersInternalApk(packageName)
            val usesSystemPaths = BoxSpaceCompatibilityProfile.usesSystemCodePaths(packageName)
            val hadInternalApk = BoxSpaceCompatibilityProfile.hasInternalApk(packageName)

            if (preferredSource != null && preferredSource.isFile && preferredSource.length() > 0) {
                stagedApk = preferredSource
                isTemporaryStaged = false
            } else {
                val shouldStage = !isHostPackage || prefersInternal || hadInternalApk
                if (shouldStage) {
                    stagedApk = ApkImportStager.stagePackageSource(
                        context = context,
                        packageName = packageName,
                        preferInstalledPackage = usesSystemPaths
                    ) { Log.i(TAG, it) }
                    isTemporaryStaged = true
                }
            }

            if (!isHostPackage && stagedApk == null) {
                return Result(false, packageName, requiredProfile, null, Failure.SOURCE_MISSING, "APK source not found")
            }

            if (stagedApk != null) {
                val archiveInfo = context.packageManager.getPackageArchiveInfo(stagedApk.absolutePath, 0)
                if (archiveInfo == null || archiveInfo.packageName != packageName) {
                    val targetStaged = stagedApk
                    if (isTemporaryStaged) {
                        ApkImportStager.discard(context, targetStaged) { Log.i(TAG, it) }
                    }
                    return Result(false, packageName, requiredProfile, targetStaged, Failure.INVALID_APK, "Invalid package archive")
                }
            }

            val core = BlackBoxCore.get()
            RuntimePreparationCache.invalidate(context.applicationContext, packageName, userId)
            core.stopPackage(packageName, userId)
            try {
                BlackBoxCore.getBActivityManager().resetPackageActivityState(packageName, userId)
            } catch (t: Throwable) {
                Log.w(TAG, "Unable to reset activity state before recreating $packageName", t)
            }

            core.uninstallPackageAsUser(packageName, userId)

            if (core.isInstalled(packageName, userId)) {
                return Result(false, packageName, requiredProfile, stagedApk, Failure.UNINSTALL_FAILED, "Uninstall verification failed")
            }

            val termResult = ResidualPackageProcessKiller.terminate(packageName)
            if (!termResult.isSuccess) {
                val survivors = termResult.survivors ?: emptyList()
                return Result(false, packageName, requiredProfile, stagedApk, Failure.CLEANUP_FAILED, "processes=${survivors.joinToString(",")}")
            }

            val dataCleanResult = VirtualPackageDataCleaner.clearPackageData(packageName, userId)
            if (!dataCleanResult.isSuccess) {
                val survivors = dataCleanResult.survivors ?: emptyList()
                return Result(false, packageName, requiredProfile, stagedApk, Failure.CLEANUP_FAILED, survivors.joinToString(","))
            }

            BoxSpaceCompatibilityProfile.setInternalSelfUpdate(packageName, userId, false)
            BoxSpaceIdentityProfile.resetForPackage(packageName, userId)

            if (!personaId.isNullOrEmpty()) {
                BoxSpaceDevicePersona.setLaboratoryPackageOverride(packageName, userId, personaId)
            }
            if (!customOverrides.isNullOrEmpty()) {
                BoxSpaceIdentityProfile.saveCustomOverrides(packageName, userId, customOverrides)
            }

            var installResult = if (stagedApk != null) {
                core.installPackageAsUser(stagedApk, userId)
            } else if (isHostPackage) {
                core.installPackageAsUser(packageName, userId)
            } else {
                return Result(false, packageName, requiredProfile, null, Failure.SOURCE_MISSING, "APK source not found")
            }

            var attempt = 1
            while (!installResult.success && attempt < MAX_INSTALL_ATTEMPTS) {
                attempt++
                Thread.sleep((attempt * 250).toLong())
                installResult = if (stagedApk != null) {
                    core.installPackageAsUser(stagedApk, userId)
                } else {
                    core.installPackageAsUser(packageName, userId)
                }
            }

            if (!installResult.success) {
                return Result(false, packageName, requiredProfile, stagedApk, Failure.INSTALL_FAILED, installResult.msg ?: "")
            }

            val installedPkg = installResult.packageName ?: packageName
            BoxSpaceCompatibilityProfile.setProfileForPackage(installedPkg, userId, requiredProfile)
            FreshCloneIdentityFinalizer.finalizeAfterInstall(installedPkg, userId)

            if (BoxSpaceCompatibilityProfile.isUnitv(installedPkg)) {
                if (isHostPackage) {
                    UnitvLaunchProfile.applyCompatibilityState(context, installedPkg, userId)
                } else {
                    BoxSpaceCompatibilityProfile.setUnitvMode(installedPkg, BoxSpaceCompatibilityProfile.MODE_DISABLED)
                    BoxSpaceCompatibilityProfile.clearSystemPackagePaths(installedPkg)
                }
            }

            if (BoxSpaceCompatibilityProfile.prefersInternalApk(installedPkg)) {
                if (BoxSpaceCompatibilityProfile.usesSystemCodePaths(installedPkg)) {
                    BoxSpaceCompatibilityProfile.cacheSystemPackageInfo(installedPkg)
                } else {
                    BoxSpaceCompatibilityProfile.clearSystemPackagePaths(installedPkg)
                }
                if (!BoxSpaceCompatibilityProfile.hasInternalApk(installedPkg)) {
                    return Result(false, installedPkg, requiredProfile, stagedApk, Failure.INTERNAL_APK_MISSING, "Internal APK missing")
                }
            }

            Log.i(TAG, "Application recreated package=$installedPkg user=$userId profile=$requiredProfile")
            return Result(true, installedPkg, requiredProfile, null, null, "Reclone successful")
        } catch (t: Throwable) {
            Log.e(TAG, "Unable to recreate $packageName", t)
            return Result(false, packageName, requiredProfile, null, Failure.UNEXPECTED, t.message ?: "")
        } finally {
            if (stagedApk != null && isTemporaryStaged) {
                ApkImportStager.discard(context, stagedApk) { Log.i(TAG, it) }
            }
        }
    }
}
