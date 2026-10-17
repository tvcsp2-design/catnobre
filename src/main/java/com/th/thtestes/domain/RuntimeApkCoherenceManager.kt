package com.th.thtestes.domain

import android.content.Context
import android.content.pm.ApplicationInfo
import android.content.pm.PackageInfo
import android.util.Log
import top.niunaijun.blackbox.BlackBoxCore
import top.niunaijun.blackbox.compat.BoxSpaceCompatibilityProfile
import top.niunaijun.blackbox.entity.pm.InstallResult
import java.io.File

object RuntimeApkCoherenceManager {
    private const val TAG = "RuntimeApkCoherence"

    enum class Action {
        NOT_REQUIRED,
        READY,
        REPAIRED,
        FAILED
    }

    data class Result(
        val action: Action,
        val canLaunch: Boolean,
        val detail: String = ""
    )

    fun prepareForLaunch(context: Context, packageName: String, userId: Int): Result {
        if (!BoxSpaceCompatibilityProfile.prefersInternalApk(packageName)) {
            return Result(Action.NOT_REQUIRED, canLaunch = true)
        }

        val appContext = context.applicationContext
        val internalApk = File(appContext.applicationInfo.dataDir, "blackbox/data/app/$packageName/base.apk")

        val currentSourceDir = try {
            val appInfo: ApplicationInfo? = BlackBoxCore.getBPackageManager().getApplicationInfo(packageName, 0, userId)
            appInfo?.sourceDir ?: ""
        } catch (t: Throwable) {
            ""
        }

        if (isSameFile(currentSourceDir, internalApk)) {
            if (isValidPackage(appContext, internalApk, packageName, userId, currentSourceDir)) {
                synchronizeSystemPathCache(packageName)
                return Result(Action.READY, canLaunch = true)
            }
        }

        Log.w(TAG, "Runtime APK repair required package=$packageName user=$userId source=$currentSourceDir internal=${internalApk.absolutePath}")

        val usesSystemPaths = BoxSpaceCompatibilityProfile.usesSystemCodePaths(packageName) &&
                !BoxSpaceCompatibilityProfile.usesInternalSelfUpdate(packageName, userId)

        val stagedApk = ApkImportStager.stagePackageSource(
            context = appContext,
            packageName = packageName,
            preferInstalledPackage = usesSystemPaths
        ) { Log.i(TAG, it) }

        if (stagedApk == null) {
            return failure("compatible APK source missing")
        }

        val core = BlackBoxCore.get()
        return try {
            RuntimePreparationCache.invalidate(appContext, packageName, userId)
            core.stopPackage(packageName, userId)
            try {
                BlackBoxCore.getBActivityManager().resetPackageActivityState(packageName, userId)
            } catch (t: Throwable) {
                Log.w(TAG, "resetPackageActivityState failed for $packageName", t)
            }

            val installResult: InstallResult = core.installPackageAsUser(stagedApk, userId)
            if (!installResult.success) {
                val msg = installResult.msg?.ifBlank { "install failed" } ?: "install failed"
                throw IllegalStateException(msg)
            }

            val requiredProfile = BoxSpaceCompatibilityProfile.requiredProfileForPackage(packageName)
                ?: (BoxSpaceCompatibilityProfile.profileForPackage(packageName, userId) ?: BoxSpaceCompatibilityProfile.PROFILE_GLOBAL)
            BoxSpaceCompatibilityProfile.setProfileForPackage(packageName, userId, requiredProfile)
            synchronizeSystemPathCache(packageName)

            val updatedAppInfo = BlackBoxCore.getBPackageManager().getApplicationInfo(packageName, 0, userId)
            val updatedSourceDir = updatedAppInfo?.sourceDir ?: ""
            if (!isSameFile(updatedSourceDir, internalApk)) {
                throw IllegalStateException("virtual source remained external: $updatedSourceDir")
            }

            if (!isValidPackage(appContext, internalApk, packageName, userId, updatedSourceDir)) {
                throw IllegalStateException("internal APK validation failed")
            }

            Log.i(TAG, "Runtime APK repaired package=$packageName user=$userId source=$updatedSourceDir")
            ApkImportStager.discard(appContext, stagedApk) { Log.i(TAG, it) }
            Result(Action.REPAIRED, canLaunch = true)
        } catch (t: Throwable) {
            Log.e(TAG, "Runtime APK repair failed package=$packageName", t)
            failure(t.message ?: "unknown repair error")
        }
    }

    private fun failure(detail: String): Result {
        return Result(Action.FAILED, canLaunch = false, detail = detail)
    }

    private fun synchronizeSystemPathCache(packageName: String) {
        if (BoxSpaceCompatibilityProfile.usesSystemCodePaths(packageName)) {
            BoxSpaceCompatibilityProfile.cacheSystemPackageInfo(packageName)
        } else {
            BoxSpaceCompatibilityProfile.clearSystemPackagePaths(packageName)
        }
    }

    private fun isValidPackage(
        context: Context,
        apk: File,
        packageName: String,
        userId: Int,
        virtualSource: String
    ): Boolean {
        if (!apk.isFile || apk.length() <= 0) {
            return false
        }
        val fingerprint = RuntimePreparationCache.apkFingerprint(apk, packageName, userId, virtualSource)
        if (RuntimePreparationCache.hasValidatedApk(context, packageName, userId, fingerprint)) {
            return true
        }

        val isValid = try {
            val archiveInfo: PackageInfo? = context.packageManager.getPackageArchiveInfo(apk.absolutePath, 0)
            archiveInfo?.packageName == packageName
        } catch (t: Throwable) {
            false
        }

        if (isValid) {
            RuntimePreparationCache.recordValidatedApk(context, packageName, userId, fingerprint)
        }
        return isValid
    }

    private fun isSameFile(path: String, target: File): Boolean {
        if (path.isBlank()) return false
        return try {
            File(path).canonicalFile == target.canonicalFile
        } catch (t: Throwable) {
            File(path).absolutePath == target.absolutePath
        }
    }
}
