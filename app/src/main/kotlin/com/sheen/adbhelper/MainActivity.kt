package com.sheen.adbhelper

import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.activity.viewModels
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.ViewModel
import androidx.lifecycle.ViewModelProvider
import androidx.lifecycle.lifecycleScope
import com.sheen.adb.feature.files.FilesViewModel
import com.sheen.adb.feature.overview.OverviewViewModel
import com.sheen.adb.ui.SheenTheme
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.compose.runtime.getValue

private const val FILE_PICKER_RETURN_STABILITY_MILLIS = 500L

class MainActivity : ComponentActivity() {
    private val hostForegroundState = MutableStateFlow(false)

    private val overviewViewModel by viewModels<OverviewViewModel> {
        val container = (application as SheenApplication).container
        object : ViewModelProvider.Factory {
            @Suppress("UNCHECKED_CAST")
            override fun <T : ViewModel> create(modelClass: Class<T>): T =
                OverviewViewModel(container.adbManager, container.quickActionUseCase) as T
        }
    }

    private val filesViewModel by viewModels<FilesViewModel> {
        val container = (application as SheenApplication).container
        object : ViewModelProvider.Factory {
            @Suppress("UNCHECKED_CAST")
            override fun <T : ViewModel> create(modelClass: Class<T>): T =
                FilesViewModel(container.adbManager, container.safDocumentStore) as T
        }
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        enableEdgeToEdge()
        val container = (application as SheenApplication).container
        setContent {
            val hostForeground by hostForegroundState.collectAsStateWithLifecycle()
            SheenTheme {
                SheenApp(
                    container = container,
                    files = filesViewModel,
                    overview = overviewViewModel,
                    hostForeground = hostForeground,
                )
            }
        }
    }

    override fun onStart() {
        super.onStart()
        hostForegroundState.value = true
        lifecycleScope.launch {
            delay(FILE_PICKER_RETURN_STABILITY_MILLIS)
            if (lifecycle.currentState.isAtLeast(Lifecycle.State.STARTED)) {
                filesViewModel.onHostStarted()
            }
        }
    }

    override fun onStop() {
        hostForegroundState.value = false
        if (shouldCancelFileTasksOnStop(isChangingConfigurations)) {
            filesViewModel.onHostStopped(isChangingConfigurations = false)
        }
        super.onStop()
    }
}

internal fun shouldCancelFileTasksOnStop(isChangingConfigurations: Boolean): Boolean =
    !isChangingConfigurations
