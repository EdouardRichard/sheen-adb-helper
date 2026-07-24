package com.sheen.adb.core.internal

import android.content.Context
import android.content.ContextWrapper
import com.sheen.adb.core.AdbManagerProvider
import com.sheen.adb.core.WirelessDiscoveryEvent
import com.sheen.adb.core.WirelessDiscoveryMode
import com.sheen.adb.core.WirelessDiscoverySource
import com.sheen.adb.core.WirelessDiscoverySourceFactory
import com.sheen.adb.core.WirelessDiscoverySourceFailure
import com.sheen.adb.core.WirelessDiscoverySourceObserver
import com.sheen.adb.core.WirelessDiscoverySourceRequest
import com.sheen.adb.core.WirelessDiscoverySourceStartResult
import com.sheen.adb.core.internal.discovery.AndroidNsdDiscoveryAdapter
import com.sheen.adb.core.internal.discovery.AndroidNsdDiscoveryPlatformGateway
import com.sheen.adb.core.internal.discovery.AndroidNsdWirelessDiscoverySourceFactory
import com.sheen.adb.core.internal.discovery.NsdDiscoveryCallbacks
import com.sheen.adb.core.internal.discovery.NsdDiscoveryFailure
import com.sheen.adb.core.internal.discovery.NsdDiscoveryObserver
import com.sheen.adb.core.internal.discovery.NsdDiscoveryPlatformGateway
import com.sheen.adb.core.internal.discovery.NsdDiscoveryPolicy
import com.sheen.adb.core.internal.discovery.NsdDiscoveryRequest
import com.sheen.adb.core.internal.discovery.NsdDiscoveryStartResult
import com.sheen.adb.core.internal.discovery.NsdNetworkChangeCallbacks
import com.sheen.adb.core.internal.discovery.NsdNetworkRef
import com.sheen.adb.core.internal.discovery.NsdPlatformFailure
import com.sheen.adb.core.internal.discovery.NsdPlatformResource
import com.sheen.adb.core.internal.discovery.NsdResolveCallbacks
import com.sheen.adb.core.internal.discovery.NsdScheduler
import com.sheen.adb.core.internal.discovery.NsdServiceRef
import java.io.File
import java.nio.file.Files
import java.util.concurrent.CancellationException
import java.util.concurrent.CountDownLatch
import java.util.concurrent.Executor
import java.util.concurrent.TimeUnit
import org.testng.Assert.assertFalse
import org.testng.Assert.assertEquals
import org.testng.Assert.assertNotNull
import org.testng.Assert.assertSame
import org.testng.Assert.assertTrue
import org.testng.Assert.fail
import org.testng.annotations.Test
import sun.misc.Unsafe

class WirelessDiscoveryFactoryTest {
    @Test
    fun `provider owns one application scoped manager and a closeable discovery source`() {
        val directory = Files.createTempDirectory("wireless-provider-").toFile()
        val application = allocate<TestApplicationContext>().also { it.directory = directory }
        val activity = allocate<ActivityScopedContext>().also { it.application = application }

        val fromActivity = AdbManagerProvider.create(activity)
        val fromApplication = AdbManagerProvider.create(application)
        try {
            assertSame(fromActivity, fromApplication, "The process must own exactly one ADB manager")

            val factory = discoveryFactory(fromActivity)
            assertNotNull(factory, "The provider must install the Android NSD discovery factory")
            assertTrue(
                contextFields(factory!!).any { it === application },
                "The discovery factory must retain the application Context",
            )
            assertFalse(
                contextFields(factory).any { it === activity },
                "The discovery factory must not retain the Activity-scoped Context",
            )

            val source = factory.create(NoOpObserver)
            source.close()
            source.close()
            assertTrue(
                source.start(
                    WirelessDiscoverySourceRequest(
                        generation = 1,
                        mode = WirelessDiscoveryMode.LAN_FOREGROUND,
                    ),
                ) is
                    WirelessDiscoverySourceStartResult.Rejected,
                "A closed Android discovery source must reject restart",
            )
        } finally {
            if (fromApplication !== fromActivity) fromApplication.close()
            fromActivity.close()
            resetProvider()
            directory.deleteRecursively()
        }
    }

    @Test
    fun `factory forwards local pairing mode to a live source without shortening its window`() {
        val source = sourceWithAdapter(adapter(TestPlatform()))

        try {
            assertSame(
                source.start(
                    WirelessDiscoverySourceRequest(
                        generation = 22,
                        mode = WirelessDiscoveryMode.LOCAL_PAIRING,
                    ),
                ),
                WirelessDiscoverySourceStartResult.Started,
            )
        } finally {
            source.close()
        }
    }

    @Test
    fun `source preserves cancellation interruption and permission failures`() {
        val cancellation = CancellationException("synthetic cancellation")
        assertSame(
            thrownBySource(cancellation),
            cancellation,
            "CancellationException must cross the Android source boundary unchanged",
        )

        val interruption = InterruptedException("synthetic interruption")
        assertSame(
            thrownBySource(interruption),
            interruption,
            "InterruptedException must cross the Android source boundary unchanged",
        )

        val source = sourceWithAdapter(
            adapter = adapter(TestPlatform(acquireFailure = SecurityException("synthetic permission denial"))),
        )
        val result = source.start(sourceRequest())
        assertTrue(result is WirelessDiscoverySourceStartResult.Rejected)
        assertSame(
            (result as WirelessDiscoverySourceStartResult.Rejected).failure,
            WirelessDiscoverySourceFailure.PERMISSION_UNAVAILABLE,
        )
        source.close()
    }

    @Test
    fun `network change terminates adapter with network unavailable`() {
        val platform = TestPlatform()
        val failures = mutableListOf<NsdDiscoveryFailure>()
        val adapter = adapter(platform) { failures += it }

        assertSame(
            adapter.start(
                NsdDiscoveryRequest(
                    generation = 2,
                    apiLevel = 33,
                    currentNetwork = NsdNetworkRef("synthetic-network"),
                ),
            ),
            NsdDiscoveryStartResult.Started,
        )
        platform.networkCallbacks.onNetworkChanged(NsdNetworkRef("changed-network"))

        assertSame(failures.single(), NsdDiscoveryFailure.NETWORK_UNAVAILABLE)
    }

    @Test
    fun `asynchronous resolve permission denial terminates and releases resources`() {
        val platform = TestPlatform(resolveFailure = SecurityException("synthetic permission denial"))
        val scheduler = RecordingScheduler()
        val failures = mutableListOf<NsdDiscoveryFailure>()
        val adapter = adapter(platform, scheduler) { failures += it }

        assertSame(
            adapter.start(NsdDiscoveryRequest(generation = 3, apiLevel = 30, currentNetwork = null)),
            NsdDiscoveryStartResult.Started,
        )
        platform.discoveryCallbacks.first().onServiceFound(
            NsdServiceRef("_adb-tls-pairing._tcp", "synthetic-service"),
        )

        assertEquals(failures.map(NsdDiscoveryFailure::name), listOf("PERMISSION_UNAVAILABLE"))
        assertTrue(platform.resources.all { it.cancelCalls == 1 }, "Every acquired platform resource must close once")
        assertSame(scheduler.resource.cancelCalls, 1, "The discovery deadline must be cancelled once")
    }

    @Test(timeOut = 5_000)
    fun `adapter callback and source close do not invert locks`() {
        val platform = TestPlatform()
        val callbackEntered = CountDownLatch(1)
        val allowObserverClose = CountDownLatch(1)
        val callbackDone = CountDownLatch(1)
        val adapter = adapter(platform) {
            callbackEntered.countDown()
            allowObserverClose.await()
            callbackDone.countDown()
        }
        val source = sourceWithAdapter(adapter)
        assertSame(source.start(sourceRequest()), WirelessDiscoverySourceStartResult.Started)

        val callbackThread = daemonThread("synthetic-nsd-callback") {
            platform.discoveryCallbacks.first().onDiscoveryFailure(NsdPlatformFailure.DISCOVERY_FAILED)
        }
        val closeDone = CountDownLatch(1)
        val closeThread = daemonThread("synthetic-source-close") {
            source.close()
            closeDone.countDown()
        }
        try {
            callbackThread.start()
            assertTrue(callbackEntered.await(1, TimeUnit.SECONDS), "The adapter callback did not reach its observer")
            closeThread.start()
            assertTrue(closeDone.await(1, TimeUnit.SECONDS), "Source close remained blocked by an external observer")
        } finally {
            allowObserverClose.countDown()
            callbackThread.join(500)
            closeThread.join(500)
            if (callbackThread.isAlive) callbackThread.interrupt()
            if (closeThread.isAlive) closeThread.interrupt()
        }
        assertTrue(callbackDone.await(1, TimeUnit.SECONDS), "The adapter callback did not finish after release")
    }

    private fun discoveryFactory(manager: Any): WirelessDiscoverySourceFactory? {
        val field = manager.javaClass.getDeclaredField("wirelessDiscoverySourceFactory")
        field.isAccessible = true
        return field.get(manager) as? WirelessDiscoverySourceFactory
    }

    private fun contextFields(instance: Any): List<Context> = instance.javaClass.declaredFields.mapNotNull { field ->
        field.isAccessible = true
        field.get(instance) as? Context
    }

    private fun resetProvider() {
        runCatching {
            AdbManagerProvider::class.java.getDeclaredField("manager").run {
                isAccessible = true
                set(AdbManagerProvider, null)
            }
        }
    }

    private fun thrownBySource(failure: Exception): Exception {
        val source = sourceWithAdapter(adapter(TestPlatform(acquireFailure = failure)))
        return try {
            source.start(sourceRequest())
            fail("Expected ${failure.javaClass.simpleName}")
            failure
        } catch (actual: Exception) {
            actual
        } finally {
            source.close()
        }
    }

    private fun sourceWithAdapter(adapter: AndroidNsdDiscoveryAdapter): WirelessDiscoverySource {
        val application = allocate<TestApplicationContext>().also {
            it.directory = File(requireNotNull(System.getProperty("java.io.tmpdir")))
        }
        val source = AndroidNsdWirelessDiscoverySourceFactory(application).create(NoOpObserver)
        setField(source, "adapter", adapter)
        setField(source, "gateway", allocate<AndroidNsdDiscoveryPlatformGateway>())
        return source
    }

    private fun adapter(
        platform: TestPlatform,
        scheduler: NsdScheduler = TestScheduler,
        onFailure: (NsdDiscoveryFailure) -> Unit = {},
    ): AndroidNsdDiscoveryAdapter = AndroidNsdDiscoveryAdapter(
        platform = platform,
        policy = NsdDiscoveryPolicy(),
        scheduler = scheduler,
        observer = object : NsdDiscoveryObserver {
            override fun onEvent(event: WirelessDiscoveryEvent) = Unit

            override fun onFailure(failure: NsdDiscoveryFailure) = onFailure(failure)
        },
    )

    private fun sourceRequest() = WirelessDiscoverySourceRequest(
        generation = 1,
        mode = WirelessDiscoveryMode.LAN_FOREGROUND,
    )

    private fun setField(instance: Any, name: String, value: Any) {
        instance.javaClass.getDeclaredField(name).run {
            isAccessible = true
            set(instance, value)
        }
    }

    private fun daemonThread(name: String, action: () -> Unit): Thread = Thread(action, name).apply {
        isDaemon = true
    }

    private inline fun <reified T> allocate(): T = unsafe.allocateInstance(T::class.java) as T

    private class TestApplicationContext private constructor() : ContextWrapper(null) {
        lateinit var directory: File

        override fun getApplicationContext(): Context = this

        override fun getNoBackupFilesDir(): File = directory

        override fun getMainExecutor(): Executor = Executor(Runnable::run)
    }

    private class ActivityScopedContext private constructor() : ContextWrapper(null) {
        lateinit var application: Context

        override fun getApplicationContext(): Context = application
    }

    private object NoOpObserver : WirelessDiscoverySourceObserver {
        override fun onEvent(event: WirelessDiscoveryEvent) = Unit

        override fun onFailure(failure: WirelessDiscoverySourceFailure) = Unit
    }

    private class TestPlatform(
        private val acquireFailure: Exception? = null,
        private val resolveFailure: Exception? = null,
    ) : NsdDiscoveryPlatformGateway {
        val discoveryCallbacks = mutableListOf<NsdDiscoveryCallbacks>()
        val resources = mutableListOf<CountingResource>()
        lateinit var networkCallbacks: NsdNetworkChangeCallbacks

        override fun acquireMulticastLock(): NsdPlatformResource {
            acquireFailure?.let { throw it }
            return resource()
        }

        override fun discover(
            serviceType: String,
            network: NsdNetworkRef?,
            callbacks: NsdDiscoveryCallbacks,
        ): NsdPlatformResource {
            discoveryCallbacks += callbacks
            return resource()
        }

        override fun resolve(
            service: NsdServiceRef,
            network: NsdNetworkRef?,
            callbacks: NsdResolveCallbacks,
        ): NsdPlatformResource {
            resolveFailure?.let { throw it }
            return resource()
        }

        override fun registerNetworkChangeCallback(
            network: NsdNetworkRef,
            callbacks: NsdNetworkChangeCallbacks,
        ): NsdPlatformResource {
            networkCallbacks = callbacks
            return resource()
        }

        private fun resource(): CountingResource = CountingResource().also(resources::add)
    }

    private object TestScheduler : NsdScheduler {
        override fun schedule(delayMillis: Long, action: () -> Unit): NsdPlatformResource = NoOpResource
    }

    private class RecordingScheduler : NsdScheduler {
        val resource = CountingResource()

        override fun schedule(delayMillis: Long, action: () -> Unit): NsdPlatformResource = resource
    }

    private class CountingResource : NsdPlatformResource {
        var cancelCalls = 0

        override fun cancel() {
            cancelCalls += 1
        }
    }

    private object NoOpResource : NsdPlatformResource {
        override fun cancel() = Unit
    }

    private companion object {
        val unsafe: Unsafe = Unsafe::class.java.getDeclaredField("theUnsafe").run {
            isAccessible = true
            get(null) as Unsafe
        }
    }
}
