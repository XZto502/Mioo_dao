package com.mioo.dao

import android.app.Application
import android.os.Looper
import androidx.compose.ui.graphics.Color
import androidx.hilt.work.HiltWorkerFactory
import androidx.work.Configuration
import coil.ImageLoader
import coil.ImageLoaderFactory
import coil.disk.DiskCache
import coil.memory.MemoryCache
import com.mioo.dao.notification.SubscriptionScheduler
import com.mioo.dao.data.repository.SettingsRepository
import com.mioo.dao.ui.components.HtmlParseCache
import dagger.hilt.android.HiltAndroidApp
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.flow.distinctUntilChanged
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.launch
import okhttp3.OkHttpClient
import javax.inject.Inject

@HiltAndroidApp
class MiooDaoApp : Application(), ImageLoaderFactory, Configuration.Provider {

    @Inject
    lateinit var okHttpClient: OkHttpClient

    @Inject
    lateinit var database: com.mioo.dao.data.local.AppDatabase

    @Inject
    lateinit var workerFactory: HiltWorkerFactory

    @Inject
    lateinit var settingsRepository: SettingsRepository

    @Inject
    lateinit var subscriptionScheduler: SubscriptionScheduler

    private val appScope = CoroutineScope(SupervisorJob() + Dispatchers.Default)

    override val workManagerConfiguration: Configuration
        get() = Configuration.Builder()
            .setWorkerFactory(workerFactory)
            .build()

    override fun onCreate() {
        super.onCreate()

        // Hilt injects after super.onCreate for @HiltAndroidApp — warm on next loop tick
        // so injection fields are ready without blocking first frame.
        Looper.myQueue().addIdleHandler {
            warmCriticalPaths()
            syncSubscriptionSchedule()
            false
        }
    }

    private fun syncSubscriptionSchedule() {
        appScope.launch {
            if (!::subscriptionScheduler.isInitialized || !::settingsRepository.isInitialized) return@launch
            settingsRepository.settings
                .map { it.subscriptionNotificationsEnabled to it.notificationIntervalMinutes }
                .distinctUntilChanged()
                .collect { (enabled, interval) ->
                    subscriptionScheduler.apply(enabled, interval)
                }
        }
    }

    private fun warmCriticalPaths() {
        appScope.launch {
            // Room
            runCatching {
                if (::database.isInitialized) {
                    database.openHelper.writableDatabase
                }
            }
            // Force ImageLoader construction + HTML parser JIT before first scroll
            runCatching {
                val loader = coil.Coil.imageLoader(this@MiooDaoApp)
                // Touch memory cache
                loader.memoryCache
            }
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
    }

    override fun newImageLoader(): ImageLoader {
        val client = if (::okHttpClient.isInitialized) okHttpClient else OkHttpClient()
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
                    .maxSizeBytes(if (lowRam) 80L * 1024 * 1024 else 150L * 1024 * 1024)
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
            .build()
    }
}
