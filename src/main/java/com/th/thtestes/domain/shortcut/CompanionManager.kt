package com.th.thtestes.domain.shortcut

import android.content.Context
import android.content.Intent
import android.content.pm.PackageManager
import android.graphics.drawable.Drawable
import android.net.Uri
import android.util.Log
import androidx.core.content.FileProvider
import java.io.File
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext

object CompanionManager {
    private const val TAG = "CompanionManager"
    const val COMPANION_PACKAGE_PREFIX = "com.th.thtestes.shortcut.pkg_"

    fun getCompanionPackageName(targetPackage: String): String {
        val sanitized = targetPackage.replace('.', '_').replace('-', '_')
        return "$COMPANION_PACKAGE_PREFIX$sanitized"
    }

    fun isCompanionInstalled(context: Context, targetPackage: String): Boolean {
        val pkg = getCompanionPackageName(targetPackage)
        return try {
            context.packageManager.getPackageInfo(pkg, 0)
            true
        } catch (e: PackageManager.NameNotFoundException) {
            false
        } catch (e: Exception) {
            Log.w(TAG, "Erro ao checar se companion está instalado para $pkg", e)
            false
        }
    }

    suspend fun prepareAndInstallCompanion(
        context: Context,
        targetPackage: String,
        appName: String,
        icon: Drawable?
    ): Result<File> = withContext(Dispatchers.IO) {
        try {
            val shortcutsDir = File(context.cacheDir, "shortcuts").apply { mkdirs() }
            val safeFileName = appName
                .replace(Regex("[^a-zA-Z0-9._-]"), "_")
                .replace(Regex("_+"), "_")
                .trim('_')
            val outputFile = File(shortcutsDir, "${safeFileName}.apk")

            val success = CompanionApkBuilder.build(
                context = context,
                targetPackage = targetPackage,
                appName = appName,
                iconDrawable = icon,
                outputFile = outputFile
            )

            if (!success || !outputFile.exists() || outputFile.length() == 0L) {
                return@withContext Result.failure(IllegalStateException("Falha na geração do arquivo APK do atalho."))
            }

            withContext(Dispatchers.Main) {
                startPackageInstaller(context, outputFile)
            }

            Result.success(outputFile)
        } catch (e: Exception) {
            Log.e(TAG, "Erro no fluxo de instalação de companion para $targetPackage", e)
            Result.failure(e)
        }
    }

    fun startPackageInstaller(context: Context, apkFile: File) {
        try {
            val contentUri: Uri = FileProvider.getUriForFile(
                context,
                "${context.packageName}.fileprovider",
                apkFile
            )

            val installIntent = Intent(Intent.ACTION_VIEW).apply {
                setDataAndType(contentUri, "application/vnd.android.package-archive")
                addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION)
                addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
            }
            context.startActivity(installIntent)
            Log.i(TAG, "Instalador do sistema acionado com sucesso para ${apkFile.name}")
        } catch (e: Exception) {
            Log.e(TAG, "Falha ao disparar instalador do sistema", e)
            throw e
        }
    }

    fun launchUninstall(context: Context, targetPackage: String) {
        try {
            val companionPkg = getCompanionPackageName(targetPackage)
            val intent = Intent(Intent.ACTION_DELETE).apply {
                data = Uri.parse("package:$companionPkg")
                addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
            }
            context.startActivity(intent)
        } catch (e: Exception) {
            Log.e(TAG, "Falha ao disparar desinstalação de $targetPackage", e)
        }
    }
}
