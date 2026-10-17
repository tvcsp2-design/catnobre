package com.th.thtestes.domain

import android.content.Context
import android.content.Intent
import android.graphics.drawable.Drawable
import androidx.core.net.toUri
import com.th.thtestes.ShortcutLaunchActivity
import com.th.thtestes.domain.shortcut.CompanionManager

object ShortcutHelper {
    private const val TAG = "ShortcutHelper"
    const val ACTION_LAUNCH_CLONE = "com.th.thtestes.action.LAUNCH_CLONE"
    const val EXTRA_PACKAGE_NAME = "extra_package_name"
    const val EXTRA_USER_ID = "extra_user_id"
    const val SHORTCUT_PREFIX = "ClonaTela - "

    fun formatShortcutLabel(label: String, packageName: String): String {
        val base = label.ifBlank { packageName }.trim()
        return if (base.startsWith(SHORTCUT_PREFIX, ignoreCase = true)) {
            base
        } else if (base.contains("ClonaTela", ignoreCase = true)) {
            base
        } else {
            "$SHORTCUT_PREFIX$base"
        }
    }

    fun createShortcutIntent(
        context: Context,
        packageName: String,
        userId: Int = AppCloner.DEFAULT_USER_ID
    ): Intent {
        return Intent(context, ShortcutLaunchActivity::class.java).apply {
            action = ACTION_LAUNCH_CLONE
            data = "cloner://launch/$packageName".toUri()
            putExtra(EXTRA_PACKAGE_NAME, packageName)
            putExtra(EXTRA_USER_ID, userId)
            addFlags(Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_CLEAR_TOP)
        }
    }

    suspend fun installCompanionShortcut(
        context: Context,
        packageName: String,
        label: String,
        icon: Drawable?,
        userId: Int = AppCloner.DEFAULT_USER_ID
    ): Boolean {
        val appName = formatShortcutLabel(label, packageName)
        val result = CompanionManager.prepareAndInstallCompanion(
            context = context,
            targetPackage = packageName,
            appName = appName,
            icon = icon
        )
        return result.isSuccess
    }

    fun isShortcutInstalled(context: Context, packageName: String): Boolean {
        return CompanionManager.isCompanionInstalled(context, packageName)
    }

    suspend fun pinShortcut(
        context: Context,
        packageName: String,
        label: String,
        icon: Drawable?,
        userId: Int = AppCloner.DEFAULT_USER_ID
    ): Boolean {
        return installCompanionShortcut(context, packageName, label, icon, userId)
    }

    suspend fun createShortcut(context: Context, app: ClonedApp): Boolean {
        return pinShortcut(context, app.packageName, app.name, app.icon, app.userId)
    }
}
