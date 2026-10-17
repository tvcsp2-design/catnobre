package com.th.thtestes.domain.logs

import android.content.Context
import android.util.Log

/**
 * Interceptador global de falhas não tratadas (Uncaught Exceptions & Errors).
 * Instalado na primeira linha de inicialização de cada processo da aplicação.
 */
class ClonaTelaCrashHandler private constructor(
    private val appContext: Context,
    private val previousHandler: Thread.UncaughtExceptionHandler?
) : Thread.UncaughtExceptionHandler {

    companion object {
        private const val TAG = "ClonaTelaCrashHandler"
        private var isInstalled = false

        /**
         * Instala o handler global na JVM atual.
         * Deve ser chamado na primeiríssima instrução de Application.attachBaseContext().
         */
        @Synchronized
        fun install(context: Context) {
            if (isInstalled) return
            try {
                val existingHandler = Thread.getDefaultUncaughtExceptionHandler()
                val customHandler = ClonaTelaCrashHandler(context.applicationContext ?: context, existingHandler)
                Thread.setDefaultUncaughtExceptionHandler(customHandler)
                isInstalled = true
                Log.i(TAG, "ClonaTelaCrashHandler instalado com sucesso no processo: ${CrashReportManager.getCurrentProcessName()}")
            } catch (t: Throwable) {
                Log.e(TAG, "Falha ao instalar ClonaTelaCrashHandler: ${t.message}", t)
            }
        }
    }

    override fun uncaughtException(thread: Thread, throwable: Throwable) {
        try {
            // 1. Grava no AppLogger
            AppLogger.e("FATAL_CRASH", "Exceção fatal não tratada na thread ${thread.name}: ${throwable.message}", throwable)

            // 2. Gera e salva o Crash Report completo com Hardware da TV Box e Logcat
            CrashReportManager.recordCrash(appContext, thread, throwable)

            // 3. Tenta sincronização imediata para a pasta pública
            LogStorageManager.syncLogsToPublic(appContext)
        } catch (t: Throwable) {
            Log.e(TAG, "Erro interno durante processamento de crash: ${t.message}", t)
        } finally {
            // 4. Encaminha para o handler anterior ou encerra processo
            if (previousHandler != null && previousHandler !is ClonaTelaCrashHandler) {
                previousHandler.uncaughtException(thread, throwable)
            } else {
                android.os.Process.killProcess(android.os.Process.myPid())
                System.exit(10)
            }
        }
    }
}
