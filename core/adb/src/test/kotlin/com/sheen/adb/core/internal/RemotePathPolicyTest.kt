package com.sheen.adb.core.internal

import org.testng.Assert.assertEquals
import org.testng.Assert.assertFalse
import org.testng.Assert.assertThrows
import org.testng.Assert.assertTrue
import org.testng.annotations.Test

class RemotePathPolicyTest {
    @Test
    fun `shared storage candidates preserve safe probe order`() {
        assertEquals(
            RemoteFileCapabilities.sharedStorageCandidates(10),
            listOf("/sdcard", "/storage/self/primary", "/storage/emulated/10"),
        )
    }

    @Test
    fun `paths must be absolute nul free and at most 1024 utf8 bytes`() {
        assertTrue(RemoteFileCapabilities.isValidAbsolutePath("/storage/emulated/0/下载"))
        assertFalse(RemoteFileCapabilities.isValidAbsolutePath("storage/emulated/0"))
        assertFalse(RemoteFileCapabilities.isValidAbsolutePath("/safe\u0000bad"))
        assertTrue(RemoteFileCapabilities.isValidAbsolutePath("/" + "a".repeat(1023)))
        assertFalse(RemoteFileCapabilities.isValidAbsolutePath("/" + "界".repeat(342)))
    }

    @Test
    fun `child paths can only be built from one verified entry name`() {
        assertEquals(RemoteFileCapabilities.safeChild("/sdcard", "a b'c"), "/sdcard/a b'c")
        listOf("", ".", "..", "a/b", "a\u0000b").forEach { name ->
            assertThrows(IllegalArgumentException::class.java) {
                RemoteFileCapabilities.safeChild("/sdcard", name)
            }
        }
    }

    @Test
    fun `directory capacity rejects complete result instead of returning partial entries`() {
        RemoteFileCapabilities.requireDirectoryCapacity(10_000)
        assertThrows(RemoteDirectoryCapacityException::class.java) {
            RemoteFileCapabilities.requireDirectoryCapacity(10_001)
        }
    }

    @Test
    fun `symlink policy denies missing denied and ancestor loops`() {
        assertEquals(
            RemoteFileCapabilities.symlinkDecision(RemoteLinkProbe.Missing),
            RemoteLinkDecision.DENY_MISSING,
        )
        assertEquals(
            RemoteFileCapabilities.symlinkDecision(RemoteLinkProbe.PermissionDenied),
            RemoteLinkDecision.DENY_PERMISSION,
        )
        assertEquals(
            RemoteFileCapabilities.symlinkDecision(
                RemoteLinkProbe.Resolved(targetDeviceId = 7, targetInode = 9, ancestors = setOf(7L to 9L)),
            ),
            RemoteLinkDecision.DENY_LOOP,
        )
        assertEquals(
            RemoteFileCapabilities.symlinkDecision(
                RemoteLinkProbe.Resolved(targetDeviceId = 7, targetInode = 9, ancestors = setOf(7L to 8L)),
            ),
            RemoteLinkDecision.ALLOW,
        )
    }
}
