package com.th.thtestes.domain.logs

import android.app.ActivityManager
import android.content.Context
import android.os.Build
import android.os.Process
import android.util.Log
import java.io.BufferedReader
import java.io.File
import java.io.FileInputStream
import java.io.InputStreamReader
import java.io.PrintWriter
import java.io.StringWriter
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

/**
 * Coletor e formatador de relatórios de falhas (crashes) e ambiente de execução.
 * Diagnostica TV Boxes, aparelhos variados e processos virtuais de clones.
 */
object CrashReportManager {

    private const val TAG = "CrashReportManager"
    private const val PREFS_NAME = "clonatela_crash_prefs"
    private const val KEY_LAST_CRASH_TIME = "last_crash_time"

    /**
     * Identifica o nome do processo atual de forma resiliente.
     * Funciona em processos principais e em processos de clone (:p0, :p1, etc.).
     */
    fun getCurrentProcessName(): String {
        // 1. Tenta API 28+ (Android 9+)
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.P) {
            try {
                val name = android.app.Application.getProcessName()
                if (!name.isNullOrBlank()) return name
            } catch (_: Throwable) {
            }
        }

        // 2. Tenta leitura direta do /proc/self/cmdline (compatível com todas as versões Linux/Android)
        try {
            BufferedReader(InputStreamReader(FileInputStream("/proc/self/cmdline"), Charsets.UTF_8)).use { reader ->
                val cmdline = reader.readLine()
                if (!cmdline.isNullOrBlank()) {
                    return cmdline.trim { it <= ' ' || it == '\u0000' }
                }
            }
        } catch (_: Throwable) {
        }

        return "pid_${Process.myPid()}"
    }

    /**
     * Extrai as últimas linhas do Logcat do sistema Android.
     * Fundamental para TV Boxes e clones para capturar falhas de C/C++ (SIGSEGV, tombstone, dlopen).
     */
    fun captureLogcatSnippet(lines: Int = 300): String {
        val sb = StringBuilder()
        try {
            val process = Runtime.getRuntime().exec(arrayOf("logcat", "-d", "-v", "time", "-t", lines.toString()))
            BufferedReader(InputStreamReader(process.inputStream)).use { reader ->
                var line: String?
                while (reader.readLine().also { line = it } != null) {
                    sb.append(line).append("\n")
                }
            }
        } catch (t: Throwable) {
            sb.append("[Falha ao capturar logcat: ").append(t.message).append("]\n")
        }
        return sb.toString()
    }

    /**
     * Gera e salva um relatório estruturado de Crash.
     */
    fun recordCrash(
        context: Context?,
        thread: Thread?,
        throwable: Throwable,
        extraInfo: String? = null
    ): File? {
        val now = Date()
        val timeStampReadable = SimpleDateFormat("yyyy-MM-dd HH:mm:ss.SSS", Locale.US).format(now)
        val fileTime = SimpleDateFormat("yyyyMMdd_HHmmss", Locale.US).format(now)
        val procName = getCurrentProcessName().replace(':', '_').replace('/', '_')

        val fileName = "crash_${procName}_$fileTime.txt"

        val report = StringBuilder()
        report.append("====================================================\n")
        report.append("          CLONATELA / TH TESTES - CRASH REPORT      \n")
        report.append("====================================================\n\n")

        report.append("--- [INFORMAÇÕES DO CRASH] ---\n")
        report.append("Data/Hora: ").append(timeStampReadable).append("\n")
        report.append("Processo: ").append(getCurrentProcessName()).append(" (PID: ").append(Process.myPid()).append(", UID: ").append(Process.myUid()).append(")\n")
        report.append("Thread: ").append(thread?.name ?: "Unknown").append(" (ID: ").append(thread?.id ?: -1).append(")\n")
        report.append("JVM Arch: ").append(if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.M && Process.is64Bit()) "64-bit" else "32-bit").append("\n")
        if (!extraInfo.isNullOrBlank()) {
            report.append("Contexto Adicional: ").append(extraInfo).append("\n")
        }
        report.append("\n")

        report.append("--- [INFORMAÇÕES DE HARDWARE & SISTEMA (TV BOX / DISPOSITIVO)] ---\n")
        report.append("Fabricante: ").append(Build.MANUFACTURER).append("\n")
        report.append("Modelo: ").append(Build.MODEL).append("\n")
        report.append("Marca/Brand: ").append(Build.BRAND).append("\n")
        report.append("Dispositivo: ").append(Build.DEVICE).append("\n")
        report.append("Placa/Board: ").append(Build.BOARD).append("\n")
        report.append("Hardware: ").append(Build.HARDWARE).append("\n")
        report.append("Produto: ").append(Build.PRODUCT).append("\n")
        report.append("Fingerprint: ").append(Build.FINGERPRINT).append("\n")
        report.append("Android Release: ").append(Build.VERSION.RELEASE).append(" (API ").append(Build.VERSION.SDK_INT).append(")\n")
        report.append("Security Patch: ").append(if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.M) Build.VERSION.SECURITY_PATCH else "N/A").append("\n")
        report.append("ABIs Suportadas: ").append(Build.SUPPORTED_ABIS.joinToString(", ")).append("\n\n")

        report.append("--- [MEMÓRIA DO PROCESSO] ---\n")
        val runtime = Runtime.getRuntime()
        val maxMemMb = runtime.maxMemory() / (1024 * 1024)
        val totalMemMb = runtime.totalMemory() / (1024 * 1024)
        val freeMemMb = runtime.freeMemory() / (1024 * 1024)
        val usedMemMb = totalMemMb - freeMemMb
        report.append("Heap JVM: Usada=${usedMemMb}MB / Alocada=${totalMemMb}MB / Limite=${maxMemMb}MB\n")

        if (context != null) {
            try {
                val am = context.getSystemService(Context.ACTIVITY_SERVICE) as? ActivityManager
                val memInfo = ActivityManager.MemoryInfo()
                am?.getMemoryInfo(memInfo)
                val totalRamMb = memInfo.totalMem / (1024 * 1024)
                val availRamMb = memInfo.availMem / (1024 * 1024)
                report.append("RAM Dispositivo: Livre=${availRamMb}MB / Total=${totalRamMb}MB (LowMemory=${memInfo.lowMemory})\n")
            } catch (_: Throwable) {
            }
        }
        report.append("\n")

        report.append("--- [STACKTRACE DA FALHA] ---\n")
        val sw = StringWriter()
        throwable.printStackTrace(PrintWriter(sw))
        report.append(sw.toString()).append("\n\n")

        // Exceções com causas secundárias
        var cause = throwable.cause
        var depth = 1
        while (cause != null && depth <= 5) {
            report.append("--- [CAUSA RAIZ #$depth] ---\n")
            val swCause = StringWriter()
            cause.printStackTrace(PrintWriter(swCause))
            report.append(swCause.toString()).append("\n\n")
            cause = cause.cause
            depth++
        }

        report.append("--- [LOGCAT RECENTE DO SISTEMA (ÚLTIMAS 300 LINHAS)] ---\n")
        val logcatContent = captureLogcatSnippet(300)
        report.append(logcatContent).append("\n")
        report.append("=================== FIM DO RELATÓRIO ===================\n")

        val content = report.toString()

        // Salva arquivo físico
        val savedFile = if (context != null) {
            LogStorageManager.writeTextFile(context, fileName, content)
        } else {
            null
        }

        // Atualiza carimbo de crash recente em SharedPreferences para aviso na UI
        if (context != null) {
            try {
                val prefs = context.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)
                prefs.edit().putLong(KEY_LAST_CRASH_TIME, now.time).apply()
            } catch (_: Throwable) {
            }
        }

        Log.e(TAG, "Crash report registrado com sucesso: $fileName (Salvo em: ${savedFile?.absolutePath})")
        return savedFile
    }

    /**
     * Verifica se houve crash nas últimas 24 horas.
     */
    fun hasRecentCrash(context: Context): Boolean {
        return try {
            val prefs = context.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)
            val lastCrashTime = prefs.getLong(KEY_LAST_CRASH_TIME, 0L)
            val diff = System.currentTimeMillis() - lastCrashTime
            diff in 0..(24 * 60 * 60 * 1000)
        } catch (_: Throwable) {
            false
        }
    }

    /**
     * Limpa o status de aviso de crash recente.
     */
    fun clearRecentCrashAlert(context: Context) {
        try {
            val prefs = context.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)
            prefs.edit().remove(KEY_LAST_CRASH_TIME).apply()
        } catch (_: Throwable) {
        }
    }

    /**
     * Gera um resumo do dispositivo para conferência técnica rápida.
     */
    fun generateDeviceInfoReport(context: Context): String {
        val sb = StringBuilder()
        sb.append("Dispositivo: ").append(Build.MANUFACTURER).append(" ").append(Build.MODEL).append("\n")
        sb.append("Android: ").append(Build.VERSION.RELEASE).append(" (SDK ").append(Build.VERSION.SDK_INT).append(")\n")
        sb.append("Placa/Chipset: ").append(Build.BOARD).append(" / ").append(Build.HARDWARE).append("\n")
        sb.append("Arquitetura JVM: ").append(if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.M && Process.is64Bit()) "64-bit" else "32-bit").append("\n")
        sb.append("ABIs Suportadas: ").append(Build.SUPPORTED_ABIS.joinToString(", ")).append("\n")
        sb.append("Pasta Pública de Logs: ").append(LogStorageManager.getPublicLogDirectory().absolutePath).append("\n")
        return sb.toString()
    }
}
