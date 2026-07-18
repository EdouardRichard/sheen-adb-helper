package com.sheen.adb.core.internal

internal object ApplicationCommands {
    const val CURRENT_USER = "am get-current-user"
    const val CURRENT_USER_FALLBACK = "cmd activity get-current-user"

    fun listThirdParty(userId: Int, fallback: Boolean = false): String =
        "${if (fallback) "cmd package" else "pm"} list packages -3 --user $userId"

    fun listDisabledThirdParty(userId: Int, fallback: Boolean = false): String =
        "${if (fallback) "cmd package" else "pm"} list packages -3 -d --user $userId"

    fun forceStop(userId: Int, packageName: String): String =
        "am force-stop --user $userId $packageName"

    fun setEnabled(userId: Int, packageName: String, enabled: Boolean): String =
        "pm ${if (enabled) "enable" else "disable-user"} --user $userId $packageName"
}

internal sealed interface PackageNamesParse {
    data class Success(val names: LinkedHashSet<String>) : PackageNamesParse
    data object Empty : PackageNamesParse
    data object Malformed : PackageNamesParse
    data object CapacityExceeded : PackageNamesParse
}

internal object ApplicationParsers {
    private const val MAX_APPLICATIONS = 20_000
    private const val MAX_PACKAGE_NAME_LENGTH = 255
    private val packageNamePattern = Regex("^[A-Za-z][A-Za-z0-9_]*(?:\\.[A-Za-z][A-Za-z0-9_]*)+$")
    private val labelledCurrentUser = Regex("^Current user:\\s*(\\d+)$", RegexOption.IGNORE_CASE)

    fun currentUser(text: String): Int? {
        val candidates = text.lineSequence().map(String::trim).filter(String::isNotEmpty).mapNotNull { line ->
            line.toIntOrNull()?.takeIf { it >= 0 }
                ?: labelledCurrentUser.matchEntire(line)?.groupValues?.get(1)?.toIntOrNull()
        }.distinct().toList()
        return candidates.singleOrNull()
    }

    fun isValidPackageName(value: String): Boolean =
        value.length in 3..MAX_PACKAGE_NAME_LENGTH && packageNamePattern.matches(value)

    fun packageNames(text: String): PackageNamesParse {
        val names = linkedSetOf<String>()
        var sawContent = false
        for (rawLine in text.lineSequence()) {
            val line = rawLine.trim()
            if (line.isEmpty()) continue
            sawContent = true
            if (!line.startsWith("package:")) return PackageNamesParse.Malformed
            val packageName = line.removePrefix("package:").trim()
            if (!isValidPackageName(packageName)) return PackageNamesParse.Malformed
            names += packageName
            if (names.size > MAX_APPLICATIONS) return PackageNamesParse.CapacityExceeded
        }
        return when {
            !sawContent -> PackageNamesParse.Empty
            names.isEmpty() -> PackageNamesParse.Malformed
            else -> PackageNamesParse.Success(names)
        }
    }

    fun rejectedOutput(stdout: String, stderr: String, exitCode: Int): ApplicationCommandRejection? {
        val output = (stdout + "\n" + stderr).lowercase()
        if (exitCode == 0 && listOf("security exception", "permission denied", "not allowed", "failure [").none(output::contains)) {
            return null
        }
        return if (listOf(
                "unknown package",
                "package not found",
                "not found",
                "not installed",
                "couldn't find package",
                "does not exist",
            ).any(output::contains)
        ) {
            ApplicationCommandRejection.PACKAGE_NOT_FOUND
        } else {
            ApplicationCommandRejection.POLICY
        }
    }
}

internal enum class ApplicationCommandRejection {
    PACKAGE_NOT_FOUND,
    POLICY,
}
