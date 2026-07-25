package com.sheen.adbhelper

import android.app.Application
import com.sheen.adb.core.AdbManagerProvider
import com.sheen.adb.data.AppTemporaryDataCleaner
import com.sheen.adb.data.CompositeTemporaryDataCleaner
import com.sheen.adb.data.DataStoreDeviceProfileRepository
import com.sheen.adb.data.SafTextExporter
import com.sheen.adb.data.SafDocumentStore
import com.sheen.adb.data.SafTemporaryDataCleaner
import com.sheen.adb.data.LogcatShareFileStore
import com.sheen.adb.data.LogcatShareTemporaryDataCleaner
import com.sheen.adb.data.QuickActionArtifactStore
import com.sheen.adb.data.QuickActionArtifactTemporaryDataCleaner
import com.sheen.adb.data.ArtifactTerminationReason
import com.sheen.adb.data.SafBinaryExporter
import com.sheen.adb.feature.overview.QuickActionUseCase
import com.sheen.adbhelper.localpairing.AndroidLocalPairingServiceLifecycle
import com.sheen.adbhelper.localpairing.LocalPairingAppBridge
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.launch

class SheenApplication : Application() {
    val container: AppContainer by lazy(LazyThreadSafetyMode.SYNCHRONIZED) { AppContainer(this) }
    private val applicationScope = CoroutineScope(SupervisorJob() + Dispatchers.IO)

    override fun onCreate() {
        super.onCreate()
        applicationScope.launch {
            runCatching {
                container.quickActionArtifactStore.cleanupAll(ArtifactTerminationReason.STARTUP)
            }
        }
    }
}

class AppContainer(application: Application) {
    val adbManager = AdbManagerProvider.create(application)
    internal val localPairingBridge = LocalPairingAppBridge(
        controller = adbManager.localPairingController,
        serviceLifecycle = AndroidLocalPairingServiceLifecycle(application),
    )
    val deviceProfiles = DataStoreDeviceProfileRepository.create(application)
    val textExporter = SafTextExporter(application)
    val safDocumentStore = SafDocumentStore(application)
    val logcatShareFileStore = LogcatShareFileStore(application.cacheDir)
    val quickActionArtifactStore = QuickActionArtifactStore(application)
    val quickActionExporter = SafBinaryExporter(application, quickActionArtifactStore)
    val quickActionUseCase = QuickActionUseCase(adbManager, quickActionArtifactStore, quickActionExporter)
    val temporaryDataCleaner = CompositeTemporaryDataCleaner(
        AppTemporaryDataCleaner(application),
        SafTemporaryDataCleaner(safDocumentStore),
        LogcatShareTemporaryDataCleaner(logcatShareFileStore),
        QuickActionArtifactTemporaryDataCleaner(quickActionArtifactStore),
    )
}
