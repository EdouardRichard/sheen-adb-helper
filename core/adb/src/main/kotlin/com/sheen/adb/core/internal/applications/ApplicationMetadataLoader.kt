package com.sheen.adb.core.internal.applications

import java.util.LinkedHashMap
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
    val evictedIconPackages: Set<String> = emptySet(),
)

/** Sequential, current-session-only metadata enrichment with an in-memory icon LRU. */
internal class ApplicationMetadataLoader(
    private val reader: RemoteApkReader,
    private val parseMetadata: (ByteArray, List<String>) -> ApplicationMetadataParseResult =
        ApplicationMetadataParser()::parse,
    private val nowMillis: () -> Long = { System.nanoTime() / 1_000_000L },
    private val batchTimeout: Duration = 10.seconds,
    private val iconCacheMaximumBytes: Long = 16L * 1024 * 1024,
) {
    private data class CacheKey(val sessionId: String, val userId: Int, val packageName: String)

    private val cacheLock = Any()
    private val iconCache = LinkedHashMap<CacheKey, ParsedApplicationIcon>(16, 0.75f, true)
    private var iconCacheBytes = 0L

    init {
        require(batchTimeout.isPositive())
        require(iconCacheMaximumBytes >= MAX_APPLICATION_ICON_BYTES)
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

    fun clear() = synchronized(cacheLock) {
        iconCache.clear()
        iconCacheBytes = 0L
    }

    fun retainSession(sessionId: String) = synchronized(cacheLock) {
        val iterator = iconCache.entries.iterator()
        while (iterator.hasNext()) {
            val entry = iterator.next()
            if (entry.key.sessionId != sessionId) {
                iconCacheBytes -= entry.value.encodedBytes.size
                iterator.remove()
            }
        }
    }

    internal fun cachedIconBytes(): Long = synchronized(cacheLock) { iconCacheBytes }

    internal fun hasCachedIcon(sessionId: String, userId: Int, packageName: String): Boolean =
        synchronized(cacheLock) { iconCache.containsKey(CacheKey(sessionId, userId, packageName)) }

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
                val key = CacheKey(sessionId, userId, packageName)
                val oversizedIcon = parsed.metadata.icon?.encodedBytes?.size?.let {
                    it > MAX_APPLICATION_ICON_BYTES
                } == true
                val metadata = if (oversizedIcon) parsed.metadata.copy(icon = null) else parsed.metadata
                val evicted = if (metadata.icon != null) cacheIcon(key, metadata.icon) else removeCachedIcon(key)
                val status = when {
                    oversizedIcon -> ApplicationMetadataLoadStatus.TOO_LARGE
                    metadata.displayName == null && metadata.icon == null -> ApplicationMetadataLoadStatus.UNAVAILABLE
                    else -> ApplicationMetadataLoadStatus.AVAILABLE
                }
                emit(update(sessionId, userId, packageName, status, metadata, evicted))
            }
        }
    }

    private fun cacheIcon(key: CacheKey, icon: ParsedApplicationIcon): Set<String> = synchronized(cacheLock) {
        iconCache.remove(key)?.let { iconCacheBytes -= it.encodedBytes.size }
        val retained = icon.copy(encodedBytes = icon.encodedBytes.copyOf())
        iconCache[key] = retained
        iconCacheBytes += retained.encodedBytes.size
        val evicted = linkedSetOf<String>()
        val iterator = iconCache.entries.iterator()
        while (iconCacheBytes > iconCacheMaximumBytes && iterator.hasNext()) {
            val eldest = iterator.next()
            iconCacheBytes -= eldest.value.encodedBytes.size
            evicted += eldest.key.packageName
            iterator.remove()
        }
        evicted
    }

    private fun removeCachedIcon(key: CacheKey): Set<String> = synchronized(cacheLock) {
        iconCache.remove(key)?.let { iconCacheBytes -= it.encodedBytes.size }
        emptySet()
    }

    private fun update(
        sessionId: String,
        userId: Int,
        packageName: String,
        status: ApplicationMetadataLoadStatus,
        metadata: ParsedApplicationMetadata? = null,
        evicted: Set<String> = emptySet(),
    ) = ApplicationMetadataLoadUpdate(sessionId, userId, packageName, status, metadata, evicted)

    private fun RemoteApkReadFailure.toLoadStatus(): ApplicationMetadataLoadStatus = when (this) {
        RemoteApkReadFailure.UNAVAILABLE,
        RemoteApkReadFailure.SPLIT_ONLY,
        -> ApplicationMetadataLoadStatus.UNAVAILABLE
        RemoteApkReadFailure.TOO_LARGE -> ApplicationMetadataLoadStatus.TOO_LARGE
        RemoteApkReadFailure.SESSION_CHANGED -> ApplicationMetadataLoadStatus.SESSION_CHANGED
        RemoteApkReadFailure.TIMEOUT -> ApplicationMetadataLoadStatus.TIMED_OUT
    }
}
