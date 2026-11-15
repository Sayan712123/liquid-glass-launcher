package com.kyant.backdrop.catalog.launcher

import android.content.ComponentName
import android.content.Context
import android.content.Intent
import android.content.pm.PackageManager
import android.graphics.Bitmap
import android.graphics.drawable.Drawable
import androidx.compose.ui.graphics.ImageBitmap
import androidx.compose.ui.graphics.asImageBitmap
import androidx.core.graphics.drawable.toBitmap
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext

data class AppEntry(
    val packageName: String,
    val label: String,
    val icon: ImageBitmap?
)

/**
 * Reads the installed launchable apps and their real icons from PackageManager.
 *
 * Icons are decoded once on a background thread and kept in a small bounded
 * cache, because a 4 GB device cannot hold 200 full-size bitmaps. They are
 * downscaled to the grid icon size before being turned into ImageBitmaps, which
 * is the single biggest memory win on a low-RAM phone.
 */
class AppRepository(private val context: Context) {

    private val pm: PackageManager = context.packageManager
    private val iconCache = LinkedHashMap<String, ImageBitmap>()
    private val maxCachedIcons = 96

    suspend fun loadApps(iconSizePx: Int): List<AppEntry> = withContext(Dispatchers.IO) {
        val intent = Intent(Intent.ACTION_MAIN).addCategory(Intent.CATEGORY_LAUNCHER)
        val resolved = pm.queryIntentActivities(intent, 0)

        resolved
            .asSequence()
            .filter { it.activityInfo.packageName != context.packageName }
            .map { ri ->
                val pkg = ri.activityInfo.packageName
                AppEntry(
                    packageName = pkg,
                    label = ri.loadLabel(pm).toString(),
                    icon = loadIcon(pkg, ri.activityInfo.name, iconSizePx)
                )
            }
            .distinctBy { it.packageName }
            .sortedBy { it.label.lowercase() }
            .toList()
    }

    private fun loadIcon(packageName: String, activityName: String, sizePx: Int): ImageBitmap? {
        iconCache[packageName]?.let { return it }
        return try {
            val drawable: Drawable = pm.getActivityIcon(ComponentName(packageName, activityName))
            val bitmap: Bitmap = drawable.toBitmap(sizePx, sizePx)
            val image = bitmap.asImageBitmap()
            if (iconCache.size >= maxCachedIcons) {
                iconCache.keys.firstOrNull()?.let { iconCache.remove(it) }
            }
            iconCache[packageName] = image
            image
        } catch (_: Throwable) {
            null
        }
    }

    fun launch(packageName: String) {
        val intent = pm.getLaunchIntentForPackage(packageName) ?: return
        intent.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_RESET_TASK_IF_NEEDED)
        try {
            context.startActivity(intent)
        } catch (_: Throwable) {
        }
    }
}
