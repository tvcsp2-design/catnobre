package com.th.thtestes

import com.th.thtestes.domain.shortcut.CompanionManager
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertTrue
import org.junit.Test
import java.io.File
import java.io.FileInputStream
import java.security.KeyStore
import java.security.PrivateKey
import java.security.cert.X509Certificate
import java.util.zip.ZipFile

class CompanionApkVerificationTest {

    @Test
    fun testCompanionPackageNameSanitization() {
        val target = "com.unitv.app-tv"
        val expected = "com.th.thtestes.shortcut.pkg_com_unitv_app_tv"
        assertEquals(expected, CompanionManager.getCompanionPackageName(target))
    }

    @Test
    fun testFormatShortcutLabelWithPrefix() {
        assertEquals("ClonaTela - UniTV", com.th.thtestes.domain.ShortcutHelper.formatShortcutLabel("UniTV", "com.unitv.app"))
        assertEquals("ClonaTela - UniTV", com.th.thtestes.domain.ShortcutHelper.formatShortcutLabel("ClonaTela - UniTV", "com.unitv.app"))
        assertEquals("ClonaTela - com.unitv.app", com.th.thtestes.domain.ShortcutHelper.formatShortcutLabel("", "com.unitv.app"))
    }

    @Test
    fun testCompanionKeystoreValidity() {
        val keystoreFile = File("src/main/assets/companion/companion.p12")
        assertTrue("Arquivo de keystore deve existir em assets", keystoreFile.exists())

        val keyStore = KeyStore.getInstance("PKCS12")
        FileInputStream(keystoreFile).use { input ->
            keyStore.load(input, "clonatela".toCharArray())
        }

        val privateKey = keyStore.getKey("clonatela_shortcut", "clonatela".toCharArray()) as? PrivateKey
        val cert = keyStore.getCertificate("clonatela_shortcut") as? X509Certificate

        assertNotNull("PrivateKey deve ser carregada com sucesso", privateKey)
        assertNotNull("Certificado X509 deve ser carregado com sucesso", cert)
        assertEquals("RSA", privateKey?.algorithm)
    }

    @Test
    fun testCompanionDerKeysValidity() {
        val pk8File = File("src/main/assets/companion/companion.pk8")
        val crtFile = File("src/main/assets/companion/companion.crt")
        assertTrue("Arquivo companion.pk8 deve existir em assets", pk8File.exists())
        assertTrue("Arquivo companion.crt deve existir em assets", crtFile.exists())

        val keyBytes = pk8File.readBytes()
        val certBytes = crtFile.readBytes()

        val keySpec = java.security.spec.PKCS8EncodedKeySpec(keyBytes)
        val keyFactory = java.security.KeyFactory.getInstance("RSA")
        val privateKey = keyFactory.generatePrivate(keySpec)

        val certFactory = java.security.cert.CertificateFactory.getInstance("X.509")
        val cert = certFactory.generateCertificate(java.io.ByteArrayInputStream(certBytes)) as? X509Certificate

        assertNotNull("PrivateKey DER deve ser carregada com sucesso", privateKey)
        assertNotNull("Certificado X509 DER deve ser carregado com sucesso", cert)
        assertEquals("RSA", privateKey.algorithm)

        // Validar correspondência entre a chave pública do certificado e a chave privada
        val rsaPrivate = privateKey as java.security.interfaces.RSAPrivateKey
        val rsaPublic = cert?.publicKey as java.security.interfaces.RSAPublicKey
        assertEquals("Modulus da chave privada e pública devem ser idênticos", rsaPrivate.modulus, rsaPublic.modulus)
    }

    @Test
    fun testParityBetweenDerAndKeystore() {
        val keystoreFile = File("src/main/assets/companion/companion.p12")
        val keyStore = KeyStore.getInstance("PKCS12")
        FileInputStream(keystoreFile).use { input ->
            keyStore.load(input, "clonatela".toCharArray())
        }
        val ksKey = keyStore.getKey("clonatela_shortcut", "clonatela".toCharArray()) as java.security.interfaces.RSAPrivateKey
        val ksCert = keyStore.getCertificate("clonatela_shortcut") as X509Certificate

        val pk8File = File("src/main/assets/companion/companion.pk8")
        val crtFile = File("src/main/assets/companion/companion.crt")

        val keyFactory = java.security.KeyFactory.getInstance("RSA")
        val derKey = keyFactory.generatePrivate(java.security.spec.PKCS8EncodedKeySpec(pk8File.readBytes())) as java.security.interfaces.RSAPrivateKey

        val certFactory = java.security.cert.CertificateFactory.getInstance("X.509")
        val derCert = certFactory.generateCertificate(java.io.ByteArrayInputStream(crtFile.readBytes())) as X509Certificate

        assertEquals("Modulus das chaves do Keystore e do DER devem ser exatamente iguais", ksKey.modulus, derKey.modulus)
        assertEquals("Subject dos certificados deve ser idêntico", ksCert.subjectDN.name, derCert.subjectDN.name)
        assertEquals("Serial number dos certificados deve ser idêntico", ksCert.serialNumber, derCert.serialNumber)
    }

    @Test
    fun testTemplateApkStructure() {
        val templateFile = File("src/main/assets/companion/template.apk")
        assertTrue("Template APK deve existir em assets", templateFile.exists())

        ZipFile(templateFile).use { zip ->
            assertNotNull("AndroidManifest.xml deve existir no template", zip.getEntry("AndroidManifest.xml"))
            assertNotNull("classes.dex deve existir no template", zip.getEntry("classes.dex"))
            assertNotNull("resources.arsc deve existir no template", zip.getEntry("resources.arsc"))
            assertNotNull("res/Cx.png deve existir no template", zip.getEntry("res/Cx.png"))
            assertNotNull("res/ph.png deve existir no template", zip.getEntry("res/ph.png"))
        }
    }
}
