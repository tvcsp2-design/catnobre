package com.th.thtestes.domain.logs

import android.content.Context
import android.media.MediaScannerConnection
import android.os.Build
import android.os.Environment
import android.util.Log
import java.io.File
import java.io.FileInputStream
import java.io.FileOutputStream
import java.io.IOException
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale
import java.util.zip.ZipEntry
import java.util.zip.ZipOutputStream

/**
 * Gerenciador multi-camadas de armazenamento de logs e relatórios de falha (crashes).
 *
 * Estratégia de Armazenamento:
 * - Tier 1 (Público): /sdcard/Download/ClonaTela_Logs/
 *   Fácil acesso para analistas via gerenciador de arquivos, conexão USB/MTP ou ADB.
 * - Tier 2 (Seguro / Fallback): context.getExternalFilesDir("logs")
 *   Nunca bloqueado por permissões de runtime do Android (funciona desde API 19).
 * - Auto-Sync: Espelha dados pendentes do armazenamento seguro para a pasta pública
 *   assim que as permissões forem concedidas.
 */
object LogStorageManager {

    private const val TAG = "LogStorageManager"
    const val PUBLIC_SUBDIR = "ClonaTela_Logs"

    /**
     * Retorna a pasta pública de logs (Downloads/ClonaTela_Logs).
     */
    fun getPublicLogDirectory(): File {
        val downloadDir = Environment.getExternalStoragePublicDirectory(Environment.DIRECTORY_DOWNLOADS)
        val logDir = File(downloadDir, PUBLIC_SUBDIR)
        if (!logDir.exists()) {
            try {
                logDir.mkdirs()
            } catch (e: Exception) {
                Log.w(TAG, "Falha ao criar diretório público primário: ${e.message}")
            }
        }
        return logDir
    }

    /**
     * Retorna o diretório seguro do app (context.getExternalFilesDir("logs")),
     * com fallback para context.filesDir se o armazenamento externo não estiver disponível.
     */
    fun getSecureLogDirectory(context: Context): File {
        val extDir = try {
            context.getExternalFilesDir("logs")
        } catch (t: Throwable) {
            null
        }
        val target = extDir ?: File(context.filesDir, "logs")
        if (!target.exists()) {
            try {
                target.mkdirs()
            } catch (t: Throwable) {
                Log.w(TAG, "Falha ao criar diretório seguro de logs: ${t.message}")
            }
        }
        return target
    }

    /**
     * Salva um conteúdo de texto de forma resiliente.
     * Tenta salvar na pasta pública E na pasta segura para garantir persistência.
     * Registra no MediaScanner para visibilidade imediata em USB.
     */
    fun writeTextFile(context: Context, fileName: String, content: String): File? {
        var resultFile: File? = null

        // 1. Tentar salvar no diretório público
        try {
            val pubDir = getPublicLogDirectory()
            if (pubDir.exists() && (pubDir.canWrite() || Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q)) {
                val pubFile = File(pubDir, fileName)
                pubFile.writeText(content, Charsets.UTF_8)
                resultFile = pubFile
                notifyMediaScanner(context, pubFile)
            }
        } catch (t: Throwable) {
            Log.w(TAG, "Não foi possível gravar diretamente na pasta pública: ${t.message}")
        }

        // 2. Sempre salvar cópia no diretório seguro (garantido)
        try {
            val secDir = getSecureLogDirectory(context)
            val secFile = File(secDir, fileName)
            secFile.writeText(content, Charsets.UTF_8)
            if (resultFile == null) {
                resultFile = secFile
            }
        } catch (t: Throwable) {
            Log.e(TAG, "Falha ao gravar arquivo no diretório seguro: ${t.message}", t)
        }

        return resultFile
    }

    /**
     * Anexa texto a um arquivo existente ou o cria caso não exista.
     */
    @Synchronized
    fun appendTextFile(context: Context, fileName: String, text: String) {
        // Grava no diretório seguro garantido
        try {
            val secDir = getSecureLogDirectory(context)
            val secFile = File(secDir, fileName)
            secFile.appendText(text, Charsets.UTF_8)
        } catch (t: Throwable) {
            Log.w(TAG, "Falha append seguro: ${t.message}")
        }

        // Tenta também no público
        try {
            val pubDir = getPublicLogDirectory()
            if (pubDir.exists() && pubDir.canWrite()) {
                val pubFile = File(pubDir, fileName)
                pubFile.appendText(text, Charsets.UTF_8)
            }
        } catch (_: Throwable) {
        }
    }

    /**
     * Sincroniza logs do armazenamento seguro para a pasta pública acessível.
     * Deve ser invocado no boot ou quando permissão de storage for concedida.
     */
    fun syncLogsToPublic(context: Context): Int {
        var copiedCount = 0
        try {
            val secDir = getSecureLogDirectory(context)
            val pubDir = getPublicLogDirectory()
            if (!pubDir.exists()) {
                pubDir.mkdirs()
            }
            if (!pubDir.exists() || !pubDir.canWrite()) {
                return 0
            }

            val secureFiles = secDir.listFiles() ?: return 0
            for (file in secureFiles) {
                if (file.isFile) {
                    val dest = File(pubDir, file.name)
                    if (!dest.exists() || dest.length() != file.length() || dest.lastModified() < file.lastModified()) {
                        try {
                            file.copyTo(dest, overwrite = true)
                            notifyMediaScanner(context, dest)
                            copiedCount++
                        } catch (t: Throwable) {
                            Log.w(TAG, "Erro ao copiar ${file.name}: ${t.message}")
                        }
                    }
                }
            }
        } catch (t: Throwable) {
            Log.e(TAG, "Erro ao sincronizar logs: ${t.message}", t)
        }
        return copiedCount
    }

    /**
     * Lista todos os arquivos de log disponíveis (unindo público e seguro).
     */
    fun listAllLogFiles(context: Context): List<File> {
        val filesMap = mutableMapOf<String, File>()

        // Adiciona arquivos do diretório seguro
        try {
            val secDir = getSecureLogDirectory(context)
            secDir.listFiles()?.forEach { file ->
                if (file.isFile) filesMap[file.name] = file
            }
        } catch (_: Throwable) {
        }

        // Adiciona/sobrescreve com arquivos do diretório público (se mais recentes ou maiores)
        try {
            val pubDir = getPublicLogDirectory()
            pubDir.listFiles()?.forEach { file ->
                if (file.isFile) {
                    val existing = filesMap[file.name]
                    if (existing == null || file.lastModified() >= existing.lastModified()) {
                        filesMap[file.name] = file
                    }
                }
            }
        } catch (_: Throwable) {
        }

        return filesMap.values.sortedByDescending { it.lastModified() }
    }

    /**
     * Limpa logs com mais de N dias para não encher o disco da TV Box.
     */
    fun pruneOldLogs(context: Context, maxAgeDays: Int = 7) {
        val threshold = System.currentTimeMillis() - (maxAgeDays.toLong() * 24 * 60 * 60 * 1000)
        listOf(getSecureLogDirectory(context), getPublicLogDirectory()).forEach { dir ->
            try {
                dir.listFiles()?.forEach { file ->
                    if (file.isFile && file.lastModified() < threshold) {
                        file.delete()
                    }
                }
            } catch (_: Throwable) {
            }
        }
    }

    /**
     * Apaga todos os arquivos de log de ambas as pastas.
     */
    fun clearAllLogs(context: Context): Boolean {
        var success = true
        listOf(getSecureLogDirectory(context), getPublicLogDirectory()).forEach { dir ->
            try {
                dir.listFiles()?.forEach { file ->
                    if (file.isFile) {
                        if (!file.delete()) success = false
                    }
                }
            } catch (_: Throwable) {
                success = false
            }
        }
        return success
    }

    /**
     * Cria um arquivo .ZIP consolidado contendo todos os logs para exportação/compartilhamento.
     */
    fun createConsolidatedZip(context: Context): File? {
        val files = listAllLogFiles(context)
        if (files.isEmpty()) return null

        val timeStamp = SimpleDateFormat("yyyyMMdd_HHmmss", Locale.US).format(Date())
        val zipName = "clonatela_logs_$timeStamp.zip"
        val zipFile = File(getPublicLogDirectory(), zipName)

        return try {
            ZipOutputStream(FileOutputStream(zipFile)).use { zos ->
                val buffer = ByteArray(8192)
                for (file in files) {
                    if (file.name.endsWith(".zip")) continue // não incluir zips anteriores
                    try {
                        FileInputStream(file).use { fis ->
                            zos.putNextEntry(ZipEntry(file.name))
                            var length: Int
                            while (fis.read(buffer).also { length = it } > 0) {
                                zos.write(buffer, 0, length)
                            }
                            zos.closeEntry()
                        }
                    } catch (t: Throwable) {
                        Log.w(TAG, "Falha ao compactar ${file.name}: ${t.message}")
                    }
                }
            }
            notifyMediaScanner(context, zipFile)
            zipFile
        } catch (t: Throwable) {
            Log.e(TAG, "Falha ao criar ZIP consolidado: ${t.message}", t)
            null
        }
    }

    /**
     * Notifica o MediaScanner do sistema Android para que o arquivo apareça
     * imediatamente quando conectado via cabo USB ao computador (MTP).
     */
    fun notifyMediaScanner(context: Context?, file: File?) {
        if (file == null || !file.isFile || !file.exists()) return

        try {
            val validContext = context?.applicationContext ?: return
            val privateFilesDir = validContext.filesDir.canonicalFile
            val cacheDir = validContext.cacheDir.canonicalFile
            val targetFile = file.canonicalFile
            if (targetFile.path.startsWith(privateFilesDir.path + File.separator)
                || targetFile.path.startsWith(cacheDir.path + File.separator)
            ) {
                return
            }

            validContext.contentResolver ?: return
            MediaScannerConnection.scanFile(
                validContext,
                arrayOf(targetFile.absolutePath),
                null,
                null
            )
        } catch (_: Throwable) {
        }
    }
}
