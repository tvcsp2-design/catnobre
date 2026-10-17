package com.th.thtestes.domain

import android.graphics.drawable.Drawable

data class ClonedApp(
    val packageName: String,
    val name: String,
    val icon: Drawable?,
    val isRunning: Boolean = false,
    val userId: Int = 0,
    val personaId: String? = null,
    val profileName: String = "global",
    val hasCustomOverrides: Boolean = false
)

data class BoxUserProfile(
    val userId: Int,
    val name: String,
    val isDefault: Boolean,
    val installedAppsCount: Int = 0,
    val createdAt: Long = System.currentTimeMillis()
)

data class HostApp(
    val packageName: String,
    val name: String,
    val icon: Drawable?,
    val sourceDir: String,
    val isCloned: Boolean = false,
    val isKnown: Boolean = false
)

data class FingerprintProfileData(
    val androidId: String = "",
    val serial: String = "",
    val imei: String = "",
    val cpuSerial: String = "",
    val wifiMac: String = "",
    val ethernetMac: String = "",
    val bluetoothMac: String = "",
    val wifiSsid: String = "",
    val buildBrand: String = "",
    val buildManufacturer: String = "",
    val buildModel: String = "",
    val buildDevice: String = "",
    val buildFingerprint: String = "",
    val advertisingId: String = "",
    val appSetId: String = "",
    val personaId: String = "",
    val isSafeMode: Boolean = false
) {
    fun toMap(): Map<String, String> = mapOf(
        "androidId" to androidId,
        "serial" to serial,
        "imei" to imei,
        "cpuSerial" to cpuSerial,
        "wifiMac" to wifiMac,
        "ethernetMac" to ethernetMac,
        "bluetoothMac" to bluetoothMac,
        "wifiSsid" to wifiSsid,
        "buildBrand" to buildBrand,
        "buildManufacturer" to buildManufacturer,
        "buildModel" to buildModel,
        "buildDevice" to buildDevice,
        "buildFingerprint" to buildFingerprint,
        "advertisingId" to advertisingId,
        "appSetId" to appSetId,
        "personaId" to personaId,
        "isSafeMode" to isSafeMode.toString()
    ).filterValues { it.isNotBlank() }

    companion object {
        fun fromIdentity(id: top.niunaijun.blackbox.compat.BoxSpaceIdentityProfile.Identity): FingerprintProfileData {
            return FingerprintProfileData(
                androidId = id.androidId ?: "",
                serial = id.serial ?: "",
                imei = id.imei ?: "",
                cpuSerial = id.cpuSerial ?: "",
                wifiMac = id.wifiMac ?: "",
                ethernetMac = id.ethernetMac ?: "",
                bluetoothMac = id.bluetoothMac ?: "",
                wifiSsid = id.wifiSsid ?: "",
                buildBrand = id.buildBrand ?: "",
                buildManufacturer = id.buildManufacturer ?: "",
                buildModel = id.buildModel ?: "",
                buildDevice = id.buildDevice ?: "",
                buildFingerprint = id.buildFingerprint ?: "",
                advertisingId = id.advertisingId ?: "",
                appSetId = id.appSetId ?: "",
                personaId = id.personaId ?: "",
                isSafeMode = id.isSafeMode
            )
        }
    }
}

