package com.sheen.adb.feature.settings

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.sheen.adb.core.AdbOperationResult
import com.sheen.adb.core.AdbSessionManager
import com.sheen.adb.data.DeviceProfileRepository
import com.sheen.adb.data.LanguagePreference
import com.sheen.adb.data.TemporaryDataCleaner
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch

data class SettingsUiState(
    val versionLabel: String,
    val showClearConfirmation: Boolean = false,
    val isClearing: Boolean = false,
    val clearResult: SettingsMessageCode? = null,
    val settingsHelp: SettingsMessageCode? = null,
    val language: LanguagePreference = LanguagePreference.ZH_CN,
    val isSavingLanguage: Boolean = false,
    val languageMessage: SettingsMessageCode? = null,
) {
    @Deprecated("Use languageMessage and resolve it in Settings presentation")
    val languageError: String?
        get() = null
}

class SettingsViewModel(
    versionLabel: String,
    private val repository: DeviceProfileRepository,
    private val manager: AdbSessionManager,
    private val temporaryDataCleaner: TemporaryDataCleaner,
) : ViewModel() {
    private val mutableState = MutableStateFlow(SettingsUiState(versionLabel))
    val state: StateFlow<SettingsUiState> = mutableState.asStateFlow()

    init {
        viewModelScope.launch {
            repository.languagePreference.collect { language ->
                mutableState.update {
                    it.copy(language = language, isSavingLanguage = false, languageMessage = null)
                }
            }
        }
    }

    fun requestClear() = mutableState.update(SettingsUiState::requestClearConfirmation)
    fun dismissClear() = mutableState.update(SettingsUiState::dismissClearConfirmation)
    fun showManualSettingsPath() = mutableState.update {
        it.copy(settingsHelp = SettingsMessageCode.MANUAL_SETTINGS_PATH)
    }

    fun selectLanguage(language: LanguagePreference) {
        if (mutableState.value.language == language && !mutableState.value.isSavingLanguage) return
        mutableState.update { it.selectLanguage(language) }
        viewModelScope.launch {
            val success = repository.setLanguagePreference(language)
            mutableState.update { it.finishLanguageSave(success) }
        }
    }

    fun clearAll() {
        if (mutableState.value.isClearing) return
        mutableState.update { it.copy(showClearConfirmation = false, isClearing = true, clearResult = null) }
        viewModelScope.launch {
            repository.clearAll()
            val identity = manager.clearHostIdentity()
            val temporary = temporaryDataCleaner.clear()
            val profilesEmpty = repository.profiles.first().isEmpty()
            val success = profilesEmpty && identity is AdbOperationResult.Success && temporary
            mutableState.update { it.applyClearResult(success) }
        }
    }
}

internal fun SettingsUiState.requestClearConfirmation(): SettingsUiState =
    copy(showClearConfirmation = true, clearResult = null)

internal fun SettingsUiState.dismissClearConfirmation(): SettingsUiState = copy(showClearConfirmation = false)

internal fun SettingsUiState.selectLanguage(language: LanguagePreference): SettingsUiState =
    copy(
        language = language,
        isSavingLanguage = true,
        languageMessage = null,
    )

internal fun SettingsUiState.finishLanguageSave(success: Boolean): SettingsUiState =
    copy(
        isSavingLanguage = false,
        languageMessage = if (success) null else SettingsMessageCode.LANGUAGE_SAVE_FAILED,
    )

internal fun SettingsUiState.applyClearResult(success: Boolean): SettingsUiState =
    copy(
        isClearing = false,
        language = if (success) LanguagePreference.ZH_CN else language,
        clearResult = if (success) {
            SettingsMessageCode.CLEAR_SUCCEEDED
        } else {
            SettingsMessageCode.CLEAR_FAILED
        },
    )
