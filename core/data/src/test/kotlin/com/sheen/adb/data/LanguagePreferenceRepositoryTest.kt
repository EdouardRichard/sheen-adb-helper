package com.sheen.adb.data

import androidx.datastore.core.DataStore
import androidx.datastore.preferences.core.Preferences
import androidx.datastore.preferences.core.emptyPreferences
import androidx.datastore.preferences.core.edit
import androidx.datastore.preferences.core.stringPreferencesKey
import java.io.File
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.runBlocking
import org.testng.Assert.assertEquals
import org.testng.Assert.assertTrue
import org.testng.annotations.Test

class LanguagePreferenceRepositoryTest {
    @Test
    fun `default save and restart recovery use the shared repository`() = runBlocking {
        val store = InMemoryPreferencesDataStore()
        val repository = DataStoreDeviceProfileRepository(store)
        assertEquals(repository.languagePreference.first(), LanguagePreference.ZH_CN)
        assertTrue(repository.setLanguagePreference(LanguagePreference.EN_US))
        assertEquals(repository.languagePreference.first(), LanguagePreference.EN_US)

        val reopened = DataStoreDeviceProfileRepository(store)
        assertEquals(reopened.languagePreference.first(), LanguagePreference.EN_US)
    }

    @Test
    fun `unknown or corrupt language safely falls back to simplified Chinese`() = runBlocking {
        val store = InMemoryPreferencesDataStore()
        store.edit { it[stringPreferencesKey("ui_language_v1")] = "not-a-supported-language" }
        val repository = DataStoreDeviceProfileRepository(store)
        assertEquals(repository.languagePreference.first(), LanguagePreference.ZH_CN)
    }

    @Test
    fun `language changes preserve profiles and clear all resets both`() = runBlocking {
        val repository = DataStoreDeviceProfileRepository(InMemoryPreferencesDataStore())
        repository.recordSuccessfulConnection(
            host = "fixture.invalid",
            port = 5555,
            suggestedName = "Fixture",
            isLocal = false,
            identityReference = "fixture-identity",
            nowEpochMillis = 100,
        )
        repository.setLanguagePreference(LanguagePreference.EN_US)
        assertEquals(repository.profiles.first().single().displayName, "Fixture")

        repository.clearAll()

        assertTrue(repository.profiles.first().isEmpty())
        assertEquals(repository.languagePreference.first(), LanguagePreference.ZH_CN)
    }

    @Test
    fun `only one sheen local data owner exists`() {
        val source = File(
            "src/main/kotlin/com/sheen/adb/data/DataStoreDeviceProfileRepository.kt",
        ).readText()
        assertEquals(
            Regex("""preferencesDataStoreFile\("sheen_local_data"\)""").findAll(source).count(),
            1,
        )
        assertEquals(Regex("""preferencesDataStoreFile\(""").findAll(source).count(), 1)
    }

    private class InMemoryPreferencesDataStore : DataStore<Preferences> {
        private val mutableData = MutableStateFlow<Preferences>(emptyPreferences())
        override val data: Flow<Preferences> = mutableData

        override suspend fun updateData(
            transform: suspend (t: Preferences) -> Preferences,
        ): Preferences {
            val updated = transform(mutableData.value)
            mutableData.value = updated
            return updated
        }
    }
}
