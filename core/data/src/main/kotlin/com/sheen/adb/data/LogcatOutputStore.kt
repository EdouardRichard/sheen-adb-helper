package com.sheen.adb.data

import java.nio.charset.StandardCharsets
import java.time.Instant
import java.time.ZoneOffset
import java.time.format.DateTimeFormatter

enum class LogcatOutputMode { SNAPSHOT, CONTINUOUS }

class LogcatOutputSnapshot private constructor(
    val windowId: String,
    val recordCount: Int,
    private val encodedPayload: ByteArray,
) {
    internal fun encodedBytes(): ByteArray = encodedPayload.copyOf()

    companion object {
        fun from(
            windowId: String,
            records: List<String>,
        ): LogcatOutputSnapshot {
            require(windowId.isNotBlank())
            val immutableRecords = records.toList()
            return LogcatOutputSnapshot(
                windowId = windowId,
                recordCount = immutableRecords.size,
                encodedPayload = immutableRecords
                    .joinToString(separator = "\n")
                    .toByteArray(StandardCharsets.UTF_8),
            )
        }
    }
}

enum class LogcatOutputPhase(
    val persistentIoStarted: Boolean,
) {
    PREPARING(false),
    WRITING(true),
    VERIFYING(true),
    COMMITTING(true),
    CLEANING(true),
    TERMINAL(false),
}

sealed interface LogcatOutputSaveResult {
    data class Success(
        val document: SafDocumentMetadata,
    ) : LogcatOutputSaveResult

    data object Cancelled : LogcatOutputSaveResult

    data class Failure(
        val error: SafStoreError,
    ) : LogcatOutputSaveResult
}

class LogcatOutputStore(
    private val saf: SafDocumentStore,
    private val clock: Instant = Instant.now(),
) {
    /**
     * Compatibility entry point for existing callers. New Logcat flows should
     * capture [LogcatOutputSnapshot] before opening the system picker.
     */
    fun save(
        treeId: String,
        mode: LogcatOutputMode,
        text: String,
        conflictPolicy: SafConflictPolicy = SafConflictPolicy.AUTO_RENAME,
    ): SafStoreResult<SafDocumentMetadata> =
        when (
            val result = save(
                treeId = treeId,
                mode = mode,
                snapshot = LogcatOutputSnapshot.from(
                    windowId = "legacy-snapshot",
                    records = listOf(text),
                ),
                conflictPolicy = conflictPolicy,
            )
        ) {
            is LogcatOutputSaveResult.Success -> SafStoreResult.Success(result.document)
            is LogcatOutputSaveResult.Failure -> SafStoreResult.Failure(result.error)
            LogcatOutputSaveResult.Cancelled -> SafStoreResult.Failure(SafStoreError.IO_FAILURE)
        }

    fun save(
        treeId: String?,
        mode: LogcatOutputMode,
        snapshot: LogcatOutputSnapshot,
        conflictPolicy: SafConflictPolicy = SafConflictPolicy.AUTO_RENAME,
        onPhase: (LogcatOutputPhase) -> Unit = {},
    ): LogcatOutputSaveResult {
        if (treeId == null) return LogcatOutputSaveResult.Cancelled

        val payload = snapshot.encodedBytes()
        if (payload.isEmpty()) return LogcatOutputSaveResult.Failure(SafStoreError.IO_FAILURE)
        val displayName = "sheen-logcat-${mode.name.lowercase()}-${UTC_FORMAT.format(clock)}.txt"
        notifyPhase(onPhase, LogcatOutputPhase.PREPARING)
        val target = when (val prepared = saf.prepareTarget(treeId, displayName, "text/plain")) {
            is SafStoreResult.Success -> prepared.value
            is SafStoreResult.Failure -> {
                notifyPhase(onPhase, LogcatOutputPhase.TERMINAL)
                return LogcatOutputSaveResult.Failure(prepared.error)
            }
        }
        return try {
            notifyPhase(onPhase, LogcatOutputPhase.WRITING)
            saf.openTarget(target).use { output ->
                output.write(payload)
                output.flush()
            }
            notifyPhase(onPhase, LogcatOutputPhase.VERIFYING)
            notifyPhase(onPhase, LogcatOutputPhase.COMMITTING)
            when (val committed = saf.commit(target, conflictPolicy)) {
                is SafStoreResult.Success -> {
                    notifyPhase(onPhase, LogcatOutputPhase.TERMINAL)
                    LogcatOutputSaveResult.Success(committed.value)
                }
                is SafStoreResult.Failure -> {
                    notifyPhase(onPhase, LogcatOutputPhase.CLEANING)
                    saf.cleanup(target)
                    notifyPhase(onPhase, LogcatOutputPhase.TERMINAL)
                    LogcatOutputSaveResult.Failure(committed.error)
                }
            }
        } catch (_: Throwable) {
            notifyPhase(onPhase, LogcatOutputPhase.CLEANING)
            saf.cleanup(target)
            notifyPhase(onPhase, LogcatOutputPhase.TERMINAL)
            LogcatOutputSaveResult.Failure(SafStoreError.IO_FAILURE)
        }
    }

    private fun notifyPhase(
        observer: (LogcatOutputPhase) -> Unit,
        phase: LogcatOutputPhase,
    ) {
        runCatching { observer(phase) }
    }

    private companion object {
        val UTC_FORMAT: DateTimeFormatter = DateTimeFormatter.ofPattern("yyyyMMdd'T'HHmmss'Z'")
            .withZone(ZoneOffset.UTC)
    }
}
