package com.th.thtestes.domain.logs

import android.content.Context
import android.util.Log
import java.io.BufferedReader
import java.io.File
import java.io.InputStreamReader
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

/**
 * Gravador sob demanda do buffer de Logcat do sistema Android.
 * Captura todas as saídas do sistema, incluindo o motor de virtualização e crashes nativos.
 */
object LogcatRecorder {

    private const val TAG = "LogcatRecorder"

    /**
     * Captura as últimas [lineCount] linhas do Logcat e grava em arquivo de texto.
     * Retorna o arquivo gerado (salvo na pasta pública/segura).
     */
    fun captureAndSaveLogcat(context: Context, lineCount: Int = 1000): File? {
        val now = Date()
        val timeStamp = SimpleDateFormat("yyyyMMdd_HHmmss", Locale.US).format(now)
        val readableTime = SimpleDateFormat("yyyy-MM-dd HH:mm:ss", Locale.US).format(now)
        val fileName = "logcat_$timeStamp.txt"

        val sb = StringBuilder()
        sb.append("====================================================\n")
        sb.append("          CLONATELA / TH TESTES - DUMP DE LOGCAT    \n")
        sb.append("====================================================\n")
        sb.append("Data/Hora: ").append(readableTime).append("\n")
        sb.append("Total de linhas solicitadas: ").append(lineCount).append("\n\n")

        sb.append("--- [INFORMAÇÕES DE HARDWARE & SISTEMA] ---\n")
        sb.append(CrashReportManager.generateDeviceInfoReport(context)).append("\n\n")

        sb.append("--- [BUFFER DO LOGCAT] ---\n")
        try {
            val process = Runtime.getRuntime().exec(arrayOf("logcat", "-d", "-v", "time", "-t", lineCount.toString()))
            BufferedReader(InputStreamReader(process.inputStream, Charsets.UTF_8)).use { reader ->
                var line: String?
                while (reader.readLine().also { line = it } != null) {
                    sb.append(line).append("\n")
                }
            }
        } catch (t: Throwable) {
            Log.e(TAG, "Erro ao capturar logcat: ${t.message}", t)
            sb.append("[FALHA AO EXECUTAR LOGCAT: ").append(t.message).append("]\n")
        }

        sb.append("=================== FIM DO LOGCAT ===================\n")

        val resultFile = LogStorageManager.writeTextFile(context, fileName, sb.toString())
        AppLogger.i(TAG, "Dump de logcat salvo em: ${resultFile?.absolutePath}")
        return resultFile
    }
}
