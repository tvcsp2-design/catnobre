package com.th.thtestes.domain

import android.content.ComponentName
import android.content.Context
import android.content.Intent
import android.content.pm.ActivityInfo
import android.content.pm.PackageInfo
import android.database.Cursor
import android.net.Uri
import android.util.Log
import androidx.core.net.toUri
import top.niunaijun.blackbox.BlackBoxCore
import java.io.File
import java.io.FileInputStream
import java.io.FileOutputStream
import java.io.InputStream
import java.security.MessageDigest
import java.util.Locale

object ApkImportStager {
    private const val TAG = "ApkImportStager"
    private const val STAGE_DIR = "boxspace_apk_stage"
    private const val MAX_STAGED_APKS = 1

    fun discard(context: Context, stagedApk: File?, logger: ((String) -> Unit)? = null): Boolean {
        if (stagedApk == null) return true
        val stageDir = File(context.filesDir, STAGE_DIR).canonicalFile
        val canonicalTarget = try {
            stagedApk.canonicalFile
        } catch (t: Throwable) {
            null
        } ?: return false

        if (canonicalTarget.parentFile != stageDir) return false
        val deleted = if (canonicalTarget.exists()) canonicalTarget.delete() else true
        logger?.invoke("apk stage discard path=${canonicalTarget.absolutePath} deleted=$deleted")
        return deleted
    }

    fun stagePackageSource(
        context: Context,
        packageName: String,
        preferInstalledPackage: Boolean = false,
        logger: ((String) -> Unit)? = null
    ): File? {
        if (packageName.isBlank()) return null
        prune(context, null)
        val candidates = LinkedHashSet<File>()

        val blackboxApk = File(context.applicationInfo.dataDir, "blackbox/data/app/$packageName/base.apk")
        val systemApk = try {
            File(context.packageManager.getApplicationInfo(packageName, 0).sourceDir)
        } catch (t: Throwable) {
            null
        }

        if (preferInstalledPackage) {
            if (systemApk != null) candidates.add(systemApk)
            candidates.add(blackboxApk)
        } else {
            candidates.add(blackboxApk)
            if (systemApk != null) candidates.add(systemApk)
        }

        val stageDirFiles = File(context.filesDir, STAGE_DIR).listFiles()?.filter { it.isFile }
            ?.sortedByDescending { it.lastModified() }
        if (stageDirFiles != null) {
            candidates.addAll(stageDirFiles)
        }

        val bPackageManager = BlackBoxCore.getBPackageManager()
        val isInstalled = try {
            bPackageManager.isInstalled(packageName, 0)
        } catch (t: Throwable) {
            false
        }

        val launchComponent = if (isInstalled) {
            try {
                bPackageManager.getLaunchIntentForPackage(packageName, 0)?.component
            } catch (t: Throwable) {
                null
            }
        } else null

        val expectedVersionCode = if (isInstalled) {
            try {
                bPackageManager.getPackageInfo(packageName, 0, 0)?.versionCode
            } catch (t: Throwable) {
                null
            }
        } else null

        for (candidate in candidates) {
            if (canReadApk(candidate)) {
                val archiveInfo = try {
                    context.packageManager.getPackageArchiveInfo(candidate.absolutePath, 0)
                } catch (t: Throwable) {
                    null
                }
                if (archiveInfo?.packageName == packageName) {
                    if (expectedVersionCode == null || archiveInfo.versionCode == expectedVersionCode || launchComponent == null || archiveContainsExpectedLaunch(context, archiveInfo.activities, launchComponent)) {
                        logger?.invoke("internal apk source resolved package=$packageName preferInstalled=$preferInstalledPackage source=${candidate.absolutePath}")
                        return stageFile(context, candidate, logger)
                    } else {
                        logger?.invoke("apk source rejected package=$packageName source=${candidate.absolutePath} missingLaunch=${launchComponent.flattenToShortString()}")
                    }
                }
            }
        }

        logger?.invoke("internal apk source missing package=$packageName")
        return null
    }

    fun stage(context: Context, source: String, logger: ((String) -> Unit)? = null): File {
        if (source.isBlank()) throw IllegalArgumentException("missing APK source")
        prune(context, source)
        val uri = source.toUri()
        val displayName = displayName(context, uri) ?: safeName(uri.lastPathSegment ?: "selected.apk")
        val target = targetFor(context, displayName, source)
        context.contentResolver.openInputStream(uri)?.use { input ->
            copyToStage(input, target)
        } ?: throw IllegalArgumentException("unable to open APK URI: $source")

        logger?.invoke("apk staged uri=$source target=${target.absolutePath} size=${target.length()}")
        return target
    }

    private fun stageFile(context: Context, source: File, logger: ((String) -> Unit)? = null): File {
        val canonical = try { source.canonicalFile } catch (t: Throwable) { source }
        val stageDir = File(context.filesDir, STAGE_DIR)
        if (canonical.absolutePath.startsWith(stageDir.absolutePath + File.separator)) {
            logger?.invoke("apk already staged path=${canonical.absolutePath} size=${canonical.length()}")
            return canonical
        }
        val target = targetFor(context, canonical.name, "${canonical.absolutePath}|${canonical.length()}|${canonical.lastModified()}")
        FileInputStream(canonical).use { input ->
            copyToStage(input, target)
        }
        target.setReadable(true, true)
        logger?.invoke("apk staged file=${canonical.absolutePath} target=${target.absolutePath} size=${target.length()}")
        return target
    }

    private fun canReadApk(file: File): Boolean {
        if (!file.isFile || file.length() <= 0) return false
        return try {
            FileInputStream(file).use { stream ->
                val header = ByteArray(4)
                if (stream.read(header) == 4) {
                    header[0] == 0x50.toByte() && header[1] == 0x4B.toByte()
                } else false
            }
        } catch (t: Throwable) {
            false
        }
    }

    private fun copyToStage(input: InputStream, target: File) {
        val parent = target.parentFile
        if (parent != null && !parent.exists()) parent.mkdirs()
        val temp = File(parent, "${target.name}.tmp")
        if (temp.exists()) temp.delete()
        FileOutputStream(temp).use { out ->
            input.copyTo(out)
        }
        if (temp.length() <= 0) throw IllegalArgumentException("staged APK is empty: ${target.absolutePath}")
        if (target.exists()) target.delete()
        if (!temp.renameTo(target)) {
            throw IllegalArgumentException("unable to finalize staged APK: ${target.absolutePath}")
        }
    }

    private fun targetFor(context: Context, displayName: String, seed: String): File {
        var clean = safeName(displayName).ifBlank { "app.apk" }
        if (!clean.lowercase(Locale.US).endsWith(".apk")) {
            clean = "$clean.apk"
        }
        val hash = sha1(seed).take(12)
        val stageDir = File(context.filesDir, STAGE_DIR)
        return File(stageDir, "${clean.removeSuffix(".apk")}-$hash.apk")
    }

    private fun displayName(context: Context, uri: Uri): String? {
        return try {
            context.contentResolver.query(uri, arrayOf("_display_name"), null, null, null)?.use { cursor ->
                if (cursor.moveToFirst()) {
                    val idx = cursor.getColumnIndex("_display_name")
                    if (idx >= 0) cursor.getString(idx) else null
                } else null
            }
        } catch (t: Throwable) {
            null
        }
    }

    private fun prune(context: Context, preserveSource: String?): Int {
        val stageDir = File(context.filesDir, STAGE_DIR)
        val files = stageDir.listFiles()?.filter { it.isFile }?.sortedByDescending { it.lastModified() } ?: return 0
        val preserveFile = if (preserveSource != null) {
            try {
                val f = File(preserveSource.removePrefix("file://")).canonicalFile
                if (f.parentFile == stageDir.canonicalFile) f else null
            } catch (t: Throwable) {
                null
            }
        } else null

        val toCheck = files.filter { it != preserveFile }
        val toDelete = toCheck.drop((MAX_STAGED_APKS - if (preserveFile != null) 1 else 0).coerceAtLeast(0))
        var deleted = 0
        for (f in toDelete) {
            if (f.delete()) deleted++
        }
        return deleted
    }

    private fun safeName(value: String): String {
        val last = value.substringAfterLast('/').substringAfterLast(':')
        return last.replace(Regex("[^A-Za-z0-9._-]"), "_").trim('_')
    }

    private fun sha1(value: String): String {
        val digest = MessageDigest.getInstance("SHA-1").digest(value.toByteArray(Charsets.UTF_8))
        return digest.joinToString("") { "%02x".format(it) }
    }

    private fun archiveContainsExpectedLaunch(
        context: Context,
        activities: Array<ActivityInfo>?,
        expectedLaunch: ComponentName
    ): Boolean {
        val names = activities?.map { it.name } ?: emptyList()
        if (names.contains(expectedLaunch.className)) return true
        val target = try {
            context.packageManager.getActivityInfo(expectedLaunch, 0).targetActivity
        } catch (t: Throwable) {
            null
        }
        return target != null && names.contains(target)
    }
}
