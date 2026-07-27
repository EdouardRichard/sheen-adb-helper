package com.sheen.adb.core.internal

import com.sheen.adb.core.ApkSignatureRelation
import com.sheen.adb.core.ApplicationClassification
import com.sheen.adb.core.internal.applications.ApkInstallAcknowledgements
import com.sheen.adb.core.internal.applications.ApkInstallExecutionKind
import com.sheen.adb.core.internal.applications.ApkInstallFacts
import com.sheen.adb.core.internal.applications.ApkInstallPolicy
import com.sheen.adb.core.internal.applications.ApkInstallPolicyDecision
import com.sheen.adb.core.internal.applications.ApkInstallRejection
import com.sheen.adb.core.internal.applications.SystemPackageRemovalCapability
import org.testng.Assert.assertEquals
import org.testng.Assert.assertFalse
import org.testng.Assert.assertTrue
import org.testng.annotations.Test

class ApkInstallPolicyTest {
    @Test
    fun `absent package is ready for a standard standalone install`() {
        val decision = ApkInstallPolicy.evaluate(
            facts = facts(
                installed = false,
                signatureRelation = ApkSignatureRelation.NOT_INSTALLED,
            ),
            acknowledgements = ApkInstallAcknowledgements(),
        ) as ApkInstallPolicyDecision.Ready

        assertEquals(decision.plan.kind, ApkInstallExecutionKind.STANDARD)
        assertFalse(decision.plan.replaceExisting)
        assertFalse(decision.plan.allowDowngrade)
    }

    @Test
    fun `same signature requires force confirmation before replace or downgrade`() {
        val upgrade = facts(
            installed = true,
            signatureRelation = ApkSignatureRelation.SAME,
            installedVersionCode = 10,
            candidateVersionCode = 11,
        )
        assertTrue(
            ApkInstallPolicy.evaluate(upgrade, ApkInstallAcknowledgements())
                is ApkInstallPolicyDecision.AwaitForceConfirmation,
        )
        val replace = ApkInstallPolicy.evaluate(
            upgrade,
            ApkInstallAcknowledgements(forceInstall = true),
        ) as ApkInstallPolicyDecision.Ready
        assertEquals(replace.plan.kind, ApkInstallExecutionKind.REPLACE)
        assertTrue(replace.plan.replaceExisting)
        assertFalse(replace.plan.allowDowngrade)

        val downgrade = ApkInstallPolicy.evaluate(
            upgrade.copy(candidateVersionCode = 9),
            ApkInstallAcknowledgements(forceInstall = true),
        ) as ApkInstallPolicyDecision.Ready
        assertEquals(downgrade.plan.kind, ApkInstallExecutionKind.REPLACE_OR_DOWNGRADE)
        assertTrue(downgrade.plan.allowDowngrade)
        assertTrue(downgrade.plan.preservePrivateData)
    }

    @Test
    fun `signature mismatch cannot uninstall until both confirmations are bound`() {
        val mismatch = facts(
            installed = true,
            signatureRelation = ApkSignatureRelation.DIFFERENT,
        )

        assertTrue(
            ApkInstallPolicy.evaluate(mismatch, ApkInstallAcknowledgements())
                is ApkInstallPolicyDecision.AwaitForceConfirmation,
        )
        assertTrue(
            ApkInstallPolicy.evaluate(
                mismatch,
                ApkInstallAcknowledgements(forceInstall = true),
            ) is ApkInstallPolicyDecision.AwaitUninstallConfirmation,
        )

        val ready = ApkInstallPolicy.evaluate(
            mismatch,
            ApkInstallAcknowledgements(
                forceInstall = true,
                uninstallOldWithDataLoss = true,
            ),
        ) as ApkInstallPolicyDecision.Ready
        assertEquals(ready.plan.kind, ApkInstallExecutionKind.UNINSTALL_THEN_INSTALL)
        assertFalse(ready.plan.preservePrivateData)
        assertFalse(ready.plan.rollbackPromised)
    }

    @Test
    fun `system package remaining after attempted removal is a policy rejection`() {
        val systemTarget = facts(
            installed = true,
            classification = ApplicationClassification.SYSTEM,
            signatureRelation = ApkSignatureRelation.DIFFERENT,
            systemRemovalCapability = SystemPackageRemovalCapability.BASE_PACKAGE_REMAINS,
        )

        val decision = ApkInstallPolicy.evaluate(
            systemTarget,
            ApkInstallAcknowledgements(
                forceInstall = true,
                uninstallOldWithDataLoss = true,
            ),
        )
        assertEquals(
            (decision as ApkInstallPolicyDecision.Rejected).reason,
            ApkInstallRejection.SYSTEM_PACKAGE_REMAINS,
        )
    }

    @Test
    fun `every executable plan forbids root and policy bypass`() {
        val plans = listOf(
            facts(false, signatureRelation = ApkSignatureRelation.NOT_INSTALLED),
            facts(
                true,
                signatureRelation = ApkSignatureRelation.SAME,
                candidateVersionCode = 11,
                installedVersionCode = 10,
            ),
            facts(true, signatureRelation = ApkSignatureRelation.DIFFERENT),
        ).map { candidate ->
            ApkInstallPolicy.evaluate(
                candidate,
                ApkInstallAcknowledgements(
                    forceInstall = true,
                    uninstallOldWithDataLoss = true,
                ),
            ) as ApkInstallPolicyDecision.Ready
        }

        assertTrue(plans.none { it.plan.requiresRoot })
        assertTrue(plans.none { it.plan.bypassDevicePolicy })
    }

    private fun facts(
        installed: Boolean,
        classification: ApplicationClassification = ApplicationClassification.ORDINARY,
        signatureRelation: ApkSignatureRelation,
        installedVersionCode: Long? = null,
        candidateVersionCode: Long = 10,
        systemRemovalCapability: SystemPackageRemovalCapability = SystemPackageRemovalCapability.NOT_APPLICABLE,
    ) = ApkInstallFacts(
        installed = installed,
        targetClassification = classification,
        signatureRelation = signatureRelation,
        installedVersionCode = installedVersionCode,
        candidateVersionCode = candidateVersionCode,
        standalone = true,
        systemRemovalCapability = systemRemovalCapability,
    )
}
