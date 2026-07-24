package com.sheen.adb.core.internal.applications

import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.flow
import kotlin.time.Duration
import kotlin.time.Duration.Companion.milliseconds
import kotlin.time.Duration.Companion.seconds

internal enum class ApplicationMetadataLoadStatus {
    AVAILABLE,
    UNAVAILABLE,
    TOO_LARGE,
    PARSE_FAILED,
    SESSION_CHANGED,
    TIMED_OUT,
}

internal data class ApplicationMetadataLoadUpdate(
    val sessionId: String,
    val userId: Int,
    val packageName: String,
    val status: ApplicationMetadataLoadStatus,
    val metadata: ParsedApplicationMetadata? = null,
)

/** Sequential, current-session-only application label enrichment. */
internal class ApplicationMetadataLoader(
    private val reader: RemoteApkReader,
    private val parseMetadata: (ByteArray, List<String>) -> ApplicationMetadataParseResult =
        ApplicationMetadataParser()::parse,
    private val nowMillis: () -> Long = { System.nanoTime() / 1_000_000L },
    private val batchTimeout: Duration = 10.seconds,
) {
    init {
        require(batchTimeout.isPositive())
    }

    fun load(
        sessionId: String,
        userId: Int,
        packageNames: List<String>,
        preferredLocaleTags: List<String>,
    ): Flow<ApplicationMetadataLoadUpdate> = flow {
        val startedAt = nowMillis()
        packageNames.forEachIndexed { index, packageName ->
            val remainingMillis = batchTimeout.inWholeMilliseconds - (nowMillis() - startedAt)
            if (remainingMillis <= 0L) {
                packageNames.drop(index).forEach { remainingPackage ->
                    emit(update(sessionId, userId, remainingPackage, ApplicationMetadataLoadStatus.TIMED_OUT))
                }
                return@flow
            }

            val request = RemoteApkReadRequest(
                packageName = packageName,
                userId = userId,
                expectedSessionId = sessionId,
                timeout = remainingMillis.coerceAtLeast(1L).milliseconds,
            )
            val read = try {
                reader.read(request)
            } catch (error: CancellationException) {
                throw error
            }
            when (read) {
                is RemoteApkReadResult.Failure -> {
                    val status = read.reason.toLoadStatus()
                    emit(update(sessionId, userId, packageName, status))
                    if (read.reason == RemoteApkReadFailure.SESSION_CHANGED) {
                        clear()
                        packageNames.drop(index + 1).forEach { remainingPackage ->
                            emit(
                                update(
                                    sessionId,
                                    userId,
                                    remainingPackage,
                                    ApplicationMetadataLoadStatus.SESSION_CHANGED,
                                ),
                            )
                        }
                        return@flow
                    }
                }
                is RemoteApkReadResult.Success -> emitParsed(
                    sessionId,
                    userId,
                    packageName,
                    preferredLocaleTags,
                    read.bytes,
                )
            }
        }
    }

    fun clear() = Unit
    fun retainSession(sessionId: String) = Unit

    private suspend fun kotlinx.coroutines.flow.FlowCollector<ApplicationMetadataLoadUpdate>.emitParsed(
        sessionId: String,
        userId: Int,
        packageName: String,
        localeTags: List<String>,
        apkBytes: ByteArray,
    ) {
        when (val parsed = parseMetadata(apkBytes, localeTags)) {
            is ApplicationMetadataParseResult.Failure -> emit(
                update(
                    sessionId,
                    userId,
                    packageName,
                    when (parsed.reason) {
                        ApplicationMetadataParseFailure.APK_TOO_LARGE -> ApplicationMetadataLoadStatus.TOO_LARGE
                        ApplicationMetadataParseFailure.SPLIT_APK_UNSUPPORTED -> ApplicationMetadataLoadStatus.UNAVAILABLE
                        ApplicationMetadataParseFailure.MALFORMED_ARCHIVE,
                        ApplicationMetadataParseFailure.UNSAFE_ARCHIVE,
                        ApplicationMetadataParseFailure.PARSE_FAILED,
                        -> ApplicationMetadataLoadStatus.PARSE_FAILED
                    },
                ),
            )
            is ApplicationMetadataParseResult.Success -> {
                if (parsed.metadata.packageName != packageName) {
                    emit(update(sessionId, userId, packageName, ApplicationMetadataLoadStatus.PARSE_FAILED))
                    return
                }
                val status = when {
                    parsed.metadata.displayName == null -> ApplicationMetadataLoadStatus.UNAVAILABLE
                    else -> ApplicationMetadataLoadStatus.AVAILABLE
                }
                emit(update(sessionId, userId, packageName, status, parsed.metadata))
            }
        }
    }

    private fun update(
        sessionId: String,
        userId: Int,
        packageName: String,
        status: ApplicationMetadataLoadStatus,
        metadata: ParsedApplicationMetadata? = null,
    ) = ApplicationMetadataLoadUpdate(sessionId, userId, packageName, status, metadata)

    private fun RemoteApkReadFailure.toLoadStatus(): ApplicationMetadataLoadStatus = when (this) {
        RemoteApkReadFailure.UNAVAILABLE,
        RemoteApkReadFailure.SPLIT_ONLY,
        -> ApplicationMetadataLoadStatus.UNAVAILABLE
        RemoteApkReadFailure.TOO_LARGE -> ApplicationMetadataLoadStatus.TOO_LARGE
        RemoteApkReadFailure.SESSION_CHANGED -> ApplicationMetadataLoadStatus.SESSION_CHANGED
        RemoteApkReadFailure.TIMEOUT -> ApplicationMetadataLoadStatus.TIMED_OUT
    }
}
