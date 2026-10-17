package com.th.thtestes.domain.shortcut

import android.content.Context
import android.graphics.Bitmap
import android.graphics.Canvas
import android.graphics.Color
import android.graphics.LinearGradient
import android.graphics.Paint
import android.graphics.PorterDuff
import android.graphics.PorterDuffColorFilter
import android.graphics.RectF
import android.graphics.Shader
import android.graphics.drawable.BitmapDrawable
import android.graphics.drawable.Drawable
import android.util.Log
import com.android.apksig.ApkSigner
import java.io.ByteArrayOutputStream
import java.io.File
import java.io.FileOutputStream
import java.nio.ByteBuffer
import java.nio.ByteOrder
import java.util.zip.ZipEntry
import java.util.zip.ZipInputStream
import java.util.zip.ZipOutputStream

object CompanionApkBuilder {
    private const val TAG = "CompanionApkBuilder"
    private const val TEMPLATE_ASSET_PATH = "companion/template.apk"
    private const val TEMPLATE_PACKAGE_NAME = "com.th.thtestes.shortcut.template"
    private const val TEMPLATE_PLACEHOLDER_TARGET = "com.th.thtestes.placeholder"
    private const val TEMPLATE_APP_NAME = "ClonaTela Shortcut"

    fun build(
        context: Context,
        targetPackage: String,
        appName: String,
        iconDrawable: Drawable?,
        outputFile: File
    ): Boolean {
        var unsignedApk: File? = null
        try {
            outputFile.parentFile?.mkdirs()
            unsignedApk = File(context.cacheDir, "unsigned_${System.currentTimeMillis()}.apk")

            val companionPackageName = CompanionManager.getCompanionPackageName(targetPackage)
            val iconPngBytes = drawableToPngBytes(context, iconDrawable)
            val bannerPngBytes = generateTvBannerPng(context, iconDrawable, appName)

            val axmlReplacements = mapOf(
                TEMPLATE_PACKAGE_NAME to companionPackageName,
                TEMPLATE_PLACEHOLDER_TARGET to targetPackage
            )
            val arscReplacements = mapOf(
                TEMPLATE_APP_NAME to appName
            )

            // 1. Process template APK and write modified contents to unsigned APK
            context.assets.open(TEMPLATE_ASSET_PATH).use { assetIn ->
                ZipInputStream(assetIn).use { zipIn ->
                    ZipOutputStream(FileOutputStream(unsignedApk)).use { zipOut ->
                        var entry: ZipEntry? = zipIn.nextEntry
                        while (entry != null) {
                            val name = entry.name
                            if (!name.startsWith("META-INF/")) {
                                val rawBytes = zipIn.readBytes()
                                val processedBytes = when (name) {
                                    "AndroidManifest.xml" -> modifyStringPoolUtf16(rawBytes, 8, axmlReplacements)
                                    "resources.arsc" -> modifyStringPoolUtf8(rawBytes, 12, arscReplacements)
                                    "res/Cx.png" -> iconPngBytes
                                    "res/ph.png" -> bannerPngBytes
                                    else -> rawBytes
                                }
                                val newEntry = ZipEntry(name).apply {
                                    time = entry.time
                                }
                                zipOut.putNextEntry(newEntry)
                                zipOut.write(processedBytes)
                                zipOut.closeEntry()
                            }
                            zipIn.closeEntry()
                            entry = zipIn.nextEntry
                        }
                    }
                }
            }

            // 2. Sign the APK using ApkSigner with V1 and V2 schemes
            val privateKey = CompanionKeyManager.getPrivateKey(context)
            val certificate = CompanionKeyManager.getCertificate(context)

            @Suppress("DEPRECATION")
            val signerConfig = ApkSigner.SignerConfig.Builder(
                "CLONATELA",
                privateKey,
                listOf(certificate)
            ).build()

            if (outputFile.exists()) {
                outputFile.delete()
            }

            ApkSigner.Builder(listOf(signerConfig))
                .setInputApk(unsignedApk)
                .setOutputApk(outputFile)
                .setV1SigningEnabled(true)
                .setV2SigningEnabled(true)
                .build()
                .sign()

            Log.i(TAG, "Companion APK gerado com sucesso em: ${outputFile.absolutePath} (${outputFile.length()} bytes)")
            return true
        } catch (e: Exception) {
            Log.e(TAG, "Erro ao gerar Companion APK para $targetPackage", e)
            if (outputFile.exists()) {
                outputFile.delete()
            }
            return false
        } finally {
            unsignedApk?.delete()
        }
    }

    private fun modifyStringPoolUtf16(
        data: ByteArray,
        spOffset: Int,
        replacements: Map<String, String>
    ): ByteArray {
        val inBuf = ByteBuffer.wrap(data).order(ByteOrder.LITTLE_ENDIAN)
        inBuf.position(spOffset)

        val chunkType = inBuf.short.toInt() and 0xFFFF
        val headerSize = inBuf.short.toInt() and 0xFFFF
        val oldChunkSize = inBuf.int
        val stringCount = inBuf.int
        val styleCount = inBuf.int
        val flags = inBuf.int
        val stringsStart = inBuf.int
        val stylesStart = inBuf.int

        val offsets = IntArray(stringCount)
        for (i in 0 until stringCount) {
            offsets[i] = inBuf.int
        }

        // Read strings
        val strings = ArrayList<String>(stringCount)
        for (off in offsets) {
            inBuf.position(spOffset + stringsStart + off)
            val u16Len = inBuf.short.toInt() and 0xFFFF
            val charCount = if ((u16Len and 0x8000) != 0) {
                val low = inBuf.short.toInt() and 0xFFFF
                ((u16Len and 0x7FFF) shl 16) or low
            } else {
                u16Len
            }
            val chars = CharArray(charCount)
            for (j in 0 until charCount) {
                chars[j] = inBuf.char
            }
            strings.add(String(chars))
        }

        // Build new string pool data
        val newStrData = ByteArrayOutputStream()
        val newOffsets = IntArray(stringCount)

        for (i in 0 until stringCount) {
            newOffsets[i] = newStrData.size()
            val originalStr = strings[i]
            val s = replacements[originalStr] ?: originalStr
            val len = s.length

            val lenBytes = ByteBuffer.allocate(if (len > 0x7FFF) 4 else 2).order(ByteOrder.LITTLE_ENDIAN)
            if (len > 0x7FFF) {
                lenBytes.putShort(((len ushr 16) or 0x8000).toShort())
                lenBytes.putShort((len and 0xFFFF).toShort())
            } else {
                lenBytes.putShort(len.toShort())
            }
            newStrData.write(lenBytes.array())

            for (c in s) {
                val charBuf = ByteBuffer.allocate(2).order(ByteOrder.LITTLE_ENDIAN)
                charBuf.putChar(c)
                newStrData.write(charBuf.array())
            }
            // Null terminator (2 bytes)
            newStrData.write(byteArrayOf(0, 0))
        }

        // 4-byte alignment
        while (newStrData.size() % 4 != 0) {
            newStrData.write(0)
        }

        val newStrBytes = newStrData.toByteArray()
        val newStringsStart = 28 + (stringCount * 4) + (styleCount * 4)
        val newChunkSize = newStringsStart + newStrBytes.size

        // Build string pool chunk
        val spOut = ByteBuffer.allocate(newChunkSize).order(ByteOrder.LITTLE_ENDIAN)
        spOut.putShort(chunkType.toShort())
        spOut.putShort(headerSize.toShort())
        spOut.putInt(newChunkSize)
        spOut.putInt(stringCount)
        spOut.putInt(styleCount)
        spOut.putInt(flags)
        spOut.putInt(newStringsStart)
        spOut.putInt(stylesStart)

        for (off in newOffsets) {
            spOut.putInt(off)
        }
        spOut.put(newStrBytes)

        val newSpBytes = spOut.array()
        val restOfAxml = data.copyOfRange(spOffset + oldChunkSize, data.size)
        val newFileSize = spOffset + newSpBytes.size + restOfAxml.size

        val outBuf = ByteBuffer.allocate(newFileSize).order(ByteOrder.LITTLE_ENDIAN)
        // File header
        outBuf.putShort(0x0003.toShort())
        outBuf.putShort(0x0008.toShort())
        outBuf.putInt(newFileSize)
        outBuf.put(newSpBytes)
        outBuf.put(restOfAxml)

        return outBuf.array()
    }

    private fun modifyStringPoolUtf8(
        data: ByteArray,
        spOffset: Int,
        replacements: Map<String, String>
    ): ByteArray {
        val inBuf = ByteBuffer.wrap(data).order(ByteOrder.LITTLE_ENDIAN)
        inBuf.position(spOffset)

        val chunkType = inBuf.short.toInt() and 0xFFFF
        val headerSize = inBuf.short.toInt() and 0xFFFF
        val oldChunkSize = inBuf.int
        val stringCount = inBuf.int
        val styleCount = inBuf.int
        val flags = inBuf.int
        val stringsStart = inBuf.int
        val stylesStart = inBuf.int

        val offsets = IntArray(stringCount)
        for (i in 0 until stringCount) {
            offsets[i] = inBuf.int
        }

        // Read strings
        val strings = ArrayList<String>(stringCount)
        for (off in offsets) {
            inBuf.position(spOffset + stringsStart + off)
            var u16Len = inBuf.get().toInt() and 0xFF
            if ((u16Len and 0x80) != 0) {
                u16Len = ((u16Len and 0x7F) shl 8) or (inBuf.get().toInt() and 0xFF)
            }
            var u8Len = inBuf.get().toInt() and 0xFF
            if ((u8Len and 0x80) != 0) {
                u8Len = ((u8Len and 0x7F) shl 8) or (inBuf.get().toInt() and 0xFF)
            }
            val strBytes = ByteArray(u8Len)
            inBuf.get(strBytes)
            inBuf.get() // null terminator
            strings.add(String(strBytes, Charsets.UTF_8))
        }

        // Build new string pool data
        val newStrData = ByteArrayOutputStream()
        val newOffsets = IntArray(stringCount)

        for (i in 0 until stringCount) {
            newOffsets[i] = newStrData.size()
            val originalStr = strings[i]
            val s = replacements[originalStr] ?: originalStr
            val u16Len = s.length
            val utf8Bytes = s.toByteArray(Charsets.UTF_8)
            val u8Len = utf8Bytes.size

            if (u16Len > 0x7F) {
                newStrData.write((u16Len ushr 8) or 0x80)
                newStrData.write(u16Len and 0xFF)
            } else {
                newStrData.write(u16Len)
            }

            if (u8Len > 0x7F) {
                newStrData.write((u8Len ushr 8) or 0x80)
                newStrData.write(u8Len and 0xFF)
            } else {
                newStrData.write(u8Len)
            }

            newStrData.write(utf8Bytes)
            newStrData.write(0) // null terminator
        }

        // 4-byte alignment
        while (newStrData.size() % 4 != 0) {
            newStrData.write(0)
        }

        val newStrBytes = newStrData.toByteArray()
        val newStringsStart = 28 + (stringCount * 4) + (styleCount * 4)
        val newChunkSize = newStringsStart + newStrBytes.size

        val spOut = ByteBuffer.allocate(newChunkSize).order(ByteOrder.LITTLE_ENDIAN)
        spOut.putShort(chunkType.toShort())
        spOut.putShort(headerSize.toShort())
        spOut.putInt(newChunkSize)
        spOut.putInt(stringCount)
        spOut.putInt(styleCount)
        spOut.putInt(flags)
        spOut.putInt(newStringsStart)
        spOut.putInt(stylesStart)

        for (off in newOffsets) {
            spOut.putInt(off)
        }
        spOut.put(newStrBytes)

        val newSpBytes = spOut.array()
        val restOfArsc = data.copyOfRange(spOffset + oldChunkSize, data.size)
        val newArscSize = spOffset + newSpBytes.size + restOfArsc.size

        val outBuf = ByteBuffer.allocate(newArscSize).order(ByteOrder.LITTLE_ENDIAN)
        // Copy ARSC header (12 bytes) updating total size
        outBuf.put(data.copyOfRange(0, 4))
        outBuf.putInt(newArscSize)
        outBuf.put(data.copyOfRange(8, 12))
        outBuf.put(newSpBytes)
        outBuf.put(restOfArsc)

        return outBuf.array()
    }

    private fun drawableToPngBytes(context: Context, drawable: Drawable?): ByteArray {
        val size = 192
        val bitmap = Bitmap.createBitmap(size, size, Bitmap.Config.ARGB_8888)
        val canvas = Canvas(bitmap)

        if (drawable != null) {
            drawable.setBounds(0, 0, size, size)
            drawable.draw(canvas)
        } else {
            // Default placeholder circle if no drawable provided
            val paint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
                color = Color.parseColor("#3B82F6")
            }
            canvas.drawCircle(size / 2f, size / 2f, size / 2f - 8, paint)
        }

        val baos = ByteArrayOutputStream()
        bitmap.compress(Bitmap.CompressFormat.PNG, 100, baos)
        bitmap.recycle()
        return baos.toByteArray()
    }

    private fun generateTvBannerPng(context: Context, icon: Drawable?, appName: String): ByteArray {
        // Standard Android TV Leanback Banner: 320x180 (16:9)
        val width = 320
        val height = 180
        val bitmap = Bitmap.createBitmap(width, height, Bitmap.Config.ARGB_8888)
        val canvas = Canvas(bitmap)

        // Gradient dark background
        val bgPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
            shader = LinearGradient(
                0f, 0f, width.toFloat(), height.toFloat(),
                Color.parseColor("#12141A"), Color.parseColor("#1E222D"),
                Shader.TileMode.CLAMP
            )
        }
        canvas.drawRect(0f, 0f, width.toFloat(), height.toFloat(), bgPaint)

        // Subtle rounded card outline
        val outlinePaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
            color = Color.parseColor("#2D3342")
            style = Paint.Style.STROKE
            strokeWidth = 2f
        }
        canvas.drawRoundRect(RectF(1f, 1f, width - 1f, height - 1f), 12f, 12f, outlinePaint)

        // Center app icon
        val iconSize = 88
        val iconLeft = (width - iconSize) / 2
        val iconTop = (height - iconSize) / 2 - 10

        if (icon != null) {
            icon.setBounds(iconLeft, iconTop, iconLeft + iconSize, iconTop + iconSize)
            icon.draw(canvas)
        } else {
            val paint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
                color = Color.parseColor("#3B82F6")
            }
            canvas.drawCircle(width / 2f, height / 2f - 10, iconSize / 2f, paint)
        }

        // Sleek small app title at bottom
        val textPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
            color = Color.WHITE
            textSize = 14f
            textAlign = Paint.Align.CENTER
            isFakeBoldText = true
        }
        val displayLabel = if (appName.length > 22) appName.take(20) + "…" else appName
        canvas.drawText(displayLabel, width / 2f, height - 18f, textPaint)

        val baos = ByteArrayOutputStream()
        bitmap.compress(Bitmap.CompressFormat.PNG, 100, baos)
        bitmap.recycle()
        return baos.toByteArray()
    }
}
