package com.sheen.adb.core.internal.processes

import com.sheen.adb.core.ProcessIdentity
import com.sheen.adb.core.ProcessTerminationScope

internal object ProcessTerminationPolicy {
    class ConfirmationGuard {
        private val issued = mutableSetOf<String>()

        @Synchronized
        fun issue(nonce: String) {
            require(nonce.isNotBlank())
            issued += nonce
        }

        @Synchronized
        fun consume(nonce: String): Boolean = issued.remove(nonce)

        @Synchronized
        fun cancel(nonce: String) {
            issued.remove(nonce)
        }
    }

    fun allowsSingle(identity: ProcessIdentity): Boolean {
        val appId = appId(identity.uid) ?: return false
        return identity.pid > 1 && appId >= 10_000 && identity.startTimeTicks != null
    }

    fun allowsWholeApplication(packageName: String?, candidates: Set<ProcessIdentity>): Boolean =
        !packageName.isNullOrBlank() && candidates.isNotEmpty() &&
            candidates.all {
                it.pid > 1 && it.startTimeTicks != null && (appId(it.uid) ?: -1) >= 10_000
            } &&
            candidates.map { it.sessionId to it.observedGeneration }.distinct().size == 1

    fun allows(scope: ProcessTerminationScope, identity: ProcessIdentity, packageName: String?, set: Set<ProcessIdentity>) =
        when (scope) {
            ProcessTerminationScope.SINGLE_PROCESS -> allowsSingle(identity)
            ProcessTerminationScope.WHOLE_APPLICATION_FORCE_STOP -> allowsWholeApplication(packageName, set)
        }

    private fun appId(uid: String?): Int? =
        uid?.toIntOrNull()?.let { it % 100_000 }
            ?: Regex("^u\\d+_a(\\d+)$").matchEntire(uid.orEmpty())
                ?.groupValues?.get(1)?.toIntOrNull()?.plus(10_000)
}
