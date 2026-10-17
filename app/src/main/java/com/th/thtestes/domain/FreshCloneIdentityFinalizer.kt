package com.th.thtestes.domain

import android.util.Log
import top.niunaijun.blackbox.BlackBoxCore
import top.niunaijun.blackbox.compat.BoxSpaceCompatibilityProfile
import top.niunaijun.blackbox.compat.BoxSpaceIdentityProfile
import top.niunaijun.blackbox.core.env.BEnvironment
import top.niunaijun.blackbox.utils.FileUtils
import java.io.File

object FreshCloneIdentityFinalizer {
    private const val TAG = "FreshCloneIdentity"

    fun finalizeAfterInstall(packageName: String, userId: Int) {
        BlackBoxCore.get().stopPackage(packageName, userId)
        try {
            BlackBoxCore.getBActivityManager().resetPackageActivityState(packageName, userId)
        } catch (t: Throwable) {
            Log.w(TAG, "Unable to reset post-install activity state for $packageName", t)
        }
        clearNegotiatedIdentityCaches(packageName, userId)
        BoxSpaceIdentityProfile.resetForPackage(packageName, userId)
        Log.i(TAG, "Fresh identity finalized package=$packageName user=$userId")
    }

    private fun clearNegotiatedIdentityCaches(packageName: String, userId: Int) {
        // 1. Limpar todos os arquivos XML de sessão e identificação em shared_prefs
        val dataDir = BEnvironment.getDataDir(packageName, userId)
        val sharedPrefs = File(dataDir, "shared_prefs")
        if (sharedPrefs.exists() && sharedPrefs.isDirectory) {
            sharedPrefs.listFiles()?.forEach { file ->
                try {
                    file.delete()
                } catch (t: Throwable) {
                    Log.w(TAG, "Failed to delete shared_pref file: ${file.name}", t)
                }
            }
        }

        // 2. Limpar pasta de external_identity privada do clone
        val externalIdentityDir = File(dataDir, "external_identity")
        if (externalIdentityDir.exists()) {
            FileUtils.deleteDir(externalIdentityDir)
        }

        // 3. Limpar artefatos residuais de identificação de hardware e TV (TudoTV, UniTV, NovaTV, etc.)
        val candidateRoots = listOfNotNull(
            BEnvironment.getExternalUserDir(userId),
            BoxSpaceCompatibilityProfile.externalStorageRootFor(packageName, userId),
            BoxSpaceCompatibilityProfile.legacyExternalStorageRootFor(packageName, userId),
            android.os.Environment.getExternalStorageDirectory(),
            File("/storage/emulated/0")
        )

        val identityNames = listOf(
            ".tudotv", ".tudo", ".szm", ".newtv", ".novatv", ".nova", ".otv", ".iptv",
            ".unitv", ".uniptv", ".DataStorage", ".UTSystemConfig", ".android", ".device",
            ".system", ".Rxst", ".Vdst", ".sysdata", ".config", ".properties",
            ".804c9a5b09dc1e99aefe17dd530290c3", ".nx_device_id"
        )

        val subPrefixes = listOf("", "Android/", "Android/data/", "Android/media/", "Download/")

        for (root in candidateRoots) {
            if (!root.exists()) continue
            for (prefix in subPrefixes) {
                for (name in identityNames) {
                    val target = File(root, prefix + name)
                    if (target.exists()) {
                        try {
                            if (target.isDirectory) {
                                FileUtils.deleteDir(target)
                            } else {
                                target.delete()
                            }
                            Log.d(TAG, "Cleaned identity artifact: ${target.absolutePath}")
                        } catch (t: Throwable) {
                            Log.w(TAG, "Unable to delete identity artifact ${target.absolutePath}: ${t.message}")
                        }
                    }
                }
            }
        }
    }
}
