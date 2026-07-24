package com.sheen.adb.core.internal.processes

internal object ProcessApplicationAssociationResolver {
    data class ResolvedApplication(
        val packageName: String,
        val applicationName: String,
        val wholeApplicationAllowed: Boolean,
    )

    fun resolve(processName: String, candidates: Collection<String>): String? {
        val exact = candidates.filter { processName == it }
        if (exact.size == 1) return exact.single()
        val suffix = candidates.filter { processName.startsWith("$it:") }
        return suffix.singleOrNull()
    }

    fun resolve(
        processName: String,
        candidates: Map<String, String?>,
    ): ResolvedApplication? {
        val matching = candidates.keys.filter { packageName ->
            processName == packageName || processName.startsWith("$packageName:")
        }
        val packageName = matching.singleOrNull() ?: return null
        return ResolvedApplication(
            packageName = packageName,
            applicationName = candidates[packageName]?.trim()?.takeIf(String::isNotEmpty)
                ?: UNKNOWN_APPLICATION_NAME,
            wholeApplicationAllowed = true,
        )
    }

    private const val UNKNOWN_APPLICATION_NAME = "无法解析应用名"
}
