package com.sheen.adbhelper

import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.activity.viewModels
import androidx.lifecycle.ViewModel
import androidx.lifecycle.ViewModelProvider
import com.sheen.adb.feature.files.FilesViewModel
import com.sheen.adb.ui.SheenTheme

class MainActivity : ComponentActivity() {
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
        setContent { SheenTheme { SheenApp(container, filesViewModel) } }
    }

    override fun onStop() {
        if (shouldCancelFileTasksOnStop(isChangingConfigurations)) {
            filesViewModel.onHostStopped(isChangingConfigurations = false)
        }
        super.onStop()
    }
}

internal fun shouldCancelFileTasksOnStop(isChangingConfigurations: Boolean): Boolean =
    !isChangingConfigurations
