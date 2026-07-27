package com.sheen.adb.core.internal.applications

internal object ApplicationPackageProtocol {
    fun installedPaths(userId: Int, packageName: String): String =
        "pm path --user $userId $packageName"

    fun uninstallForUser(userId: Int, packageName: String): String =
        "pm uninstall --user $userId $packageName"

    fun packagePresence(userId: Int, packageName: String): String =
        "pm list packages --user $userId $packageName"

    fun versionedPackages(userId: Int): String =
        "pm list packages --show-versioncode --user $userId"

    fun parseVersionedPackages(output: String): Map<String, Long>? {
        val result = linkedMapOf<String, Long>()
        val lines = output.lineSequence().map(String::trim).filter(String::isNotEmpty).toList()
        for (line in lines) {
            val match = VERSIONED_PACKAGE.matchEntire(line) ?: return null
            val packageName = match.groupValues[1]
            val versionCode = match.groupValues[2].toLongOrNull()?.takeIf { it >= 0L } ?: return null
            if (result.put(packageName, versionCode) != null) return null
        }
        return result
    }

    fun singleInstalledOrChanged(
        before: Map<String, Long>?,
        after: Map<String, Long>?,
    ): String? {
        if (before == null || after == null) return null
        return after.keys.filter { packageName ->
            packageName !in before || before[packageName] != after[packageName]
        }.singleOrNull()
    }

    fun stagedInstall(
        stagedRemotePath: String,
        replaceExisting: Boolean,
        allowDowngrade: Boolean,
    ): String = buildString {
        append("pm install")
        if (replaceExisting) append(" -r")
        if (allowDowngrade) append(" -d")
        append(' ')
        append(stagedRemotePath)
    }

    fun deleteStagedApk(stagedRemotePath: String): String =
        "rm -f $stagedRemotePath"

    private val VERSIONED_PACKAGE =
        Regex("""^package:([A-Za-z][A-Za-z0-9_]*(?:\.[A-Za-z][A-Za-z0-9_]*)*)\s+versionCode:(\d+)$""")
}
