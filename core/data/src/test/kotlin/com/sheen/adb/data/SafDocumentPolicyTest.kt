package com.sheen.adb.data

import java.io.ByteArrayInputStream
import java.io.ByteArrayOutputStream
import org.testng.Assert.assertEquals
import org.testng.Assert.assertFalse
import org.testng.Assert.assertTrue
import org.testng.annotations.Test

class SafDocumentPolicyTest {
    @Test
    fun `only a pure tree URI is resolved to its root document`() {
        assertTrue(SafDocumentReferencePolicy.requiresTreeRootResolution(isTreeUri = true, isDocumentUri = false))
        assertFalse(SafDocumentReferencePolicy.requiresTreeRootResolution(isTreeUri = true, isDocumentUri = true))
        assertFalse(SafDocumentReferencePolicy.requiresTreeRootResolution(isTreeUri = false, isDocumentUri = true))
    }

    @Test
    fun `open source uses portable metadata instead of document capability metadata`() {
        val backend = FakeSafBackend()
        backend.addFile("source", "source.bin", byteArrayOf(1, 2, 3))
        backend.failDocumentMetadataIds += "source"
        val store = SafDocumentStore(backend)

        val result = store.openSource("source")

        assertTrue(result is SafStoreResult.Success)
        val source = (result as SafStoreResult.Success).value
        assertEquals(source.metadata.displayName, "source.bin")
        assertEquals(source.open().use { it.readBytes() }, byteArrayOf(1, 2, 3))
        assertTrue(store.verifySourceUnchanged(source) is SafStoreResult.Success)
    }

    @Test
    fun `open document exposes metadata and tree creates hidden capable staging`() {
        val backend = FakeSafBackend()
        backend.addFile("source", "source.bin", byteArrayOf(1, 2, 3))
        backend.addTree("tree", availableBytes = 4_096)
        val store = SafDocumentStore(backend)

        val source = store.openSource("source") as SafStoreResult.Success
        assertEquals(source.value.metadata.displayName, "source.bin")
        assertEquals(source.value.metadata.sizeBytes, 3L)
        assertEquals(source.value.open().use { it.readBytes() }, byteArrayOf(1, 2, 3))

        val prepared = store.prepareTarget("tree", "report.bin", "application/octet-stream")
            as SafStoreResult.Success
        assertEquals(prepared.value.availableBytes, 4_096L)
        assertTrue(prepared.value.temporary.displayName.startsWith(".sheen-"))
        assertTrue(prepared.value.temporary.displayName.endsWith(".part"))
        assertTrue(
            prepared.value.temporary.capabilities.containsAll(
                setOf(SafCapability.WRITE, SafCapability.DELETE, SafCapability.RENAME),
            ),
        )
    }

    @Test
    fun `provider missing safe target capabilities is rejected before writing`() {
        val backend = FakeSafBackend(createdCapabilities = setOf(SafCapability.WRITE))
        backend.addTree("tree")
        val store = SafDocumentStore(backend)

        val result = store.prepareTarget("tree", "report.bin", "application/octet-stream")

        assertTrue(result is SafStoreResult.Failure && result.error == SafStoreError.PROVIDER_UNSUPPORTED)
        assertFalse(backend.nodes.values.any { it.displayName.startsWith(".sheen-") })
    }

    @Test
    fun `conflict defaults to cancel and auto rename preserves original`() {
        val backend = FakeSafBackend()
        backend.addTree("tree")
        backend.addFile("original", "report.txt", "old".encodeToByteArray(), parent = "tree")
        val store = SafDocumentStore(backend)
        val target = (store.prepareTarget("tree", "report.txt", "text/plain") as SafStoreResult.Success).value
        store.openTarget(target).use { it.write("new".encodeToByteArray()) }

        val cancelled = store.commit(target, SafConflictPolicy.CANCEL)
        assertTrue(cancelled is SafStoreResult.Failure && cancelled.error == SafStoreError.CONFLICT)
        assertEquals(backend.bytes("original").decodeToString(), "old")

        val renamed = store.commit(target, SafConflictPolicy.AUTO_RENAME) as SafStoreResult.Success
        assertEquals(renamed.value.displayName, "report (1).txt")
        assertEquals(backend.bytes("original").decodeToString(), "old")
        assertEquals(backend.bytes(renamed.value.documentId).decodeToString(), "new")
    }

    @Test
    fun `overwrite backs up original and rolls back when staging rename fails`() {
        val backend = FakeSafBackend()
        backend.addTree("tree")
        backend.addFile("original", "report.txt", "old".encodeToByteArray(), parent = "tree")
        val store = SafDocumentStore(backend)
        val target = (store.prepareTarget("tree", "report.txt", "text/plain") as SafStoreResult.Success).value
        store.openTarget(target).use { it.write("new".encodeToByteArray()) }
        backend.failRenameIds += target.temporary.documentId

        val result = store.commit(target, SafConflictPolicy.OVERWRITE)

        assertTrue(result is SafStoreResult.Failure && result.error == SafStoreError.COMMIT_FAILED)
        assertEquals(backend.childByName("tree", "report.txt")?.bytes?.decodeToString(), "old")
        assertFalse(backend.nodes.values.any { it.displayName.endsWith(".bak") })
    }

    @Test
    fun `overwrite restores original when backup cleanup fails`() {
        val backend = FakeSafBackend()
        backend.addTree("tree")
        backend.addFile("original", "report.txt", "old".encodeToByteArray(), parent = "tree")
        val store = SafDocumentStore(backend)
        val target = (store.prepareTarget("tree", "report.txt", "text/plain") as SafStoreResult.Success).value
        store.openTarget(target).use { it.write("new".encodeToByteArray()) }
        backend.failDeleteNames += { it.endsWith(".bak") }

        val result = store.commit(target, SafConflictPolicy.OVERWRITE)

        assertTrue(result is SafStoreResult.Failure && result.error == SafStoreError.COMMIT_FAILED)
        assertEquals(backend.childByName("tree", "report.txt")?.bytes?.decodeToString(), "old")
        assertFalse(backend.nodes.values.any { it.displayName.endsWith(".bak") })
    }

    @Test
    fun `cleanup failure is explicit and source stability supports metadata or digest`() {
        val backend = FakeSafBackend()
        backend.addTree("tree")
        backend.addFile("source", "source.bin", byteArrayOf(4, 5, 6))
        val store = SafDocumentStore(backend)
        val target = (store.prepareTarget("tree", "report.bin", "application/octet-stream") as SafStoreResult.Success).value
        backend.failDeleteIds += target.temporary.documentId

        val cleanup = store.cleanup(target)
        assertTrue(cleanup is SafStoreResult.Failure && cleanup.error == SafStoreError.CLEANUP_FAILED)

        val snapshot = (store.openSource("source") as SafStoreResult.Success).value
        assertTrue(store.verifySourceUnchanged(snapshot) is SafStoreResult.Success)
        backend.nodes.getValue("source").bytes = byteArrayOf(9, 9, 9)
        backend.nodes.getValue("source").modifiedAt++
        val changed = store.verifySourceUnchanged(snapshot)
        assertTrue(changed is SafStoreResult.Failure && changed.error == SafStoreError.SOURCE_CHANGED)
    }

    private class FakeSafBackend(
        private val createdCapabilities: Set<SafCapability> = setOf(
            SafCapability.READ,
            SafCapability.WRITE,
            SafCapability.DELETE,
            SafCapability.RENAME,
        ),
    ) : SafDocumentBackend {
        data class Node(
            val id: String,
            var displayName: String,
            val mimeType: String,
            var bytes: ByteArray,
            val parent: String?,
            val capabilities: Set<SafCapability>,
            var modifiedAt: Long = 1,
            val availableBytes: Long? = null,
        )

        val nodes = linkedMapOf<String, Node>()
        val failRenameIds = mutableSetOf<String>()
        val failDeleteIds = mutableSetOf<String>()
        val failDeleteNames = mutableListOf<(String) -> Boolean>()
        val failDocumentMetadataIds = mutableSetOf<String>()
        private var sequence = 0

        fun addTree(id: String, availableBytes: Long? = null) {
            nodes[id] = Node(
                id,
                id,
                SafDocumentStore.DIRECTORY_MIME_TYPE,
                ByteArray(0),
                null,
                setOf(SafCapability.READ, SafCapability.CREATE),
                availableBytes = availableBytes,
            )
        }

        fun addFile(id: String, name: String, bytes: ByteArray, parent: String? = null) {
            nodes[id] = Node(id, name, "application/octet-stream", bytes, parent, createdCapabilities)
        }

        fun bytes(id: String): ByteArray = nodes.getValue(id).bytes
        fun childByName(parent: String, name: String): Node? =
            nodes.values.firstOrNull { it.parent == parent && it.displayName == name }

        override fun sourceMetadata(documentId: String): SafDocumentMetadata? =
            metadataFor(documentId)?.copy(modifiedAtEpochMillis = null)

        override fun metadata(documentId: String): SafDocumentMetadata? {
            if (documentId in failDocumentMetadataIds) error("document-specific columns unavailable")
            return metadataFor(documentId)
        }

        private fun metadataFor(documentId: String): SafDocumentMetadata? = nodes[documentId]?.let {
            SafDocumentMetadata(
                documentId = it.id,
                displayName = it.displayName,
                mimeType = it.mimeType,
                sizeBytes = it.bytes.size.toLong().takeUnless { _ -> it.mimeType == SafDocumentStore.DIRECTORY_MIME_TYPE },
                modifiedAtEpochMillis = it.modifiedAt,
                capabilities = it.capabilities,
                availableBytes = it.availableBytes,
            )
        }

        override fun children(treeId: String): List<SafDocumentMetadata> =
            nodes.values.filter { it.parent == treeId }.map { metadata(it.id)!! }

        override fun create(treeId: String, mimeType: String, displayName: String): String? {
            val id = "created-${++sequence}"
            nodes[id] = Node(id, displayName, mimeType, ByteArray(0), treeId, createdCapabilities)
            return id
        }

        override fun openInput(documentId: String) = ByteArrayInputStream(nodes.getValue(documentId).bytes)

        override fun openOutput(documentId: String) = object : ByteArrayOutputStream() {
            override fun close() {
                super.close()
                nodes.getValue(documentId).bytes = toByteArray()
                nodes.getValue(documentId).modifiedAt++
            }
        }

        override fun rename(documentId: String, displayName: String): String? {
            if (documentId in failRenameIds) return null
            nodes.getValue(documentId).displayName = displayName
            return documentId
        }

        override fun delete(documentId: String): Boolean {
            if (documentId in failDeleteIds) return false
            if (failDeleteNames.any { predicate -> predicate(nodes[documentId]?.displayName.orEmpty()) }) return false
            return nodes.remove(documentId) != null
        }
    }
}
