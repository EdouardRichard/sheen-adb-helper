package com.sheen.adb.data

import java.io.OutputStream

enum class ComponentOutputResult {
    COMMITTED,
    FAILED,
    CANCELLED,
    NOT_ATTEMPTED,
    OUTCOME_UNKNOWN,
}

enum class ComponentOutputSummary {
    COMPLETE_SUCCESS,
    PARTIAL_SUCCESS,
    FAILED_NONE_COMMITTED,
    CANCELLED,
    OUTCOME_UNKNOWN,
}

data class ComponentOutputItem(
    val componentId: String,
    val displayName: String,
    val result: ComponentOutputResult,
    val committedDocument: SafDocumentMetadata? = null,
    val error: SafStoreError? = null,
)

data class StagedComponentOutput(
    val componentId: String,
    val expectedSizeBytes: Long?,
    val target: SafStagedTarget,
)

data class ComponentOutputReport(
    val summary: ComponentOutputSummary,
    val items: List<ComponentOutputItem>,
) {
    val committedComponents: List<ComponentOutputItem>
        get() = items.filter { it.result == ComponentOutputResult.COMMITTED }

    val notAttempted: List<ComponentOutputItem>
        get() = items.filter { it.result == ComponentOutputResult.NOT_ATTEMPTED }
}

class SafComponentOutputStore(
    private val documents: SafDocumentStore,
) {
    private val committedComponents = linkedMapOf<String, ComponentOutputItem>()
    private var current: StagedComponentOutput? = null

    fun createComponentDirectory(
        parentTreeId: String,
        displayName: String,
    ): SafStoreResult<SafPreparedDirectory> =
        documents.prepareDirectory(parentTreeId, displayName)

    fun stageComponent(
        directoryId: String,
        componentId: String,
        displayName: String,
        expectedSizeBytes: Long?,
        mimeType: String = APK_MIME_TYPE,
    ): SafStoreResult<StagedComponentOutput> {
        if (current != null || componentId.isBlank()) {
            return SafStoreResult.Failure(SafStoreError.CONFLICT)
        }
        return when (val staged = documents.prepareTarget(directoryId, displayName, mimeType)) {
            is SafStoreResult.Success -> SafStoreResult.Success(
                StagedComponentOutput(componentId, expectedSizeBytes, staged.value).also {
                    current = it
                },
            )
            is SafStoreResult.Failure -> staged
        }
    }

    fun openTarget(staged: StagedComponentOutput): OutputStream {
        require(current == staged) { "component output is not the active staged target" }
        return documents.openTarget(staged.target)
    }

    fun verifyComponent(staged: StagedComponentOutput): SafStoreResult<SafDocumentMetadata> {
        if (current != staged) return SafStoreResult.Failure(SafStoreError.CONFLICT)
        return documents.verifyTarget(staged.target, staged.expectedSizeBytes)
    }

    fun commitComponent(
        staged: StagedComponentOutput,
        conflictPolicy: SafConflictPolicy = SafConflictPolicy.AUTO_RENAME,
    ): SafStoreResult<ComponentOutputItem> {
        if (current != staged) return SafStoreResult.Failure(SafStoreError.CONFLICT)
        return when (val verified = verifyComponent(staged)) {
            is SafStoreResult.Failure -> verified
            is SafStoreResult.Success -> when (val committed = documents.commit(staged.target, conflictPolicy)) {
                is SafStoreResult.Failure -> committed
                is SafStoreResult.Success -> {
                    val item = ComponentOutputItem(
                        componentId = staged.componentId,
                        displayName = committed.value.displayName,
                        result = ComponentOutputResult.COMMITTED,
                        committedDocument = committed.value,
                    )
                    committedComponents[staged.componentId] = item
                    current = null
                    SafStoreResult.Success(item)
                }
            }
        }
    }

    fun cleanupCurrentTemporary(): SafStoreResult<Unit> {
        val staged = current ?: return SafStoreResult.Success(Unit)
        return documents.cleanup(staged.target).also { result ->
            if (result is SafStoreResult.Success) current = null
        }
    }

    fun retainCommitted(): List<ComponentOutputItem> = committedComponents.values.toList()

    fun failedItem(
        componentId: String,
        displayName: String,
        error: SafStoreError,
    ): ComponentOutputItem = ComponentOutputItem(
        componentId = componentId,
        displayName = displayName,
        result = ComponentOutputResult.FAILED,
        error = error,
    )

    fun terminalReport(
        expectedComponents: List<Pair<String, String>>,
        terminalResult: ComponentOutputResult,
        failedComponentId: String? = null,
        error: SafStoreError? = null,
    ): ComponentOutputReport {
        val items = expectedComponents.map { (componentId, displayName) ->
            committedComponents[componentId] ?: ComponentOutputItem(
                componentId = componentId,
                displayName = displayName,
                result = if (componentId == failedComponentId) terminalResult else {
                    ComponentOutputResult.NOT_ATTEMPTED
                },
                error = error.takeIf { componentId == failedComponentId },
            )
        }
        val committed = items.count { it.result == ComponentOutputResult.COMMITTED }
        val summary = when {
            items.any { it.result == ComponentOutputResult.OUTCOME_UNKNOWN } ->
                ComponentOutputSummary.OUTCOME_UNKNOWN
            committed == items.size && items.isNotEmpty() -> ComponentOutputSummary.COMPLETE_SUCCESS
            committed > 0 -> ComponentOutputSummary.PARTIAL_SUCCESS
            items.any { it.result == ComponentOutputResult.CANCELLED } ->
                ComponentOutputSummary.CANCELLED
            else -> ComponentOutputSummary.FAILED_NONE_COMMITTED
        }
        return ComponentOutputReport(summary, items)
    }

    private companion object {
        const val APK_MIME_TYPE = "application/vnd.android.package-archive"
    }
}
