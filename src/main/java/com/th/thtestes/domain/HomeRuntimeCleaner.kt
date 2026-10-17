package com.th.thtestes.domain

import android.content.Context
import android.util.Log
import top.niunaijun.blackbox.BlackBoxCore

object HomeRuntimeCleaner {
    private const val TAG = "HomeRuntimeCleaner"
    data class Result(
        val inspectedPackages: Int,
        val stoppedPackages: Int,
        val clearedCaches: Int,
        val failures: Int
    )

    fun stopUserApplications(
        context: Context,
        userId: Int = 0,
        recentlyActivePackageName: String? = null,
        preservedPackageName: String? = null
    ): Result {
        val core = BlackBoxCore.get()
        val installedApps = try {
            core.getInstalledApplications(0, userId)
        } catch (t: Throwable) {
            Log.e(TAG, "Unable to list applications for user $userId", t)
            return Result(0, 0, 0, 1)
        } ?: emptyList()

        val packageNames = installedApps.mapNotNull { it?.packageName }
            .filter { it != preservedPackageName }
            .distinct()

        val clearCacheOnExit = context.getSharedPreferences("thtestes_preferences", Context.MODE_PRIVATE)
            .getBoolean("clear_cache_on_exit", true)
        var stoppedCount = 0
        var clearedCacheCount = 0
        var failureCount = 0

        for (pkg in packageNames) {
            val wasRunning = try {
                BlackBoxCore.isRunningApplication(pkg, userId)
            } catch (t: Throwable) {
                false
            }

            var stopSuccess = false
            try {
                core.stopPackage(pkg, userId)
                stopSuccess = true
            } catch (t: Throwable) {
                Log.e(TAG, "Unable to stop $pkg for user $userId", t)
            }

            var resetSuccess = false
            try {
                BlackBoxCore.getBActivityManager().resetPackageActivityState(pkg, userId)
                resetSuccess = true
            } catch (t: Throwable) {
                Log.e(TAG, "Unable to reset activity state for $pkg user $userId", t)
            }

            if (stopSuccess && resetSuccess) {
                stoppedCount++
                if (clearCacheOnExit && (wasRunning || pkg == recentlyActivePackageName)) {
                    val clearedEntries = try {
                        core.clearPackageCache(pkg, userId)
                    } catch (t: Throwable) {
                        Log.e(TAG, "Unable to clear cache for $pkg user $userId", t)
                        -1
                    }
                    if (clearedEntries >= 0) {
                        clearedCacheCount++
                        Log.i(TAG, "Cleared cache for $pkg user=$userId entries=$clearedEntries")
                    }
                }
            } else {
                failureCount++
            }
        }

        return Result(
            inspectedPackages = packageNames.size,
            stoppedPackages = stoppedCount,
            clearedCaches = clearedCacheCount,
            failures = failureCount
        )
    }
}
