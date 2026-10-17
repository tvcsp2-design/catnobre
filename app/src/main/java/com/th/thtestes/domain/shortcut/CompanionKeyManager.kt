package com.th.thtestes.domain.shortcut

import android.content.Context
import android.util.Log
import java.io.ByteArrayInputStream
import java.security.KeyFactory
import java.security.KeyStore
import java.security.PrivateKey
import java.security.cert.CertificateFactory
import java.security.cert.X509Certificate
import java.security.spec.PKCS8EncodedKeySpec

object CompanionKeyManager {
    private const val TAG = "CompanionKeyManager"
    const val PK8_KEY_PATH = "companion/companion.pk8"
    const val CRT_PATH = "companion/companion.crt"
    const val KEYSTORE_PATH = "companion/companion.p12"
    const val KEYSTORE_PASSWORD = "clonatela"
    const val KEY_ALIAS = "clonatela_shortcut"

    private var cachedPrivateKey: PrivateKey? = null
    private var cachedCertificate: X509Certificate? = null

    @Synchronized
    fun getPrivateKey(context: Context): PrivateKey {
        cachedPrivateKey?.let { return it }
        loadKeys(context)
        return cachedPrivateKey ?: error("Falha ao carregar PrivateKey de atalhos")
    }

    @Synchronized
    fun getCertificate(context: Context): X509Certificate {
        cachedCertificate?.let { return it }
        loadKeys(context)
        return cachedCertificate ?: error("Falha ao carregar X509Certificate de atalhos")
    }

    @Synchronized
    fun resetCache() {
        cachedPrivateKey = null
        cachedCertificate = null
    }

    private fun loadKeys(context: Context) {
        // 1. Tentar carregamento direto via DER (PKCS#8 + X.509)
        // Isso é universal, instantâneo e elimina dependência de KDF/SecretKeyFactory em firmwares de TV Box genéricas
        if (loadFromDerAssets(context)) {
            return
        }

        // 2. Fallback para KeyStore PKCS#12 legado
        loadFromKeystoreAsset(context)
    }

    fun loadFromDerAssets(context: Context): Boolean {
        return try {
            val keyBytes = context.assets.open(PK8_KEY_PATH).use { it.readBytes() }
            val certBytes = context.assets.open(CRT_PATH).use { it.readBytes() }

            val keySpec = PKCS8EncodedKeySpec(keyBytes)
            val keyFactory = KeyFactory.getInstance("RSA")
            val privateKey = keyFactory.generatePrivate(keySpec)

            val certFactory = CertificateFactory.getInstance("X.509")
            val cert = certFactory.generateCertificate(ByteArrayInputStream(certBytes)) as? X509Certificate
                ?: error("Falha ao decodificar certificado X.509")

            cachedPrivateKey = privateKey
            cachedCertificate = cert
            Log.d(TAG, "Chave privada e certificado carregados com sucesso via DER.")
            true
        } catch (e: Exception) {
            Log.w(TAG, "Tentativa de carregar chaves via DER falhou, tentando fallback: ${e.message}")
            false
        }
    }

    fun loadFromKeystoreAsset(context: Context) {
        try {
            val keyStore = KeyStore.getInstance("PKCS12")
            context.assets.open(KEYSTORE_PATH).use { inputStream ->
                keyStore.load(inputStream, KEYSTORE_PASSWORD.toCharArray())
            }

            val privateKey = keyStore.getKey(KEY_ALIAS, KEYSTORE_PASSWORD.toCharArray()) as? PrivateKey
                ?: error("Chave privada '$KEY_ALIAS' não encontrada no keystore")
            val certificate = keyStore.getCertificate(KEY_ALIAS) as? X509Certificate
                ?: error("Certificado '$KEY_ALIAS' não encontrado no keystore")

            cachedPrivateKey = privateKey
            cachedCertificate = certificate
            Log.d(TAG, "Chave e certificado carregados com sucesso via KeyStore PKCS12.")
        } catch (e: Exception) {
            Log.e(TAG, "Erro ao carregar keystore de atalhos", e)
            throw e
        }
    }
}
