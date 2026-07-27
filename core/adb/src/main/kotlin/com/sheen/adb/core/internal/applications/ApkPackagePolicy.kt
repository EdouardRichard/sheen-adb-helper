package com.sheen.adb.core.internal.applications

import com.sheen.adb.core.ApkComponentRole
import com.sheen.adb.core.ApkSignatureRelation
import com.sheen.adb.core.ApplicationClassification
import java.security.MessageDigest

internal enum class ApkArchiveShape {
    Standalone,
    IsolatedSplit,
    BaseRequiringSplits,
    Unknown,
}

internal sealed interface ApkInputDecision {
    data object Accepted : ApkInputDecision
    data object UnsupportedContainer : ApkInputDecision
    data object IsolatedSplit : ApkInputDecision
    data object MissingRequiredSplits : ApkInputDecision
}

internal class InstalledApkComponent(
    val componentId: String,
    val role: ApkComponentRole,
    val displayName: String,
    internal val remotePath: String,
) {
    override fun toString(): String =
        "InstalledApkComponent(componentId=$componentId, role=$role, displayName=$displayName)"
}

internal sealed interface InstalledComponentDecision {
    data class Accepted(val components: List<InstalledApkComponent>) : InstalledComponentDecision
    data object MissingBase : InstalledComponentDecision
    data object AmbiguousBase : InstalledComponentDecision
}

internal object ApkPackagePolicy {
    fun resolveInstalledComponents(paths: List<String>): InstalledComponentDecision {
        val normalized = paths.map(String::trim).filter(String::isNotEmpty).distinct()
        val baseCandidates = normalized.filter { path ->
            val name = path.substringAfterLast('/')
            name == "base.apk" || name.endsWith("-base.apk")
        }
        if (baseCandidates.isEmpty()) return InstalledComponentDecision.MissingBase
        if (baseCandidates.size != 1) return InstalledComponentDecision.AmbiguousBase

        val base = baseCandidates.single()
        val ordered = listOf(base) + normalized.filterNot(base::equals)
        return InstalledComponentDecision.Accepted(
            ordered.map { path ->
                InstalledApkComponent(
                    componentId = opaqueComponentId(path),
                    role = if (path == base) ApkComponentRole.BASE else ApkComponentRole.SPLIT,
                    displayName = path.substringAfterLast('/').ifBlank { "component.apk" },
                    remotePath = path,
                )
            },
        )
    }

    fun validateSingleInstallInput(
        displayName: String,
        archiveShape: ApkArchiveShape,
    ): ApkInputDecision {
        val lowerName = displayName.trim().lowercase()
        if (!lowerName.endsWith(".apk")) return ApkInputDecision.UnsupportedContainer
        return when (archiveShape) {
            ApkArchiveShape.Standalone -> ApkInputDecision.Accepted
            ApkArchiveShape.IsolatedSplit -> ApkInputDecision.IsolatedSplit
            ApkArchiveShape.BaseRequiringSplits -> ApkInputDecision.MissingRequiredSplits
            ApkArchiveShape.Unknown -> ApkInputDecision.Accepted
        }
    }

    fun signatureRelation(
        installedSigners: List<ByteArray>?,
        candidateSigners: List<ByteArray>?,
    ): ApkSignatureRelation {
        if (installedSigners == null || candidateSigners == null) return ApkSignatureRelation.UNKNOWN
        val installed = installedSigners.map(::digest).sorted()
        val candidate = candidateSigners.map(::digest).sorted()
        return if (installed == candidate) ApkSignatureRelation.SAME else ApkSignatureRelation.DIFFERENT
    }

    private fun opaqueComponentId(remotePath: String): String =
        digest(remotePath.toByteArray()).take(24)

    private fun digest(bytes: ByteArray): String =
        MessageDigest.getInstance("SHA-256")
            .digest(bytes)
            .joinToString(separator = "") { "%02x".format(it) }
}

internal enum class SystemPackageRemovalCapability {
    NOT_APPLICABLE,
    REMOVABLE_FOR_USER,
    BASE_PACKAGE_REMAINS,
}

internal data class ApkInstallFacts(
    val installed: Boolean,
    val targetClassification: ApplicationClassification,
    val signatureRelation: ApkSignatureRelation,
    val installedVersionCode: Long?,
    val candidateVersionCode: Long,
    val standalone: Boolean,
    val systemRemovalCapability: SystemPackageRemovalCapability,
)

internal data class ApkInstallAcknowledgements(
    val forceInstall: Boolean = false,
    val uninstallOldWithDataLoss: Boolean = false,
)

internal enum class ApkInstallExecutionKind {
    STANDARD,
    REPLACE,
    REPLACE_OR_DOWNGRADE,
    UNINSTALL_THEN_INSTALL,
}

internal data class ApkInstallPlan(
    val kind: ApkInstallExecutionKind,
    val replaceExisting: Boolean,
    val allowDowngrade: Boolean,
    val preservePrivateData: Boolean,
    val rollbackPromised: Boolean = false,
    val requiresRoot: Boolean = false,
    val bypassDevicePolicy: Boolean = false,
)

internal enum class ApkInstallRejection {
    INPUT_NOT_STANDALONE,
    SIGNATURE_UNKNOWN,
    SYSTEM_PACKAGE_REMAINS,
}

internal sealed interface ApkInstallPolicyDecision {
    data class Ready(val plan: ApkInstallPlan) : ApkInstallPolicyDecision
    data object AwaitForceConfirmation : ApkInstallPolicyDecision
    data object AwaitUninstallConfirmation : ApkInstallPolicyDecision
    data class Rejected(val reason: ApkInstallRejection) : ApkInstallPolicyDecision
}

internal object ApkInstallPolicy {
    fun evaluate(
        facts: ApkInstallFacts,
        acknowledgements: ApkInstallAcknowledgements,
    ): ApkInstallPolicyDecision {
        if (!facts.standalone) {
            return ApkInstallPolicyDecision.Rejected(ApkInstallRejection.INPUT_NOT_STANDALONE)
        }
        if (!facts.installed || facts.signatureRelation == ApkSignatureRelation.NOT_INSTALLED) {
            return ApkInstallPolicyDecision.Ready(
                ApkInstallPlan(
                    kind = ApkInstallExecutionKind.STANDARD,
                    replaceExisting = false,
                    allowDowngrade = false,
                    preservePrivateData = false,
                ),
            )
        }
        if (!acknowledgements.forceInstall) return ApkInstallPolicyDecision.AwaitForceConfirmation

        if (facts.signatureRelation == ApkSignatureRelation.SAME) {
            val downgrade = facts.installedVersionCode?.let { facts.candidateVersionCode < it } == true
            return ApkInstallPolicyDecision.Ready(
                ApkInstallPlan(
                    kind = if (downgrade) {
                        ApkInstallExecutionKind.REPLACE_OR_DOWNGRADE
                    } else {
                        ApkInstallExecutionKind.REPLACE
                    },
                    replaceExisting = true,
                    allowDowngrade = downgrade,
                    preservePrivateData = true,
                ),
            )
        }

        if (!acknowledgements.uninstallOldWithDataLoss) {
            return ApkInstallPolicyDecision.AwaitUninstallConfirmation
        }
        if (facts.systemRemovalCapability == SystemPackageRemovalCapability.BASE_PACKAGE_REMAINS) {
            return ApkInstallPolicyDecision.Rejected(ApkInstallRejection.SYSTEM_PACKAGE_REMAINS)
        }
        return ApkInstallPolicyDecision.Ready(
            ApkInstallPlan(
                kind = ApkInstallExecutionKind.UNINSTALL_THEN_INSTALL,
                replaceExisting = false,
                allowDowngrade = false,
                preservePrivateData = false,
            ),
        )
    }
}
