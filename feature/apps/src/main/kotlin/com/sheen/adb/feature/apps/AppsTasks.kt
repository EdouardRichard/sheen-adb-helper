package com.sheen.adb.feature.apps

import com.sheen.adb.core.AdbOperationResult
import com.sheen.adb.core.AdbSessionManager
import com.sheen.adb.core.ApkExtractionRequest
import com.sheen.adb.core.ApkInstallMode
import com.sheen.adb.core.ApkInstallRequest
import com.sheen.adb.core.ApkInstallResult
import com.sheen.adb.core.ApplicationUninstallPreparation
import com.sheen.adb.core.ApplicationUninstallRequest
import com.sheen.adb.core.ApplicationUninstallResult
import com.sheen.adb.data.ComponentOutputResult
import com.sheen.adb.data.SafComponentOutputStore
import com.sheen.adb.data.SafSource
import com.sheen.adb.data.SafStoreResult
import java.security.MessageDigest
import java.util.UUID

internal class AppsTasks(
    private val manager: AdbSessionManager,
) {
    val extractionTaskKind: AppsTaskKind get() = AppsTaskKind.APK_EXTRACTION
    val installationTaskKind: AppsTaskKind get() = AppsTaskKind.APK_INSTALLATION

    private var pendingUninstall: ApplicationUninstallPreparation? = null
    private var installStep: AppsInstallStep? = null
    private var confirmationNonce: String? = null

    suspend fun extractApk(
        request: ApkExtractionRequest,
        destinationTreeId: String,
        directoryDisplayName: String,
        outputs: SafComponentOutputStore,
        onPhase: (AppsTaskPhase) -> Unit,
    ): AppsComponentResult {
        val opened = manager.openApkExtraction(request)
        val handle = (opened as? AdbOperationResult.Success)?.value
            ?: return AppsComponentResult.NoneCommittedFailure(0, cleanupConfirmed = true, resourceUncertain = false)
        handle.use {
            val expectedComponents = handle.components
            val directoryId = if (expectedComponents.size > 1) {
                val prepared = outputs.createComponentDirectory(destinationTreeId, directoryDisplayName)
                (prepared as? SafStoreResult.Success)?.value?.directoryId
                    ?: return AppsComponentResult.NoneCommittedFailure(
                        expectedComponents.size,
                        cleanupConfirmed = true,
                        resourceUncertain = false,
                    )
            } else {
                destinationTreeId
            }
            var committedComponents = 0
            var cleanupConfirmed = true
            expectedComponents.forEach { component ->
                onPhase(AppsTaskPhase.Preparing)
                val staged = outputs.stageComponent(
                    directoryId = directoryId,
                    componentId = component.componentId,
                    displayName = component.displayName,
                    expectedSizeBytes = component.expectedSizeBytes,
                )
                val target = (staged as? SafStoreResult.Success)?.value ?: return@forEach
                val transferred = outputs.openTarget(target).use { destination ->
                    handle.transfer(
                        componentId = component.componentId,
                        destination = destination,
                        progress = { value ->
                            onPhase(
                                AppsTaskPhase.Transferring(
                                    componentId = component.componentId,
                                    transferredBytes = value.transferredBytes,
                                ),
                            )
                        },
                    )
                }
                if (transferred !is AdbOperationResult.Success) {
                    cleanupConfirmed = outputs.cleanupCurrentTemporary() is SafStoreResult.Success &&
                        cleanupConfirmed
                    return@forEach
                }
                onPhase(AppsTaskPhase.Writing)
                if (outputs.commitComponent(target) is SafStoreResult.Success) {
                    committedComponents++
                } else {
                    cleanupConfirmed = outputs.cleanupCurrentTemporary() is SafStoreResult.Success &&
                        cleanupConfirmed
                }
            }
            val resourceUncertain = !cleanupConfirmed
            return when {
                committedComponents == expectedComponents.size ->
                    AppsComponentResult.CompleteSuccess(expectedComponents.size)
                committedComponents > 0 ->
                    AppsComponentResult.PartialSuccess(
                        expectedComponents = expectedComponents.size,
                        committedComponents = committedComponents,
                        cleanupConfirmed = cleanupConfirmed,
                        resourceUncertain = resourceUncertain,
                    )
                else ->
                    AppsComponentResult.NoneCommittedFailure(
                        expectedComponents = expectedComponents.size,
                        cleanupConfirmed = cleanupConfirmed,
                        resourceUncertain = resourceUncertain,
                    )
            }
        }
    }

    suspend fun installApk(
        source: SafSource,
        expectedSessionId: String,
        userId: Int,
        mode: ApkInstallMode,
        expectedPackageName: String?,
        onPhase: (AppsTaskPhase) -> Unit,
    ): ApkInstallResult? {
        val request = ApkInstallRequest(
            expectedSessionId = expectedSessionId,
            userId = userId,
            displayName = source.metadata.displayName,
            sourceFingerprint = fingerprint(source.metadata.documentId),
            sourceSizeBytes = source.metadata.sizeBytes,
            source = source::open,
            mode = mode,
            expectedPackageName = expectedPackageName,
        )
        return when (
            val result = manager.installApk(
                request,
                progress = {
                    onPhase(
                        if (it.name == "STAGING") AppsTaskPhase.Transferring(null, 0L)
                        else AppsTaskPhase.Writing
                    )
                },
            )
        ) {
            is AdbOperationResult.Success -> result.value
            else -> null
        }
    }

    suspend fun requestUninstall(
        expectedSessionId: String,
        userId: Int,
        packageName: String,
        expectedGeneration: Long,
    ): ApplicationUninstallPreparation? {
        val result = manager.prepareApplicationUninstall(
            expectedSessionId,
            userId,
            packageName,
            expectedGeneration,
        )
        return (result as? AdbOperationResult.Success)?.value?.also {
            pendingUninstall = it
            confirmationNonce = it.confirmationNonce
        }
    }

    suspend fun confirmUninstall(
        deletePrivateDataAcknowledged: Boolean,
    ): ApplicationUninstallResult? {
        val prepared = pendingUninstall ?: return null
        if (prepared.confirmationNonce != confirmationNonce) return null
        pendingUninstall = null
        confirmationNonce = null
        val request = ApplicationUninstallRequest(
            expectedSessionId = prepared.expectedSessionId,
            userId = prepared.userId,
            packageName = prepared.packageName,
            expectedGeneration = prepared.expectedGeneration,
            confirmationNonce = prepared.confirmationNonce,
            deletePrivateDataAcknowledged = deletePrivateDataAcknowledged,
        )
        return (manager.uninstallApplication(request) as? AdbOperationResult.Success)?.value
    }

    fun requestForceInstall(signatureMismatch: Boolean): AppsInstallStep {
        installStep = if (signatureMismatch) {
            AppsInstallStep.AWAITING_SIGNATURE_MISMATCH_CONFIRMATION
        } else {
            AppsInstallStep.AWAITING_REPLACE_CONFIRMATION
        }
        confirmationNonce = UUID.randomUUID().toString()
        return checkNotNull(installStep)
    }

    fun confirmSignatureMismatch(nonce: String): Boolean {
        if (installStep != AppsInstallStep.AWAITING_SIGNATURE_MISMATCH_CONFIRMATION ||
            confirmationNonce != nonce
        ) return false
        installStep = AppsInstallStep.UNINSTALLING_OLD
        confirmationNonce = null
        return true
    }

    fun markInstallingNew() {
        if (installStep == AppsInstallStep.UNINSTALLING_OLD) {
            installStep = AppsInstallStep.INSTALLING_NEW
        }
    }

    fun cancelPendingConfirmation() {
        pendingUninstall = null
        confirmationNonce = null
        installStep = null
    }

    private fun fingerprint(value: String): String =
        MessageDigest.getInstance("SHA-256")
            .digest(value.toByteArray())
            .joinToString("") { "%02x".format(it) }
}
