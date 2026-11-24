package com.gendev.shorts.data.cache

import android.content.Context
import android.webkit.CookieManager
import android.webkit.WebStorage
import androidx.media3.common.util.UnstableApi
import androidx.media3.database.StandaloneDatabaseProvider
import androidx.media3.datasource.cache.LeastRecentlyUsedCacheEvictor
import androidx.media3.datasource.cache.SimpleCache
import coil.imageLoader
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import java.io.File

@OptIn(UnstableApi::class)
class MediaCacheManager(context: Context) {

    private val appContext = context.applicationContext
    private val mediaCacheDir = File(appContext.cacheDir, "gendev_media_cache")

    @Volatile
    private var simpleCache: SimpleCache? = null

    fun getOrCreateSimpleCache(): SimpleCache {
        return simpleCache ?: synchronized(this) {
            simpleCache ?: run {
                if (!mediaCacheDir.exists()) mediaCacheDir.mkdirs()
                val evictor = LeastRecentlyUsedCacheEvictor(MAX_CACHE_BYTES)
                val dbProvider = StandaloneDatabaseProvider(appContext)
                SimpleCache(mediaCacheDir, evictor, dbProvider).also { simpleCache = it }
            }
        }
    }

    suspend fun getTemporaryCacheSizeBytes(): Long = withContext(Dispatchers.IO) {
        calculateDirectorySize(appContext.cacheDir)
    }

    suspend fun clearAllTemporaryCache(): Long = withContext(Dispatchers.IO) {
        synchronized(this@MediaCacheManager) {
            runCatching {
                simpleCache?.release()
                simpleCache = null
            }
            mediaCacheDir.deleteRecursively()
        }

        // Clear Coil image cache
        runCatching {
            appContext.imageLoader.memoryCache?.clear()
            appContext.imageLoader.diskCache?.clear()
        }

        // Clear any temporary WebView cookies/storage
        withContext(Dispatchers.Main) {
            runCatching {
                CookieManager.getInstance().removeAllCookies(null)
                CookieManager.getInstance().flush()
                WebStorage.getInstance().deleteAllData()
            }
        }

        // Delete remaining temporary files in cacheDir
        appContext.cacheDir.listFiles()?.forEach { child ->
            runCatching { child.deleteRecursively() }
        }

        calculateDirectorySize(appContext.cacheDir)
    }

    fun release() {
        synchronized(this) {
            runCatching {
                simpleCache?.release()
                simpleCache = null
            }
        }
    }

    private fun calculateDirectorySize(dir: File): Long {
        if (!dir.exists()) return 0L
        var total = 0L
        dir.listFiles()?.forEach { file ->
            total += if (file.isDirectory) calculateDirectorySize(file) else file.length()
        }
        return total
    }

    companion object {
        private const val MAX_CACHE_BYTES = 50L * 1024L * 1024L // 50 MB low-RAM cap
    }
}
