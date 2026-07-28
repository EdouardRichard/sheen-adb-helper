package com.sheen.adb.feature.settings

import android.content.ActivityNotFoundException
import android.content.Context
import android.content.Intent
import android.provider.Settings
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.RadioButton
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.sheen.adb.ui.SheenDimensions
import com.sheen.adb.ui.SheenTonalLayers
import com.sheen.adb.data.LanguagePreference
import com.sheen.adb.ui.UiLanguage

@Composable
fun SettingsRoute(
    viewModel: SettingsViewModel,
    language: UiLanguage = UiLanguage.ZH_CN,
) {
    val state by viewModel.state.collectAsStateWithLifecycle()
    val context = LocalContext.current
    Column(
        Modifier.verticalScroll(rememberScrollState()).padding(SheenDimensions.screenPadding),
        verticalArrangement = Arrangement.spacedBy(SheenDimensions.itemSpacing),
    ) {
        Text(
            SettingsStrings.resolve(language, SettingsStringKey.TITLE),
            style = MaterialTheme.typography.headlineSmall,
        )
        Card(
            modifier = Modifier.fillMaxWidth(),
            colors = CardDefaults.cardColors(
                containerColor = MaterialTheme.colorScheme.surfaceContainerLow,
            ),
            border = BorderStroke(
                1.dp,
                MaterialTheme.colorScheme.outlineVariant.copy(
                    alpha = SheenTonalLayers.subtleOutlineAlpha,
                ),
            ),
        ) {
            Column(Modifier.padding(16.dp)) {
                Text(
                    SettingsStrings.resolve(language, SettingsStringKey.LANGUAGE),
                    style = MaterialTheme.typography.titleMedium,
                )
                LanguageOption(
                    label = SettingsStrings.resolve(
                        language,
                        SettingsStringKey.LANGUAGE_CHINESE,
                    ),
                    selected = state.language == LanguagePreference.ZH_CN,
                    enabled = !state.isSavingLanguage,
                    onClick = { viewModel.selectLanguage(LanguagePreference.ZH_CN) },
                )
                LanguageOption(
                    label = SettingsStrings.resolve(
                        language,
                        SettingsStringKey.LANGUAGE_ENGLISH,
                    ),
                    selected = state.language == LanguagePreference.EN_US,
                    enabled = !state.isSavingLanguage,
                    onClick = { viewModel.selectLanguage(LanguagePreference.EN_US) },
                )
                state.languageMessage?.let { message ->
                    Text(
                        SettingsStrings.resolve(language, SettingsStrings.keyFor(message)),
                        color = MaterialTheme.colorScheme.error,
                    )
                }
            }
        }
        InfoCard(
            SettingsStrings.resolve(language, SettingsStringKey.VERSION),
            state.versionLabel,
        )
        InfoCard(
            SettingsStrings.resolve(language, SettingsStringKey.PRIVACY_TITLE),
            SettingsStrings.resolve(language, SettingsStringKey.PRIVACY_BODY),
        )
        InfoCard(
            SettingsStrings.resolve(language, SettingsStringKey.SUPPORT_TITLE),
            SettingsStrings.resolve(language, SettingsStringKey.SUPPORT_BODY),
        )
        InfoCard(
            SettingsStrings.resolve(language, SettingsStringKey.LICENSES_TITLE),
            SettingsStrings.resolve(language, SettingsStringKey.LICENSES_BODY),
        )
        InfoCard(
            SettingsStrings.resolve(language, SettingsStringKey.PAIRING_HELP_TITLE),
            SettingsStrings.resolve(language, SettingsStringKey.PAIRING_HELP_BODY),
        )
        Button(onClick = { if (!openSettings(context, Intent("android.settings.WIRELESS_DEBUGGING_SETTINGS"))) viewModel.showManualSettingsPath() }) {
            Text(SettingsStrings.resolve(language, SettingsStringKey.OPEN_WIRELESS_DEBUGGING))
        }
        OutlinedButton(onClick = {
            if (!openSettings(context, Intent(Settings.ACTION_APPLICATION_DEVELOPMENT_SETTINGS))) viewModel.showManualSettingsPath()
        }) {
            Text(SettingsStrings.resolve(language, SettingsStringKey.OPEN_DEVELOPER_OPTIONS))
        }
        state.settingsHelp?.let {
            Text(SettingsStrings.resolve(language, SettingsStrings.keyFor(it)))
        }
        OutlinedButton(onClick = viewModel::requestClear, enabled = !state.isClearing) {
            Text(SettingsStrings.resolve(language, SettingsStringKey.CLEAR_ALL_DATA))
        }
        if (state.isClearing) {
            Text(SettingsStrings.resolve(language, SettingsStringKey.CLEARING_DATA))
        }
        state.clearResult?.let {
            Text(SettingsStrings.resolve(language, SettingsStrings.keyFor(it)))
        }
    }
    if (state.showClearConfirmation) AlertDialog(
        onDismissRequest = viewModel::dismissClear,
        title = {
            Text(SettingsStrings.resolve(language, SettingsStringKey.CLEAR_CONFIRM_TITLE))
        },
        text = {
            Text(SettingsStrings.resolve(language, SettingsStringKey.CLEAR_CONFIRM_BODY))
        },
        confirmButton = {
            TextButton(onClick = viewModel::clearAll) {
                Text(SettingsStrings.resolve(language, SettingsStringKey.CLEAR_CONFIRM))
            }
        },
        dismissButton = {
            TextButton(onClick = viewModel::dismissClear) {
                Text(SettingsStrings.resolve(language, SettingsStringKey.CANCEL))
            }
        },
    )
}

@Composable
private fun LanguageOption(
    label: String,
    selected: Boolean,
    enabled: Boolean,
    onClick: () -> Unit,
) {
    Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
        Text(label, modifier = Modifier.padding(vertical = 12.dp))
        RadioButton(
            selected = selected,
            onClick = onClick,
            enabled = enabled,
        )
    }
}

@Composable
private fun InfoCard(title: String, body: String) {
    Card(
        modifier = Modifier.fillMaxWidth(),
        colors = CardDefaults.cardColors(
            containerColor = MaterialTheme.colorScheme.surfaceContainerLow,
        ),
        border = BorderStroke(
            1.dp,
            MaterialTheme.colorScheme.outlineVariant.copy(
                alpha = SheenTonalLayers.quietOutlineAlpha,
            ),
        ),
    ) {
        Column(Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(6.dp)) {
            Text(title, style = MaterialTheme.typography.titleMedium)
            Text(body)
        }
    }
}

private fun openSettings(context: Context, intent: Intent): Boolean = try {
    context.startActivity(intent.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK))
    true
} catch (_: ActivityNotFoundException) {
    false
} catch (_: SecurityException) {
    false
}
