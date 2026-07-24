package com.sheen.adb.core.internal.applications

import java.io.ByteArrayInputStream
import java.util.Locale
import java.util.zip.ZipException
import java.util.zip.ZipInputStream
import net.dongliu.apk.parser.ByteArrayApkFile

internal const val MAX_APPLICATION_APK_BYTES: Int = 32 * 1024 * 1024

internal enum class ApplicationMetadataParseFailure {
    APK_TOO_LARGE,
    MALFORMED_ARCHIVE,
    UNSAFE_ARCHIVE,
    SPLIT_APK_UNSUPPORTED,
    PARSE_FAILED,
}

internal data class ParsedApplicationMetadata(
    val packageName: String,
    val displayName: String?,
)

internal sealed interface ApplicationMetadataParseResult {
    data class Success(val metadata: ParsedApplicationMetadata) : ApplicationMetadataParseResult
    data class Failure(val reason: ApplicationMetadataParseFailure) : ApplicationMetadataParseResult
}

internal data class DecodedApkMetadata(
    val packageName: String,
    val splitName: String?,
    val label: String?,
)

internal fun interface ApkMetadataDecoder {
    fun decode(apkBytes: ByteArray, locale: Locale): DecodedApkMetadata
}

/**
 * Security boundary around the archived third-party APK parser.
 *
 * Only bounded in-memory APK bytes and project-owned values cross this adapter. The archive is
 * inspected before the dependency receives it, and no path or decoded application context is
 * logged or persisted here.
 */
internal class ApplicationMetadataParser(
    private val decoder: ApkMetadataDecoder = DongliuApkMetadataDecoder,
) {
    fun parse(
        apkBytes: ByteArray,
        preferredLocaleTags: List<String>,
    ): ApplicationMetadataParseResult {
        if (apkBytes.size > MAX_APPLICATION_APK_BYTES) {
            return ApplicationMetadataParseResult.Failure(ApplicationMetadataParseFailure.APK_TOO_LARGE)
        }

        when (inspectArchive(apkBytes)) {
            ArchiveInspection.SAFE -> Unit
            ArchiveInspection.MALFORMED -> return ApplicationMetadataParseResult.Failure(
                ApplicationMetadataParseFailure.MALFORMED_ARCHIVE,
            )
            ArchiveInspection.UNSAFE -> return ApplicationMetadataParseResult.Failure(
                ApplicationMetadataParseFailure.UNSAFE_ARCHIVE,
            )
        }

        val locales = preferredLocaleTags
            .asSequence()
            .map(String::trim)
            .filter(String::isNotEmpty)
            .map(Locale::forLanguageTag)
            .filter { it.language.isNotEmpty() }
            .distinctBy(Locale::toLanguageTag)
            .toList()
            .ifEmpty { listOf(Locale.US) }

        return try {
            var packageName: String? = null
            var displayName: String? = null
            for (locale in locales) {
                val decoded = decoder.decode(apkBytes, locale)
                if (!decoded.splitName.isNullOrBlank()) {
                    return ApplicationMetadataParseResult.Failure(
                        ApplicationMetadataParseFailure.SPLIT_APK_UNSUPPORTED,
                    )
                }
                val decodedPackage = decoded.packageName.trim()
                if (decodedPackage.isEmpty() || packageName != null && packageName != decodedPackage) {
                    return ApplicationMetadataParseResult.Failure(ApplicationMetadataParseFailure.PARSE_FAILED)
                }
                packageName = decodedPackage
                if (displayName == null) {
                    displayName = decoded.label?.trim()?.takeIf(String::isNotEmpty)
                }
                if (displayName != null) break
            }

            val verifiedPackage = packageName
                ?: return ApplicationMetadataParseResult.Failure(ApplicationMetadataParseFailure.PARSE_FAILED)
            ApplicationMetadataParseResult.Success(
                ParsedApplicationMetadata(
                    packageName = verifiedPackage,
                    displayName = displayName,
                ),
            )
        } catch (_: Exception) {
            ApplicationMetadataParseResult.Failure(ApplicationMetadataParseFailure.PARSE_FAILED)
        } catch (_: LinkageError) {
            ApplicationMetadataParseResult.Failure(ApplicationMetadataParseFailure.PARSE_FAILED)
        }
    }

    private fun inspectArchive(bytes: ByteArray): ArchiveInspection {
        if (bytes.isEmpty()) return ArchiveInspection.MALFORMED
        var entryCount = 0
        var expandedBytes = 0L
        var hasManifest = false
        val names = HashSet<String>()
        val buffer = ByteArray(8192)
        return try {
            ZipInputStream(ByteArrayInputStream(bytes)).use { zip ->
                while (true) {
                    val entry = zip.nextEntry ?: break
                    entryCount += 1
                    if (entryCount > MAX_ARCHIVE_ENTRIES || !isSafeEntryName(entry.name) || !names.add(entry.name)) {
                        return ArchiveInspection.UNSAFE
                    }
                    if (entry.name == "AndroidManifest.xml") hasManifest = true
                    while (true) {
                        val count = zip.read(buffer)
                        if (count < 0) break
                        expandedBytes += count
                        val ratioLimit = bytes.size.toLong() * MAX_COMPRESSION_RATIO + COMPRESSION_RATIO_GRACE_BYTES
                        if (expandedBytes > MAX_EXPANDED_ARCHIVE_BYTES || expandedBytes > ratioLimit) {
                            return ArchiveInspection.UNSAFE
                        }
                    }
                    zip.closeEntry()
                }
            }
            if (entryCount == 0 || !hasManifest) ArchiveInspection.MALFORMED else ArchiveInspection.SAFE
        } catch (_: ZipException) {
            ArchiveInspection.MALFORMED
        } catch (_: RuntimeException) {
            ArchiveInspection.MALFORMED
        }
    }

    private fun isSafeEntryName(name: String): Boolean {
        if (name.isEmpty() || name.startsWith('/') || name.startsWith('\\') || '\\' in name) return false
        if (name.length > MAX_ENTRY_NAME_LENGTH || ':' in name || '\u0000' in name) return false
        return name.split('/').none { it == ".." || it == "." }
    }

    private enum class ArchiveInspection { SAFE, MALFORMED, UNSAFE }

    private companion object {
        const val MAX_ARCHIVE_ENTRIES = 4096
        const val MAX_ENTRY_NAME_LENGTH = 512
        const val MAX_EXPANDED_ARCHIVE_BYTES = 64L * 1024 * 1024
        const val MAX_COMPRESSION_RATIO = 100L
        const val COMPRESSION_RATIO_GRACE_BYTES = 1024L * 1024
    }
}

private object DongliuApkMetadataDecoder : ApkMetadataDecoder {
    override fun decode(apkBytes: ByteArray, locale: Locale): DecodedApkMetadata =
        ByteArrayApkFile(apkBytes).use { apk ->
            apk.preferredLocale = locale
            val meta = apk.apkMeta
            DecodedApkMetadata(
                packageName = meta.packageName.orEmpty(),
                splitName = meta.split,
                label = meta.label,
            )
        }
}
