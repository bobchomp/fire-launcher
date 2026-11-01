package com.bobchomp.firelauncher

import android.content.Context
import android.graphics.Bitmap
import android.util.LruCache
import androidx.compose.ui.graphics.ImageBitmap
import androidx.compose.ui.graphics.asImageBitmap
import androidx.core.graphics.drawable.toBitmap
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext

/** A decoded banner (16:9) or, when an app has none, its square icon. */
class AppArt(val image: ImageBitmap, val isBanner: Boolean, val byteCount: Int)

/**
 * Loads app banners lazily, sized for the grid cell, with a small in-memory cache.
 * Each card asks for its own art, so startup only pays for the cards on screen.
 */
class AppArtLoader(context: Context) {

    private val pm = context.packageManager

    // 1/8 of the heap, capped at 24 MB: about 100 banners at 320x180.
    private val cache = object : LruCache<String, AppArt>(
        (Runtime.getRuntime().maxMemory() / 8).coerceAtMost(24L * 1024 * 1024).toInt(),
    ) {
        override fun sizeOf(key: String, value: AppArt) = value.byteCount
    }

    fun cached(app: LauncherApp): AppArt? = cache.get(app.key)

    suspend fun load(app: LauncherApp): AppArt? {
        cache.get(app.key)?.let { return it }
        return withContext(Dispatchers.IO) {
            runCatching { decode(app) }.getOrNull()?.also { cache.put(app.key, it) }
        }
    }

    /** Drops cached art for a package after it is updated or removed. */
    fun evict(packageName: String) {
        cache.snapshot().keys
            .filter { it.startsWith("$packageName/") }
            .forEach { cache.remove(it) }
    }

    private fun decode(app: LauncherApp): AppArt {
        // ActivityInfo.loadBanner falls back to the application's banner.
        val banner = app.activityInfo.loadBanner(pm)
        val bitmap: Bitmap
        val isBanner: Boolean
        if (banner != null) {
            bitmap = banner.toBitmap(BANNER_WIDTH_PX, BANNER_HEIGHT_PX)
            isBanner = true
        } else {
            bitmap = app.activityInfo.loadIcon(pm).toBitmap(ICON_SIZE_PX, ICON_SIZE_PX)
            isBanner = false
        }
        return AppArt(bitmap.asImageBitmap(), isBanner, bitmap.byteCount)
    }

    private companion object {
        // Native Android TV banner size; matches a grid cell on a 1080p xhdpi Fire TV.
        const val BANNER_WIDTH_PX = 320
        const val BANNER_HEIGHT_PX = 180
        const val ICON_SIZE_PX = 96
    }
}
