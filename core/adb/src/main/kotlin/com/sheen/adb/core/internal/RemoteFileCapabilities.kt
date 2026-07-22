package com.sheen.adb.core.internal

internal class RemoteDirectoryCapacityException : IllegalStateException()

internal enum class RemoteLinkDecision { ALLOW, DENY_MISSING, DENY_PERMISSION, DENY_LOOP }

internal sealed interface RemoteLinkProbe {
    data object Missing : RemoteLinkProbe
    data object PermissionDenied : RemoteLinkProbe
    data class Resolved(
        val targetDeviceId: Long,
        val targetInode: Long,
        val ancestors: Set<Pair<Long, Long>>,
    ) : RemoteLinkProbe
}

internal object RemoteFileCapabilities {
    const val MAX_PATH_BYTES = 1_024
    const val MAX_DIRECTORY_ENTRIES = 10_000

    fun sharedStorageCandidates(currentUserId: Int): List<String> {
        require(currentUserId >= 0)
        return listOf("/sdcard", "/storage/self/primary", "/storage/emulated/$currentUserId")
    }

    fun isValidAbsolutePath(path: String): Boolean =
        path.startsWith('/') &&
            !path.contains('\u0000') &&
            path.encodeToByteArray().size <= MAX_PATH_BYTES

    fun requireValidAbsolutePath(path: String): String = path.also {
        require(isValidAbsolutePath(it))
    }

    fun safeChild(parent: String, entryName: String): String {
        requireValidAbsolutePath(parent)
        require(entryName.isNotEmpty() && entryName != "." && entryName != "..")
        require(!entryName.contains('/') && !entryName.contains('\u0000'))
        val child = if (parent == "/") "/$entryName" else "${parent.trimEnd('/')}/$entryName"
        return requireValidAbsolutePath(child)
    }

    fun requireDirectoryCapacity(entryCount: Int) {
        require(entryCount >= 0)
        if (entryCount > MAX_DIRECTORY_ENTRIES) throw RemoteDirectoryCapacityException()
    }

    fun symlinkDecision(probe: RemoteLinkProbe): RemoteLinkDecision = when (probe) {
        RemoteLinkProbe.Missing -> RemoteLinkDecision.DENY_MISSING
        RemoteLinkProbe.PermissionDenied -> RemoteLinkDecision.DENY_PERMISSION
        is RemoteLinkProbe.Resolved -> if (
            probe.targetDeviceId to probe.targetInode in probe.ancestors
        ) RemoteLinkDecision.DENY_LOOP else RemoteLinkDecision.ALLOW
    }
}
