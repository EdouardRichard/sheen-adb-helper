package com.sheen.adb.core.internal.applications

import com.sheen.adb.core.ApkComponentRole
import com.sheen.adb.core.ApkSignatureRelation
import org.testng.Assert.assertEquals
import org.testng.Assert.assertFalse
import org.testng.Assert.assertNotEquals
import org.testng.Assert.assertTrue
import org.testng.annotations.Test

class ApkPackagePolicyTest {
    @Test
    fun `versioned package snapshots identify a single added upgraded or downgraded package`() {
        val before = ApplicationPackageProtocol.parseVersionedPackages(
            "package:com.example.one versionCode:10\npackage:com.example.two versionCode:20\n",
        )
        val upgraded = ApplicationPackageProtocol.parseVersionedPackages(
            "package:com.example.one versionCode:11\npackage:com.example.two versionCode:20\n",
        )
        val downgraded = ApplicationPackageProtocol.parseVersionedPackages(
            "package:com.example.one versionCode:9\npackage:com.example.two versionCode:20\n",
        )
        val added = ApplicationPackageProtocol.parseVersionedPackages(
            "package:com.example.one versionCode:10\npackage:com.example.two versionCode:20\n" +
                "package:com.example.three versionCode:1\n",
        )

        assertEquals(ApplicationPackageProtocol.singleInstalledOrChanged(before, upgraded), "com.example.one")
        assertEquals(ApplicationPackageProtocol.singleInstalledOrChanged(before, downgraded), "com.example.one")
        assertEquals(ApplicationPackageProtocol.singleInstalledOrChanged(before, added), "com.example.three")
        assertEquals(ApplicationPackageProtocol.singleInstalledOrChanged(before, before), null)
        assertEquals(ApplicationPackageProtocol.parseVersionedPackages("vendor noise"), null)
    }

    @Test
    fun `component policy requires one base and preserves every split as an opaque component`() {
        val resolved = ApkPackagePolicy.resolveInstalledComponents(
            listOf(
                "/data/app/example/base.apk",
                "/data/app/example/split_config.en.apk",
                "/data/app/example/split_config.arm64_v8a.apk",
            ),
        ) as InstalledComponentDecision.Accepted

        assertEquals(
            resolved.components.map { it.role },
            listOf(ApkComponentRole.BASE, ApkComponentRole.SPLIT, ApkComponentRole.SPLIT),
        )
        assertEquals(
            resolved.components.map { it.displayName },
            listOf("base.apk", "split_config.en.apk", "split_config.arm64_v8a.apk"),
        )
        assertEquals(resolved.components.map { it.componentId }.distinct().size, 3)
        assertTrue(resolved.components.none { it.toString().contains("/data/app/") })
    }

    @Test
    fun `single base is standalone while missing duplicate or isolated base is rejected`() {
        val standalone = ApkPackagePolicy.resolveInstalledComponents(
            listOf("/data/app/example/base.apk"),
        ) as InstalledComponentDecision.Accepted
        assertEquals(standalone.components.single().role, ApkComponentRole.BASE)

        assertTrue(
            ApkPackagePolicy.resolveInstalledComponents(
                listOf("/data/app/example/split_config.en.apk"),
            ) is InstalledComponentDecision.MissingBase,
        )
        assertTrue(
            ApkPackagePolicy.resolveInstalledComponents(
                listOf("/data/app/example/base.apk", "/data/app/example/other-base.apk"),
            ) is InstalledComponentDecision.AmbiguousBase,
        )
    }

    @Test
    fun `install input accepts one standalone apk and rejects archive containers`() {
        assertEquals(
            ApkPackagePolicy.validateSingleInstallInput(
                displayName = "client.apk",
                archiveShape = ApkArchiveShape.Standalone,
            ),
            ApkInputDecision.Accepted,
        )
        listOf("bundle.apks", "bundle.xapk", "client.zip").forEach { displayName ->
            assertEquals(
                ApkPackagePolicy.validateSingleInstallInput(
                    displayName = displayName,
                    archiveShape = ApkArchiveShape.Unknown,
                ),
                ApkInputDecision.UnsupportedContainer,
            )
        }
    }

    @Test
    fun `install input rejects isolated split and base with missing required splits`() {
        assertEquals(
            ApkPackagePolicy.validateSingleInstallInput(
                displayName = "split_config.en.apk",
                archiveShape = ApkArchiveShape.IsolatedSplit,
            ),
            ApkInputDecision.IsolatedSplit,
        )
        assertEquals(
            ApkPackagePolicy.validateSingleInstallInput(
                displayName = "client.apk",
                archiveShape = ApkArchiveShape.BaseRequiringSplits,
            ),
            ApkInputDecision.MissingRequiredSplits,
        )
    }

    @Test
    fun `signature relation is semantic and does not expose signer bytes`() {
        val first = byteArrayOf(0x11, 0x22, 0x33)
        val same = byteArrayOf(0x11, 0x22, 0x33)
        val other = byteArrayOf(0x44, 0x55, 0x66)

        assertEquals(
            ApkPackagePolicy.signatureRelation(listOf(first), listOf(same)),
            ApkSignatureRelation.SAME,
        )
        assertEquals(
            ApkPackagePolicy.signatureRelation(listOf(first), listOf(other)),
            ApkSignatureRelation.DIFFERENT,
        )
        assertEquals(
            ApkPackagePolicy.signatureRelation(null, listOf(other)),
            ApkSignatureRelation.UNKNOWN,
        )

        val rendered = listOf(
            ApkPackagePolicy.signatureRelation(listOf(first), listOf(same)),
            ApkPackagePolicy.signatureRelation(listOf(first), listOf(other)),
        ).joinToString()
        assertFalse(rendered.contains("112233", ignoreCase = true))
        assertFalse(rendered.contains("445566", ignoreCase = true))
        assertNotEquals(rendered, first.contentToString())
    }
}
