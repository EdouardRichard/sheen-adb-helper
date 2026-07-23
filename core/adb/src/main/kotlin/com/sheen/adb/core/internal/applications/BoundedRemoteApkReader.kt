package com.sheen.adb.core.internal.applications

import com.sheen.adb.core.internal.AdbProtocolClient
import com.sheen.adb.core.internal.KadbRemoteFileProtocol
import com.sheen.adb.core.internal.ProtocolLocalDestinationException
import com.sheen.adb.core.internal.ProtocolNoProgressTimeoutException
import java.io.ByteArrayOutputStream
import java.io.IOException
import java.io.OutputStream
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.CoroutineDispatcher
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.TimeoutCancellationException
import kotlinx.coroutines.runInterruptible
import kotlinx.coroutines.withContext
import kotlinx.coroutines.withTimeout
import kotlin.time.Duration
import kotlin.time.Duration.Companion.milliseconds

internal enum class RemoteApkReadFailure {
    UNAVAILABLE,
    TOO_LARGE,
    SPLIT_ONLY,
    SESSION_CHANGED,
    TIMEOUT,
}

internal data class RemoteApkReadRequest(
    val packageName: String,
    val userId: Int,
    val expectedSessionId: String,
    val timeout: Duration,
)

internal sealed interface RemoteApkReadResult {
    data class Success(val bytes: ByteArray, val remotePath: String) : RemoteApkReadResult
    data class Failure(val reason: RemoteApkReadFailure) : RemoteApkReadResult
}

internal fun interface RemoteApkReader {
    suspend fun read(request: RemoteApkReadRequest): RemoteApkReadResult
}

/** Reads one verified base APK through ADB Sync without touching local storage. */
internal class BoundedRemoteApkReader(
    private val client: AdbProtocolClient,
    private val sessionIsCurrent: () -> Boolean,
    private val ioDispatcher: CoroutineDispatcher = Dispatchers.IO,
    private val cancellationGrace: Duration = 3_000.milliseconds,
    private val onForcedSessionClose: () -> Unit = { client.close() },
) : RemoteApkReader {
    override suspend fun read(request: RemoteApkReadRequest): RemoteApkReadResult {
        if (!request.timeout.isPositive() || !sessionIsCurrent()) return failure(RemoteApkReadFailure.SESSION_CHANGED)
        if (!isSafePackageName(request.packageName) || request.userId < 0) return failure(RemoteApkReadFailure.UNAVAILABLE)

        return try {
            withTimeout(request.timeout) {
                guardSession()
                val response = withContext(ioDispatcher) {
                    runInterruptible {
                        client.execute("pm path --user ${request.userId} ${request.packageName}")
                    }
                }
                guardSession()
                if (response.exitCode != 0 || response.wasTruncated) return@withTimeout failure(
                    RemoteApkReadFailure.UNAVAILABLE,
                )
                val pathResult = selectBaseApkPath(response.stdout)
                val basePath = when (pathResult) {
                    BaseApkPath.SplitOnly -> return@withTimeout failure(RemoteApkReadFailure.SPLIT_ONLY)
                    BaseApkPath.Unavailable -> return@withTimeout failure(RemoteApkReadFailure.UNAVAILABLE)
                    is BaseApkPath.Found -> pathResult.path
                }

                val stat = KadbRemoteFileProtocol.stat(client, basePath, request.timeout)
                guardSession()
                if (stat.size <= 0L) return@withTimeout failure(RemoteApkReadFailure.UNAVAILABLE)
                if (stat.size > MAX_APPLICATION_APK_BYTES) return@withTimeout failure(RemoteApkReadFailure.TOO_LARGE)

                val destination = BoundedSessionOutputStream(MAX_APPLICATION_APK_BYTES, sessionIsCurrent)
                KadbRemoteFileProtocol.receive(
                    client = client,
                    path = basePath,
                    destination = destination,
                    noProgressTimeout = request.timeout,
                    cancellationGrace = cancellationGrace,
                    onForcedSessionClose = onForcedSessionClose,
                )
                guardSession()
                val bytes = destination.toByteArray()
                if (bytes.isEmpty()) failure(RemoteApkReadFailure.UNAVAILABLE)
                else RemoteApkReadResult.Success(bytes, basePath)
            }
        } catch (_: TimeoutCancellationException) {
            failure(RemoteApkReadFailure.TIMEOUT)
        } catch (error: CancellationException) {
            throw error
        } catch (_: ProtocolNoProgressTimeoutException) {
            failure(RemoteApkReadFailure.TIMEOUT)
        } catch (error: ProtocolLocalDestinationException) {
            when {
                error.hasCause<MetadataTooLargeException>() -> failure(RemoteApkReadFailure.TOO_LARGE)
                error.hasCause<MetadataSessionChangedException>() -> failure(RemoteApkReadFailure.SESSION_CHANGED)
                else -> failure(RemoteApkReadFailure.UNAVAILABLE)
            }
        } catch (_: MetadataSessionChangedException) {
            failure(RemoteApkReadFailure.SESSION_CHANGED)
        } catch (_: Exception) {
            failure(RemoteApkReadFailure.UNAVAILABLE)
        }
    }

    private fun guardSession() {
        if (!sessionIsCurrent()) throw MetadataSessionChangedException()
    }

    private fun selectBaseApkPath(stdout: String): BaseApkPath {
        val paths = stdout.lineSequence()
            .map(String::trim)
            .filter(String::isNotEmpty)
            .mapNotNull { line -> line.removePrefix(PACKAGE_PREFIX).takeIf { line.startsWith(PACKAGE_PREFIX) } }
            .filter(::isSafeRemotePath)
            .distinct()
            .toList()
        val base = paths.singleOrNull { it.substringAfterLast('/') == BASE_APK_NAME }
        if (base != null) return BaseApkPath.Found(base)
        return if (paths.any { it.endsWith(".apk", ignoreCase = true) }) {
            BaseApkPath.SplitOnly
        } else {
            BaseApkPath.Unavailable
        }
    }

    private fun isSafeRemotePath(path: String): Boolean =
        path.startsWith('/') && path.length <= MAX_REMOTE_PATH_CHARS && '\u0000' !in path && '\n' !in path && '\r' !in path

    private fun isSafePackageName(packageName: String): Boolean =
        packageName.length in 3..MAX_PACKAGE_NAME_CHARS &&
            packageName.matches(PACKAGE_NAME_PATTERN) &&
            !packageName.startsWith('.') && !packageName.endsWith('.') && ".." !in packageName

    private sealed interface BaseApkPath {
        data class Found(val path: String) : BaseApkPath
        data object SplitOnly : BaseApkPath
        data object Unavailable : BaseApkPath
    }

    private companion object {
        const val PACKAGE_PREFIX = "package:"
        const val BASE_APK_NAME = "base.apk"
        const val MAX_REMOTE_PATH_CHARS = 4096
        const val MAX_PACKAGE_NAME_CHARS = 255
        val PACKAGE_NAME_PATTERN = Regex("[A-Za-z0-9_.]+")
    }
}

private class BoundedSessionOutputStream(
    private val maximumBytes: Int,
    private val sessionIsCurrent: () -> Boolean,
) : OutputStream() {
    private val delegate = ByteArrayOutputStream()

    override fun write(value: Int) {
        checkWrite(1)
        delegate.write(value)
    }

    override fun write(buffer: ByteArray, offset: Int, length: Int) {
        require(offset >= 0 && length >= 0 && offset + length <= buffer.size)
        checkWrite(length)
        delegate.write(buffer, offset, length)
    }

    fun toByteArray(): ByteArray = delegate.toByteArray()

    private fun checkWrite(length: Int) {
        if (!sessionIsCurrent()) throw MetadataSessionChangedException()
        if (delegate.size().toLong() + length > maximumBytes) throw MetadataTooLargeException()
    }
}

private class MetadataTooLargeException : IOException()
private class MetadataSessionChangedException : IOException()

private inline fun <reified T : Throwable> Throwable.hasCause(): Boolean =
    generateSequence<Throwable>(this) { it.cause }.take(8).any { it is T }

private fun failure(reason: RemoteApkReadFailure) = RemoteApkReadResult.Failure(reason)
