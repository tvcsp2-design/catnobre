package com.th.thtestes.domain

import android.content.Context
import android.util.Log
import top.niunaijun.blackbox.BlackBoxCore
import top.niunaijun.blackbox.core.system.user.BUserInfo
import top.niunaijun.blackbox.fake.frameworks.BUserManager

object UserProfileManager {
    private const val TAG = "UserProfileManager"
    private const val PREFS_NAME = "clonatela_user_profiles"
    private const val KEY_ACTIVE_USER_ID = "active_user_id"
    private const val KEY_PROFILE_NAME_PREFIX = "profile_name_"

    fun getActiveUserId(context: Context): Int {
        val prefs = context.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)
        val savedUserId = prefs.getInt(KEY_ACTIVE_USER_ID, 0)
        val users = getUsers()
        if (users.any { it.id == savedUserId }) {
            return savedUserId
        }
        return 0
    }

    fun setActiveUserId(context: Context, userId: Int) {
        val prefs = context.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)
        prefs.edit().putInt(KEY_ACTIVE_USER_ID, userId).apply()
    }

    fun getProfiles(context: Context): List<BoxUserProfile> {
        val bUsers = getUsers()
        val prefs = context.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)
        val core = BlackBoxCore.get()

        val list = mutableListOf<BoxUserProfile>()
        for (user in bUsers) {
            val defaultName = if (user.id == 0) "Principal" else "Perfil ${user.id + 1}"
            val displayName = prefs.getString("$KEY_PROFILE_NAME_PREFIX${user.id}", defaultName) ?: defaultName
            val appCount = try {
                core.getInstalledApplications(0, user.id)?.size ?: 0
            } catch (t: Throwable) {
                0
            }

            list.add(
                BoxUserProfile(
                    userId = user.id,
                    name = displayName,
                    isDefault = user.id == 0,
                    installedAppsCount = appCount,
                    createdAt = user.createTime
                )
            )
        }

        return list.sortedBy { it.userId }
    }

    fun createProfile(context: Context, customName: String? = null): BoxUserProfile? {
        val bUserManager = BUserManager.get()
        val currentUsers = getUsers()
        val nextId = ((currentUsers.map { it.id }.maxOrNull() ?: -1) + 1).coerceAtLeast(0)

        val createdInfo: BUserInfo? = bUserManager.createUser(nextId)
        if (createdInfo == null) {
            Log.e(TAG, "Failed to create virtual user $nextId")
            return null
        }

        val profileName = customName?.takeIf { it.isNotBlank() } ?: "Perfil ${createdInfo.id + 1}"
        val prefs = context.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)
        prefs.edit().putString("$KEY_PROFILE_NAME_PREFIX${createdInfo.id}", profileName).apply()

        Log.i(TAG, "Virtual profile created: id=${createdInfo.id}, name=$profileName")
        return BoxUserProfile(
            userId = createdInfo.id,
            name = profileName,
            isDefault = createdInfo.id == 0,
            installedAppsCount = 0,
            createdAt = createdInfo.createTime
        )
    }

    fun deleteProfile(context: Context, userId: Int): Boolean {
        if (userId == 0) {
            Log.w(TAG, "Cannot delete default profile (userId=0)")
            return false
        }

        val core = BlackBoxCore.get()
        try {
            val installedApps = core.getInstalledApplications(0, userId) ?: emptyList()
            for (app in installedApps) {
                if (app != null && !app.packageName.isNullOrBlank()) {
                    core.stopPackage(app.packageName, userId)
                    core.uninstallPackageAsUser(app.packageName, userId)
                }
            }
        } catch (t: Throwable) {
            Log.w(TAG, "Error cleaning applications for user $userId", t)
        }

        BUserManager.get().deleteUser(userId)

        val prefs = context.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)
        prefs.edit().remove("$KEY_PROFILE_NAME_PREFIX$userId").apply()

        if (getActiveUserId(context) == userId) {
            setActiveUserId(context, 0)
        }

        Log.i(TAG, "Virtual profile deleted: id=$userId")
        return true
    }

    fun renameProfile(context: Context, userId: Int, newName: String): Boolean {
        if (newName.isBlank()) return false
        val prefs = context.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)
        prefs.edit().putString("$KEY_PROFILE_NAME_PREFIX$userId", newName.trim()).apply()
        return true
    }

    private fun getUsers(): List<BUserInfo> {
        val users = BUserManager.get().users
        if (users == null || users.isEmpty()) {
            val defaultUser = BUserManager.get().createUser(0)
            return if (defaultUser != null) listOf(defaultUser) else emptyList()
        }
        return users
    }
}
