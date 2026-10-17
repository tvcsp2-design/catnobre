package com.th.thtestes

import android.app.Activity
import android.app.Application
import android.content.Context
import android.os.Bundle
import com.th.thtestes.domain.logs.AppLogger
import com.th.thtestes.domain.logs.ClonaTelaCrashHandler
import com.th.thtestes.domain.logs.CrashReportManager
import com.th.thtestes.domain.logs.LogStorageManager
import top.niunaijun.blackbox.BlackBoxCore
import top.niunaijun.blackbox.app.configuration.AppLifecycleCallback
import top.niunaijun.blackbox.app.configuration.ClientConfiguration

class App : Application() {

    companion object {
        lateinit var instance: App
            private set
    }

    override fun attachBaseContext(base: Context?) {
        // Instalação prioritária antes de qualquer subsistema
        base?.let {
            ClonaTelaCrashHandler.install(it)
            AppLogger.init(it)
        }
        super.attachBaseContext(base)
        instance = this

        AppLogger.i("AppLifecycle", "attachBaseContext no processo: ${CrashReportManager.getCurrentProcessName()}")

        try {
            BlackBoxCore.get().doAttachBaseContext(base, object : ClientConfiguration() {
                override fun getHostPackageName(): String = packageName
                override fun isHideRoot(): Boolean = true
                override fun isEnableDaemonService(): Boolean = true
                override fun isDisableFlagSecure(): Boolean = false
            })
        } catch (t: Throwable) {
            AppLogger.e("AppLifecycle", "Falha crítica em BlackBoxCore.doAttachBaseContext: ${t.message}", t)
            CrashReportManager.recordCrash(base, Thread.currentThread(), t, "Erro em doAttachBaseContext")
        }
    }

    override fun onCreate() {
        super.onCreate()
        AppLogger.init(this)
        AppLogger.i("AppLifecycle", "onCreate no processo: ${CrashReportManager.getCurrentProcessName()}")

        try {
            // Conecta interceptador de falhas virtuais nos processos filhos
            BlackBoxCore.get().setExceptionHandler { thread, throwable ->
                AppLogger.e("VirtualProcessCrash", "Falha no processo virtual (${thread.name}): ${throwable.message}", throwable)
                CrashReportManager.recordCrash(this, thread, throwable, "Crash capturado via BlackBoxCore.ExceptionHandler")
            }

            // Monitoramento detalhado de cada estágio do ciclo de vida dos clones
            BlackBoxCore.get().addAppLifecycleCallback(object : AppLifecycleCallback() {
                override fun beforeMainLaunchApk(packageName: String?, userid: Int) {
                    AppLogger.i("CloneLifecycle", "beforeMainLaunchApk: pkg=$packageName user=$userid")
                }

                override fun beforeCreateApplication(packageName: String?, processName: String?, context: Context?, userId: Int) {
                    AppLogger.i("CloneLifecycle", "beforeCreateApplication: pkg=$packageName proc=$processName user=$userId")
                }

                override fun beforeApplicationOnCreate(packageName: String?, processName: String?, application: Application?, userId: Int) {
                    AppLogger.i("CloneLifecycle", "beforeApplicationOnCreate: pkg=$packageName proc=$processName user=$userId")
                }

                override fun afterApplicationOnCreate(packageName: String?, processName: String?, application: Application?, userId: Int) {
                    AppLogger.i("CloneLifecycle", "afterApplicationOnCreate: pkg=$packageName proc=$processName user=$userId")
                }

                override fun beforeMainActivityOnCreate(activity: Activity) {
                    AppLogger.i("CloneLifecycle", "beforeMainActivityOnCreate: ${activity.componentName.flattenToShortString()}")
                }

                override fun afterMainActivityOnCreate(activity: Activity) {
                    AppLogger.i("CloneLifecycle", "afterMainActivityOnCreate: ${activity.componentName.flattenToShortString()}")
                }

                override fun onActivityCreated(activity: Activity, savedInstanceState: Bundle?) {
                    AppLogger.i("CloneLifecycle", "onActivityCreated: ${activity.componentName.flattenToShortString()}")
                }
            })

            BlackBoxCore.get().doCreate()
            if (BlackBoxCore.get().users.isEmpty()) {
                BlackBoxCore.get().createUser(0)
            }
        } catch (t: Throwable) {
            AppLogger.e("AppLifecycle", "Falha crítica em BlackBoxCore.doCreate: ${t.message}", t)
            CrashReportManager.recordCrash(this, Thread.currentThread(), t, "Erro em BlackBoxCore.doCreate")
        }

        // Tenta sincronizar relatórios anteriores para a pasta pública
        try {
            LogStorageManager.syncLogsToPublic(this)
        } catch (_: Throwable) {
        }
    }
}

