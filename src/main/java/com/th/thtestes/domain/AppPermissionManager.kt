package com.th.thtestes.domain

import android.Manifest
import android.content.Context
import android.content.Intent
import android.content.pm.PackageManager
import android.net.Uri
import android.os.Build
import android.os.Environment
import android.provider.Settings
import androidx.core.content.ContextCompat
import androidx.core.net.toUri

/**
 * Representa o tipo de permissão para diferenciação de ações de solicitação.
 */
enum class PermissionType {
    ALL_FILES_ACCESS,
    STORAGE_MEDIA,
    NOTIFICATIONS,
    CAMERA,
    RECORD_AUDIO,
    LOCATION
}

/**
 * Modelo de dados para exibição do status individual de cada permissão na UI.
 */
data class PermissionRequirement(
    val id: String,
    val title: String,
    val description: String,
    val isGranted: Boolean,
    val isSpecial: Boolean = false,
    val type: PermissionType
)

/**
 * Gerenciador centralizado de permissões do ClonaTela.
 * Garante que o ambiente de sandbox possua todos os acessos essenciais para isolamento e virtualização.
 */
object AppPermissionManager {

    /**
     * Verifica se o acesso a todos os arquivos (MANAGE_EXTERNAL_STORAGE) está concedido.
     * No Android 11+ (API 30+), requer Environment.isExternalStorageManager().
     */
    fun isAllFilesAccessGranted(): Boolean {
        return if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.R) {
            Environment.isExternalStorageManager()
        } else {
            true
        }
    }

    /**
     * Verifica se as permissões de leitura/escrita de armazenamento ou mídia foram concedidas.
     */
    fun isStorageGranted(context: Context): Boolean {
        return if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
            val images = ContextCompat.checkSelfPermission(context, Manifest.permission.READ_MEDIA_IMAGES) == PackageManager.PERMISSION_GRANTED
            val video = ContextCompat.checkSelfPermission(context, Manifest.permission.READ_MEDIA_VIDEO) == PackageManager.PERMISSION_GRANTED
            val audio = ContextCompat.checkSelfPermission(context, Manifest.permission.READ_MEDIA_AUDIO) == PackageManager.PERMISSION_GRANTED
            images && video && audio
        } else if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.R) {
            ContextCompat.checkSelfPermission(context, Manifest.permission.READ_EXTERNAL_STORAGE) == PackageManager.PERMISSION_GRANTED
        } else {
            val read = ContextCompat.checkSelfPermission(context, Manifest.permission.READ_EXTERNAL_STORAGE) == PackageManager.PERMISSION_GRANTED
            val write = ContextCompat.checkSelfPermission(context, Manifest.permission.WRITE_EXTERNAL_STORAGE) == PackageManager.PERMISSION_GRANTED
            read && write
        }
    }

    /**
     * Verifica se a permissão de notificações foi concedida (Android 13+ / API 33+).
     */
    fun isNotificationsGranted(context: Context): Boolean {
        return if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
            ContextCompat.checkSelfPermission(
                context,
                Manifest.permission.POST_NOTIFICATIONS
            ) == PackageManager.PERMISSION_GRANTED
        } else {
            true
        }
    }

    /**
     * Verifica se a permissão de câmera foi concedida.
     */
    fun isCameraGranted(context: Context): Boolean {
        return ContextCompat.checkSelfPermission(
            context,
            Manifest.permission.CAMERA
        ) == PackageManager.PERMISSION_GRANTED
    }

    /**
     * Verifica se a permissão de microfone/gravação de áudio foi concedida.
     */
    fun isAudioGranted(context: Context): Boolean {
        return ContextCompat.checkSelfPermission(
            context,
            Manifest.permission.RECORD_AUDIO
        ) == PackageManager.PERMISSION_GRANTED
    }

    /**
     * Verifica se a permissão de localização foi concedida.
     */
    fun isLocationGranted(context: Context): Boolean {
        val fine = ContextCompat.checkSelfPermission(
            context,
            Manifest.permission.ACCESS_FINE_LOCATION
        ) == PackageManager.PERMISSION_GRANTED
        val coarse = ContextCompat.checkSelfPermission(
            context,
            Manifest.permission.ACCESS_COARSE_LOCATION
        ) == PackageManager.PERMISSION_GRANTED
        return fine || coarse
    }

    /**
     * Retorna se TODAS as permissões obrigatórias estão concedidas.
     * O app nunca deve ser liberado sem que esta função retorne true.
     */
    fun hasAllRequiredPermissions(context: Context): Boolean {
        val allFiles = isAllFilesAccessGranted()
        val storage = isStorageGranted(context)
        val notifications = isNotificationsGranted(context)
        val camera = isCameraGranted(context)
        val audio = isAudioGranted(context)
        val location = isLocationGranted(context)
        return allFiles && storage && notifications && camera && audio && location
    }

    /**
     * Retorna a lista detalhada de requisitos de permissão e seus estados atuais.
     */
    fun getRequiredPermissions(context: Context): List<PermissionRequirement> {
        val requirements = mutableListOf<PermissionRequirement>()

        // 1. Acesso a Todos os Arquivos (Android 11+)
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.R) {
            requirements.add(
                PermissionRequirement(
                    id = "all_files_access",
                    title = "Acesso a Todos os Arquivos",
                    description = "Necessário para virtualizar pastas, dados e sistema de arquivos isolado dos aplicativos clonados.",
                    isGranted = isAllFilesAccessGranted(),
                    isSpecial = true,
                    type = PermissionType.ALL_FILES_ACCESS
                )
            )
        }

        // 2. Armazenamento e Mídia
        val storageGranted = isStorageGranted(context)
        val storageDesc = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
            "Permite ao ClonaTela ler APKs instalados, mídias e arquivos do ambiente hospedeiro."
        } else {
            "Permite ao ClonaTela ler e gravar dados para o funcionamento dos clones."
        }
        requirements.add(
            PermissionRequirement(
                id = "storage_media",
                title = "Armazenamento & Mídia",
                description = storageDesc,
                isGranted = storageGranted,
                isSpecial = false,
                type = PermissionType.STORAGE_MEDIA
            )
        )

        // 3. Notificações (Android 13+)
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
            requirements.add(
                PermissionRequirement(
                    id = "notifications",
                    title = "Notificações",
                    description = "Necessário para manter processos de clones em segundo plano e alertas de execução.",
                    isGranted = isNotificationsGranted(context),
                    isSpecial = false,
                    type = PermissionType.NOTIFICATIONS
                )
            )
        }

        // 4. Câmera
        requirements.add(
            PermissionRequirement(
                id = "camera",
                title = "Câmera",
                description = "Necessário para que aplicativos clonados possam capturar fotos, vídeos e ler QR Codes.",
                isGranted = isCameraGranted(context),
                isSpecial = false,
                type = PermissionType.CAMERA
            )
        )

        // 5. Microfone
        requirements.add(
            PermissionRequirement(
                id = "audio",
                title = "Microfone / Áudio",
                description = "Necessário para gravação e chamadas de voz dentro dos aplicativos clonados.",
                isGranted = isAudioGranted(context),
                isSpecial = false,
                type = PermissionType.RECORD_AUDIO
            )
        )

        // 6. Localização
        requirements.add(
            PermissionRequirement(
                id = "location",
                title = "Localização",
                description = "Permite aos aplicativos clonados obter coordenadas de GPS e geolocalização.",
                isGranted = isLocationGranted(context),
                isSpecial = false,
                type = PermissionType.LOCATION
            )
        )

        return requirements
    }

    /**
     * Retorna a lista de permissões padrão de runtime que ainda não foram concedidas.
     */
    fun getRuntimePermissionsToRequest(context: Context): Array<String> {
        val needed = mutableListOf<String>()

        // 1. Notificações (Android 13+)
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
            if (ContextCompat.checkSelfPermission(context, Manifest.permission.POST_NOTIFICATIONS) != PackageManager.PERMISSION_GRANTED) {
                needed.add(Manifest.permission.POST_NOTIFICATIONS)
            }
        }

        // 2. Câmera
        if (ContextCompat.checkSelfPermission(context, Manifest.permission.CAMERA) != PackageManager.PERMISSION_GRANTED) {
            needed.add(Manifest.permission.CAMERA)
        }

        // 3. Microfone
        if (ContextCompat.checkSelfPermission(context, Manifest.permission.RECORD_AUDIO) != PackageManager.PERMISSION_GRANTED) {
            needed.add(Manifest.permission.RECORD_AUDIO)
        }

        // 4. Localização (Fine + Coarse)
        if (ContextCompat.checkSelfPermission(context, Manifest.permission.ACCESS_FINE_LOCATION) != PackageManager.PERMISSION_GRANTED) {
            needed.add(Manifest.permission.ACCESS_FINE_LOCATION)
        }
        if (ContextCompat.checkSelfPermission(context, Manifest.permission.ACCESS_COARSE_LOCATION) != PackageManager.PERMISSION_GRANTED) {
            needed.add(Manifest.permission.ACCESS_COARSE_LOCATION)
        }

        // 5. Armazenamento e Mídia
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
            if (ContextCompat.checkSelfPermission(context, Manifest.permission.READ_MEDIA_IMAGES) != PackageManager.PERMISSION_GRANTED) {
                needed.add(Manifest.permission.READ_MEDIA_IMAGES)
            }
            if (ContextCompat.checkSelfPermission(context, Manifest.permission.READ_MEDIA_VIDEO) != PackageManager.PERMISSION_GRANTED) {
                needed.add(Manifest.permission.READ_MEDIA_VIDEO)
            }
            if (ContextCompat.checkSelfPermission(context, Manifest.permission.READ_MEDIA_AUDIO) != PackageManager.PERMISSION_GRANTED) {
                needed.add(Manifest.permission.READ_MEDIA_AUDIO)
            }
        } else if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.R) {
            if (ContextCompat.checkSelfPermission(context, Manifest.permission.READ_EXTERNAL_STORAGE) != PackageManager.PERMISSION_GRANTED) {
                needed.add(Manifest.permission.READ_EXTERNAL_STORAGE)
            }
        } else {
            if (ContextCompat.checkSelfPermission(context, Manifest.permission.READ_EXTERNAL_STORAGE) != PackageManager.PERMISSION_GRANTED) {
                needed.add(Manifest.permission.READ_EXTERNAL_STORAGE)
            }
            if (ContextCompat.checkSelfPermission(context, Manifest.permission.WRITE_EXTERNAL_STORAGE) != PackageManager.PERMISSION_GRANTED) {
                needed.add(Manifest.permission.WRITE_EXTERNAL_STORAGE)
            }
        }

        return needed.toTypedArray()
    }

    /**
     * Cria uma Intent para a tela de Acesso a Todos os Arquivos (MANAGE_EXTERNAL_STORAGE).
     */
    fun createAllFilesAccessIntent(context: Context): Intent {
        return if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.R) {
            try {
                Intent(Settings.ACTION_MANAGE_APP_ALL_FILES_ACCESS_PERMISSION).apply {
                    data = "package:${context.packageName}".toUri()
                }
            } catch (e: Exception) {
                Intent(Settings.ACTION_MANAGE_ALL_FILES_ACCESS_PERMISSION)
            }
        } else {
            createAppSettingsIntent(context)
        }
    }

    /**
     * Cria uma Intent para a tela de Detalhes do Aplicativo nas Configurações do Android.
     */
    fun createAppSettingsIntent(context: Context): Intent {
        return Intent(Settings.ACTION_APPLICATION_DETAILS_SETTINGS).apply {
            data = Uri.fromParts("package", context.packageName, null)
            flags = Intent.FLAG_ACTIVITY_NEW_TASK
        }
    }

    /**
     * Abre a tela de Acesso a Todos os Arquivos.
     */
    fun openAllFilesAccessSettings(context: Context) {
        try {
            val intent = createAllFilesAccessIntent(context)
            if (context !is android.app.Activity) {
                intent.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
            }
            context.startActivity(intent)
        } catch (e: Exception) {
            openAppSettings(context)
        }
    }

    /**
     * Abre a tela de Configurações do Aplicativo.
     */
    fun openAppSettings(context: Context) {
        try {
            val intent = createAppSettingsIntent(context)
            if (context !is android.app.Activity) {
                intent.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
            }
            context.startActivity(intent)
        } catch (e: Exception) {
            e.printStackTrace()
        }
    }
}
