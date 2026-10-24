package com.duplexa.duplicator.nexus

import android.content.Context
import com.android.apksig.ApkSigner
import org.bouncycastle.asn1.x500.X500Name
import org.bouncycastle.cert.jcajce.JcaX509CertificateConverter
import org.bouncycastle.cert.jcajce.JcaX509v3CertificateBuilder
import org.bouncycastle.jce.provider.BouncyCastleProvider
import org.bouncycastle.operator.jcajce.JcaContentSignerBuilder
import java.io.ByteArrayOutputStream
import java.io.File
import java.io.FileOutputStream
import java.io.OutputStream
import java.math.BigInteger
import java.nio.charset.StandardCharsets
import java.security.KeyFactory
import java.security.KeyPairGenerator
import java.security.PrivateKey
import java.security.Security
import java.security.cert.CertificateFactory
import java.security.cert.X509Certificate
import java.security.spec.PKCS8EncodedKeySpec
import java.util.Date
import java.util.Locale
import java.util.zip.CRC32
import java.util.zip.ZipEntry
import java.util.zip.ZipFile
import java.util.zip.ZipOutputStream

/**
 * Low-level Native APK Binary Patcher, 4-Byte Zip Aligner, and On-Device V1/V2 APK Signer
 * for Duplexa Apps Duplicator - Nexus Edition.
 *
 * Operates 100% natively on installed Android APK files with zero WebKit dependencies.
 */
object ApkBinaryPatcher {

    private const val KEYSTORE_DIR_NAME = "duplexa_signing_keys"
    private const val PRIVATE_KEY_FILE = "duplexa_vessel_key.pk8"
    private const val CERTIFICATE_FILE = "duplexa_vessel_cert.der"
    private const val ZIP_ALIGNMENT_BYTES = 4

    init {
        if (Security.getProvider(BouncyCastleProvider.PROVIDER_NAME) == null) {
            Security.addProvider(BouncyCastleProvider())
        }
    }

    /**
     * Generates a deterministic, length-aligned package name for the duplicated D_V# APK.
     *
     * Keeping the exact character and byte length identical to [originalPackageName] allows
     * in-place binary patching of AndroidManifest.xml (AXML) without shifting string pool
     * offsets or corrupting chunk headers, while leaving Activity/Service class paths intact.
     */
    fun deriveAlignedDuplicatePackageName(
        originalPackageName: String,
        duplicationVersion: Int
    ): String {
        val versionSuffix = "dv${duplicationVersion.coerceAtLeast(1)}"
        if (originalPackageName.length <= versionSuffix.length + 4) {
            return originalPackageName
        }

        val chars = originalPackageName.toCharArray()
        var replaceIdx = chars.size - 1
        var suffixIdx = versionSuffix.length - 1

        while (replaceIdx >= 0 && suffixIdx >= 0) {
            if (chars[replaceIdx] == '.') {
                replaceIdx--
                continue
            }
            chars[replaceIdx] = versionSuffix[suffixIdx]
            replaceIdx--
            suffixIdx--
        }

        val candidate = String(chars)
        return if (candidate != originalPackageName) {
            candidate
        } else {
            val fallbackChars = originalPackageName.toCharArray()
            val lastIndex = fallbackChars.lastIndex
            fallbackChars[lastIndex] = if (fallbackChars[lastIndex] == 'x') 'v' else 'x'
            String(fallbackChars)
        }
    }

    /**
     * Full pipeline to extract, patch, align, and cryptographically sign (V1 + V2)
     * a standalone duplicated APK file.
     */
    fun forgeStandaloneDuplicateApk(
        context: Context,
        sourceApkFile: File,
        originalPackageName: String,
        duplicatedPackageName: String,
        outputSignedApkFile: File,
        onStepUpdate: ((String) -> Unit)? = null
    ) {
        val stagingDir = File(context.cacheDir, "apk_staging").apply { mkdirs() }
        val unsignedAlignedApk = File(
            stagingDir,
            "unsigned_${System.currentTimeMillis()}_${outputSignedApkFile.name}"
        )

        try {
            onStepUpdate?.invoke(context.getString(R.string.status_step_extracting))
            repackageAndAlignUnsignedApk(
                sourceApkFile = sourceApkFile,
                originalPackageName = originalPackageName,
                duplicatedPackageName = duplicatedPackageName,
                outputUnsignedApk = unsignedAlignedApk
            )

            onStepUpdate?.invoke(context.getString(R.string.status_step_signing))
            outputSignedApkFile.parentFile?.mkdirs()
            if (outputSignedApkFile.exists()) {
                outputSignedApkFile.delete()
            }

            signStandaloneApk(
                context = context,
                inputUnsignedApk = unsignedAlignedApk,
                outputSignedApk = outputSignedApkFile
            )
        } finally {
            if (unsignedAlignedApk.exists()) {
                unsignedAlignedApk.delete()
            }
        }
    }

    /**
     * Repackages the source APK into an unsigned, 4-byte aligned APK while:
     * 1. Stripping existing META-INF signatures.
     * 2. Patching the standalone package attribute inside binary AndroidManifest.xml.
     * 3. Enforcing 4-byte uncompressed alignment on resources.arsc and native .so libraries
     *    for full Android 11–15 PackageInstaller compliance.
     */
    private fun repackageAndAlignUnsignedApk(
        sourceApkFile: File,
        originalPackageName: String,
        duplicatedPackageName: String,
        outputUnsignedApk: File
    ) {
        ZipFile(sourceApkFile).use { zipFile ->
            FileOutputStream(outputUnsignedApk).use { fileOut ->
                val countingOut = CountingOutputStream(fileOut)
                ZipOutputStream(countingOut).use { zipOut ->
                    val entries = zipFile.entries()
                    while (entries.hasMoreElements()) {
                        val entry = entries.nextElement()
                        val entryName = entry.name

                        if (isSignatureFile(entryName)) {
                            continue
                        }

                        val rawBytes = zipFile.getInputStream(entry).use { it.readBytes() }
                        val processedBytes = if (entryName == "AndroidManifest.xml") {
                            patchBinaryManifestPackageId(
                                manifestBytes = rawBytes,
                                originalPkg = originalPackageName,
                                newPkg = duplicatedPackageName
                            )
                        } else {
                            rawBytes
                        }

                        val mustStoreUncompressed = entry.method == ZipEntry.STORED ||
                            entryName == "resources.arsc" ||
                            entryName.endsWith(".so", ignoreCase = true)

                        val newEntry = ZipEntry(entryName)
                        newEntry.time = 0L // Deterministic timestamp

                        if (mustStoreUncompressed) {
                            newEntry.method = ZipEntry.STORED
                            newEntry.size = processedBytes.size.toLong()
                            newEntry.compressedSize = processedBytes.size.toLong()
                            val crc = CRC32()
                            crc.update(processedBytes)
                            newEntry.crc = crc.value

                            // Calculate 4-byte alignment padding for STORED entry data payload
                            val nameBytesLen = entryName.toByteArray(StandardCharsets.UTF_8).size
                            val dataStartWithoutExtra = countingOut.bytesWritten + 30L + nameBytesLen
                            val remainder = (dataStartWithoutExtra % ZIP_ALIGNMENT_BYTES).toInt()
                            if (remainder != 0) {
                                val padLength = ZIP_ALIGNMENT_BYTES - remainder
                                newEntry.extra = ByteArray(padLength)
                            }
                        } else {
                            newEntry.method = ZipEntry.DEFLATED
                        }

                        zipOut.putNextEntry(newEntry)
                        zipOut.write(processedBytes)
                        zipOut.closeEntry()
                    }
                }
            }
        }
    }

    /**
     * Safely patches the standalone package identifier in Android Binary XML (AXML)
     * for both UTF-8 and UTF-16LE string pools when lengths match.
     *
     * Only replaces null-terminated standalone matches of [originalPkg], preserving
     * fully-qualified class names (e.g., "com.example.app.MainActivity") so DEX classloading
     * continues to resolve every Activity and Service without modification.
     */
    fun patchBinaryManifestPackageId(
        manifestBytes: ByteArray,
        originalPkg: String,
        newPkg: String
    ): ByteArray {
        if (originalPkg.length != newPkg.length || originalPkg == newPkg) {
            return manifestBytes
        }

        val patched = manifestBytes.copyOf()

        // 1. Patch UTF-16LE null-terminated standalone package string in AXML String Pool
        val origUtf16 = originalPkg.toByteArray(StandardCharsets.UTF_16LE)
        val newUtf16 = newPkg.toByteArray(StandardCharsets.UTF_16LE)
        if (origUtf16.size == newUtf16.size) {
            val patternUtf16 = origUtf16 + byteArrayOf(0x00, 0x00)
            val replacementUtf16 = newUtf16 + byteArrayOf(0x00, 0x00)
            replaceAllBytePatternsInPlace(patched, patternUtf16, replacementUtf16)
        }

        // 2. Patch UTF-8 null-terminated standalone package string in AXML String Pool
        val origUtf8 = originalPkg.toByteArray(StandardCharsets.UTF_8)
        val newUtf8 = newPkg.toByteArray(StandardCharsets.UTF_8)
        if (origUtf8.size == newUtf8.size) {
            val patternUtf8 = origUtf8 + byteArrayOf(0x00)
            val replacementUtf8 = newUtf8 + byteArrayOf(0x00)
            replaceAllBytePatternsInPlace(patched, patternUtf8, replacementUtf8)
        }

        return patched
    }

    private fun replaceAllBytePatternsInPlace(
        buffer: ByteArray,
        targetPattern: ByteArray,
        replacementPattern: ByteArray
    ) {
        if (targetPattern.isEmpty() || targetPattern.size != replacementPattern.size) return
        val maxStart = buffer.size - targetPattern.size
        var i = 0
        while (i <= maxStart) {
            var matches = true
            for (j in targetPattern.indices) {
                if (buffer[i + j] != targetPattern[j]) {
                    matches = false
                    break
                }
            }
            if (matches) {
                System.arraycopy(replacementPattern, 0, buffer, i, replacementPattern.size)
                i += targetPattern.size
            } else {
                i++
            }
        }
    }

    /**
     * Checks if a ZIP entry inside the source APK is an old signature/manifest file
     * that must be stripped prior to re-signing with Duplexa's V1/V2 key.
     */
    private fun isSignatureFile(entryName: String): Boolean {
        val upper = entryName.uppercase(Locale.US)
        if (!upper.startsWith("META-INF/")) return false
        return upper == "META-INF/MANIFEST.MF" ||
            upper.endsWith(".SF") ||
            upper.endsWith(".RSA") ||
            upper.endsWith(".DSA") ||
            upper.endsWith(".EC") ||
            upper.startsWith("META-INF/SIG-")
    }

    /**
     * Signs the aligned APK using Google's official ApkSigner (V1 JAR + V2 APK Signature Scheme).
     */
    private fun signStandaloneApk(
        context: Context,
        inputUnsignedApk: File,
        outputSignedApk: File
    ) {
        val (privateKey, certificate) = getOrCreateSigningCredentials(context)

        val signerConfig = ApkSigner.SignerConfig.Builder(
            "DUPLEXA_NEXUS",
            privateKey,
            listOf(certificate)
        ).build()

        val apkSigner = ApkSigner.Builder(listOf(signerConfig))
            .setInputApk(inputUnsignedApk)
            .setOutputApk(outputSignedApk)
            .setMinSdkVersion(26)
            .setV1SigningEnabled(true)
            .setV2SigningEnabled(true)
            .build()

        apkSigner.sign()
    }

    /**
     * Loads or generates a persistent RSA-2048 PrivateKey and self-signed X.509 Certificate
     * on the device so all D_V# duplicates signed by Duplexa share a consistent signature.
     */
    @Synchronized
    private fun getOrCreateSigningCredentials(
        context: Context
    ): Pair<PrivateKey, X509Certificate> {
        val keyDir = File(context.filesDir, KEYSTORE_DIR_NAME).apply { mkdirs() }
        val pkFile = File(keyDir, PRIVATE_KEY_FILE)
        val certFile = File(keyDir, CERTIFICATE_FILE)

        if (pkFile.exists() && certFile.exists()) {
            try {
                val keySpec = PKCS8EncodedKeySpec(pkFile.readBytes())
                val privateKey = KeyFactory.getInstance("RSA").generatePrivate(keySpec)
                val certFactory = CertificateFactory.getInstance("X.509")
                val certificate = certFile.inputStream().use {
                    certFactory.generateCertificate(it) as X509Certificate
                }
                return Pair(privateKey, certificate)
            } catch (_: Exception) {
                pkFile.delete()
                certFile.delete()
            }
        }

        // Generate fresh 2048-bit RSA KeyPair and 25-year X.509 Certificate
        val keyPairGenerator = KeyPairGenerator.getInstance("RSA")
        keyPairGenerator.initialize(2048)
        val keyPair = keyPairGenerator.generateKeyPair()

        val now = System.currentTimeMillis()
        val notBefore = Date(now - 86_400_000L)
        val notAfter = Date(now + 25L * 365 * 86_400_000L)
        val serial = BigInteger.valueOf(now)
        val issuer = X500Name("CN=Duplexa Nexus Vessel Signer, OU=Nexus Edition, O=GNP PRIDE, C=NG")

        val certBuilder = JcaX509v3CertificateBuilder(
            issuer,
            serial,
            notBefore,
            notAfter,
            issuer,
            keyPair.public
        )

        val contentSigner = JcaContentSignerBuilder("SHA256WithRSA")
            .build(keyPair.private)

        val certificateHolder = certBuilder.build(contentSigner)
        val certificate = JcaX509CertificateConverter()
            .getCertificate(certificateHolder)

        pkFile.writeBytes(keyPair.private.encoded)
        certFile.writeBytes(certificate.encoded)

        return Pair(keyPair.private, certificate)
    }

    /**
     * Tracks exact byte offset written to the underlying FileOutputStream so ZipEntry
     * extra fields can pad STORED entries to 4-byte boundaries.
     */
    private class CountingOutputStream(
        private val delegate: OutputStream
    ) : OutputStream() {
        var bytesWritten: Long = 0L
            private set

        override fun write(b: Int) {
            delegate.write(b)
            bytesWritten += 1L
        }

        override fun write(b: ByteArray) {
            delegate.write(b)
            bytesWritten += b.size
        }

        override fun write(b: ByteArray, off: Int, len: Int) {
            delegate.write(b, off, len)
            bytesWritten += len
        }

        override fun flush() {
            delegate.flush()
        }

        override fun close() {
            delegate.close()
        }
    }
}
