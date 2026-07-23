package com.sheen.adb.core.internal.diagnostics

import com.sheen.adb.core.AndroidUidIdentity
import com.sheen.adb.core.DeviceProcess
import com.sheen.adb.core.ProcessAnalysisCapability
import com.sheen.adb.core.ProcessAnalysisEntry
import com.sheen.adb.core.ProcessAnalysisSnapshot
import com.sheen.adb.core.ProcessApplicationAssociation
import com.sheen.adb.core.ProcessAssociationUnknownReason
import com.sheen.adb.core.ProcessRecordAssociation
import com.sheen.adb.core.RemoteApplication

internal object ProcessAssociation {
    private val applicationUidName = Regex("^u(\\d+)_a(\\d+)$")

    fun parseProcessUid(value: String?): AndroidUidIdentity? {
        val normalized = value?.trim()?.takeIf(String::isNotEmpty) ?: return null
        normalized.toIntOrNull()?.let { return AndroidUidIdentity.fromRawUid(it) }
        val match = applicationUidName.matchEntire(normalized) ?: return null
        val userId = match.groupValues[1].toIntOrNull() ?: return null
        val applicationOffset = match.groupValues[2].toIntOrNull() ?: return null
        val appId = APPLICATION_UID_START.toLong() + applicationOffset
        if (userId < 0 || appId !in APPLICATION_UID_START.toLong() until PER_USER_RANGE.toLong()) return null
        return AndroidUidIdentity(userId, appId.toInt())
    }

    fun resolve(
        expectedSessionId: String,
        applicationSessionId: String,
        expectedGeneration: Long,
        applicationGeneration: Long,
        applications: List<RemoteApplication>,
        processes: List<DeviceProcess>,
        degradedReason: String? = null,
    ): ProcessAnalysisSnapshot {
        val globalFailure = when {
            expectedSessionId != applicationSessionId -> ProcessAssociationUnknownReason.SESSION_MISMATCH
            expectedGeneration != applicationGeneration -> ProcessAssociationUnknownReason.GENERATION_MISMATCH
            else -> null
        }
        val applicationsByUid = applications
            .asSequence()
            .mapNotNull { application ->
                val identity = application.uidIdentity ?: return@mapNotNull null
                if (identity.userId != application.userId) return@mapNotNull null
                identity to application.packageName
            }
            .groupBy({ it.first }, { it.second })

        val entries = processes.map { process ->
            val unavailable = linkedSetOf<ProcessAnalysisCapability>()
            if (process.uid == null) unavailable += ProcessAnalysisCapability.UID
            if (process.state == null) unavailable += ProcessAnalysisCapability.STATE
            if (process.residentMemoryBytes == null) unavailable += ProcessAnalysisCapability.RESIDENT_MEMORY
            val association = if (globalFailure != null) {
                ProcessApplicationAssociation.Unknown(globalFailure)
            } else {
                associationFor(process, applicationsByUid)
            }
            if (association is ProcessApplicationAssociation.Unknown) {
                unavailable += ProcessAnalysisCapability.APPLICATION_ASSOCIATION
            }
            ProcessAnalysisEntry(
                snapshotGeneration = expectedGeneration,
                process = process,
                applicationAssociation = association,
                unavailableCapabilities = unavailable,
            )
        }
        return ProcessAnalysisSnapshot(
            sessionId = expectedSessionId,
            generation = expectedGeneration,
            entries = entries,
            degradedReason = degradedReason,
        )
    }

    fun associatePid(
        snapshot: ProcessAnalysisSnapshot,
        expectedSessionId: String,
        expectedGeneration: Long,
        pid: Int,
    ): ProcessRecordAssociation {
        if (snapshot.sessionId != expectedSessionId) {
            return unknown(ProcessAssociationUnknownReason.SESSION_MISMATCH)
        }
        if (snapshot.generation != expectedGeneration) {
            return unknown(ProcessAssociationUnknownReason.GENERATION_MISMATCH)
        }
        val matches = snapshot.entries.filter { it.process.pid == pid }
        if (matches.size != 1) return unknown(ProcessAssociationUnknownReason.PROCESS_EXITED)
        val entry = matches.single()
        return ProcessRecordAssociation(entry.process.name, entry.applicationAssociation)
    }

    private fun associationFor(
        process: DeviceProcess,
        applicationsByUid: Map<AndroidUidIdentity, List<String>>,
    ): ProcessApplicationAssociation {
        val rawUid = process.uid
            ?: return ProcessApplicationAssociation.Unknown(ProcessAssociationUnknownReason.MISSING_UID)
        val identity = parseProcessUid(rawUid)
            ?: return ProcessApplicationAssociation.Unknown(ProcessAssociationUnknownReason.INVALID_UID)
        val candidates = applicationsByUid[identity]
            ?.distinct()
            ?.sorted()
            .orEmpty()
        return when (candidates.size) {
            0 -> ProcessApplicationAssociation.Unknown(ProcessAssociationUnknownReason.NO_MATCH)
            1 -> ProcessApplicationAssociation.Verified(candidates.single())
            else -> ProcessApplicationAssociation.Multiple(candidates.toSet())
        }
    }

    private fun unknown(reason: ProcessAssociationUnknownReason) = ProcessRecordAssociation(
        processName = null,
        applicationAssociation = ProcessApplicationAssociation.Unknown(reason),
    )

    private const val APPLICATION_UID_START = 10_000
    private const val PER_USER_RANGE = 100_000
}
