package com.th.thtestes.domain.logs

import android.content.Context
import android.util.Log
import java.io.PrintWriter
import java.io.StringWriter
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale
import java.util.concurrent.Executors

/**
 * Sistema unificado de logging contínuo para o ClonaTela.
 * Registra logs simultaneamente no Logcat e em arquivos rotativos em disco.
 */
object AppLogger {

    private const val TAG = "AppLogger"
    private var appContext: Context? = null
    private var maintenanceScheduled = false
    private val logExecutor = Executors.newSingleThreadExecutor()

    @Synchronized
    fun init(context: Context) {
        val validContext = context.applicationContext ?: context
        appContext = validContext
        if (maintenanceScheduled) return
        maintenanceScheduled = true

        // Rotação: remove logs com mais de 7 dias
        logExecutor.execute {
            try {
                LogStorageManager.pruneOldLogs(validContext, 7)
            } catch (_: Throwable) {
            }
        }
    }

    fun v(tag: String, msg: String) {
        Log.v(tag, msg)
        writeLogEntry("VERBOSE", tag, msg, null)
    }

    fun d(tag: String, msg: String) {
        Log.d(tag, msg)
        writeLogEntry("DEBUG", tag, msg, null)
    }

    fun i(tag: String, msg: String) {
        Log.i(tag, msg)
        writeLogEntry("INFO", tag, msg, null)
    }

    fun w(tag: String, msg: String, tr: Throwable? = null) {
        Log.w(tag, msg, tr)
        writeLogEntry("WARN", tag, msg, tr)
    }

    fun e(tag: String, msg: String, tr: Throwable? = null) {
        Log.e(tag, msg, tr)
        writeLogEntry("ERROR", tag, msg, tr)
    }

    private fun writeLogEntry(level: String, tag: String, msg: String, tr: Throwable?) {
        val ctx = appContext ?: return
        val now = Date()
        val dateStamp = SimpleDateFormat("yyyy-MM-dd", Locale.US).format(now)
        val timeStamp = SimpleDateFormat("HH:mm:ss.SSS", Locale.US).format(now)
        val fileName = "clonatela_$dateStamp.log"
        val procName = CrashReportManager.getCurrentProcessName()
        val threadName = Thread.currentThread().name

        val logLine = buildString {
            append("[").append(timeStamp).append("] ")
            append("[").append(procName).append("/")
            append(threadName).append("] ")
            append("[").append(level).append("/").append(tag).append("]: ")
            append(msg)
            if (tr != null) {
                append("\n")
                val sw = StringWriter()
                tr.printStackTrace(PrintWriter(sw))
                append(sw.toString().trimEnd())
            }
            append("\n")
        }

        logExecutor.execute {
            try {
                LogStorageManager.appendTextFile(ctx, fileName, logLine)
            } catch (t: Throwable) {
                Log.w(TAG, "Falha ao gravar linha no log: ${t.message}")
            }
        }
    }
}
