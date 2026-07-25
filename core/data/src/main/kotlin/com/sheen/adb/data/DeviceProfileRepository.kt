package com.sheen.adb.data

import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.flowOf

enum class LanguagePreference(val persistedValue: String) {
    ZH_CN("zh-CN"),
    EN_US("en-US"),
    ;

    companion object {
        fun fromPersisted(value: String?): LanguagePreference =
            entries.firstOrNull {
                it.persistedValue.equals(value?.trim(), ignoreCase = true)
            } ?: ZH_CN
    }
}

interface DeviceProfileRepository {
    val profiles: Flow<List<DeviceProfile>>
    val languagePreference: Flow<LanguagePreference>
        get() = flowOf(LanguagePreference.ZH_CN)

    suspend fun recordSuccessfulConnection(
        host: String,
        port: Int,
        suggestedName: String,
        isLocal: Boolean,
        identityReference: String,
        nowEpochMillis: Long,
    ): DeviceProfile

    suspend fun rename(profileId: String, displayName: String): Boolean
    suspend fun delete(profileId: String): DeviceProfile?
    suspend fun setLanguagePreference(language: LanguagePreference): Boolean = false
    suspend fun clearAll()
}
