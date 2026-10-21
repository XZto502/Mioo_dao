package com.mioo.dao

import android.app.Application
import android.os.Build
import android.os.Looper
import androidx.compose.ui.graphics.Color
import androidx.hilt.work.HiltWorkerFactory
import androidx.work.Configuration
import coil.Coil
import coil.ImageLoader
import coil.ImageLoaderFactory
import coil.annotation.ExperimentalCoilApi
import coil.decode.GifDecoder
import coil.decode.ImageDecoderDecoder
import coil.disk.DiskCache
import coil.memory.MemoryCache
import com.mioo.dao.data.cdn.ImageCdn
import com.mioo.dao.data.local.AppDatabase
import com.mioo.dao.data.local.SettingsDataStore
import com.mioo.dao.data.model.ImageCacheLimit
import com.mioo.dao.data.repository.SettingsRepository
import com.mioo.dao.notification.SubscriptionScheduler
import com.mioo.dao.ui.components.HtmlParseCache
import dagger.hilt.EntryPoint
import dagger.hilt.InstallIn
import dagger.hilt.android.EntryPointAccessors
import dagger.hilt.android.HiltAndroidApp
import dagger.hilt.components.SingletonComponent
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.flow.distinctUntilChanged
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.launch
import okhttp3.OkHttpClient
import java.util.concurrent.TimeUnit
import javax.inject.Inject

/**
 * Late bindings for work that must not inflate Application field injection on cold start.
 */
@EntryPoint
@InstallIn(SingletonComponent::class)
interface AppLateInitEntryPoint {
    fun database(): AppDatabase
    fun settingsRepository(): SettingsRepository
    fun subscriptionScheduler(): SubscriptionScheduler
    fun imageCdn(): ImageCdn
}

@HiltAndroidApp
class MiooDaoApp : Application(), ImageLoaderFactory, Configuration.Provider {

    // Room / subscription wiring is deferred — avoid pulling the full graph onto the
    // first application inject tick when only ImageLoader + WorkManager are needed.
    // 看图使用独立客户端，不挂饼干拦截器，也不占用接口的 HTTP 缓存。

    @Inject
    lateinit var workerFactory: HiltWorkerFactory

    private val appScope = CoroutineScope(SupervisorJob() + Dispatchers.Default)

    private val imageLoaderLock = Any()

    /** Bytes passed into the next Coil disk cache. Default matches the settings default. */
    @Volatile
    private var diskCacheMaxBytes: Long = ImageCacheLimit.toBytes(ImageCacheLimit.DEFAULT_MB)

    /** Last limit that was actually trimmed on disk. -1 until the first trim. */
    @Volatile
    private var enforcedDiskCacheBytes: Long = -1L

    private var installedImageLoader: ImageLoader? = null
    private var imageHttpClient: OkHttpClient? = null

    override val workManagerConfiguration: Configuration
        get() = Configuration.Builder()
            .setWorkerFactory(workerFactory)
            .build()

    override fun onCreate() {
        super.onCreate()

        // Read the image-cache cap before the first picture. DataStore only — no Hilt graph.
        appScope.launch(Dispatchers.IO) {
            runCatching {
                SettingsDataStore(this@MiooDaoApp).cacheLimitMbFlow
                    .distinctUntilChanged()
                    .collect { applyImageCacheLimit(it) }
            }
        }

        // Room open is the critical cold path for SWR list cache — start immediately on IO
        // (do not wait for main-thread idle; idle often lands after first Activity frames).
        appScope.launch(Dispatchers.IO) {
            runCatching {
                val entryPoint = EntryPointAccessors.fromApplication(
                    this@MiooDaoApp,
                    AppLateInitEntryPoint::class.java
                )
                entryPoint.database().openHelper.writableDatabase
            }
        }

        // Non-critical warm + WorkManager after first list paint window
        Looper.myQueue().addIdleHandler {
            deferNonCriticalWarm()
            false
        }
    }

    private fun deferNonCriticalWarm() {
        appScope.launch(Dispatchers.IO) {
            kotlinx.coroutines.delay(2200)
            runCatching {
                EntryPointAccessors.fromApplication(
                    this@MiooDaoApp,
                    AppLateInitEntryPoint::class.java
                ).imageCdn().warm()
            }
        }
        appScope.launch {
            // After cold list + splash typically done
            kotlinx.coroutines.delay(1800)
            runCatching {
                val loader = coil.Coil.imageLoader(this@MiooDaoApp)
                loader.memoryCache
            }
            kotlinx.coroutines.delay(400)
            runCatching {
                HtmlParseCache.prewarm(
                    listOf(
                        "预热<br>测试&gt;&gt;No.1",
                        "<font color=\"#789922\">&gt;&gt;No.2</font> hello"
                    ),
                    Color(0xFF6750A4)
                )
            }
        }
        appScope.launch {
            kotlinx.coroutines.delay(3500)
            runCatching {
                val entryPoint = EntryPointAccessors.fromApplication(
                    this@MiooDaoApp,
                    AppLateInitEntryPoint::class.java
                )
                val settingsRepository = entryPoint.settingsRepository()
                val subscriptionScheduler = entryPoint.subscriptionScheduler()
                settingsRepository.settings
                    .map { it.subscriptionNotificationsEnabled to it.notificationIntervalMinutes }
                    .distinctUntilChanged()
                    .collect { (enabled, interval) ->
                        subscriptionScheduler.apply(enabled, interval)
                    }
            }
        }
    }

    override fun newImageLoader(): ImageLoader {
        synchronized(imageLoaderLock) {
            installedImageLoader?.let { return it }
            enforceDiskCacheLimit(diskCacheMaxBytes)
            val loader = buildImageLoader(diskCacheMaxBytes)
            installedImageLoader = loader
            return loader
        }
    }

    /**
     * Coil opens one disk cache per directory and only trims down on close.
     * Shut the current cache, trim to the new cap, then install a new loader.
     * [Coil.setImageLoader] stays outside [imageLoaderLock]: newImageLoader runs
     * under Coil's lock and then takes this lock.
     */
    @OptIn(ExperimentalCoilApi::class)
    private fun applyImageCacheLimit(mb: Int) {
        val bytes = ImageCacheLimit.toBytes(mb)
        val swap = synchronized(imageLoaderLock) {
            if (bytes == diskCacheMaxBytes && enforcedDiskCacheBytes == bytes) return
            diskCacheMaxBytes = bytes
            val previous = installedImageLoader
            if (previous == null) {
                enforceDiskCacheLimit(bytes)
                return
            }
            val previousClient = imageHttpClient
            previous.shutdown()
            closeCoilDiskCache(previous.diskCache)
            enforceDiskCacheLimit(bytes)
            val replacement = buildImageLoader(bytes)
            installedImageLoader = replacement
            ImageLoaderSwap(previousClient, replacement)
        }
        closeImageHttpClient(swap.previousClient)
        Coil.setImageLoader(swap.replacement)
    }

    private fun buildImageLoader(maxBytes: Long): ImageLoader {
        val client = OkHttpClient.Builder()
            .connectTimeout(15, TimeUnit.SECONDS)
            .readTimeout(20, TimeUnit.SECONDS)
            .retryOnConnectionFailure(true)
            .connectionPool(okhttp3.ConnectionPool(8, 5, TimeUnit.MINUTES))
            .build()
        imageHttpClient = client
        val lowRam = try {
            val am = getSystemService(ACTIVITY_SERVICE) as? android.app.ActivityManager
            am?.isLowRamDevice == true
        } catch (_: Exception) {
            false
        }
        return ImageLoader.Builder(this)
            .okHttpClient(client)
            .memoryCache {
                MemoryCache.Builder(this)
                    .maxSizePercent(if (lowRam) 0.15 else 0.22)
                    .build()
            }
            .diskCache {
                DiskCache.Builder()
                    .directory(cacheDir.resolve("image_cache"))
                    .maxSizeBytes(maxBytes)
                    .build()
            }
            // No crossfade on cold path — less GPU/animation work during first flings
            .crossfade(false)
            .allowHardware(true)
            .bitmapConfig(
                if (lowRam) android.graphics.Bitmap.Config.RGB_565
                else android.graphics.Bitmap.Config.ARGB_8888
            )
            .respectCacheHeaders(false)
            .components {
                if (Build.VERSION.SDK_INT >= 28) {
                    add(ImageDecoderDecoder.Factory())
                } else {
                    add(GifDecoder.Factory())
                }
            }
            .build()
    }

    /**
     * Open the image directory at [maxBytes] and close it. Coil trims the oldest
     * files inside DiskLruCache.close, which is not on the public DiskCache API.
     */
    @OptIn(ExperimentalCoilApi::class)
    private fun enforceDiskCacheLimit(maxBytes: Long) {
        if (enforcedDiskCacheBytes == maxBytes) return
        val cache = DiskCache.Builder()
            .directory(cacheDir.resolve("image_cache"))
            .maxSizeBytes(maxBytes)
            .build()
        try {
            cache.size
        } finally {
            closeCoilDiskCache(cache)
            enforcedDiskCacheBytes = maxBytes
        }
    }

    @OptIn(ExperimentalCoilApi::class)
    private fun closeCoilDiskCache(cache: DiskCache?) {
        if (cache == null) return
        runCatching {
            val lru = cache.javaClass.getDeclaredField("cache").apply { isAccessible = true }.get(cache)
            lru.javaClass.getMethod("close").invoke(lru)
        }
    }

    private fun closeImageHttpClient(client: OkHttpClient?) {
        if (client == null) return
        runCatching {
            client.connectionPool.evictAll()
            client.dispatcher.executorService.shutdown()
        }
    }
}

private class ImageLoaderSwap(
    val previousClient: OkHttpClient?,
    val replacement: ImageLoader
)
