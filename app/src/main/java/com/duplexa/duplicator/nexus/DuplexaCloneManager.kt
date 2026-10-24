package com.duplexa.duplicator.nexus

import android.content.ContentValues
import android.content.Context
import android.content.Intent
import android.content.pm.ApplicationInfo
import android.content.pm.PackageManager
import android.graphics.Bitmap
import android.graphics.BitmapFactory
import android.graphics.Canvas
import android.graphics.Color
import android.graphics.Paint
import android.graphics.RectF
import android.graphics.Typeface
import android.graphics.drawable.BitmapDrawable
import android.graphics.drawable.Drawable
import android.os.Build
import android.os.Environment
import android.provider.MediaStore
import androidx.core.content.FileProvider
import androidx.core.content.pm.ShortcutInfoCompat
import androidx.core.content.pm.ShortcutManagerCompat
import androidx.core.graphics.drawable.IconCompat
import org.json.JSONArray
import java.io.File
import java.io.FileOutputStream
import java.util.UUID

/**
 * Core Duplication & Library Management Engine for Duplexa Apps Duplicator - Nexus Edition.
 *
 * Responsibilities:
 * 1. Scans installed Android applications on the user's device.
 * 2. Automatically increments D_V# (D_V1, D_V2, D_V3...) version tags per package.
 * 3. Renders dynamic D_V# badged app icons (proving duplicate status visually).
 * 4. Orchestrates ApkBinaryPatcher to produce signed, standalone D_V# .apk files.
 * 5. Executes the 3-Option Cloned App Menu:
 *    - OPEN (Full-screen standalone native execution)
 *    - INSTALL / DOWNLOAD APK (System multi-app installer chooser OR Downloads export)
 *    - DELETE (Full removal from Duplexa Library & storage cleanup)
 */
class DuplexaCloneManager(private val context: Context) {

    companion object {
        private const val REGISTRY_FILE_NAME = "duplexa_library_registry.json"
        private const val APK_VAULT_DIR = "duplicated_apks"
        private const val BADGED_ICONS_DIR = "badged_icons"
        private const val VESSELS_SANDBOX_DIR = "vessels"
        private const val PUBLIC_DOWNLOADS_SUBFOLDER = "Duplexa_APKs"
    }

    private val appContext: Context = context.applicationContext
    private val packageManager: PackageManager = appContext.packageManager

    private val registryFile: File
        get() = File(appContext.filesDir, REGISTRY_FILE_NAME)

    private val apkVaultDir: File
        get() = File(appContext.filesDir, APK_VAULT_DIR).apply { mkdirs() }

    private val badgedIconsDir: File
        get() = File(appContext.filesDir, BADGED_ICONS_DIR).apply { mkdirs() }

    private val vesselsSandboxDir: File
        get() = File(appContext.filesDir, VESSELS_SANDBOX_DIR).apply { mkdirs() }

    /**
     * Returns all duplicated standalone D_V# apps currently registered in Duplexa's App Library,
     * sorted newest first.
     */
    @Synchronized
    fun getDuplicatedAppsLibrary(): List<DuplicatedApp> {
        if (!registryFile.exists()) return emptyList()
        return try {
            val rawJson = registryFile.readText()
            val array = JSONArray(rawJson)
            val result = mutableListOf<DuplicatedApp>()
            for (i in 0 until array.length()) {
                val item = DuplicatedApp.fromJson(array.getJSONObject(i))
                result.add(item)
            }
            result.sortedByDescending { it.createdAtTimestamp }
        } catch (_: Exception) {
            emptyList()
        }
    }

    /**
     * Finds a specific DuplicatedApp in Duplexa's library by its unique ID.
     */
    fun getDuplicatedAppById(id: String): DuplicatedApp? {
        return getDuplicatedAppsLibrary().firstOrNull { it.id == id }
    }

    /**
     * Calculates the next sequential Duplication_Version (D_V1, D_V2, D_V3...)
     * for a given original package name.
     */
    fun getNextDuplicationVersion(originalPackageName: String): Int {
        val existingVersions = getDuplicatedAppsLibrary()
            .filter { it.originalPackageName == originalPackageName }
            .map { it.duplicationVersion }
        val maxExisting = existingVersions.maxOrNull() ?: 0
        return maxExisting + 1
    }

    /**
     * Scans all launchable applications installed on the device for the App Selection interface.
     * Excludes Duplexa itself and attaches the existing duplicate count and next D_V# number.
     */
    fun getInstalledDeviceApps(includeSystemApps: Boolean = false): List<InstalledAppInfo> {
        val libraryByPackage = getDuplicatedAppsLibrary().groupBy { it.originalPackageName }
        val mainIntent = Intent(Intent.ACTION_MAIN, null).apply {
            addCategory(Intent.CATEGORY_LAUNCHER)
        }

        val resolveInfos = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
            packageManager.queryIntentActivities(
                mainIntent,
                PackageManager.ResolveInfoFlags.of(0L)
            )
        } else {
            @Suppress("DEPRECATION")
            packageManager.queryIntentActivities(mainIntent, 0)
        }

        val seenPackages = mutableSetOf<String>()
        val installedList = mutableListOf<InstalledAppInfo>()

        for (resolveInfo in resolveInfos) {
            val activityInfo = resolveInfo.activityInfo ?: continue
            val pkgName = activityInfo.packageName ?: continue

            if (pkgName == appContext.packageName || !seenPackages.add(pkgName)) {
                continue
            }

            val appInfo = activityInfo.applicationInfo ?: continue
            val isSystem = (appInfo.flags and ApplicationInfo.FLAG_SYSTEM) != 0 &&
                (appInfo.flags and ApplicationInfo.FLAG_UPDATED_SYSTEM_APP) == 0

            if (isSystem && !includeSystemApps) {
                continue
            }

            val sourceApkPath = appInfo.sourceDir ?: continue
            val sourceFile = File(sourceApkPath)
            if (!sourceFile.exists()) continue

            val appLabel = appInfo.loadLabel(packageManager)?.toString()?.trim()
                ?.takeIf { it.isNotEmpty() } ?: pkgName

            val pkgInfo = try {
                if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
                    packageManager.getPackageInfo(
                        pkgName,
                        PackageManager.PackageInfoFlags.of(0L)
                    )
                } else {
                    @Suppress("DEPRECATION")
                    packageManager.getPackageInfo(pkgName, 0)
                }
            } catch (_: Exception) {
                null
            }

            val versionName = pkgInfo?.versionName ?: "1.0"
            val versionCode = if (pkgInfo != null) {
                if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.P) {
                    pkgInfo.longVersionCode
                } else {
                    @Suppress("DEPRECATION")
                    pkgInfo.versionCode.toLong()
                }
            } else {
                1L
            }

            val existingDuplicates = libraryByPackage[pkgName] ?: emptyList()
            val maxDv = existingDuplicates.maxOfOrNull { it.duplicationVersion } ?: 0

            installedList.add(
                InstalledAppInfo(
                    appName = appLabel,
                    packageName = pkgName,
                    sourceApkPath = sourceApkPath,
                    versionName = versionName,
                    versionCode = versionCode,
                    apkSizeBytes = sourceFile.length(),
                    isSystemApp = isSystem,
                    existingDuplicateCount = existingDuplicates.size,
                    nextDuplicationVersion = maxDv + 1,
                    iconDrawable = try {
                        appInfo.loadIcon(packageManager)
                    } catch (_: Exception) {
                        null
                    }
                )
            )
        }

        return installedList.sortedBy { it.appName.lowercase() }
    }

    /**
     * Forges a complete, signed standalone D_V# duplicate of the selected installed application.
     *
     * Pipeline:
     * 1. Assigns unique ID and next sequential D_V# version number.
     * 2. Creates isolated native sandbox directory (`files/vessels/[id]/`).
     * 3. Generates badged icon PNG stamped with `D_V#`.
     * 4. Extracts base APK, patches binary AndroidManifest package identity, aligns,
     *    and signs the standalone `.apk` with V1/V2 signatures.
     * 5. Saves the DuplicatedApp record in Duplexa's App Library.
     */
    fun createDuplicateApp(
        installedApp: InstalledAppInfo,
        onStepUpdate: ((String) -> Unit)? = null
    ): DuplicatedApp {
        val dvNumber = getNextDuplicationVersion(installedApp.packageName)
        val duplicateId = UUID.randomUUID().toString()
        val displayName = appContext.getString(
            R.string.dv_app_display_format,
            installedApp.appName,
            dvNumber
        )
        val dvBadgeText = appContext.getString(R.string.dv_badge_format, dvNumber)

        val duplicatedPkgName = ApkBinaryPatcher.deriveAlignedDuplicatePackageName(
            originalPackageName = installedApp.packageName,
            duplicationVersion = dvNumber
        )

        // 1. Provision isolated native vessel directory for standalone execution inside Duplexa
        val vesselDir = File(vesselsSandboxDir, "${installedApp.packageName}_D_V${dvNumber}_$duplicateId").apply {
            mkdirs()
            File(this, "files").mkdirs()
            File(this, "cache").mkdirs()
            File(this, "databases").mkdirs()
            File(this, "shared_prefs").mkdirs()
            File(this, "code_cache").mkdirs()
        }

        // 2. Render & save dynamic D_V# badged icon PNG
        onStepUpdate?.invoke(appContext.getString(R.string.status_step_patching, dvNumber))
        val badgedIconFile = File(badgedIconsDir, "icon_${installedApp.packageName}_D_V${dvNumber}_$duplicateId.png")
        createBadgedIconPng(
            originalPkgName = installedApp.packageName,
            fallbackDrawable = installedApp.iconDrawable,
            dvBadgeText = dvBadgeText,
            outputFile = badgedIconFile
        )

        // 3. Extract, patch binary manifest, 4-byte align, and V1/V2 sign standalone APK
        val sanitizedPkg = installedApp.packageName.replace('.', '_')
        val outputApkFile = File(apkVaultDir, "${sanitizedPkg}_D_V${dvNumber}_${duplicateId.take(8)}.apk")

        ApkBinaryPatcher.forgeStandaloneDuplicateApk(
            context = appContext,
            sourceApkFile = File(installedApp.sourceApkPath),
            originalPackageName = installedApp.packageName,
            duplicatedPackageName = duplicatedPkgName,
            outputSignedApkFile = outputApkFile,
            onStepUpdate = onStepUpdate
        )

        val duplicatedApp = DuplicatedApp(
            id = duplicateId,
            originalPackageName = installedApp.packageName,
            duplicatedPackageName = duplicatedPkgName,
            originalAppName = installedApp.appName,
            duplicationVersion = dvNumber,
            displayName = displayName,
            signedApkPath = outputApkFile.absolutePath,
            badgedIconPath = badgedIconFile.absolutePath,
            isolatedDataDirPath = vesselDir.absolutePath,
            versionName = installedApp.versionName,
            versionCode = installedApp.versionCode,
            apkSizeBytes = outputApkFile.length(),
            createdAtTimestamp = System.currentTimeMillis()
        )

        saveDuplicatedAppToRegistry(duplicatedApp)
        return duplicatedApp
    }

    /**
     * Renders the original app icon onto a 192x192 bitmap and stamps a high-contrast
     * Trickster Gold & Obsidian "D_V#" badge in the bottom-right corner.
     */
    private fun createBadgedIconPng(
        originalPkgName: String,
        fallbackDrawable: Drawable?,
        dvBadgeText: String,
        outputFile: File
    ) {
        val sizePx = 192
        val bitmap = Bitmap.createBitmap(sizePx, sizePx, Bitmap.Config.ARGB_8888)
        val canvas = Canvas(bitmap)

        val sourceDrawable = fallbackDrawable ?: try {
            packageManager.getApplicationIcon(originalPkgName)
        } catch (_: Exception) {
            null
        }

        if (sourceDrawable != null) {
            sourceDrawable.setBounds(0, 0, sizePx, sizePx)
            sourceDrawable.draw(canvas)
        } else {
            val bgPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
                color = Color.parseColor("#131B18")
            }
            canvas.drawRoundRect(
                RectF(0f, 0f, sizePx.toFloat(), sizePx.toFloat()),
                36f,
                36f,
                bgPaint
            )
        }

        // Draw D_V# pill badge in bottom-right corner
        val textPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
            color = Color.parseColor("#06281E")
            textSize = 34f
            typeface = Typeface.create(Typeface.MONOSPACE, Typeface.BOLD)
            textAlign = Paint.Align.CENTER
        }

        val textWidth = textPaint.measureText(dvBadgeText)
        val padH = 18f
        val badgeHeight = 52f
        val badgeWidth = (textWidth + padH * 2).coerceAtLeast(88f)

        val right = sizePx - 6f
        val bottom = sizePx - 6f
        val left = right - badgeWidth
        val top = bottom - badgeHeight
        val pillRect = RectF(left, top, right, bottom)

        // Outer Obsidian border for separation against any app icon background
        val borderPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
            color = Color.parseColor("#0B0F0E")
            style = Paint.Style.FILL
        }
        val borderRect = RectF(left - 4f, top - 4f, right + 2f, bottom + 2f)
        canvas.drawRoundRect(borderRect, 18f, 18f, borderPaint)

        // Inner Trickster Gold badge fill
        val fillPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
            color = Color.parseColor("#D4AF37")
            style = Paint.Style.FILL
        }
        canvas.drawRoundRect(pillRect, 14f, 14f, fillPaint)

        val fontMetrics = textPaint.fontMetrics
        val textCenterY = pillRect.centerY() - (fontMetrics.ascent + fontMetrics.descent) / 2f
        canvas.drawText(dvBadgeText, pillRect.centerX(), textCenterY, textPaint)

        FileOutputStream(outputFile).use { out ->
            bitmap.compress(Bitmap.CompressFormat.PNG, 100, out)
        }
    }

    /**
     * Loads the badged D_V# icon Drawable for display in Duplexa's App Library.
     */
    fun loadBadgedIconDrawable(duplicatedApp: DuplicatedApp): Drawable? {
        val iconFile = File(duplicatedApp.badgedIconPath)
        if (iconFile.exists()) {
            val bmp = BitmapFactory.decodeFile(iconFile.absolutePath)
            if (bmp != null) {
                return BitmapDrawable(appContext.resources, bmp)
            }
        }
        return try {
            packageManager.getApplicationIcon(duplicatedApp.originalPackageName)
        } catch (_: Exception) {
            null
        }
    }

    // =========================================================================
    // CLONED APP 3-OPTION CONTEXT MENU ACTIONS
    // 1. OPEN (Full-screen standalone native execution)
    // 2. INSTALL / DOWNLOAD APK (System Chooser OR Downloads Folder Export)
    // 3. DELETE (Complete removal from Duplexa Library & file cleanup)
    // =========================================================================

    /**
     * MENU OPTION 1: OPEN
     * Opens the duplicated app in 100% full-screen standalone native mode via CloneSandboxActivity.
     */
    fun openDuplicatedAppInDuplexa(context: Context, duplicatedApp: DuplicatedApp) {
        val intent = Intent(context, CloneSandboxActivity::class.java).apply {
            putExtra(CloneSandboxActivity.EXTRA_DUPLICATE_ID, duplicatedApp.id)
            addFlags(Intent.FLAG_ACTIVITY_NEW_DOCUMENT)
            addFlags(Intent.FLAG_ACTIVITY_MULTIPLE_TASK)
        }
        context.startActivity(intent)
    }

    /**
     * MENU OPTION 2A: INSTALL VIA SYSTEM / APP CHOOSER
     * Does NOT force a single silent install. Instead, uses FileProvider + Intent.createChooser
     * to pop up all available options on the user's device (System Package Installer, Chrome,
     * File Manager, third-party APK installers) so the user can choose how to install the APK.
     */
    fun launchSystemInstallChooser(context: Context, duplicatedApp: DuplicatedApp): Boolean {
        val apkFile = File(duplicatedApp.signedApkPath)
        if (!apkFile.exists()) return false

        val authority = "${appContext.packageName}.fileprovider"
        val apkUri = FileProvider.getUriForFile(appContext, authority, apkFile)

        val viewInstallIntent = Intent(Intent.ACTION_VIEW).apply {
            setDataAndType(apkUri, "application/vnd.android.package-archive")
            addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION)
            addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
        }

        val shareApkIntent = Intent(Intent.ACTION_SEND).apply {
            type = "application/vnd.android.package-archive"
            putExtra(Intent.EXTRA_STREAM, apkUri)
            addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION)
        }

        val chooserTitle = context.getString(
            R.string.chooser_install_apk_prompt,
            duplicatedApp.displayName
        )

        val chooserIntent = Intent.createChooser(viewInstallIntent, chooserTitle).apply {
            putExtra(Intent.EXTRA_INITIAL_INTENTS, arrayOf(shareApkIntent))
            addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
        }

        context.startActivity(chooserIntent)
        return true
    }

    /**
     * MENU OPTION 2B: DOWNLOAD / EXPORT STANDALONE APK TO DEVICE STORAGE
     * Saves the signed standalone `[AppName]_D_V[N].apk` directly into the device's public
     * `Downloads/Duplexa_APKs/` folder so the user can also install it anytime from their
     * phone's File Manager or Chrome Downloads.
     */
    fun exportStandaloneApkToDownloads(duplicatedApp: DuplicatedApp): String? {
        val sourceApk = File(duplicatedApp.signedApkPath)
        if (!sourceApk.exists()) return null

        val fileName = duplicatedApp.getExportApkFileName()

        return try {
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
                val resolver = appContext.contentResolver
                val contentValues = ContentValues().apply {
                    put(MediaStore.Downloads.DISPLAY_NAME, fileName)
                    put(MediaStore.Downloads.MIME_TYPE, "application/vnd.android.package-archive")
                    put(
                        MediaStore.Downloads.RELATIVE_PATH,
                        "${Environment.DIRECTORY_DOWNLOADS}/$PUBLIC_DOWNLOADS_SUBFOLDER"
                    )
                }
                val uri = resolver.insert(MediaStore.Downloads.EXTERNAL_CONTENT_URI, contentValues)
                    ?: return null
                resolver.openOutputStream(uri)?.use { outStream ->
                    sourceApk.inputStream().use { inStream ->
                        inStream.copyTo(outStream)
                    }
                }
                fileName
            } else {
                @Suppress("DEPRECATION")
                val downloadsDir = File(
                    Environment.getExternalStoragePublicDirectory(Environment.DIRECTORY_DOWNLOADS),
                    PUBLIC_DOWNLOADS_SUBFOLDER
                ).apply { mkdirs() }
                val targetFile = File(downloadsDir, fileName)
                sourceApk.copyTo(targetFile, overwrite = true)
                targetFile.name
            }
        } catch (_: Exception) {
            null
        }
    }

    /**
     * MENU OPTION 3: DELETE
     * Completely removes the duplicated app from Duplexa's App Library, deletes its
     * generated standalone .apk file, removes its badged icon, and wipes its isolated sandbox.
     */
    @Synchronized
    fun deleteDuplicatedApp(duplicateId: String): Boolean {
        val currentList = getDuplicatedAppsLibrary().toMutableList()
        val target = currentList.firstOrNull { it.id == duplicateId } ?: return false

        // Remove signed standalone APK
        val apkFile = File(target.signedApkPath)
        if (apkFile.exists()) {
            apkFile.delete()
        }

        // Remove badged D_V# icon PNG
        val iconFile = File(target.badgedIconPath)
        if (iconFile.exists()) {
            iconFile.delete()
        }

        // Recursively wipe isolated native vessel directory
        val vesselDir = File(target.isolatedDataDirPath)
        if (vesselDir.exists()) {
            vesselDir.deleteRecursively()
        }

        currentList.removeAll { it.id == duplicateId }
        persistLibraryList(currentList)
        return true
    }

    /**
     * Optional helper to pin a direct home-screen shortcut with the badged D_V# icon.
     */
    fun pinShortcutToHomeScreen(duplicatedApp: DuplicatedApp): Boolean {
        if (!ShortcutManagerCompat.isRequestPinShortcutSupported(appContext)) {
            return false
        }

        val iconFile = File(duplicatedApp.badgedIconPath)
        val iconCompat = if (iconFile.exists()) {
            val bmp = BitmapFactory.decodeFile(iconFile.absolutePath)
            if (bmp != null) IconCompat.createWithBitmap(bmp)
            else IconCompat.createWithResource(appContext, R.drawable.ic_duplexa_logo_placeholder)
        } else {
            IconCompat.createWithResource(appContext, R.drawable.ic_duplexa_logo_placeholder)
        }

        val launchIntent = Intent(appContext, CloneLauncherActivity::class.java).apply {
            action = "com.duplexa.duplicator.nexus.ACTION_LAUNCH_DUPLICATE"
            putExtra(CloneSandboxActivity.EXTRA_DUPLICATE_ID, duplicatedApp.id)
        }

        val shortcutInfo = ShortcutInfoCompat.Builder(appContext, "duplexa_dv_${duplicatedApp.id}")
            .setShortLabel(duplicatedApp.displayName)
            .setLongLabel(duplicatedApp.displayName)
            .setIcon(iconCompat)
            .setIntent(launchIntent)
            .build()

        return ShortcutManagerCompat.requestPinShortcut(appContext, shortcutInfo, null)
    }

    @Synchronized
    private fun saveDuplicatedAppToRegistry(newApp: DuplicatedApp) {
        val updated = getDuplicatedAppsLibrary().toMutableList()
        updated.removeAll { it.id == newApp.id }
        updated.add(0, newApp)
        persistLibraryList(updated)
    }

    @Synchronized
    private fun persistLibraryList(list: List<DuplicatedApp>) {
        val array = JSONArray()
        for (item in list) {
            array.put(item.toJson())
        }
        registryFile.writeText(array.toString(2))
    }
}
