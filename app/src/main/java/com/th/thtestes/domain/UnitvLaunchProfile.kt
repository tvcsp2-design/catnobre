package com.th.thtestes.domain

import android.content.Context
import android.content.Intent
import android.content.pm.ApplicationInfo
import android.content.pm.PackageInfo
import android.util.Log
import com.th.thtestes.domain.logs.AppLogger
import top.niunaijun.blackbox.BlackBoxCore
import top.niunaijun.blackbox.compat.BoxSpaceCompatibilityProfile
import java.io.File

object UnitvLaunchProfile {
    private const val TAG = "UnitvLaunchProfile"
    private const val WELCOME_ACTIVITY = "com.interactive.brasiliptv.ui.activity.WelcomeActivity"
    private const val MOBILE_SPLASH_ACTIVITY = "com.mobile.brasiltv.activity.SplashAty"

    fun isProfiled(packageName: String): Boolean {
        return packageName == BoxSpaceCompatibilityProfile.UNITV_PACKAGE ||
               packageName == BoxSpaceCompatibilityProfile.UNITV_MOBILE_PACKAGE ||
               packageName == BoxSpaceCompatibilityProfile.YOUCINE_TV_PACKAGE ||
               packageName == BoxSpaceCompatibilityProfile.YOUCINE_MOBILE_PACKAGE
    }

    fun applyCompatibilityState(context: Context, packageName: String, userId: Int) {
        try {
            val packageInfo: PackageInfo? = try {
                context.packageManager.getPackageInfo(packageName, 0)
            } catch (t: Throwable) {
                null
            }

            val appInfo: ApplicationInfo? = packageInfo?.applicationInfo ?: try {
                context.packageManager.getApplicationInfo(packageName, 0)
            } catch (t: Throwable) {
                null
            }

            val hostInstalled = packageInfo != null
            if (BoxSpaceCompatibilityProfile.isUnitv(packageName)) {
                val unitvMode = BoxSpaceCompatibilityProfile.getUnitvMode(packageName)
                val mode = if (hostInstalled) {
                    if (unitvMode > 0) unitvMode else BoxSpaceCompatibilityProfile.MODE_LOAD_FROM_SYSTEM
                } else {
                    BoxSpaceCompatibilityProfile.MODE_DISABLED
                }
                BoxSpaceCompatibilityProfile.setUnitvMode(packageName, mode)
            }

            val vCode = packageInfo?.versionCode?.toLong() ?: 0L
            val vName = packageInfo?.versionName ?: ""
            if (vCode > 0) {
                BoxSpaceCompatibilityProfile.cachePackageVersionInfo(packageName, vCode, vName, vCode, vName)
            }

            if (appInfo != null) {
                var libDir = appInfo.nativeLibraryDir ?: ""
                val armDir = File(libDir, "arm")
                if (armDir.exists() && armDir.isDirectory) {
                    libDir = armDir.absolutePath
                }
                BoxSpaceCompatibilityProfile.cacheSystemPackagePaths(
                    packageName,
                    appInfo.sourceDir ?: "",
                    libDir,
                    appInfo.dataDir ?: ""
                )
            } else {
                BoxSpaceCompatibilityProfile.cacheSystemPackageInfo(packageName)
            }
            Log.i(TAG, "Compat profile applied pkg=$packageName user=$userId hostInstalled=$hostInstalled")
        } catch (t: Throwable) {
            Log.w(TAG, "compat profile warning: ${t.message}", t)
        }
    }

    fun launchExplicitWelcome(core: BlackBoxCore, packageName: String, userId: Int): Boolean {
        val targetActivity = if (packageName == BoxSpaceCompatibilityProfile.UNITV_MOBILE_PACKAGE) {
            MOBILE_SPLASH_ACTIVITY
        } else if (packageName == BoxSpaceCompatibilityProfile.UNITV_PACKAGE) {
            WELCOME_ACTIVITY
        } else {
            null
        }
        if (targetActivity != null) {
            return try {
                val intent = Intent("android.intent.action.MAIN")
                    .addCategory("android.intent.category.LAUNCHER")
                    .setPackage(packageName)
                    .setClassName(packageName, targetActivity)
                    .addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
                val res = core.startActivity(intent, userId)
                AppLogger.i(TAG, "Unitv explicit launch component=$packageName/$targetActivity (res=$res)")
                true
            } catch (t: Throwable) {
                AppLogger.w(TAG, "Explicit launch failed, falling back to core.launchApk: ${t.message}", t)
                val fallbackRes = core.launchApk(packageName, userId)
                AppLogger.i(TAG, "Fallback launchApk result: $fallbackRes for pkg=$packageName")
                fallbackRes
            }
        }
        val res = core.launchApk(packageName, userId)
        AppLogger.i(TAG, "Standard core.launchApk result: $res for pkg=$packageName (user=$userId)")
        return res
    }

    fun launch(context: Context, packageName: String, userId: Int): Boolean {
        AppLogger.i(TAG, "Iniciando lançamento do pacote: $packageName para user=$userId")
        try {
            System.gc()
        } catch (_: Throwable) {
        }
        val core = BlackBoxCore.get()
        applyCompatibilityState(context, packageName, userId)
        if (isProfiled(packageName)) {
            return launchExplicitWelcome(core, packageName, userId)
        }
        val res = core.launchApk(packageName, userId)
        AppLogger.i(TAG, "Lançamento de $packageName finalizado com resultado=$res")
        return res
    }
}

