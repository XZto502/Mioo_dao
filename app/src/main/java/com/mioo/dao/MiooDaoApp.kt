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
import com.mioo.dao.data.local.AppDatabase
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
}

@HiltAndroidApp
class MiooDaoApp : Application(), ImageLoaderFactory, Configuration.Provider {

    @Inject
    lateinit var okHttpClient: OkHttpClient

    // Room / subscription wiring is deferred — avoid pulling the full graph onto the
    // first application inject tick when only ImageLoader + WorkManager are needed.

    @Inject
    lateinit var workerFactory: HiltWorkerFactory

    private val appScope = CoroutineScope(SupervisorJob() + Dispatchers.Default)

    override val workManagerConfiguration: Configuration
        get() = Configuration.Builder()
            .setWorkerFactory(workerFactory)
            .build()

    override fun onCreate() {
        super.onCreate()

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
