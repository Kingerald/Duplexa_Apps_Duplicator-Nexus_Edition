package com.duplexa.duplicator.nexus

import android.graphics.drawable.Drawable
import org.json.JSONObject
import java.util.Locale

/**
 * Core data models for Duplexa Apps Duplicator - Nexus Edition.
 * Enforces the D_V (Duplication_Version) standalone native APK architecture
 * with zero WebKit/WebView dependencies.
 */

/**
 * Represents a duplicated standalone Android application stored inside Duplexa's App Library.
 * Each instance has its own patched & signed standalone .apk file, badged D_V# icon,
 * and isolated native execution directory.
 */
data class DuplicatedApp(
    val id: String,
    val originalPackageName: String,
    val duplicatedPackageName: String,
    val originalAppName: String,
    val duplicationVersion: Int,
    val displayName: String = "$originalAppName D_V$duplicationVersion",
    val signedApkPath: String,
    val badgedIconPath: String,
    val isolatedDataDirPath: String,
    val versionName: String,
    val versionCode: Long,
    val apkSizeBytes: Long,
    val createdAtTimestamp: Long
) {
    /**
     * Formatted Duplication Version badge tag (e.g., "D_V1", "D_V2").
     */
    val dvBadgeLabel: String
        get() = "D_V$duplicationVersion"

    /**
     * Human-readable file size of the generated standalone APK (e.g., "24.6 MB").
     */
    fun getFormattedApkSize(): String {
        if (apkSizeBytes <= 0L) return "Unknown Size"
        val kb = apkSizeBytes / 1024.0
        val mb = kb / 1024.0
        return if (mb >= 1.0) {
            String.format(Locale.US, "%.2f MB", mb)
        } else {
            String.format(Locale.US, "%.0f KB", kb)
        }
    }

    /**
     * Suggested filename when exporting/downloading the standalone APK to device Downloads.
     */
    fun getExportApkFileName(): String {
        val sanitizedBase = originalAppName
            .replace(Regex("[^a-zA-Z0-9._-]"), "_")
            .trim('_')
            .ifEmpty { "App" }
        return "${sanitizedBase}_D_V${duplicationVersion}.apk"
    }

    /**
     * Serializes this DuplicatedApp metadata into a JSONObject for persistent library storage.
     */
    fun toJson(): JSONObject {
        return JSONObject().apply {
            put("id", id)
            put("originalPackageName", originalPackageName)
            put("duplicatedPackageName", duplicatedPackageName)
            put("originalAppName", originalAppName)
            put("duplicationVersion", duplicationVersion)
            put("displayName", displayName)
            put("signedApkPath", signedApkPath)
            put("badgedIconPath", badgedIconPath)
            put("isolatedDataDirPath", isolatedDataDirPath)
            put("versionName", versionName)
            put("versionCode", versionCode)
            put("apkSizeBytes", apkSizeBytes)
            put("createdAtTimestamp", createdAtTimestamp)
        }
    }

    companion object {
        /**
         * Deserializes a DuplicatedApp instance from persistent JSON storage.
         */
        fun fromJson(json: JSONObject): DuplicatedApp {
            val origName = json.optString("originalAppName", "App")
            val dvNumber = json.optInt("duplicationVersion", 1).coerceAtLeast(1)
            val fallbackDisplayName = "$origName D_V$dvNumber"

            return DuplicatedApp(
                id = json.getString("id"),
                originalPackageName = json.getString("originalPackageName"),
                duplicatedPackageName = json.optString(
                    "duplicatedPackageName",
                    "${json.getString("originalPackageName")}.dv$dvNumber"
                ),
                originalAppName = origName,
                duplicationVersion = dvNumber,
                displayName = json.optString("displayName", fallbackDisplayName),
                signedApkPath = json.optString("signedApkPath", ""),
                badgedIconPath = json.optString("badgedIconPath", ""),
                isolatedDataDirPath = json.optString("isolatedDataDirPath", ""),
                versionName = json.optString("versionName", "1.0"),
                versionCode = json.optLong("versionCode", 1L),
                apkSizeBytes = json.optLong("apkSizeBytes", 0L),
                createdAtTimestamp = json.optLong("createdAtTimestamp", System.currentTimeMillis())
            )
        }
    }
}

/**
 * Represents an installed Android application discovered on the user's device
 * and displayed in the App Selection interface for duplication.
 */
data class InstalledAppInfo(
    val appName: String,
    val packageName: String,
    val sourceApkPath: String,
    val versionName: String,
    val versionCode: Long,
    val apkSizeBytes: Long,
    val isSystemApp: Boolean,
    val existingDuplicateCount: Int,
    val nextDuplicationVersion: Int = existingDuplicateCount + 1,
    val iconDrawable: Drawable? = null
) {
    /**
     * Preview of the exact D_V badge that will be assigned if the user duplicates this app next.
     */
    val nextDvBadgeLabel: String
        get() = "D_V$nextDuplicationVersion"

    /**
     * Preview of the full duplicated app display name (e.g., "Telegram D_V2").
     */
    val nextDuplicateDisplayName: String
        get() = "$appName D_V$nextDuplicationVersion"

    fun getFormattedApkSize(): String {
        if (apkSizeBytes <= 0L) return ""
        val mb = apkSizeBytes / (1024.0 * 1024.0)
        return String.format(Locale.US, "%.1f MB", mb)
    }
}

/**
 * Represents the 3 primary context menu actions available when a user clicks the options
 * menu on a duplicated app inside Duplexa's App Library.
 */
enum class DuplicateMenuAction {
    /**
     * Opens the duplicated app directly in full-screen standalone native mode.
     */
    OPEN,

    /**
     * Pops up the Install / Download options so the user can install the standalone D_V# APK
     * via the device's system chooser (Package Installer, Chrome, File Manager, etc.)
     * or export it directly to the Downloads folder.
     */
    INSTALL_OR_DOWNLOAD,

    /**
     * Completely removes the duplicated app from Duplexa's library and deletes its
     * generated standalone APK and isolated sandbox directory.
     */
    DELETE
}

/**
 * Sub-options when the user selects INSTALL_OR_DOWNLOAD from the duplicated app menu.
 */
enum class ApkDeliveryMethod {
    /**
     * Uses FileProvider + Intent.createChooser to present all available APK installers,
     * browsers, and file handlers on the user's phone.
     */
    SYSTEM_CHOOSER_INSTALL,

    /**
     * Copies the signed standalone [AppName]_D_V[N].apk into the device's public
     * Downloads/Duplexa_APKs/ directory for manual installation anytime.
     */
    DOWNLOAD_TO_STORAGE
}
