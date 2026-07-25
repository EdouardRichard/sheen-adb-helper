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
    val clearResult: String? = null,
    val settingsHelp: String? = null,
    val language: LanguagePreference = LanguagePreference.ZH_CN,
    val isSavingLanguage: Boolean = false,
    val languageError: String? = null,
)

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
                    it.copy(language = language, isSavingLanguage = false, languageError = null)
                }
            }
        }
    }

    fun requestClear() = mutableState.update(SettingsUiState::requestClearConfirmation)
    fun dismissClear() = mutableState.update(SettingsUiState::dismissClearConfirmation)
    fun showManualSettingsPath() = mutableState.update {
        it.copy(settingsHelp = "请手动打开：系统设置 → 关于手机 → 连续点击系统版本开启开发者选项 → 更多设置 → 开发者选项 → 无线调试。")
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
        languageError = null,
    )

internal fun SettingsUiState.finishLanguageSave(success: Boolean): SettingsUiState =
    copy(
        isSavingLanguage = false,
        languageError = if (success) null else "语言偏好保存失败，请重试。",
    )

internal fun SettingsUiState.applyClearResult(success: Boolean): SettingsUiState =
    copy(
        isClearing = false,
        language = if (success) LanguagePreference.ZH_CN else language,
        clearResult = if (success) {
            "所有本地数据已清除并验证不可继续使用旧身份。"
        } else {
            "清除未完全成功，请重启应用后重试。"
        },
    )
