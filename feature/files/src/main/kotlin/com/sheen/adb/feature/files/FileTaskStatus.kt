package com.sheen.adb.feature.files

enum class FileTaskErrorCategory {
    PERMISSION_DENIED,
    PATH_NOT_FOUND,
    PATH_TOO_LONG,
    UNSUPPORTED_TYPE,
    SYMLINK_DENIED,
    SYMLINK_MISSING,
    SYMLINK_LOOP,
    DIRECTORY_CAPACITY_EXCEEDED,
    SPACE_INSUFFICIENT,
    CONFLICT,
    OPERATION_CONFLICT,
    PROVIDER_UNSUPPORTED,
    SOURCE_CHANGED,
    INTEGRITY_UNAVAILABLE,
    APK_INCOMPLETE,
    NO_PROGRESS_TIMEOUT,
    STARTUP_TIMEOUT,
    SESSION_INVALID,
    STREAM_CLOSED,
    CANCELLED,
    CLEANUP_FAILED,
    LOGCAT_CAPABILITY_LIMITED,
}

data class FileTaskError(
    val category: FileTaskErrorCategory,
    val userMessage: String,
    val nextStep: String,
    val technicalCode: String,
) {
    init {
        require(userMessage.isNotBlank())
        require(nextStep.isNotBlank())
        require(technicalCode.matches(Regex("[A-Z][A-Z0-9_]{0,63}")))
    }
}

sealed interface FileTaskStatus {
    val isTerminal: Boolean

    data object Preparing : FileTaskStatus {
        override val isTerminal = false
    }

    data object AwaitingConflict : FileTaskStatus {
        override val isTerminal = false
    }

    data class Transferring(
        val transferredBytes: Long,
        val totalBytes: Long?,
    ) : FileTaskStatus {
        init {
            require(transferredBytes >= 0L)
            require(totalBytes == null || totalBytes >= 0L)
        }

        override val isTerminal = false
    }

    data object Verifying : FileTaskStatus {
        override val isTerminal = false
    }

    data object Committing : FileTaskStatus {
        override val isTerminal = false
    }

    data object Succeeded : FileTaskStatus {
        override val isTerminal = true
    }

    data class Failed(val error: FileTaskError) : FileTaskStatus {
        override val isTerminal = true
    }

    data object Cancelled : FileTaskStatus {
        override val isTerminal = true
    }

    data class CleanupFailed(val error: FileTaskError) : FileTaskStatus {
        override val isTerminal = true
    }
}
