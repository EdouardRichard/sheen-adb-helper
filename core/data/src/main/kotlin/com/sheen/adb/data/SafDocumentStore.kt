package com.sheen.adb.data

import android.content.ContentResolver
import android.content.Context
import android.database.Cursor
import android.net.Uri
import android.provider.DocumentsContract
import android.provider.OpenableColumns
import java.io.InputStream
import java.io.OutputStream
import java.security.MessageDigest
import java.util.UUID

enum class SafCapability { READ, WRITE, CREATE, DELETE, RENAME }

enum class SafStoreError {
    NOT_FOUND,
    PERMISSION_DENIED,
    SPACE_INSUFFICIENT,
    CONFLICT,
    PROVIDER_UNSUPPORTED,
    SOURCE_CHANGED,
    INTEGRITY_UNAVAILABLE,
    COMMIT_FAILED,
    CLEANUP_FAILED,
    IO_FAILURE,
}

sealed interface SafStoreResult<out T> {
    data class Success<T>(val value: T) : SafStoreResult<T>
    data class Failure(val error: SafStoreError) : SafStoreResult<Nothing>
}

enum class SafConflictPolicy { CANCEL, OVERWRITE, AUTO_RENAME }

data class SafDocumentMetadata(
    val documentId: String,
    val displayName: String,
    val mimeType: String,
    val sizeBytes: Long?,
    val modifiedAtEpochMillis: Long?,
    val capabilities: Set<SafCapability>,
    val availableBytes: Long? = null,
)

class SafSource internal constructor(
    val metadata: SafDocumentMetadata,
    internal val baselineDigest: ByteArray?,
    private val opener: () -> InputStream,
) {
    fun open(): InputStream = opener()
}

data class SafStagedTarget(
    val treeId: String,
    val temporary: SafDocumentMetadata,
    val finalDisplayName: String,
    val mimeType: String,
    val original: SafDocumentMetadata?,
    val availableBytes: Long?,
)

internal interface SafDocumentBackend {
    fun sourceMetadata(documentId: String): SafDocumentMetadata?
    fun metadata(documentId: String): SafDocumentMetadata?
    fun children(treeId: String): List<SafDocumentMetadata>
    fun create(treeId: String, mimeType: String, displayName: String): String?
    fun openInput(documentId: String): InputStream?
    fun openOutput(documentId: String): OutputStream?
    fun rename(documentId: String, displayName: String): String?
    fun delete(documentId: String): Boolean
}

internal object SafDocumentReferencePolicy {
    fun requiresTreeRootResolution(isTreeUri: Boolean, isDocumentUri: Boolean): Boolean =
        isTreeUri && !isDocumentUri
}

class SafDocumentStore internal constructor(
    private val backend: SafDocumentBackend,
) {
    constructor(context: Context) : this(AndroidSafDocumentBackend(context.applicationContext))

    private val temporaryIds = linkedSetOf<String>()

    fun openSource(documentId: String): SafStoreResult<SafSource> = safely {
        val metadata = backend.sourceMetadata(documentId)
            ?: return@safely SafStoreResult.Failure(SafStoreError.NOT_FOUND)
        if (SafCapability.READ !in metadata.capabilities) {
            return@safely SafStoreResult.Failure(SafStoreError.PERMISSION_DENIED)
        }
        val baselineDigest = if (metadata.hasReliableStabilityMetadata()) {
            null
        } else {
            backend.openInput(documentId)?.use(::sha256)
                ?: return@safely SafStoreResult.Failure(SafStoreError.INTEGRITY_UNAVAILABLE)
        }
        SafStoreResult.Success(
            SafSource(metadata, baselineDigest) {
                backend.openInput(documentId) ?: throw IllegalStateException("source unavailable")
            },
        )
    }

    fun verifySourceUnchanged(source: SafSource): SafStoreResult<Unit> = safely {
        val current = backend.sourceMetadata(source.metadata.documentId)
            ?: return@safely SafStoreResult.Failure(SafStoreError.SOURCE_CHANGED)
        val stable = if (source.metadata.hasReliableStabilityMetadata()) {
            source.metadata.sizeBytes == current.sizeBytes &&
                source.metadata.modifiedAtEpochMillis == current.modifiedAtEpochMillis
        } else {
            val currentDigest = backend.openInput(current.documentId)?.use(::sha256)
                ?: return@safely SafStoreResult.Failure(SafStoreError.INTEGRITY_UNAVAILABLE)
            source.baselineDigest?.contentEquals(currentDigest) == true
        }
        if (stable) SafStoreResult.Success(Unit) else SafStoreResult.Failure(SafStoreError.SOURCE_CHANGED)
    }

    fun prepareTarget(
        treeId: String,
        finalDisplayName: String,
        mimeType: String,
    ): SafStoreResult<SafStagedTarget> = safely {
        if (!isSafeDisplayName(finalDisplayName)) {
            return@safely SafStoreResult.Failure(SafStoreError.PROVIDER_UNSUPPORTED)
        }
        val tree = backend.metadata(treeId) ?: return@safely SafStoreResult.Failure(SafStoreError.NOT_FOUND)
        if (SafCapability.CREATE !in tree.capabilities) {
            return@safely SafStoreResult.Failure(SafStoreError.PROVIDER_UNSUPPORTED)
        }
        val children = backend.children(treeId)
        val temporaryName = generateSequence {
            ".sheen-${UUID.randomUUID().toString().replace("-", "").take(16)}.part"
        }.first { candidate -> children.none { it.displayName == candidate } }
        val temporaryId = backend.create(treeId, mimeType, temporaryName)
            ?: return@safely SafStoreResult.Failure(SafStoreError.PROVIDER_UNSUPPORTED)
        val temporary = backend.metadata(temporaryId)
        val required = setOf(SafCapability.WRITE, SafCapability.DELETE, SafCapability.RENAME)
        if (temporary == null || !temporary.capabilities.containsAll(required)) {
            backend.delete(temporaryId)
            return@safely SafStoreResult.Failure(SafStoreError.PROVIDER_UNSUPPORTED)
        }
        temporaryIds += temporaryId
        SafStoreResult.Success(
            SafStagedTarget(
                treeId = treeId,
                temporary = temporary,
                finalDisplayName = finalDisplayName,
                mimeType = mimeType,
                original = children.firstOrNull { it.displayName == finalDisplayName },
                availableBytes = tree.availableBytes,
            ),
        )
    }

    fun openTarget(target: SafStagedTarget): OutputStream =
        backend.openOutput(target.temporary.documentId)
            ?: throw IllegalStateException("target unavailable")

    fun commit(
        target: SafStagedTarget,
        conflictPolicy: SafConflictPolicy,
    ): SafStoreResult<SafDocumentMetadata> = safely {
        val currentChildren = backend.children(target.treeId)
        val original = currentChildren.firstOrNull { it.displayName == target.finalDisplayName }
        if (original != null && conflictPolicy == SafConflictPolicy.CANCEL) {
            return@safely SafStoreResult.Failure(SafStoreError.CONFLICT)
        }
        val finalName = if (original != null && conflictPolicy == SafConflictPolicy.AUTO_RENAME) {
            autoRenamedName(target.finalDisplayName, currentChildren.mapTo(mutableSetOf()) { it.displayName })
                ?: return@safely SafStoreResult.Failure(SafStoreError.CONFLICT)
        } else {
            target.finalDisplayName
        }
        val committedId = if (original != null && conflictPolicy == SafConflictPolicy.OVERWRITE) {
            commitOverwrite(target, original, finalName)
                ?: return@safely SafStoreResult.Failure(SafStoreError.COMMIT_FAILED)
        } else {
            backend.rename(target.temporary.documentId, finalName)
                ?: return@safely SafStoreResult.Failure(SafStoreError.COMMIT_FAILED)
        }
        temporaryIds -= target.temporary.documentId
        val committed = backend.metadata(committedId)
            ?: return@safely SafStoreResult.Failure(SafStoreError.COMMIT_FAILED)
        SafStoreResult.Success(committed)
    }

    fun cleanup(target: SafStagedTarget): SafStoreResult<Unit> = safely {
        val removed = backend.delete(target.temporary.documentId)
        if (removed) {
            temporaryIds -= target.temporary.documentId
            SafStoreResult.Success(Unit)
        } else {
            SafStoreResult.Failure(SafStoreError.CLEANUP_FAILED)
        }
    }

    fun clearTrackedTemporaries(): Boolean {
        val snapshot = temporaryIds.toList()
        val cleared = snapshot.all { id -> backend.delete(id).also { if (it) temporaryIds -= id } }
        return cleared && temporaryIds.isEmpty()
    }

    private fun commitOverwrite(
        target: SafStagedTarget,
        original: SafDocumentMetadata,
        finalName: String,
    ): String? {
        val names = backend.children(target.treeId).mapTo(mutableSetOf()) { it.displayName }
        val backupName = generateSequence {
            ".sheen-${UUID.randomUUID().toString().replace("-", "").take(16)}.bak"
        }.first { it !in names }
        val backupId = backend.rename(original.documentId, backupName) ?: return null
        val committedId = backend.rename(target.temporary.documentId, finalName)
        if (committedId == null) {
            backend.rename(backupId, original.displayName)
            return null
        }
        if (!backend.delete(backupId)) {
            if (backend.delete(committedId)) {
                backend.rename(backupId, original.displayName)
            }
            return null
        }
        return committedId
    }

    private fun autoRenamedName(requestedName: String, existing: Set<String>): String? {
        val dot = requestedName.lastIndexOf('.').takeIf { it > 0 }
        val stem = dot?.let { requestedName.substring(0, it) } ?: requestedName
        val extension = dot?.let { requestedName.substring(it) }.orEmpty()
        return (1..MAX_AUTO_RENAME_ATTEMPTS)
            .asSequence()
            .map { "$stem ($it)$extension" }
            .firstOrNull { it !in existing }
    }

    private fun SafDocumentMetadata.hasReliableStabilityMetadata(): Boolean =
        sizeBytes != null && sizeBytes >= 0L && modifiedAtEpochMillis != null && modifiedAtEpochMillis > 0L

    private fun sha256(input: InputStream): ByteArray {
        val digest = MessageDigest.getInstance("SHA-256")
        val buffer = ByteArray(TRANSFER_CHUNK_BYTES)
        while (true) {
            val count = input.read(buffer)
            if (count < 0) break
            if (count > 0) digest.update(buffer, 0, count)
        }
        return digest.digest()
    }

    private fun isSafeDisplayName(value: String): Boolean =
        value.isNotBlank() && value != "." && value != ".." && !value.contains('/') && !value.contains('\u0000')

    private inline fun <T> safely(block: () -> SafStoreResult<T>): SafStoreResult<T> = try {
        block()
    } catch (_: SecurityException) {
        SafStoreResult.Failure(SafStoreError.PERMISSION_DENIED)
    } catch (_: Throwable) {
        SafStoreResult.Failure(SafStoreError.IO_FAILURE)
    }

    companion object {
        const val DIRECTORY_MIME_TYPE = DocumentsContract.Document.MIME_TYPE_DIR
        private const val TRANSFER_CHUNK_BYTES = 64 * 1024
        private const val MAX_AUTO_RENAME_ATTEMPTS = 1_000
    }
}

private class AndroidSafDocumentBackend(
    private val context: Context,
) : SafDocumentBackend {
    private val resolver: ContentResolver = context.contentResolver

    override fun sourceMetadata(documentId: String): SafDocumentMetadata? {
        val uri = Uri.parse(documentId)
        var displayName: String? = null
        var sizeBytes: Long? = null
        resolver.query(uri, SOURCE_PROJECTION, null, null, null)?.use { cursor ->
            if (cursor.moveToFirst()) {
                val displayNameIndex = cursor.getColumnIndex(OpenableColumns.DISPLAY_NAME)
                val sizeIndex = cursor.getColumnIndex(OpenableColumns.SIZE)
                displayName = displayNameIndex
                    .takeIf { it >= 0 && !cursor.isNull(it) }
                    ?.let(cursor::getString)
                sizeBytes = sizeIndex
                    .takeIf { it >= 0 && !cursor.isNull(it) }
                    ?.let(cursor::getLong)
            }
        }
        val safeDisplayName = displayName
            ?.takeIf { it.isNotBlank() }
            ?: "selected-file"
        return SafDocumentMetadata(
            documentId = documentId,
            displayName = safeDisplayName,
            mimeType = resolver.getType(uri) ?: "application/octet-stream",
            sizeBytes = sizeBytes,
            modifiedAtEpochMillis = null,
            capabilities = setOf(SafCapability.READ),
        )
    }

    override fun metadata(documentId: String): SafDocumentMetadata? {
        val uri = documentUri(documentId)
        return resolver.query(uri, PROJECTION, null, null, null)?.use { cursor ->
            if (!cursor.moveToFirst()) null else cursor.toMetadata(uri.toString())
        }
    }

    override fun children(treeId: String): List<SafDocumentMetadata> {
        val tree = Uri.parse(treeId)
        val parentId = DocumentsContract.getTreeDocumentId(tree)
        val children = DocumentsContract.buildChildDocumentsUriUsingTree(tree, parentId)
        return resolver.query(children, PROJECTION, null, null, null)?.use { cursor ->
            buildList {
                while (cursor.moveToNext()) {
                    val childId = cursor.getString(cursor.getColumnIndexOrThrow(DocumentsContract.Document.COLUMN_DOCUMENT_ID))
                    val childUri = DocumentsContract.buildDocumentUriUsingTree(tree, childId)
                    add(cursor.toMetadata(childUri.toString()))
                }
            }
        }.orEmpty()
    }

    override fun create(treeId: String, mimeType: String, displayName: String): String? {
        val parent = documentUri(treeId)
        return DocumentsContract.createDocument(resolver, parent, mimeType, displayName)?.toString()
    }

    override fun openInput(documentId: String): InputStream? = resolver.openInputStream(documentUri(documentId))

    override fun openOutput(documentId: String): OutputStream? =
        resolver.openOutputStream(documentUri(documentId), "wt")

    override fun rename(documentId: String, displayName: String): String? =
        DocumentsContract.renameDocument(resolver, documentUri(documentId), displayName)?.toString()

    override fun delete(documentId: String): Boolean =
        DocumentsContract.deleteDocument(resolver, documentUri(documentId))

    private fun documentUri(reference: String): Uri {
        val uri = Uri.parse(reference)
        return if (
            SafDocumentReferencePolicy.requiresTreeRootResolution(
                isTreeUri = DocumentsContract.isTreeUri(uri),
                isDocumentUri = DocumentsContract.isDocumentUri(context, uri),
            )
        ) {
            DocumentsContract.buildDocumentUriUsingTree(uri, DocumentsContract.getTreeDocumentId(uri))
        } else {
            uri
        }
    }

    private fun Cursor.toMetadata(reference: String): SafDocumentMetadata {
        val flags = getLong(getColumnIndexOrThrow(DocumentsContract.Document.COLUMN_FLAGS))
        val capabilities = buildSet {
            add(SafCapability.READ)
            if (flags and DocumentsContract.Document.FLAG_SUPPORTS_WRITE.toLong() != 0L) add(SafCapability.WRITE)
            if (flags and DocumentsContract.Document.FLAG_DIR_SUPPORTS_CREATE.toLong() != 0L) add(SafCapability.CREATE)
            if (flags and DocumentsContract.Document.FLAG_SUPPORTS_DELETE.toLong() != 0L) add(SafCapability.DELETE)
            if (flags and DocumentsContract.Document.FLAG_SUPPORTS_RENAME.toLong() != 0L) add(SafCapability.RENAME)
        }
        val sizeIndex = getColumnIndex(DocumentsContract.Document.COLUMN_SIZE)
        val modifiedIndex = getColumnIndex(DocumentsContract.Document.COLUMN_LAST_MODIFIED)
        return SafDocumentMetadata(
            documentId = reference,
            displayName = getString(getColumnIndexOrThrow(DocumentsContract.Document.COLUMN_DISPLAY_NAME)),
            mimeType = getString(getColumnIndexOrThrow(DocumentsContract.Document.COLUMN_MIME_TYPE)),
            sizeBytes = sizeIndex.takeIf { it >= 0 && !isNull(it) }?.let(::getLong),
            modifiedAtEpochMillis = modifiedIndex.takeIf { it >= 0 && !isNull(it) }?.let(::getLong),
            capabilities = capabilities,
        )
    }

    private companion object {
        val SOURCE_PROJECTION = arrayOf(
            OpenableColumns.DISPLAY_NAME,
            OpenableColumns.SIZE,
        )

        val PROJECTION = arrayOf(
            DocumentsContract.Document.COLUMN_DOCUMENT_ID,
            DocumentsContract.Document.COLUMN_DISPLAY_NAME,
            DocumentsContract.Document.COLUMN_MIME_TYPE,
            DocumentsContract.Document.COLUMN_SIZE,
            DocumentsContract.Document.COLUMN_LAST_MODIFIED,
            DocumentsContract.Document.COLUMN_FLAGS,
        )
    }
}
