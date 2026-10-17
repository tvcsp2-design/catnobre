package com.th.thtestes

import android.annotation.SuppressLint
import android.app.Activity
import android.content.Intent
import android.os.Bundle
import android.widget.Toast
import com.th.thtestes.domain.AppCloner
import com.th.thtestes.domain.AppPermissionManager
import com.th.thtestes.domain.ShortcutHelper
import top.niunaijun.blackbox.BlackBoxCore
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch

@SuppressLint("CustomSplashScreen")
class ShortcutLaunchActivity : Activity() {

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        handleLaunch(intent)
    }

    override fun onNewIntent(intent: Intent?) {
        super.onNewIntent(intent)
        intent?.let { handleLaunch(it) }
    }

    private fun handleLaunch(intent: Intent) {
        val packageName = intent.getStringExtra(ShortcutHelper.EXTRA_PACKAGE_NAME)
            ?: intent.data?.lastPathSegment

        if (packageName.isNullOrBlank()) {
            Toast.makeText(this, "Aplicativo inválido para atalho.", Toast.LENGTH_SHORT).show()
            finish()
            return
        }

        if (!AppPermissionManager.hasAllRequiredPermissions(this)) {
            Toast.makeText(
                this,
                "Conceda as permissões necessárias no ClonaTela antes de abrir atalhos.",
                Toast.LENGTH_LONG
            ).show()
            val mainIntent = Intent(this, MainActivity::class.java).apply {
                addFlags(Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_CLEAR_TOP)
            }
            startActivity(mainIntent)
            finish()
            return
        }

        val userId = intent.getIntExtra(ShortcutHelper.EXTRA_USER_ID, AppCloner.DEFAULT_USER_ID)

        CoroutineScope(Dispatchers.Main).launch {
            try {
                if (!BlackBoxCore.get().isInstalled(packageName, userId)) {
                    val cloneRes = AppCloner.cloneApp(applicationContext, packageName, userId)
                    if (cloneRes.isFailure) {
                        Toast.makeText(applicationContext, "Falha ao clonar: ${cloneRes.exceptionOrNull()?.message}", Toast.LENGTH_SHORT).show()
                    }
                }
                val launched = AppCloner.launchApp(applicationContext, packageName, userId)
                if (!launched) {
                    Toast.makeText(applicationContext, "Falha ao abrir $packageName", Toast.LENGTH_SHORT).show()
                }
            } catch (e: Exception) {
                Toast.makeText(applicationContext, "Erro ao abrir: ${e.message}", Toast.LENGTH_SHORT).show()
            } finally {
                setResult(Activity.RESULT_OK)
                finish()
            }
        }
    }
}
